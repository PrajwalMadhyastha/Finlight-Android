package io.pm.finlight.domain.usecase

import androidx.room.withTransaction
import io.pm.finlight.data.model.MergedTransactionItem
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionTagCrossRef
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.data.db.entity.MergeRecord
import io.pm.finlight.data.db.entity.MergeType
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MergeTransactionsUseCase(
    private val transactionQueryDao: TransactionQueryDao,
    private val transactionWriteDao: TransactionWriteDao,
    private val transactionReimbursementDao: TransactionReimbursementDao,
    private val mergeRecordDao: MergeRecordDao,
    private val deletedSmsHashDao: DeletedSmsHashDao,
    private val db: AppDatabase,
) {
    /**
     * Automated / nudge merge of [childTxnId] into [parentTxnId].
     */
    suspend fun autoMerge(
        parentTxnId: Int,
        childTxnId: Int,
        childSmsBody: String? = null,
        childSmsDate: Long? = null,
    ) {
        var activeParentId = parentTxnId
        var parentTxn = transactionQueryDao.getTransactionByIdSync(activeParentId)
        val childTxn = transactionQueryDao.getTransactionByIdSync(childTxnId) ?: return

        if (parentTxn == null) {
            val timeWindowStart = childTxn.date - (3 * 60 * 60 * 1000L)
            val newParent =
                transactionQueryDao.findRecentTransactionForMerge(
                    merchant = childTxn.description,
                    accountId = childTxn.accountId,
                    transactionType = childTxn.transactionType,
                    timeWindowStart = timeWindowStart,
                    newTxnId = childTxnId,
                )
            if (newParent != null) {
                activeParentId = newParent.id
                parentTxn = newParent
            } else {
                return
            }
        }

        val finalParentTxn = parentTxn ?: return

        // Snapshot BEFORE any mutation so the merge is fully reversible
        mergeRecordDao.insert(
            createMergeRecord(
                parentTxnId = activeParentId,
                originalParentAmount = finalParentTxn.amount,
                originalParentDate = finalParentTxn.date,
                originalParentNotes = finalParentTxn.notes,
                childTxn = childTxn,
                mergeGroupId = "",
                mergeType = MergeType.AUTO,
            ),
        )

        transactionWriteDao.updateMergeDismissed(childTxnId, true)

        val newAmount = finalParentTxn.amount + childTxn.amount
        val newDate = maxOf(finalParentTxn.date, childTxn.date)

        val existingNotes = finalParentTxn.notes ?: ""
        val dateString =
            if (childSmsDate != null) {
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(childSmsDate))
            } else {
                Date(childTxn.date).toString()
            }

        var childNote =
            if (childSmsBody != null) {
                "Merged on $dateString:\n$childSmsBody"
            } else {
                "Merged Transaction: ${childTxn.amount} on $dateString"
            }

        if (!childTxn.notes.isNullOrBlank()) {
            childNote += "\n\n${childTxn.notes}"
        }

        val newNotes =
            if (existingNotes.isBlank()) {
                childNote
            } else {
                "$existingNotes\n\n$childNote"
            }

        transactionWriteDao.updateAmount(activeParentId, newAmount)
        transactionWriteDao.updateDate(activeParentId, newDate)
        transactionWriteDao.updateNotes(activeParentId, newNotes)

        childTxn.sourceSmsHash?.let { hash ->
            deletedSmsHashDao.insert(DeletedSmsHash(smsHash = hash))
        }

        transactionWriteDao.delete(childTxn)
    }

    /**
     * Operator overload delegating to [autoMerge].
     */
    suspend operator fun invoke(
        parentTxnId: Int,
        childTxnId: Int,
        childSmsBody: String? = null,
        childSmsDate: Long? = null,
    ) = autoMerge(parentTxnId, childTxnId, childSmsBody, childSmsDate)

    /**
     * Merges [anchorTxnId] with all [childTxnIds] into a single transaction.
     *
     * Algorithm:
     *  1. Anchor = transaction provided by the user (largest amount by default).
     *  2. Net amount = anchor_signed + sum(child_signed), where income = positive, expense = negative.
     *  3. Final type = "income" if net > 0, else "expense". Amount stored as absolute value.
     *  4. Date = most recent date across all transactions.
     *  5. Tags = union of all tags from anchor + all children.
     *  6. Notes = anchor's notes + appended block per child.
     *  7. One MergeRecord per child, all sharing the same UUID [mergeGroupId], type = "MANUAL".
     *
     * The entire operation is wrapped in a Room [withTransaction] for full atomicity.
     */
    suspend fun manualMerge(
        anchorTxnId: Int,
        childTxnIds: List<Int>,
    ) {
        db.withTransaction {
            val anchorTxn = transactionQueryDao.getTransactionByIdSync(anchorTxnId) ?: return@withTransaction
            val childTxns = childTxnIds.mapNotNull { transactionQueryDao.getTransactionByIdSync(it) }
            if (childTxns.isEmpty()) return@withTransaction

            val groupId = UUID.randomUUID().toString()
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

            // Snapshot parent state
            val originalParentAmount = anchorTxn.amount
            val originalParentDate = anchorTxn.date
            val originalParentNotes = anchorTxn.notes

            fun signedAmount(txn: Transaction): Double =
                if (txn.transactionType == TransactionType.INCOME) txn.amount else -txn.amount

            val anchorSigned = signedAmount(anchorTxn)
            val netSigned = anchorSigned + childTxns.sumOf { signedAmount(it) }
            val hasReimbursements = transactionReimbursementDao.getReimbursementsCountSync(anchorTxnId) > 0

            val finalType =
                if (hasReimbursements) {
                    TransactionType.EXPENSE
                } else if (netSigned >= 0.0) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }

            val finalAmount = kotlin.math.abs(netSigned)
            val finalDate = (childTxns.map { it.date } + anchorTxn.date).max()

            // Union all tags
            val anchorTags = transactionQueryDao.getTagsForTransactionSimple(anchorTxnId).map { it.id }.toMutableSet()
            for (childTxn in childTxns) {
                val childTagIds = transactionQueryDao.getTagsForTransactionSimple(childTxn.id).map { it.id }
                anchorTags.addAll(childTagIds)
            }
            transactionWriteDao.clearTagsForTransaction(anchorTxnId)
            if (anchorTags.isNotEmpty()) {
                transactionWriteDao.addTagsToTransaction(
                    anchorTags.map { tagId -> TransactionTagCrossRef(transactionId = anchorTxnId, tagId = tagId) },
                )
            }

            // Build appended notes
            var notes = anchorTxn.notes ?: ""
            for (childTxn in childTxns) {
                val dateStr = sdf.format(Date(childTxn.date))
                val sign = if (childTxn.transactionType == TransactionType.INCOME) "+" else "-"
                var childNote = "[Merged] ${childTxn.description} ($sign₹${"%.2f".format(childTxn.amount)}) · $dateStr"
                if (!childTxn.notes.isNullOrBlank()) {
                    childNote += "\n\n${childTxn.notes}"
                }
                notes = if (notes.isBlank()) childNote else "$notes\n\n$childNote"
            }

            // Persist one MergeRecord per child
            for (childTxn in childTxns) {
                mergeRecordDao.insert(
                    createMergeRecord(
                        parentTxnId = anchorTxnId,
                        originalParentAmount = originalParentAmount,
                        originalParentDate = originalParentDate,
                        originalParentNotes = originalParentNotes,
                        childTxn = childTxn,
                        mergeGroupId = groupId,
                        mergeType = MergeType.MANUAL,
                    ),
                )
                // Prevent SMS re-processing of merged children
                childTxn.sourceSmsHash?.let { hash ->
                    deletedSmsHashDao.insert(DeletedSmsHash(smsHash = hash))
                }
                transactionWriteDao.delete(childTxn)
            }

            // Update the anchor
            transactionWriteDao.updateAmount(anchorTxnId, finalAmount)
            transactionWriteDao.updateDate(anchorTxnId, finalDate)
            transactionWriteDao.updateNotes(anchorTxnId, notes)
            if (anchorTxn.transactionType != finalType) {
                transactionWriteDao.updateTransactionType(anchorTxnId, finalType)
            }
        }
    }

    /**
     * Builds the per-account contribution breakdown for a merged transaction.
     * Returns an empty list if the transaction has no merge records.
     */
    suspend fun getMergedTransactionBreakdown(parentTxnId: Int): List<MergedTransactionItem> {
        val records = mergeRecordDao.getAllForParentAnyType(parentTxnId)
        if (records.isEmpty()) return emptyList()

        val anchorTxn = transactionQueryDao.getTransactionByIdSync(parentTxnId) ?: return emptyList()
        val anchorAccount = db.accountDao().getAccountByIdBlocking(anchorTxn.accountId)

        val entries = mutableListOf<MergedTransactionItem>()

        fun signedAmount(
            type: TransactionType,
            amount: Double,
        ): Double =
            if (type == TransactionType.INCOME) amount else -amount

        val currentSigned = signedAmount(anchorTxn.transactionType, anchorTxn.amount)
        val childrenSigned = records.sumOf { signedAmount(it.childTransactionType, it.childAmount) }
        val anchorSigned = currentSigned - childrenSigned

        val anchorOriginalType =
            if (anchorSigned > 0.0) {
                TransactionType.INCOME
            } else if (anchorSigned < 0.0) {
                TransactionType.EXPENSE
            } else {
                anchorTxn.transactionType
            }

        val firstRecord = records.first()
        val anchorOriginalAmount = firstRecord.originalParentAmount
        entries.add(
            MergedTransactionItem(
                accountId = anchorTxn.accountId,
                accountName = anchorAccount?.name ?: "Unknown",
                amount = anchorOriginalAmount,
                transactionType = anchorOriginalType,
                isAnchor = true,
                description = anchorTxn.description,
                date = firstRecord.originalParentDate,
            ),
        )

        for (r in records) {
            val childAccount = db.accountDao().getAccountByIdBlocking(r.childAccountId)
            entries.add(
                MergedTransactionItem(
                    accountId = r.childAccountId,
                    accountName = childAccount?.name ?: "Unknown",
                    amount = r.childAmount,
                    transactionType = r.childTransactionType,
                    isAnchor = false,
                    description = r.childDescription,
                    date = r.childDate,
                ),
            )
        }

        return entries
    }

    /**
     * Fully reverses the most recent merge for [parentTxnId].
     *
     * For AUTO merges (legacy 1-to-1): restores parent + re-inserts the single child.
     * For MANUAL merges (N-to-1): restores parent to its pre-merge state + re-inserts ALL children
     * using the shared [MergeRecord.mergeGroupId].
     */
    suspend fun unmerge(parentTxnId: Int) {
        val record = mergeRecordDao.getForParentSync(parentTxnId) ?: return

        if (record.mergeType == MergeType.MANUAL && record.mergeGroupId.isNotBlank()) {
            // MANUAL path: restore all N children
            val groupId = record.mergeGroupId
            require(groupId.isNotBlank()) {
                "Manual merge groupId must not be blank for parentTxnId=$parentTxnId"
            }
            val allRecords = mergeRecordDao.getAllForGroup(groupId)
            if (allRecords.isEmpty()) return

            db.withTransaction {
                val currentParent = transactionQueryDao.getTransactionByIdSync(parentTxnId) ?: return@withTransaction

                fun signedAmount(
                    type: TransactionType,
                    amount: Double,
                ): Double =
                    if (type == TransactionType.INCOME) amount else -amount

                val currentSigned = signedAmount(currentParent.transactionType, currentParent.amount)
                val childrenSigned = allRecords.sumOf { signedAmount(it.childTransactionType, it.childAmount) }
                val newSigned = currentSigned - childrenSigned
                val hasReimbursements = transactionReimbursementDao.getReimbursementsCountSync(parentTxnId) > 0

                val finalType =
                    if (hasReimbursements) {
                        TransactionType.EXPENSE
                    } else if (newSigned > 0.0) {
                        TransactionType.INCOME
                    } else if (newSigned < 0.0) {
                        TransactionType.EXPENSE
                    } else {
                        currentParent.transactionType
                    }

                val finalAmount = kotlin.math.abs(newSigned)

                val first = allRecords.first()
                transactionWriteDao.updateAmount(parentTxnId, finalAmount)
                if (currentParent.transactionType != finalType) {
                    transactionWriteDao.updateTransactionType(parentTxnId, finalType)
                }
                transactionWriteDao.updateDate(parentTxnId, first.originalParentDate)
                transactionWriteDao.updateNotes(parentTxnId, first.originalParentNotes)

                for (r in allRecords) {
                    val restoredChild = restoreTransactionFromMergeRecord(r)
                    transactionWriteDao.insert(restoredChild)

                    r.childSourceSmsHash?.let { hash ->
                        deletedSmsHashDao.deleteByHash(hash)
                    }
                }

                mergeRecordDao.deleteByGroupId(groupId)
            }
        } else {
            // AUTO path (chained 1-to-1): restore ALL children for this parent
            val allAutoRecords =
                mergeRecordDao.getAllForParentSync(parentTxnId)
                    .filter { it.mergeType == MergeType.AUTO }
            if (allAutoRecords.isEmpty()) return

            db.withTransaction {
                val currentParent = transactionQueryDao.getTransactionByIdSync(parentTxnId) ?: return@withTransaction

                val totalMergedAmount = allAutoRecords.sumOf { it.childAmount }
                val newAmount = currentParent.amount - totalMergedAmount

                val first = allAutoRecords.first()
                transactionWriteDao.updateAmount(parentTxnId, newAmount)
                transactionWriteDao.updateDate(parentTxnId, first.originalParentDate)
                transactionWriteDao.updateNotes(parentTxnId, first.originalParentNotes)

                for (r in allAutoRecords) {
                    val restoredChild = restoreTransactionFromMergeRecord(r)
                    transactionWriteDao.insert(restoredChild)

                    r.childSourceSmsHash?.let { hash ->
                        deletedSmsHashDao.deleteByHash(hash)
                    }

                    mergeRecordDao.deleteById(r.id)
                }
            }
        }
    }

    /**
     * Observes whether a merge snapshot exists for the given parent transaction.
     */
    fun observeMergeRecord(parentTxnId: Int): Flow<MergeRecord?> =
        mergeRecordDao.observeForParent(parentTxnId)

    private fun createMergeRecord(
        parentTxnId: Int,
        originalParentAmount: Double,
        originalParentDate: Long,
        originalParentNotes: String?,
        childTxn: Transaction,
        mergeGroupId: String,
        mergeType: MergeType,
    ): MergeRecord {
        return MergeRecord(
            parentTxnId = parentTxnId,
            originalParentAmount = originalParentAmount,
            originalParentDate = originalParentDate,
            originalParentNotes = originalParentNotes,
            childDescription = childTxn.description,
            childAmount = childTxn.amount,
            childDate = childTxn.date,
            childAccountId = childTxn.accountId,
            childCategoryId = childTxn.categoryId,
            childTransactionType = childTxn.transactionType,
            childSource = childTxn.source,
            childNotes = childTxn.notes,
            childSourceSmsId = childTxn.sourceSmsId,
            childSourceSmsHash = childTxn.sourceSmsHash,
            childSmsSignature = childTxn.smsSignature,
            childOriginalDescription = childTxn.originalDescription,
            childOriginalAmount = childTxn.originalAmount,
            childCurrencyCode = childTxn.currencyCode,
            childConversionRate = childTxn.conversionRate,
            mergeGroupId = mergeGroupId,
            mergeType = mergeType,
        )
    }

    private fun restoreTransactionFromMergeRecord(r: MergeRecord): Transaction {
        return Transaction(
            description = r.childDescription,
            amount = r.childAmount,
            date = r.childDate,
            accountId = r.childAccountId,
            categoryId = r.childCategoryId,
            transactionType = r.childTransactionType,
            source = r.childSource,
            notes = r.childNotes,
            sourceSmsId = r.childSourceSmsId,
            sourceSmsHash = r.childSourceSmsHash,
            smsSignature = r.childSmsSignature,
            originalDescription = r.childOriginalDescription,
            originalAmount = r.childOriginalAmount,
            currencyCode = r.childCurrencyCode,
            conversionRate = r.childConversionRate,
            mergeDismissed = false,
        )
    }
}
