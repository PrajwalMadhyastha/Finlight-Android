// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TransactionRepository.kt
// REASON: FEATURE (Consistency) - Added the new `getMonthlyConsistencyData`
// function. This centralizes the logic for the "Monthly-First" consistency
// calculation, making it the single source of truth for all heatmaps.
// FIX (Bug) - The new function uses `(budget.toDouble() / daysInMonth).roundToLong()`
// to calculate the daily safe-to-spend amount, fixing the integer truncation
// bug that existed in the old ViewModel implementations.
//
// REASON: FIX (Consistency) - The `getMonthlyConsistencyData` function is
// updated to handle a nullable `Float?` budget. If the budget received from
// `SettingsRepository` is `null`, this function now generates all daily
// statuses as `SpendingStatus.NO_DATA`, fixing the "No Budget" bug that
// incorrectly showed days as blue.
//
// REASON: FIX (Consistency) - Corrected the logic in `getMonthlyConsistencyData`
// to handle all edge cases:
// 1. If `budget == null`, all past days are now `NO_DATA` (gray), even if
//    `amountSpent` was 0. This fixes the "green day" bug.
// 2. If `budget == 0f`, any spending (`amountSpent > 0`) is now correctly
//    marked as `OVER_LIMIT` (red). This fixes the original "blue day" bug.
// =================================================================================
package io.pm.finlight

import android.util.Log
import io.pm.finlight.data.model.MerchantPrediction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val settingsRepository: SettingsRepository,
    private val tagRepository: TagRepository
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

    fun getFinancialSummaryForRangeFlow(startDate: Long, endDate: Long): Flow<FinancialSummary?> {
        return transactionDao.getFinancialSummaryForRangeFlow(startDate, endDate)
    }

    fun getTopSpendingCategoriesForRangeFlow(startDate: Long, endDate: Long): Flow<CategorySpending?> {
        return transactionDao.getTopSpendingCategoriesForRangeFlow(startDate, endDate)
    }

    fun getIncomeTransactionsForRange(startDate: Long, endDate: Long, keyword: String?, accountId: Int?, categoryId: Int?): Flow<List<TransactionDetails>> {
        return transactionDao.getIncomeTransactionsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getIncomeByCategoryForMonth(startDate: Long, endDate: Long, keyword: String?, accountId: Int?, categoryId: Int?): Flow<List<CategorySpending>> {
        return transactionDao.getIncomeByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId)
    }

    fun getSpendingByMerchantForMonth(startDate: Long, endDate: Long, keyword: String?, accountId: Int?, categoryId: Int?): Flow<List<MerchantSpendingSummary>> {
        return transactionDao.getSpendingByMerchantForMonth(startDate, endDate, keyword, accountId, categoryId)
    }

    suspend fun addImageToTransaction(transactionId: Int, imageUri: String) {
        val transactionImage = TransactionImage(transactionId = transactionId, imageUri = imageUri)
        transactionDao.insertImage(transactionImage)
    }

    suspend fun deleteImage(transactionImage: TransactionImage) {
        transactionDao.deleteImage(transactionImage)
    }

    fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>> {
        return transactionDao.getImagesForTransaction(transactionId)
    }

    suspend fun updateDescription(id: Int, description: String) = transactionDao.updateDescription(id, description)
    suspend fun updateAmount(id: Int, amount: Double) = transactionDao.updateAmount(id, amount)
    suspend fun updateNotes(id: Int, notes: String?) = transactionDao.updateNotes(id, notes)
    suspend fun updateCategoryId(id: Int, categoryId: Int?) = transactionDao.updateCategoryId(id, categoryId)
    suspend fun updateAccountId(id: Int, accountId: Int) = transactionDao.updateAccountId(id, accountId)
    suspend fun updateDate(id: Int, date: Long) = transactionDao.updateDate(id, date)
    suspend fun updateExclusionStatus(id: Int, isExcluded: Boolean) = transactionDao.updateExclusionStatus(id, isExcluded)

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
        categoryId: Int?
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

    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>> {
        return transactionDao.getTransactionsForAccount(accountId)
    }

    fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?
    ): Flow<List<CategorySpending>> {
        return transactionDao.getSpendingByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId)
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

    suspend fun updateTagsForTransaction(transactionId: Int, tags: Set<Tag>) {
        transactionDao.clearTagsForTransaction(transactionId)
        if (tags.isNotEmpty()) {
            val crossRefs = tags.map { tag ->
                TransactionTagCrossRef(transactionId = transactionId, tagId = tag.id)
            }
            transactionDao.addTagsToTransaction(crossRefs)
        }
    }

    private suspend fun getFinalTagsForTransaction(transaction: Transaction, initialTags: Set<Tag>): Set<Tag> {
        val finalTags = initialTags.toMutableSet()
        val travelSettings = settingsRepository.getTravelModeSettings().first()
        if (travelSettings?.isEnabled == true && transaction.date >= travelSettings.startDate && transaction.date <= travelSettings.endDate) {
            val tripTag = tagRepository.findOrCreateTag(travelSettings.tripName)
            finalTags.add(tripTag)
        }
        return finalTags
    }

    suspend fun insertTransactionWithTags(transaction: Transaction, tags: Set<Tag>): Long {
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        val transactionId = transactionDao.insert(transaction)
        if (finalTags.isNotEmpty()) {
            val crossRefs = finalTags.map { tag ->
                TransactionTagCrossRef(transactionId = transactionId.toInt(), tagId = tag.id)
            }
            transactionDao.addTagsToTransaction(crossRefs)
        }
        return transactionId
    }

    suspend fun updateTransactionWithTags(transaction: Transaction, tags: Set<Tag>) {
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        transactionDao.update(transaction)
        transactionDao.clearTagsForTransaction(transaction.id)
        if (finalTags.isNotEmpty()) {
            val crossRefs = finalTags.map { tag ->
                TransactionTagCrossRef(transactionId = transaction.id, tagId = tag.id)
            }
            transactionDao.addTagsToTransaction(crossRefs)
        }
    }

    suspend fun insertTransactionWithTagsAndImages(
        transaction: Transaction,
        tags: Set<Tag>,
        imagePaths: List<String>
    ): Long {
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        val newTransactionId = transactionDao.insert(transaction)
        if (finalTags.isNotEmpty()) {
            val crossRefs = finalTags.map { tag ->
                TransactionTagCrossRef(transactionId = newTransactionId.toInt(), tagId = tag.id)
            }
            transactionDao.addTagsToTransaction(crossRefs)
        }
        imagePaths.forEach { path ->
            val imageEntity = TransactionImage(
                transactionId = newTransactionId.toInt(),
                imageUri = path
            )
            transactionDao.insertImage(imageEntity)
        }
        return newTransactionId
    }

    suspend fun delete(transaction: Transaction) {
        transactionDao.delete(transaction)
    }

    suspend fun setSmsHash(transactionId: Int, smsHash: String) {
        transactionDao.setSmsHash(transactionId, smsHash)
    }

    fun getTransactionCountForMerchant(description: String): Flow<Int> {
        return transactionDao.getTransactionCountForMerchant(description)
    }

    suspend fun findSimilarTransactions(description: String, excludeId: Int): List<Transaction> {
        return transactionDao.findSimilarTransactions(description, excludeId)
    }

    suspend fun updateCategoryForIds(ids: List<Int>, categoryId: Int) {
        transactionDao.updateCategoryForIds(ids, categoryId)
    }

    suspend fun updateDescriptionForIds(ids: List<Int>, newDescription: String) {
        transactionDao.updateDescriptionForIds(ids, newDescription)
    }

    fun getDailySpendingForDateRange(startDate: Long, endDate: Long): Flow<List<DailyTotal>> {
        return transactionDao.getDailySpendingForDateRange(startDate, endDate)
    }

    // --- NEW: Functions for retrospective tagging ---
    suspend fun addTagForDateRange(tagId: Int, startDate: Long, endDate: Long) {
        transactionDao.addTagForDateRange(tagId, startDate, endDate)
    }

    suspend fun removeTagForDateRange(tagId: Int, startDate: Long, endDate: Long) {
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
    private fun isBeforeDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR) ||
                (cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                        cal1.get(Calendar.DAY_OF_YEAR) < cal2.get(Calendar.DAY_OF_YEAR))
    }

    /**
     * Generates the consistency data for a single month, based on that month's budget.
     * This is the new single source of truth for all heatmap/calendar logic.
     */
    fun getMonthlyConsistencyData(year: Int, month: Int): Flow<List<CalendarDayStatus>> {
        // Calculate start and end of the given month
        val monthStartCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1) // Calendar.MONTH is 0-indexed
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthEndCal = (monthStartCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        val daysInMonth = monthStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Combine the three flows we need
        // --- UPDATED: The budget flow is now nullable (Flow<Float?>) ---
        return combine(
            settingsRepository.getOverallBudgetForMonth(year, month),
            transactionDao.getDailySpendingForDateRange(monthStartCal.timeInMillis, monthEndCal.timeInMillis),
            transactionDao.getFirstTransactionDate()
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
                        resultList.add(CalendarDayStatus(date, SpendingStatus.NO_DATA, amountSpent, 0L))
                    }
                }
            } else {
                // CASE 2: A BUDGET IS SET (e.g., 0f or 145000f)
                val safeToSpend = if (budget > 0 && daysInMonth > 0) (budget.toDouble() / daysInMonth).roundToLong() else 0L

                for (i in 1..daysInMonth) {
                    dayIterator.set(Calendar.DAY_OF_MONTH, i)
                    val date = dayIterator.time

                    if (dayIterator.after(today) || (firstDataCal != null && isBeforeDay(dayIterator, firstDataCal))) {
                        resultList.add(CalendarDayStatus(date, SpendingStatus.NO_DATA, 0L, 0L))
                        continue
                    }

                    val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)
                    val amountSpent = (spendingMap[dateKey] ?: 0.0).roundToLong()

                    // This is the new, more robust 'when' block that fixes the original bug
                    val status = when {
                        amountSpent == 0L && safeToSpend == 0L -> SpendingStatus.WITHIN_LIMIT // Met 0 budget (blue)
                        amountSpent == 0L && safeToSpend > 0L -> SpendingStatus.NO_SPEND     // No spend on a day with a budget (green)
                        amountSpent > 0L && safeToSpend == 0L -> SpendingStatus.OVER_LIMIT   // Spent > 0 on a 0 budget (red)
                        amountSpent > safeToSpend -> SpendingStatus.OVER_LIMIT               // Spent > budget (red)
                        else -> SpendingStatus.WITHIN_LIMIT // Spent <= budget (and not 0) (blue)
                    }
                    resultList.add(CalendarDayStatus(date, status, amountSpent, safeToSpend))
                }
            }
            resultList // This is the value emitted by the combine
        }.flowOn(Dispatchers.Default) // Run the calculation on a background thread
    }
}

