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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.data.db.entity.MergeRecord

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val settingsRepository: SettingsRepository,
    private val tagRepository: TagRepository,
    private val deletedSmsHashDao: DeletedSmsHashDao,
    private val mergeRecordDao: MergeRecordDao,
    private val db: AppDatabase,
) {
    // --- NEW: Function for Spending Velocity feature ---
    suspend fun getTotalExpensesSince(startDate: Long): Double {
        return transactionDao.getTotalExpensesSince(startDate) ?: 0.0
    }

    // --- NEW: Function to search for merchant predictions ---
    fun searchMerchants(query: String): Flow<List<MerchantPrediction>> {
        return transactionDao.searchMerchants(query)
    }

    suspend fun deleteByIds(transactionIds: List<Int>) {
        transactionDao.deleteByIds(transactionIds)
    }

    fun getTransactionWithSplits(transactionId: Int): Flow<TransactionWithSplits?> {
        return transactionDao.getTransactionWithSplits(transactionId)
    }

    val allTransactions: Flow<List<TransactionDetails>> =
        transactionDao.getAllTransactions()
            .onEach { transactions ->
                Log.d(
                    "TransactionFlowDebug",
                    "Repository Flow Emitted. Count: ${transactions.size}. Newest: ${transactions.firstOrNull()?.transaction?.description}",
                )
            }

    fun getFirstTransactionDate(): Flow<Long?> {
        return transactionDao.getFirstTransactionDate()
    }

    fun getFinancialSummaryForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<FinancialSummary?> {
        return transactionDao.getFinancialSummaryForRangeFlow(startDate, endDate)
    }

    fun getTopSpendingCategoriesForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<CategorySpending?> {
        return transactionDao.getTopSpendingCategoriesForRangeFlow(startDate, endDate)
    }

    fun getIncomeTransactionsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>> {
        return transactionDao.getIncomeTransactionsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getIncomeByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<CategorySpending>> {
        return transactionDao.getIncomeByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getSpendingByMerchantForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<MerchantSpendingSummary>> {
        return transactionDao.getSpendingByMerchantForMonth(startDate, endDate, keyword, accountId, categoryId, transactionType)
    }

    suspend fun addImageToTransaction(
        transactionId: Int,
        imageUri: String,
    ) {
        val transactionImage = TransactionImage(transactionId = transactionId, imageUri = imageUri)
        transactionDao.insertImage(transactionImage)
    }

    suspend fun deleteImage(transactionImage: TransactionImage) {
        transactionDao.deleteImage(transactionImage)
    }

    fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>> {
        return transactionDao.getImagesForTransaction(transactionId)
    }

    suspend fun updateDescription(
        id: Int,
        description: String,
    ) = transactionDao.updateDescription(id, description)

    suspend fun updateAmount(
        id: Int,
        amount: Double,
    ) = transactionDao.updateAmount(id, amount)

    suspend fun updateManualAmountEdit(
        id: Int,
        amount: Double,
    ) = transactionDao.updateManualAmountEdit(id, amount)

    suspend fun updateNotes(
        id: Int,
        notes: String?,
    ) = transactionDao.updateNotes(id, notes)

    suspend fun updateCategoryId(
        id: Int,
        categoryId: Int?,
    ) = transactionDao.updateCategoryId(id, categoryId)

    suspend fun updateAccountId(
        id: Int,
        accountId: Int,
    ) = transactionDao.updateAccountId(id, accountId)

    suspend fun updateDate(
        id: Int,
        date: Long,
    ) = transactionDao.updateDate(id, date)

    suspend fun updateExclusionStatus(
        id: Int,
        isExcluded: Boolean,
    ) = transactionDao.updateExclusionStatus(id, isExcluded)

    // --- NEW: Function to update transaction type ---
    suspend fun updateTransactionType(
        id: Int,
        transactionType: TransactionType,
    ) {
        transactionDao.updateTransactionType(id, transactionType)
    }

    suspend fun clearReviewFlag(id: Int) {
        transactionDao.clearReviewFlag(id)
    }

    fun getTransactionDetailsById(id: Int): Flow<TransactionDetails?> {
        return transactionDao.getTransactionDetailsById(id)
    }

    val recentTransactions: Flow<List<TransactionDetails>> = transactionDao.getRecentTransactionDetails()

    fun getAllSmsHashes(): Flow<List<String>> {
        return transactionDao.getAllSmsHashes()
    }

    fun getTransactionsForAccountDetails(accountId: Int): Flow<List<TransactionDetails>> {
        return transactionDao.getTransactionsForAccountDetails(accountId)
    }

    fun getTransactionDetailsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>> {
        return transactionDao.getTransactionDetailsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getAllTransactionsForRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<Transaction>> {
        return transactionDao.getAllTransactionsForRange(startDate, endDate)
    }

    fun getTransactionById(id: Int): Flow<Transaction?> {
        return transactionDao.getTransactionById(id)
    }

    suspend fun getTransactionSync(id: Int): Transaction? {
        return transactionDao.getTransactionByIdSync(id)
    }

    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>> {
        return transactionDao.getTransactionsForAccount(accountId)
    }

    fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<CategorySpending>> {
        return transactionDao.getSpendingByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId, transactionType)
    }

    fun getMonthlyTrends(startDate: Long): Flow<List<MonthlyTrend>> {
        return transactionDao.getMonthlyTrends(startDate)
    }

    suspend fun countTransactionsForCategory(categoryId: Int): Int {
        return transactionDao.countTransactionsForCategory(categoryId)
    }

    fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>> {
        return transactionDao.getTagsForTransaction(transactionId)
    }

    suspend fun getTagsForTransactionSimple(transactionId: Int): List<Tag> {
        return transactionDao.getTagsForTransactionSimple(transactionId)
    }

    suspend fun updateTagsForTransaction(
        transactionId: Int,
        tags: Set<Tag>,
    ) {
        transactionDao.clearTagsForTransaction(transactionId)
        if (tags.isNotEmpty()) {
            val crossRefs =
                tags.map { tag ->
                    TransactionTagCrossRef(transactionId = transactionId, tagId = tag.id)
                }
            transactionDao.addTagsToTransaction(crossRefs)
        }
    }

    private suspend fun getFinalTagsForTransaction(
        transaction: Transaction,
        initialTags: Set<Tag>,
    ): Set<Tag> {
        val finalTags = initialTags.toMutableSet()
        val travelSettings = settingsRepository.getTravelModeSettings().first()
        if (travelSettings?.isEnabled == true && transaction.date >= travelSettings.startDate && transaction.date <= travelSettings.endDate) {
            val tripTag = tagRepository.findOrCreateTag(travelSettings.tripName)
            finalTags.add(tripTag)
        }
        return finalTags
    }

    suspend fun insertTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    ): Long {
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        val transactionId = transactionDao.insert(transaction)
        if (finalTags.isNotEmpty()) {
            val crossRefs =
                finalTags.map { tag ->
                    TransactionTagCrossRef(transactionId = transactionId.toInt(), tagId = tag.id)
                }
            transactionDao.addTagsToTransaction(crossRefs)
        }
        return transactionId
    }

    suspend fun updateTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    ) {
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        transactionDao.update(transaction)
        transactionDao.clearTagsForTransaction(transaction.id)
        if (finalTags.isNotEmpty()) {
            val crossRefs =
                finalTags.map { tag ->
                    TransactionTagCrossRef(transactionId = transaction.id, tagId = tag.id)
                }
            transactionDao.addTagsToTransaction(crossRefs)
        }
    }

    suspend fun insertTransactionWithTagsAndImages(
        transaction: Transaction,
        tags: Set<Tag>,
        imagePaths: List<String>,
    ): Long {
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        val newTransactionId = transactionDao.insert(transaction)
        if (finalTags.isNotEmpty()) {
            val crossRefs =
                finalTags.map { tag ->
                    TransactionTagCrossRef(transactionId = newTransactionId.toInt(), tagId = tag.id)
                }
            transactionDao.addTagsToTransaction(crossRefs)
        }
        imagePaths.forEach { path ->
            val imageEntity =
                TransactionImage(
                    transactionId = newTransactionId.toInt(),
                    imageUri = path,
                )
            transactionDao.insertImage(imageEntity)
        }
        return newTransactionId
    }

    suspend fun delete(transaction: Transaction) {
        transactionDao.delete(transaction)
    }

    suspend fun setSmsHash(
        transactionId: Int,
        smsHash: String,
    ) {
        transactionDao.setSmsHash(transactionId, smsHash)
    }

    fun getTransactionCountForMerchant(description: String): Flow<Int> {
        return transactionDao.getTransactionCountForMerchant(description)
    }

    suspend fun findSimilarTransactions(
        description: String,
        excludeId: Int,
    ): List<Transaction> {
        return transactionDao.findSimilarTransactions(description, excludeId)
    }

    /** Returns all distinct [Transaction.originalDescription] values for cross-account nudge scanning. */
    suspend fun getDistinctOriginalDescriptions(): List<String> = transactionDao.getDistinctOriginalDescriptions()

    /** Returns IDs of all transactions sharing the given [originalDesc] (case-insensitive). */
    suspend fun getTransactionIdsByOriginalDescription(originalDesc: String): List<Int> =
        transactionDao.getTransactionIdsByOriginalDescription(originalDesc)

    suspend fun updateCategoryForIds(
        ids: List<Int>,
        categoryId: Int,
    ) {
        transactionDao.updateCategoryForIds(ids, categoryId)
    }

    suspend fun updateDescriptionForIds(
        ids: List<Int>,
        newDescription: String,
    ) {
        transactionDao.updateDescriptionForIds(ids, newDescription)
    }

    fun getDailySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<DailyTotal>> {
        return transactionDao.getDailySpendingForDateRange(startDate, endDate)
    }

    // --- NEW: Functions for retrospective tagging ---
    suspend fun addTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ) {
        transactionDao.addTagForDateRange(tagId, startDate, endDate)
    }

    suspend fun removeTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ) {
        transactionDao.removeTagForDateRange(tagId, startDate, endDate)
    }

    // --- NEW: Get all transactions for a specific tag ---
    fun getTransactionsByTagId(tagId: Int): Flow<List<TransactionDetails>> {
        return transactionDao.getTransactionsByTagId(tagId)
    }

    // --- NEW: Expose the function to remove all tags ---
    suspend fun removeAllTransactionsForTag(tagId: Int) {
        transactionDao.removeAllTransactionsForTag(tagId)
    }

    // --- NEW: Centralized "Monthly-First" Consistency Logic ---

    /**
     * Helper to check if cal1 is on a day *before* cal2, ignoring time.
     */
    private fun isBeforeDay(
        cal1: Calendar,
        cal2: Calendar,
    ): Boolean {
        return cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR) ||
            (
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) < cal2.get(Calendar.DAY_OF_YEAR)
            )
    }

    /**
     * Generates the consistency data for a single month, based on that month's budget.
     * This is the new single source of truth for all heatmap/calendar logic.
     */
    fun getMonthlyConsistencyData(
        year: Int,
        month: Int,
    ): Flow<List<CalendarDayStatus>> {
        // Calculate start and end of the given month
        val monthStartCal =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1) // Calendar.MONTH is 0-indexed
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val monthEndCal =
            (monthStartCal.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
        val daysInMonth = monthStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Combine the three flows we need
        // --- UPDATED: The budget flow is now nullable (Flow<Float?>) ---
        return combine(
            settingsRepository.getOverallBudgetForMonth(year, month),
            transactionDao.getDailySpendingForDateRange(monthStartCal.timeInMillis, monthEndCal.timeInMillis),
            transactionDao.getFirstTransactionDate(),
        ) { budget: Float?, dailyTotals: List<DailyTotal>, firstTransactionDate: Long? ->
            val firstDataCal = firstTransactionDate?.let { Calendar.getInstance().apply { timeInMillis = it } }
            val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })
            val resultList = mutableListOf<CalendarDayStatus>()
            val dayIterator = (monthStartCal.clone() as Calendar)
            val today = Calendar.getInstance()

            // --- UPDATED LOGIC (Fix for "green day" and "blue day" bugs) ---
            if (budget == null) {
                // CASE 1: NO BUDGET SET (null)
                // All past days are NO_DATA (gray).
                for (i in 1..daysInMonth) {
                    dayIterator.set(Calendar.DAY_OF_MONTH, i)
                    val date = dayIterator.time

                    if (dayIterator.after(today) || (firstDataCal != null && isBeforeDay(dayIterator, firstDataCal))) {
                        resultList.add(CalendarDayStatus(date, SpendingStatus.NO_DATA, 0L, 0L))
                    } else {
                        // Any past day with NO BUDGET is NO_DATA, even if there was no spending.
                        val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)
                        val amountSpent = (spendingMap[dateKey] ?: 0.0).roundToLong()
                        val status = SpendingStatus.NO_DATA
                        resultList.add(CalendarDayStatus(date, status, amountSpent, 0L))
                    }
                }
            } else {
                // CASE 2: A BUDGET IS SET (e.g., 0f or 145000f)
                var cumulativeSpending = 0.0
                val totalBudget = budget.toDouble()

                for (i in 1..daysInMonth) {
                    dayIterator.set(Calendar.DAY_OF_MONTH, i)
                    val date = dayIterator.time

                    if (dayIterator.after(today) || (firstDataCal != null && isBeforeDay(dayIterator, firstDataCal))) {
                        resultList.add(CalendarDayStatus(date, SpendingStatus.NO_DATA, 0L, 0L))
                        continue
                    }

                    val remainingBudget = totalBudget - cumulativeSpending
                    val remainingDays = (daysInMonth - i + 1).coerceAtLeast(1)
                    val safeToSpend = if (remainingBudget > 0) (remainingBudget / remainingDays).roundToLong() else 0L

                    val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)
                    val amountSpent = (spendingMap[dateKey] ?: 0.0)
                    val amountSpentLong = amountSpent.roundToLong()

                    // This is the new, more robust 'when' block that fixes the original bug
                    val status =
                        when {
                            amountSpentLong == 0L && safeToSpend == 0L -> SpendingStatus.WITHIN_LIMIT // Met 0 budget (blue)
                            amountSpentLong == 0L && safeToSpend > 0L -> SpendingStatus.NO_SPEND // No spend on a day with a budget (green)
                            amountSpentLong > 0L && safeToSpend == 0L -> SpendingStatus.OVER_LIMIT // Spent > 0 on a 0 budget (red)
                            amountSpentLong > safeToSpend -> SpendingStatus.OVER_LIMIT // Spent > budget (red)
                            else -> SpendingStatus.WITHIN_LIMIT // Spent <= budget (and not 0) (blue)
                        }
                    Log.d("HeatmapDebug", "Date: $dateKey, Spent: $amountSpentLong, Threshold: $safeToSpend, Status: $status")
                    resultList.add(CalendarDayStatus(date, status, amountSpentLong, safeToSpend))
                    cumulativeSpending += amountSpent
                }
            }
            resultList // This is the value emitted by the combine
        }.flowOn(Dispatchers.Default) // Run the calculation on a background thread
    }

    // --- NEW: Expose the quick fill query ---
    fun getRecentManualTransactions(limit: Int): Flow<List<TransactionDetails>> {
        return transactionDao.getRecentManualTransactions(limit)
    }

    // --- NEW: Reimbursement / Offset Feature ---

    fun getReimbursementsForExpense(expenseId: Int): Flow<List<TransactionDetails>> =
        transactionDao.getReimbursementsForExpense(expenseId)

    fun getCandidateReimbursements(excludeExpenseId: Int): Flow<List<TransactionDetails>> =
        transactionDao.getCandidateReimbursements(excludeExpenseId)

    fun getLinkedExpenseForReimbursement(incomeId: Int): Flow<TransactionDetails?> =
        transactionDao.getLinkedExpenseForReimbursement(incomeId)

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
        val incomeTxn = transactionDao.getTransactionByIdSync(incomeId) ?: return
        val expenseTxn = transactionDao.getTransactionByIdSync(expenseId) ?: return
        transactionDao.linkReimbursement(incomeId, expenseId)
        val newExpenseAmount = expenseTxn.amount - incomeTxn.amount
        transactionDao.updateAmount(expenseId, newExpenseAmount)
    }

    /**
     * Removes the reimbursement link from [incomeId]:
     * - Clears parentReimbursementId and removes the excluded flag.
     * - Adds the income amount back onto the parent expense.
     */
    suspend fun unlinkReimbursement(incomeId: Int) {
        val incomeTxn = transactionDao.getTransactionByIdSync(incomeId) ?: return
        val parentId = incomeTxn.parentReimbursementId ?: return
        val expenseTxn = transactionDao.getTransactionByIdSync(parentId) ?: return
        transactionDao.unlinkReimbursement(incomeId)
        val restoredExpenseAmount = expenseTxn.amount + incomeTxn.amount
        transactionDao.updateAmount(parentId, restoredExpenseAmount)
    }

    // --- NEW: Smart Transaction Merge ---
    suspend fun findRecentTransactionForMerge(
        merchant: String,
        accountId: Int,
        transactionType: TransactionType,
        timeWindowStart: Long,
        newTxnId: Int
    ): Transaction? {
        return transactionDao.findRecentTransactionForMerge(merchant, accountId, transactionType, timeWindowStart, newTxnId)
    }

    suspend fun dismissMerge(id: Int) {
        transactionDao.updateMergeDismissed(id, true)
    }

    suspend fun mergeTransactions(
        parentTxnId: Int,
        childTxnId: Int,
        childSmsBody: String? = null,
        childSmsDate: Long? = null
    ) {
        var activeParentId = parentTxnId
        var parentTxn = transactionDao.getTransactionByIdSync(activeParentId)
        val childTxn = transactionDao.getTransactionByIdSync(childTxnId)

        if (childTxn == null) return

        if (parentTxn == null) {
            val timeWindowStart = childTxn.date - (3 * 60 * 60 * 1000L)
            val newParent =
                transactionDao.findRecentTransactionForMerge(
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

        transactionDao.updateMergeDismissed(childTxnId, true)

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

        transactionDao.updateAmount(activeParentId, newAmount)
        transactionDao.updateDate(activeParentId, newDate)
        transactionDao.updateNotes(activeParentId, newNotes)

        childTxn.sourceSmsHash?.let { hash ->
            deletedSmsHashDao.insert(DeletedSmsHash(smsHash = hash))
        }

        transactionDao.delete(childTxn)
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
            childTransactionType = childTxn.transactionType.name.lowercase(),
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
            val anchorTxn = transactionDao.getTransactionByIdSync(anchorTxnId) ?: return@withTransaction
            val childTxns = childTxnIds.mapNotNull { transactionDao.getTransactionByIdSync(it) }
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
            val hasReimbursements = transactionDao.getReimbursementsCountSync(anchorTxnId) > 0

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
            val anchorTags = transactionDao.getTagsForTransactionSimple(anchorTxnId).map { it.id }.toMutableSet()
            for (childTxn in childTxns) {
                val childTagIds = transactionDao.getTagsForTransactionSimple(childTxn.id).map { it.id }
                anchorTags.addAll(childTagIds)
            }
            transactionDao.clearTagsForTransaction(anchorTxnId)
            if (anchorTags.isNotEmpty()) {
                transactionDao.addTagsToTransaction(
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
                transactionDao.delete(childTxn)
            }

            // ── Update the anchor ──────────────────────────────────────────
            transactionDao.updateAmount(anchorTxnId, finalAmount)
            transactionDao.updateDate(anchorTxnId, finalDate)
            transactionDao.updateNotes(anchorTxnId, notes)
            if (anchorTxn.transactionType != finalType) {
                transactionDao.updateTransactionType(anchorTxnId, finalType)
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
            transactionType = TransactionType.fromString(r.childTransactionType),
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

        val anchorTxn = transactionDao.getTransactionByIdSync(parentTxnId) ?: return emptyList()
        val anchorAccount = db.accountDao().getAccountByIdBlocking(anchorTxn.accountId)

        val entries = mutableListOf<MergedTransactionItem>()

        fun signedAmount(
            type: TransactionType,
            amount: Double,
        ): Double =
            if (type == TransactionType.INCOME) amount else -amount

        val currentSigned = signedAmount(anchorTxn.transactionType, anchorTxn.amount)
        val childrenSigned = records.sumOf { signedAmount(TransactionType.fromString(it.childTransactionType), it.childAmount) }
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
                transactionType = anchorOriginalType.name.lowercase(),
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
                val currentParent = transactionDao.getTransactionByIdSync(parentTxnId) ?: return@withTransaction

                fun signedAmount(
                    type: TransactionType,
                    amount: Double
                ): Double =
                    if (type == TransactionType.INCOME) amount else -amount

                val currentSigned = signedAmount(currentParent.transactionType, currentParent.amount)
                val childrenSigned = allRecords.sumOf { signedAmount(TransactionType.fromString(it.childTransactionType), it.childAmount) }
                val newSigned = currentSigned - childrenSigned
                val hasReimbursements = transactionDao.getReimbursementsCountSync(parentTxnId) > 0

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
                transactionDao.updateAmount(parentTxnId, finalAmount)
                if (currentParent.transactionType != finalType) {
                    transactionDao.updateTransactionType(parentTxnId, finalType)
                }
                transactionDao.updateDate(parentTxnId, first.originalParentDate)
                transactionDao.updateNotes(parentTxnId, first.originalParentNotes)

                // Re-insert each child
                for (r in allRecords) {
                    val restoredChild = restoreTransactionFromMergeRecord(r)
                    transactionDao.insert(restoredChild)

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
                val currentParent = transactionDao.getTransactionByIdSync(parentTxnId) ?: return@withTransaction

                // AUTO merges just sum the absolute amounts.
                val totalMergedAmount = allAutoRecords.sumOf { it.childAmount }
                val newAmount = currentParent.amount - totalMergedAmount

                // The VERY FIRST record (oldest) has the original parent state
                val first = allAutoRecords.first()
                transactionDao.updateAmount(parentTxnId, newAmount)
                transactionDao.updateDate(parentTxnId, first.originalParentDate)
                transactionDao.updateNotes(parentTxnId, first.originalParentNotes)

                // Re-insert each child
                for (r in allAutoRecords) {
                    val restoredChild = restoreTransactionFromMergeRecord(r)
                    transactionDao.insert(restoredChild)

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
            transactionDao.findPotentialTransfers(
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
                    transactionDao.updateTransferLinkStatus(newTxn.id, candidate.id, true)
                    transactionDao.updateTransferLinkStatus(candidate.id, newTxn.id, true)
                }
                break // Only link the first match
            }
        }
    }
}
