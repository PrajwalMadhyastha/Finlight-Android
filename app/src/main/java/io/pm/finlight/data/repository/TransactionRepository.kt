// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TransactionRepository.kt
// REASON: FEATURE (Manual Merge) - Added `manualMergeTransactions()` for atomic
// N-to-1 user-initiated transaction merges. Updated `unmergeTransactions()` to
// handle both the legacy 1-to-1 AUTO path and the new N-to-1 MANUAL path.
// =================================================================================
package io.pm.finlight

import android.util.Log
import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.data.model.MergedTransactionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.util.Locale
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.data.db.entity.MergeRecord

class TransactionRepository(
    private val transactionWriteDao: TransactionWriteDao,
    private val transactionQueryDao: TransactionQueryDao,
    private val transactionAnalyticsDao: TransactionAnalyticsDao,
    private val transactionReimbursementDao: TransactionReimbursementDao,
    private val deletedSmsHashDao: DeletedSmsHashDao,
    private val mergeRecordDao: MergeRecordDao,
    private val db: AppDatabase,
) {
    @Deprecated("Use domain DAO constructor", level = DeprecationLevel.WARNING)
    constructor(
        transactionDao: TransactionDao,
        deletedSmsHashDao: DeletedSmsHashDao,
        mergeRecordDao: MergeRecordDao,
        db: AppDatabase,
    ) : this(
        transactionWriteDao = transactionDao,
        transactionQueryDao = transactionDao,
        transactionAnalyticsDao = transactionDao,
        transactionReimbursementDao = transactionDao,
        deletedSmsHashDao = deletedSmsHashDao,
        mergeRecordDao = mergeRecordDao,
        db = db,
    )

    // --- NEW: Function for Spending Velocity feature ---
    suspend fun getTotalExpensesSince(startDate: Long): Double {
        return transactionAnalyticsDao.getTotalExpensesSince(startDate) ?: 0.0
    }

    // --- NEW: Function to search for merchant predictions ---
    fun searchMerchants(query: String): Flow<List<MerchantPrediction>> {
        return transactionQueryDao.searchMerchants(query)
    }

    suspend fun deleteByIds(transactionIds: List<Int>) {
        transactionWriteDao.deleteByIds(transactionIds)
    }

    fun getTransactionWithSplits(transactionId: Int): Flow<TransactionWithSplits?> {
        return transactionQueryDao.getTransactionWithSplits(transactionId)
    }

    val allTransactions: Flow<List<TransactionDetails>> =
        transactionQueryDao.getAllTransactions()
            .onEach { transactions ->
                Log.d(
                    "TransactionFlowDebug",
                    "Repository Flow Emitted. Count: ${transactions.size}. Newest: ${transactions.firstOrNull()?.transaction?.description}",
                )
            }

    fun getFirstTransactionDate(): Flow<Long?> {
        return transactionQueryDao.getFirstTransactionDate()
    }

    fun getFinancialSummaryForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<FinancialSummary?> {
        return transactionAnalyticsDao.getFinancialSummaryForRangeFlow(startDate, endDate)
    }

    fun getTopSpendingCategoriesForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<CategorySpending?> {
        return transactionAnalyticsDao.getTopSpendingCategoriesForRangeFlow(startDate, endDate)
    }

    fun getIncomeTransactionsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getIncomeTransactionsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getIncomeByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<CategorySpending>> {
        return transactionAnalyticsDao.getIncomeByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getSpendingByMerchantForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<MerchantSpendingSummary>> {
        return transactionAnalyticsDao.getSpendingByMerchantForMonth(startDate, endDate, keyword, accountId, categoryId, transactionType)
    }

    suspend fun addImageToTransaction(
        transactionId: Int,
        imageUri: String,
    ) {
        val transactionImage = TransactionImage(transactionId = transactionId, imageUri = imageUri)
        transactionWriteDao.insertImage(transactionImage)
    }

    suspend fun deleteImage(transactionImage: TransactionImage) {
        transactionWriteDao.deleteImage(transactionImage)
    }

    fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>> {
        return transactionQueryDao.getImagesForTransaction(transactionId)
    }

    suspend fun updateDescription(
        id: Int,
        description: String,
    ) = transactionWriteDao.updateDescription(id, description)

    suspend fun updateAmount(
        id: Int,
        amount: Double,
    ) = transactionWriteDao.updateAmount(id, amount)

    suspend fun updateManualAmountEdit(
        id: Int,
        amount: Double,
    ) = transactionWriteDao.updateManualAmountEdit(id, amount)

    suspend fun updateNotes(
        id: Int,
        notes: String?,
    ) = transactionWriteDao.updateNotes(id, notes)

    suspend fun updateCategoryId(
        id: Int,
        categoryId: Int?,
    ) = transactionWriteDao.updateCategoryId(id, categoryId)

    suspend fun updateAccountId(
        id: Int,
        accountId: Int,
    ) = transactionWriteDao.updateAccountId(id, accountId)

    suspend fun updateDate(
        id: Int,
        date: Long,
    ) = transactionWriteDao.updateDate(id, date)

    suspend fun updateExclusionStatus(
        id: Int,
        isExcluded: Boolean,
    ) = transactionWriteDao.updateExclusionStatus(id, isExcluded)

    // --- NEW: Function to update transaction type ---
    suspend fun updateTransactionType(
        id: Int,
        transactionType: TransactionType,
    ) {
        transactionWriteDao.updateTransactionType(id, transactionType)
    }

    suspend fun clearReviewFlag(id: Int) {
        transactionWriteDao.clearReviewFlag(id)
    }

    fun getTransactionDetailsById(id: Int): Flow<TransactionDetails?> {
        return transactionQueryDao.getTransactionDetailsById(id)
    }

    val recentTransactions: Flow<List<TransactionDetails>> = transactionQueryDao.getRecentTransactionDetails()

    fun getAllSmsHashes(): Flow<List<String>> {
        return transactionQueryDao.getAllSmsHashes()
    }

    fun getTransactionsForAccountDetails(accountId: Int): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getTransactionsForAccountDetails(accountId)
    }

    fun getTransactionDetailsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getTransactionDetailsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getAllTransactionsForRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<Transaction>> {
        return transactionQueryDao.getAllTransactionsForRange(startDate, endDate)
    }

    fun getTransactionById(id: Int): Flow<Transaction?> {
        return transactionQueryDao.getTransactionById(id)
    }

    suspend fun getTransactionSync(id: Int): Transaction? {
        return transactionQueryDao.getTransactionByIdSync(id)
    }

    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>> {
        return transactionQueryDao.getTransactionsForAccount(accountId)
    }

    fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<CategorySpending>> {
        return transactionAnalyticsDao.getSpendingByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId, transactionType)
    }

    fun getMonthlyTrends(startDate: Long): Flow<List<MonthlyTrend>> {
        return transactionAnalyticsDao.getMonthlyTrends(startDate)
    }

    suspend fun countTransactionsForCategory(categoryId: Int): Int {
        return transactionQueryDao.countTransactionsForCategory(categoryId)
    }

    fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>> {
        return transactionQueryDao.getTagsForTransaction(transactionId)
    }

    suspend fun getTagsForTransactionSimple(transactionId: Int): List<Tag> {
        return transactionQueryDao.getTagsForTransactionSimple(transactionId)
    }

    suspend fun updateTagsForTransaction(
        transactionId: Int,
        tags: Set<Tag>,
    ) {
        transactionWriteDao.clearTagsForTransaction(transactionId)
        if (tags.isNotEmpty()) {
            val crossRefs =
                tags.map { tag ->
                    TransactionTagCrossRef(transactionId = transactionId, tagId = tag.id)
                }
            transactionWriteDao.addTagsToTransaction(crossRefs)
        }
    }

    suspend fun insertTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    ): Long {
        val transactionId = transactionWriteDao.insert(transaction)
        if (tags.isNotEmpty()) {
            val crossRefs =
                tags.map { tag ->
                    TransactionTagCrossRef(transactionId = transactionId.toInt(), tagId = tag.id)
                }
            transactionWriteDao.addTagsToTransaction(crossRefs)
        }
        return transactionId
    }

    suspend fun updateTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    ) {
        transactionWriteDao.update(transaction)
        transactionWriteDao.clearTagsForTransaction(transaction.id)
        if (tags.isNotEmpty()) {
            val crossRefs =
                tags.map { tag ->
                    TransactionTagCrossRef(transactionId = transaction.id, tagId = tag.id)
                }
            transactionWriteDao.addTagsToTransaction(crossRefs)
        }
    }

    suspend fun insertTransactionWithTagsAndImages(
        transaction: Transaction,
        tags: Set<Tag>,
        imagePaths: List<String>,
    ): Long {
        val newTransactionId = transactionWriteDao.insert(transaction)
        if (tags.isNotEmpty()) {
            val crossRefs =
                tags.map { tag ->
                    TransactionTagCrossRef(transactionId = newTransactionId.toInt(), tagId = tag.id)
                }
            transactionWriteDao.addTagsToTransaction(crossRefs)
        }
        imagePaths.forEach { path ->
            val imageEntity =
                TransactionImage(
                    transactionId = newTransactionId.toInt(),
                    imageUri = path,
                )
            transactionWriteDao.insertImage(imageEntity)
        }
        return newTransactionId
    }

    suspend fun delete(transaction: Transaction) {
        transactionWriteDao.delete(transaction)
    }

    suspend fun setSmsHash(
        transactionId: Int,
        smsHash: String,
    ) {
        transactionWriteDao.setSmsHash(transactionId, smsHash)
    }

    fun getTransactionCountForMerchant(description: String): Flow<Int> {
        return transactionQueryDao.getTransactionCountForMerchant(description)
    }

    suspend fun findSimilarTransactions(
        description: String,
        excludeId: Int,
    ): List<Transaction> {
        return transactionQueryDao.findSimilarTransactions(description, excludeId)
    }

    /** Returns all distinct [Transaction.originalDescription] values for cross-account nudge scanning. */
    suspend fun getDistinctOriginalDescriptions(): List<String> = transactionQueryDao.getDistinctOriginalDescriptions()

    /** Returns IDs of all transactions sharing the given [originalDesc] (case-insensitive). */
    suspend fun getTransactionIdsByOriginalDescription(originalDesc: String): List<Int> =
        transactionQueryDao.getTransactionIdsByOriginalDescription(originalDesc)

    suspend fun updateCategoryForIds(
        ids: List<Int>,
        categoryId: Int,
    ) {
        transactionWriteDao.updateCategoryForIds(ids, categoryId)
    }

    suspend fun updateDescriptionForIds(
        ids: List<Int>,
        newDescription: String,
    ) {
        transactionWriteDao.updateDescriptionForIds(ids, newDescription)
    }

    fun getDailySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<DailyTotal>> {
        return transactionAnalyticsDao.getDailySpendingForDateRange(startDate, endDate)
    }

    // --- NEW: Functions for retrospective tagging ---
    suspend fun addTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ) {
        transactionWriteDao.addTagForDateRange(tagId, startDate, endDate)
    }

    suspend fun removeTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ) {
        transactionWriteDao.removeTagForDateRange(tagId, startDate, endDate)
    }

    // --- NEW: Get all transactions for a specific tag ---
    fun getTransactionsByTagId(tagId: Int): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getTransactionsByTagId(tagId)
    }

    // --- NEW: Expose the function to remove all tags ---
    suspend fun removeAllTransactionsForTag(tagId: Int) {
        transactionWriteDao.removeAllTransactionsForTag(tagId)
    }

    // --- NEW: Expose the quick fill query ---
    fun getRecentManualTransactions(limit: Int): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getRecentManualTransactions(limit)
    }

    // --- NEW: Reimbursement / Offset Feature ---

    fun getReimbursementsForExpense(expenseId: Int): Flow<List<TransactionDetails>> =
        transactionReimbursementDao.getReimbursementsForExpense(expenseId)

    fun getCandidateReimbursements(excludeExpenseId: Int): Flow<List<TransactionDetails>> =
        transactionReimbursementDao.getCandidateReimbursements(excludeExpenseId)

    fun getLinkedExpenseForReimbursement(incomeId: Int): Flow<TransactionDetails?> =
        transactionReimbursementDao.getLinkedExpenseForReimbursement(incomeId)

    /**
     * Links [incomeId] as a reimbursement for [expenseId]:
     * - Sets parentReimbursementId on the income and marks it as excluded from totals.
     * - Deducts the income amount from the expense, so budget/spending totals
     *   automatically reflect the net cost.
     */
    suspend fun linkReimbursement(
        incomeId: Int,
        expenseId: Int
    ) {
        val incomeTxn = transactionQueryDao.getTransactionByIdSync(incomeId) ?: return
        val expenseTxn = transactionQueryDao.getTransactionByIdSync(expenseId) ?: return
        transactionReimbursementDao.linkReimbursement(incomeId, expenseId)
        val newExpenseAmount = expenseTxn.amount - incomeTxn.amount
        transactionWriteDao.updateAmount(expenseId, newExpenseAmount)
    }

    /**
     * Removes the reimbursement link from [incomeId]:
     * - Clears parentReimbursementId and removes the excluded flag.
     * - Adds the income amount back onto the parent expense.
     */
    suspend fun unlinkReimbursement(incomeId: Int) {
        val incomeTxn = transactionQueryDao.getTransactionByIdSync(incomeId) ?: return
        val parentId = incomeTxn.parentReimbursementId ?: return
        val expenseTxn = transactionQueryDao.getTransactionByIdSync(parentId) ?: return
        transactionReimbursementDao.unlinkReimbursement(incomeId)
        val restoredExpenseAmount = expenseTxn.amount + incomeTxn.amount
        transactionWriteDao.updateAmount(parentId, restoredExpenseAmount)
    }

    // --- NEW: Smart Transaction Merge ---
    suspend fun findRecentTransactionForMerge(
        merchant: String,
        accountId: Int,
        transactionType: TransactionType,
        timeWindowStart: Long,
        newTxnId: Int
    ): Transaction? {
        return transactionQueryDao.findRecentTransactionForMerge(merchant, accountId, transactionType, timeWindowStart, newTxnId)
    }

    suspend fun dismissMerge(id: Int) {
        transactionWriteDao.updateMergeDismissed(id, true)
    }

    suspend fun mergeTransactions(
        parentTxnId: Int,
        childTxnId: Int,
        childSmsBody: String? = null,
        childSmsDate: Long? = null
    ) {
        var activeParentId = parentTxnId
        var parentTxn = transactionQueryDao.getTransactionByIdSync(activeParentId)
        val childTxn = transactionQueryDao.getTransactionByIdSync(childTxnId)

        if (childTxn == null) return

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

        // ── Snapshot BEFORE any mutation so the merge is fully reversible ────
        mergeRecordDao.insert(
            createMergeRecord(
                parentTxnId = activeParentId,
                originalParentAmount = finalParentTxn.amount,
                originalParentDate = finalParentTxn.date,
                originalParentNotes = finalParentTxn.notes,
                childTxn = childTxn,
                mergeGroupId = "",
                mergeType = "AUTO",
            )
        )

        transactionWriteDao.updateMergeDismissed(childTxnId, true)

        val newAmount = finalParentTxn.amount + childTxn.amount
        val newDate = maxOf(finalParentTxn.date, childTxn.date)

        val existingNotes = finalParentTxn.notes ?: ""
        val dateString =
            if (childSmsDate != null) {
                java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(childSmsDate))
            } else {
                java.util.Date(childTxn.date).toString()
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

    // --- FEATURE: Manual Transaction Merge ---

    private fun createMergeRecord(
        parentTxnId: Int,
        originalParentAmount: Double,
        originalParentDate: Long,
        originalParentNotes: String?,
        childTxn: Transaction,
        mergeGroupId: String,
        mergeType: String
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
    suspend fun manualMergeTransactions(
        anchorTxnId: Int,
        childTxnIds: List<Int>,
    ) {
        db.withTransaction {
            val anchorTxn = transactionQueryDao.getTransactionByIdSync(anchorTxnId) ?: return@withTransaction
            val childTxns = childTxnIds.mapNotNull { transactionQueryDao.getTransactionByIdSync(it) }
            if (childTxns.isEmpty()) return@withTransaction

            val groupId = java.util.UUID.randomUUID().toString()
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())

            // ── Snapshot parent state ─────────────────────────────────────────
            val originalParentAmount = anchorTxn.amount
            val originalParentDate = anchorTxn.date
            val originalParentNotes = anchorTxn.notes

            // ── Compute net amount using signed arithmetic ─────────────────
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

            val finalAmount = if (finalType == TransactionType.EXPENSE) -netSigned else netSigned

            // ── Compute most-recent date ──────────────────────────────────
            val finalDate = (childTxns.map { it.date } + anchorTxn.date).max()

            // ── Union all tags ──────────────────────────────────────────────
            val anchorTags = transactionQueryDao.getTagsForTransactionSimple(anchorTxnId).map { it.id }.toMutableSet()
            for (childTxn in childTxns) {
                val childTagIds = transactionQueryDao.getTagsForTransactionSimple(childTxn.id).map { it.id }
                anchorTags.addAll(childTagIds)
            }
            transactionWriteDao.clearTagsForTransaction(anchorTxnId)
            if (anchorTags.isNotEmpty()) {
                transactionWriteDao.addTagsToTransaction(
                    anchorTags.map { tagId -> TransactionTagCrossRef(transactionId = anchorTxnId, tagId = tagId) }
                )
            }

            // ── Build appended notes ──────────────────────────────────────
            // Each merged child is stamped with a structured prefix so the UI
            // can differentiate merged entries from hand-typed notes.
            var notes = anchorTxn.notes ?: ""
            for (childTxn in childTxns) {
                val dateStr = sdf.format(java.util.Date(childTxn.date))
                val sign = if (childTxn.transactionType == TransactionType.INCOME) "+" else "-"
                var childNote = "[Merged] ${childTxn.description} ($sign₹${"%.2f".format(childTxn.amount)}) · $dateStr"
                if (!childTxn.notes.isNullOrBlank()) {
                    childNote += "\n\n${childTxn.notes}"
                }
                notes = if (notes.isBlank()) childNote else "$notes\n\n$childNote"
            }

            // ── Persist one MergeRecord per child ──────────────────────────
            for (childTxn in childTxns) {
                mergeRecordDao.insert(
                    createMergeRecord(
                        parentTxnId = anchorTxnId,
                        originalParentAmount = originalParentAmount,
                        originalParentDate = originalParentDate,
                        originalParentNotes = originalParentNotes,
                        childTxn = childTxn,
                        mergeGroupId = groupId,
                        mergeType = "MANUAL",
                    )
                )
                // Prevent SMS re-processing of merged children
                childTxn.sourceSmsHash?.let { hash ->
                    deletedSmsHashDao.insert(DeletedSmsHash(smsHash = hash))
                }
                transactionWriteDao.delete(childTxn)
            }

            // ── Update the anchor ──────────────────────────────────────────
            transactionWriteDao.updateAmount(anchorTxnId, finalAmount)
            transactionWriteDao.updateDate(anchorTxnId, finalDate)
            transactionWriteDao.updateNotes(anchorTxnId, notes)
            if (anchorTxn.transactionType != finalType) {
                transactionWriteDao.updateTransactionType(anchorTxnId, finalType)
            }
        }
    }

    // ─── Unmerge ────────────────────────────────────────────────────────────

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

    /**
     * Observes whether a merge snapshot exists for the given parent transaction.
     * The UI uses this to decide whether to show the "Unmerge" option.
     * Emits null when no snapshot is found (never merged, or already unmerged).
     */
    fun observeMergeRecord(parentTxnId: Int): Flow<MergeRecord?> =
        mergeRecordDao.observeForParent(parentTxnId)

    /**
     * Builds the per-account contribution breakdown for a merged transaction.
     * Returns an empty list if the transaction has no merge records.
     *
     * The list always contains:
     *  - The anchor account entry: shows the anchor's original amount BEFORE the merge
     *    (sourced from [MergeRecord.originalParentAmount] on the oldest record).
     *  - One entry per child account: amount from [MergeRecord.childAmount].
     *
     * Works for both MANUAL (N-to-1) and AUTO (chained 1-to-1) merges.
     * The UI uses this to render [MergedAccountsCard] when multiple accounts are involved.
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

        // Anchor entry — use the pre-merge snapshot amount so that each account's
        // contribution reflects what actually left/arrived at that account.
        // The oldest record (ASC order) holds the true original parent state.
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

        // One entry per child — each record fully snapshots the child's account + amount.
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
                    date = r.childDate
                )
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
     *
     * This is a no-op if no snapshot exists for the given parent.
     */
    suspend fun unmergeTransactions(parentTxnId: Int) {
        val record = mergeRecordDao.getForParentSync(parentTxnId) ?: return

        if (record.mergeType == "MANUAL" && record.mergeGroupId.isNotBlank()) {
            // ── MANUAL path: restore all N children ──────────────────────
            val groupId = record.mergeGroupId
            // Defensive guard: a blank groupId would match all AUTO rows (mergeGroupId=''),
            // which would catastrophically wipe unrelated merge records.
            require(groupId.isNotBlank()) {
                "Manual merge groupId must not be blank for parentTxnId=$parentTxnId"
            }
            val allRecords = mergeRecordDao.getAllForGroup(groupId)
            if (allRecords.isEmpty()) return

            db.withTransaction {
                val currentParent = transactionQueryDao.getTransactionByIdSync(parentTxnId) ?: return@withTransaction

                fun signedAmount(
                    type: TransactionType,
                    amount: Double
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

                // If finalType is "expense", the signed amount is negative, so we negate it to get the mathematical amount.
                // This preserves any negative balance if the transaction was over-repaid.
                val finalAmount = if (finalType == TransactionType.EXPENSE) -newSigned else newSigned

                // Restore parent to its pre-merge state (all records share the same parent snapshot)
                val first = allRecords.first()
                transactionWriteDao.updateAmount(parentTxnId, finalAmount)
                if (currentParent.transactionType != finalType) {
                    transactionWriteDao.updateTransactionType(parentTxnId, finalType)
                }
                transactionWriteDao.updateDate(parentTxnId, first.originalParentDate)
                transactionWriteDao.updateNotes(parentTxnId, first.originalParentNotes)

                // Re-insert each child
                for (r in allRecords) {
                    val restoredChild = restoreTransactionFromMergeRecord(r)
                    transactionWriteDao.insert(restoredChild)

                    // Unblock the child's SMS so it can be re-scanned
                    r.childSourceSmsHash?.let { hash ->
                        deletedSmsHashDao.deleteByHash(hash)
                    }
                }

                // Clean up all records in this group atomically with the restore
                mergeRecordDao.deleteByGroupId(groupId)
            }
        } else {
            // ── AUTO path (chained 1-to-1): restore ALL children for this parent ───────────
            val allAutoRecords =
                mergeRecordDao.getAllForParentSync(parentTxnId)
                    .filter { it.mergeType == "AUTO" }
            if (allAutoRecords.isEmpty()) return

            db.withTransaction {
                val currentParent = transactionQueryDao.getTransactionByIdSync(parentTxnId) ?: return@withTransaction

                // AUTO merges just sum the absolute amounts.
                val totalMergedAmount = allAutoRecords.sumOf { it.childAmount }
                val newAmount = currentParent.amount - totalMergedAmount

                // The VERY FIRST record (oldest) has the original parent state
                val first = allAutoRecords.first()
                transactionWriteDao.updateAmount(parentTxnId, newAmount)
                transactionWriteDao.updateDate(parentTxnId, first.originalParentDate)
                transactionWriteDao.updateNotes(parentTxnId, first.originalParentNotes)

                // Re-insert each child
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

    // ─── Self Transfer Detection ──────────────────────────────────────────

    /**
     * Automatically detects if a newly inserted transaction is part of a self-transfer
     * between two user accounts, based on a two-tiered logic:
     * 1. Strict Time (<= 5 mins): Matches exactly on Amount.
     * 2. Loose Time (<= 6 hours): Matches on Amount AND Account Alias/Name text.
     */
    suspend fun detectAndLinkSelfTransfer(newTxn: Transaction) {
        if (newTxn.sourceSmsId == null || newTxn.linkedTransferId != null || newTxn.isExcluded || newTxn.isSplit) return

        // 6-hour window
        val windowMs = 6 * 60 * 60 * 1000L
        val startTime = newTxn.date - windowMs
        val endTime = newTxn.date + windowMs

        val candidates =
            transactionQueryDao.findPotentialTransfers(
                amount = newTxn.amount,
                accountId = newTxn.accountId,
                transactionType = newTxn.transactionType,
                startTime = startTime,
                endTime = endTime
            )

        for (candidate in candidates) {
            val timeDiff = kotlin.math.abs(candidate.date - newTxn.date)
            var isMatch = false

            // Tier 2: Strict Time (<= 5 minutes)
            if (timeDiff <= 5 * 60 * 1000L) {
                isMatch = true
            } else {
                // Tier 1: Text Validation
                val newTxnAliases = db.accountAliasDao().getAliasesForAccount(newTxn.accountId)
                val candidateAliases = db.accountAliasDao().getAliasesForAccount(candidate.accountId)

                val newTxnDesc = newTxn.originalDescription?.lowercase(Locale.ROOT) ?: ""
                val candidateDesc = candidate.originalDescription?.lowercase(Locale.ROOT) ?: ""

                // Extract digits from alias and check, or use token overlap
                val candidateAliasMatches =
                    candidateAliases.any { alias ->
                        val digits = alias.aliasName.filter { it.isDigit() }
                        (digits.isNotEmpty() && newTxnDesc.contains(digits)) ||
                            io.pm.finlight.core.utils.StringSimilarity.calculateTokenOverlapScore(alias.aliasName, newTxnDesc) > 0.6
                    }

                val newTxnAliasMatches =
                    newTxnAliases.any { alias ->
                        val digits = alias.aliasName.filter { it.isDigit() }
                        (digits.isNotEmpty() && candidateDesc.contains(digits)) ||
                            io.pm.finlight.core.utils.StringSimilarity.calculateTokenOverlapScore(alias.aliasName, candidateDesc) > 0.6
                    }

                val newTxnAccount = db.accountDao().getAccountByIdBlocking(newTxn.accountId)
                val candidateAccount = db.accountDao().getAccountByIdBlocking(candidate.accountId)

                val candidateBankNameMatches =
                    candidateAccount?.name?.let {
                        io.pm.finlight.core.utils.StringSimilarity.calculateTokenOverlapScore(it, newTxnDesc) > 0.6
                    } == true
                val newTxnBankNameMatches =
                    newTxnAccount?.name?.let {
                        io.pm.finlight.core.utils.StringSimilarity.calculateTokenOverlapScore(it, candidateDesc) > 0.6
                    } == true

                // Extra check for keywords we discussed
                val containsKeywords1 = newTxnDesc.contains("neft") || newTxnDesc.contains("imps") || newTxnDesc.contains("transfer")
                val containsKeywords2 = candidateDesc.contains("neft") || candidateDesc.contains("imps") || candidateDesc.contains("transfer")

                if (candidateAliasMatches || newTxnAliasMatches || candidateBankNameMatches || newTxnBankNameMatches || (containsKeywords1 && containsKeywords2)) {
                    isMatch = true
                }
            }

            if (isMatch) {
                // Link them atomically
                db.withTransaction {
                    transactionWriteDao.updateTransferLinkStatus(newTxn.id, candidate.id, true)
                    transactionWriteDao.updateTransferLinkStatus(candidate.id, newTxn.id, true)
                }
                break // Only link the first match
            }
        }
    }
}
