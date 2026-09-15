// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TransactionRepository.kt
// REASON: REFACTOR (Issue #284) - Extracted self-transfer heuristic matching logic
// into dedicated `DetectSelfTransferUseCase`. Removed direct references to
// `db.accountDao()` and `db.accountAliasDao()`. Added clean persistence methods
// `findPotentialTransfers` and `linkTransfer`.
// =================================================================================
package io.pm.finlight

import android.util.Log
import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class TransactionRepository(
    private val transactionWriteDao: TransactionWriteDao,
    private val transactionQueryDao: TransactionQueryDao,
    private val transactionAnalyticsDao: TransactionAnalyticsDao,
    private val transactionReimbursementDao: TransactionReimbursementDao,
    private val db: AppDatabase,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val manageReimbursementUseCase: ManageReimbursementUseCase =
        ManageReimbursementUseCase(
            transactionQueryDao = transactionQueryDao,
            transactionWriteDao = transactionWriteDao,
            transactionReimbursementDao = transactionReimbursementDao,
            db = db,
            dispatcherProvider = dispatcherProvider,
        ),
) : ITransactionRepository {
    @Deprecated("Use domain DAO constructor", level = DeprecationLevel.WARNING)
    constructor(
        transactionDao: TransactionDao,
        db: AppDatabase,
        dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
        manageReimbursementUseCase: ManageReimbursementUseCase =
            ManageReimbursementUseCase(
                transactionQueryDao = transactionDao,
                transactionWriteDao = transactionDao,
                transactionReimbursementDao = transactionDao,
                db = db,
                dispatcherProvider = dispatcherProvider,
            ),
    ) : this(
        transactionWriteDao = transactionDao,
        transactionQueryDao = transactionDao,
        transactionAnalyticsDao = transactionDao,
        transactionReimbursementDao = transactionDao,
        db = db,
        dispatcherProvider = dispatcherProvider,
        manageReimbursementUseCase = manageReimbursementUseCase,
    )

    // --- NEW: Function for Spending Velocity feature ---
    override suspend fun getTotalExpensesSince(startDate: Long): Double {
        return transactionAnalyticsDao.getTotalExpensesSince(startDate) ?: 0.0
    }

    // --- NEW: Function to search for merchant predictions ---
    override fun searchMerchants(query: String): Flow<List<MerchantPrediction>> {
        return transactionQueryDao.searchMerchants(query)
    }

    override suspend fun deleteByIds(transactionIds: List<Int>) {
        transactionWriteDao.deleteByIds(transactionIds)
    }

    override fun getTransactionWithSplits(transactionId: Int): Flow<TransactionWithSplits?> {
        return transactionQueryDao.getTransactionWithSplits(transactionId)
    }

    override val allTransactions: Flow<List<TransactionDetails>> =
        transactionQueryDao.getAllTransactions()
            .onEach { transactions ->
                Log.d(
                    "TransactionFlowDebug",
                    "Repository Flow Emitted. Count: ${transactions.size}. Newest: ${transactions.firstOrNull()?.transaction?.description}",
                )
            }

    override fun getFirstTransactionDate(): Flow<Long?> {
        return transactionQueryDao.getFirstTransactionDate()
    }

    override fun getFinancialSummaryForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<FinancialSummary?> {
        return transactionAnalyticsDao.getFinancialSummaryForRangeFlow(startDate, endDate)
    }

    override fun getTopSpendingCategoriesForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<CategorySpending?> {
        return transactionAnalyticsDao.getTopSpendingCategoriesForRangeFlow(startDate, endDate)
    }

    override fun getIncomeTransactionsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getIncomeTransactionsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    override fun getIncomeByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<CategorySpending>> {
        return transactionAnalyticsDao.getIncomeByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId)
    }

    override fun getSpendingByMerchantForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<MerchantSpendingSummary>> {
        return transactionAnalyticsDao.getSpendingByMerchantForMonth(startDate, endDate, keyword, accountId, categoryId, transactionType)
    }

    override suspend fun addImageToTransaction(
        transactionId: Int,
        imageUri: String,
    ) {
        val transactionImage = TransactionImage(transactionId = transactionId, imageUri = imageUri)
        transactionWriteDao.insertImage(transactionImage)
    }

    override suspend fun deleteImage(transactionImage: TransactionImage) {
        transactionWriteDao.deleteImage(transactionImage)
    }

    override fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>> {
        return transactionQueryDao.getImagesForTransaction(transactionId)
    }

    override suspend fun updateDescription(
        id: Int,
        description: String,
    ) = transactionWriteDao.updateDescription(id, description)

    override suspend fun updateAmount(
        id: Int,
        amount: Double,
    ) = transactionWriteDao.updateAmount(id, amount)

    override suspend fun updateManualAmountEdit(
        id: Int,
        amount: Double,
    ) = transactionWriteDao.updateManualAmountEdit(id, amount)

    override suspend fun updateNotes(
        id: Int,
        notes: String?,
    ) = transactionWriteDao.updateNotes(id, notes)

    override suspend fun updateCategoryId(
        id: Int,
        categoryId: Int?,
    ) = transactionWriteDao.updateCategoryId(id, categoryId)

    override suspend fun updateAccountId(
        id: Int,
        accountId: Int,
    ) = transactionWriteDao.updateAccountId(id, accountId)

    override suspend fun updateDate(
        id: Int,
        date: Long,
    ) = transactionWriteDao.updateDate(id, date)

    override suspend fun updateExclusionStatus(
        id: Int,
        isExcluded: Boolean,
    ) = transactionWriteDao.updateExclusionStatus(id, isExcluded)

    // --- NEW: Function to update transaction type ---
    override suspend fun updateTransactionType(
        id: Int,
        transactionType: TransactionType,
    ) {
        transactionWriteDao.updateTransactionType(id, transactionType)
    }

    override suspend fun clearReviewFlag(id: Int) {
        transactionWriteDao.clearReviewFlag(id)
    }

    override fun getTransactionDetailsById(id: Int): Flow<TransactionDetails?> {
        return transactionQueryDao.getTransactionDetailsById(id)
    }

    override val recentTransactions: Flow<List<TransactionDetails>> = transactionQueryDao.getRecentTransactionDetails()

    override fun getAllSmsHashes(): Flow<List<String>> {
        return transactionQueryDao.getAllSmsHashes()
    }

    override fun getTransactionsForAccountDetails(accountId: Int): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getTransactionsForAccountDetails(accountId)
    }

    override fun getTransactionDetailsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getTransactionDetailsForRange(startDate, endDate, keyword, accountId, categoryId)
    }

    override fun getAllTransactionsForRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<Transaction>> {
        return transactionQueryDao.getAllTransactionsForRange(startDate, endDate)
    }

    override fun getTransactionById(id: Int): Flow<Transaction?> {
        return transactionQueryDao.getTransactionById(id)
    }

    override suspend fun getTransactionSync(id: Int): Transaction? {
        return transactionQueryDao.getTransactionByIdSync(id)
    }

    override fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>> {
        return transactionQueryDao.getTransactionsForAccount(accountId)
    }

    override fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<CategorySpending>> {
        return transactionAnalyticsDao.getSpendingByCategoryForMonth(startDate, endDate, keyword, accountId, categoryId, transactionType)
    }

    override fun getMonthlyTrends(startDate: Long): Flow<List<MonthlyTrend>> {
        return transactionAnalyticsDao.getMonthlyTrends(startDate)
    }

    override suspend fun countTransactionsForCategory(categoryId: Int): Int {
        return transactionQueryDao.countTransactionsForCategory(categoryId)
    }

    override fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>> {
        return transactionQueryDao.getTagsForTransaction(transactionId)
    }

    override suspend fun getTagsForTransactionSimple(transactionId: Int): List<Tag> {
        return transactionQueryDao.getTagsForTransactionSimple(transactionId)
    }

    override suspend fun updateTagsForTransaction(
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

    override suspend fun insertTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    ): Long {
        val transactionId = transactionWriteDao.insert(transaction)
        if (transactionId <= 0L) {
            return transactionId
        }
        if (tags.isNotEmpty()) {
            val crossRefs =
                tags.map { tag ->
                    TransactionTagCrossRef(transactionId = transactionId.toInt(), tagId = tag.id)
                }
            transactionWriteDao.addTagsToTransaction(crossRefs)
        }
        return transactionId
    }

    override suspend fun updateTransactionWithTags(
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

    override suspend fun insertTransactionWithTagsAndImages(
        transaction: Transaction,
        tags: Set<Tag>,
        imagePaths: List<String>,
    ): Long {
        val newTransactionId = transactionWriteDao.insert(transaction)
        if (newTransactionId <= 0L) {
            return newTransactionId
        }
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

    override suspend fun delete(transaction: Transaction) {
        transactionWriteDao.delete(transaction)
    }

    override suspend fun setSmsHash(
        transactionId: Int,
        smsHash: String,
    ) {
        transactionWriteDao.setSmsHash(transactionId, smsHash)
    }

    override fun getTransactionCountForMerchant(description: String): Flow<Int> {
        return transactionQueryDao.getTransactionCountForMerchant(description)
    }

    override suspend fun findSimilarTransactions(
        description: String,
        excludeId: Int,
    ): List<Transaction> {
        return transactionQueryDao.findSimilarTransactions(description, excludeId)
    }

    /** Returns all distinct [Transaction.originalDescription] values for cross-account nudge scanning. */
    override suspend fun getDistinctOriginalDescriptions(): List<String> = transactionQueryDao.getDistinctOriginalDescriptions()

    /** Returns IDs of all transactions sharing the given [originalDesc] (case-insensitive). */
    override suspend fun getTransactionIdsByOriginalDescription(originalDesc: String): List<Int> =
        transactionQueryDao.getTransactionIdsByOriginalDescription(originalDesc)

    override suspend fun updateCategoryForIds(
        ids: List<Int>,
        categoryId: Int,
    ) {
        transactionWriteDao.updateCategoryForIds(ids, categoryId)
    }

    override suspend fun updateDescriptionForIds(
        ids: List<Int>,
        newDescription: String,
    ) {
        transactionWriteDao.updateDescriptionForIds(ids, newDescription)
    }

    override fun getTransactionCountsByOriginalDescription(): Flow<Map<String, Int>> =
        transactionQueryDao.getTransactionCountsByOriginalDescription().map { list ->
            list.associate { it.originalDesc.lowercase() to it.count }
        }

    override fun getTransactionsByOriginalDescription(originalDesc: String): Flow<List<TransactionDetails>> =
        transactionQueryDao.getTransactionsByOriginalDescription(originalDesc)

    override suspend fun updateDescriptionByOriginalDescription(
        originalDesc: String,
        newDescription: String,
    ): Int =
        withContext(dispatcherProvider.io) {
            transactionWriteDao.updateDescriptionByOriginalDescription(originalDesc, newDescription)
        }

    override fun getDailySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<DailyTotal>> {
        return transactionAnalyticsDao.getDailySpendingForDateRange(startDate, endDate)
    }

    // --- NEW: Functions for retrospective tagging ---
    override suspend fun addTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ) {
        transactionWriteDao.addTagForDateRange(tagId, startDate, endDate)
    }

    override suspend fun removeTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ) {
        transactionWriteDao.removeTagForDateRange(tagId, startDate, endDate)
    }

    // --- NEW: Get all transactions for a specific tag ---
    override fun getTransactionsByTagId(tagId: Int): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getTransactionsByTagId(tagId)
    }

    // --- NEW: Expose the function to remove all tags ---
    override suspend fun removeAllTransactionsForTag(tagId: Int) {
        transactionWriteDao.removeAllTransactionsForTag(tagId)
    }

    // --- NEW: Expose the quick fill query ---
    override fun getRecentManualTransactions(limit: Int): Flow<List<TransactionDetails>> {
        return transactionQueryDao.getRecentManualTransactions(limit)
    }

    // --- NEW: Reimbursement / Offset Feature ---

    override fun getReimbursementsForExpense(expenseId: Int): Flow<List<TransactionDetails>> =
        transactionReimbursementDao.getReimbursementsForExpense(expenseId)

    override fun getCandidateReimbursements(excludeExpenseId: Int): Flow<List<TransactionDetails>> =
        transactionReimbursementDao.getCandidateReimbursements(excludeExpenseId)

    override fun getLinkedExpenseForReimbursement(incomeId: Int): Flow<TransactionDetails?> =
        transactionReimbursementDao.getLinkedExpenseForReimbursement(incomeId)

    /**
     * Links [incomeId] as a reimbursement for [expenseId].
     * Business calculation and lifecycle management delegated to [ManageReimbursementUseCase].
     */
    @Deprecated(
        message = "Use ManageReimbursementUseCase directly from presentation/domain layer.",
        replaceWith = ReplaceWith("manageReimbursementUseCase.linkReimbursement(incomeId, expenseId)"),
    )
    override suspend fun linkReimbursement(
        incomeId: Int,
        expenseId: Int,
    ) {
        manageReimbursementUseCase.linkReimbursement(incomeId, expenseId)
    }

    /**
     * Removes the reimbursement link from [incomeId].
     * Business calculation and lifecycle management delegated to [ManageReimbursementUseCase].
     */
    @Deprecated(
        message = "Use ManageReimbursementUseCase directly from presentation/domain layer.",
        replaceWith = ReplaceWith("manageReimbursementUseCase.unlinkReimbursement(incomeId)"),
    )
    override suspend fun unlinkReimbursement(incomeId: Int) {
        manageReimbursementUseCase.unlinkReimbursement(incomeId)
    }

    // --- NEW: Smart Transaction Merge ---
    override suspend fun findRecentTransactionForMerge(
        merchant: String,
        accountId: Int,
        transactionType: TransactionType,
        timeWindowStart: Long,
        newTxnId: Int
    ): Transaction? {
        return transactionQueryDao.findRecentTransactionForMerge(merchant, accountId, transactionType, timeWindowStart, newTxnId)
    }

    override suspend fun dismissMerge(id: Int) {
        transactionWriteDao.updateMergeDismissed(id, true)
    }

    // ─── Self Transfer Detection & Linkage ─────────────────────────────────

    override suspend fun findPotentialTransfers(
        amount: Double,
        accountId: Int,
        transactionType: TransactionType,
        startTime: Long,
        endTime: Long,
    ): List<Transaction> =
        withContext(dispatcherProvider.io) {
            transactionQueryDao.findPotentialTransfers(
                amount = amount,
                accountId = accountId,
                transactionType = transactionType,
                startTime = startTime,
                endTime = endTime,
            )
        }

    override suspend fun linkTransfer(
        primaryTxnId: Int,
        secondaryTxnId: Int,
    ) = withContext(dispatcherProvider.io) {
        db.withTransaction {
            transactionWriteDao.updateTransferLinkStatus(primaryTxnId, secondaryTxnId, true)
            transactionWriteDao.updateTransferLinkStatus(secondaryTxnId, primaryTxnId, true)
        }
    }
}
