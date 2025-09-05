// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TransactionRepository.kt
// REASON: FEATURE - Added `getTransactionsByTagId` to expose the new DAO
// method to the ViewModel layer. This is required for the new TripDetailViewModel.
// =================================================================================
package io.pm.finlight

import android.util.Log
import io.pm.finlight.data.model.MerchantPrediction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class TransactionRepository(private val transactionDao: TransactionDao) {

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

    suspend fun insertTransactionWithTags(transaction: Transaction, tags: Set<Tag>): Long {
        val transactionId = transactionDao.insert(transaction)
        if (tags.isNotEmpty()) {
            val crossRefs = tags.map { tag ->
                TransactionTagCrossRef(transactionId = transactionId.toInt(), tagId = tag.id)
            }
            transactionDao.addTagsToTransaction(crossRefs)
        }
        return transactionId
    }

    suspend fun updateTransactionWithTags(transaction: Transaction, tags: Set<Tag>) {
        transactionDao.update(transaction)
        transactionDao.clearTagsForTransaction(transaction.id)
        if (tags.isNotEmpty()) {
            val crossRefs = tags.map { tag ->
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
        val newTransactionId = transactionDao.insert(transaction)
        if (tags.isNotEmpty()) {
            val crossRefs = tags.map { tag ->
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
}