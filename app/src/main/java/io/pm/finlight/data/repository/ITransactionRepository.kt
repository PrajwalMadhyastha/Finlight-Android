package io.pm.finlight

import io.pm.finlight.data.model.MerchantPrediction
import kotlinx.coroutines.flow.Flow

interface ITransactionRepository {
    val allTransactions: Flow<List<TransactionDetails>>
    val recentTransactions: Flow<List<TransactionDetails>>

    suspend fun getTotalExpensesSince(startDate: Long): Double

    fun searchMerchants(query: String): Flow<List<MerchantPrediction>>

    suspend fun deleteByIds(transactionIds: List<Int>)

    fun getTransactionWithSplits(transactionId: Int): Flow<TransactionWithSplits?>

    fun getFirstTransactionDate(): Flow<Long?>

    fun getFinancialSummaryForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<FinancialSummary?>

    fun getTopSpendingCategoriesForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<CategorySpending?>

    fun getIncomeTransactionsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>>

    fun getIncomeByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<CategorySpending>>

    fun getSpendingByMerchantForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<MerchantSpendingSummary>>

    suspend fun addImageToTransaction(
        transactionId: Int,
        imageUri: String,
    )

    suspend fun deleteImage(transactionImage: TransactionImage)

    fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>>

    suspend fun updateDescription(
        id: Int,
        description: String,
    )

    suspend fun updateAmount(
        id: Int,
        amount: Double,
    )

    suspend fun updateManualAmountEdit(
        id: Int,
        amount: Double,
    )

    suspend fun updateNotes(
        id: Int,
        notes: String?,
    )

    suspend fun updateCategoryId(
        id: Int,
        categoryId: Int?,
    )

    suspend fun updateAccountId(
        id: Int,
        accountId: Int,
    )

    suspend fun updateDate(
        id: Int,
        date: Long,
    )

    suspend fun updateExclusionStatus(
        id: Int,
        isExcluded: Boolean,
    )

    suspend fun updateTransactionType(
        id: Int,
        transactionType: TransactionType,
    )

    suspend fun clearReviewFlag(id: Int)

    fun getTransactionDetailsById(id: Int): Flow<TransactionDetails?>

    fun getAllSmsHashes(): Flow<List<String>>

    fun getTransactionsForAccountDetails(accountId: Int): Flow<List<TransactionDetails>>

    fun getTransactionDetailsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>>

    fun getAllTransactionsForRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<Transaction>>

    fun getTransactionById(id: Int): Flow<Transaction?>

    suspend fun getTransactionSync(id: Int): Transaction?

    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>>

    fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<CategorySpending>>

    fun getMonthlyTrends(startDate: Long): Flow<List<MonthlyTrend>>

    suspend fun countTransactionsForCategory(categoryId: Int): Int

    fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>>

    suspend fun getTagsForTransactionSimple(transactionId: Int): List<Tag>

    suspend fun updateTagsForTransaction(
        transactionId: Int,
        tags: Set<Tag>,
    )

    suspend fun insertTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    ): Long

    suspend fun updateTransactionWithTags(
        transaction: Transaction,
        tags: Set<Tag>,
    )

    suspend fun insertTransactionWithTagsAndImages(
        transaction: Transaction,
        tags: Set<Tag>,
        imagePaths: List<String>,
    ): Long

    suspend fun delete(transaction: Transaction)

    suspend fun setSmsHash(
        transactionId: Int,
        smsHash: String,
    )

    fun getTransactionCountForMerchant(description: String): Flow<Int>

    suspend fun findSimilarTransactions(
        description: String,
        excludeId: Int,
    ): List<Transaction>

    suspend fun getDistinctOriginalDescriptions(): List<String>

    suspend fun getTransactionIdsByOriginalDescription(originalDesc: String): List<Int>

    suspend fun updateCategoryForIds(
        ids: List<Int>,
        categoryId: Int,
    )

    suspend fun updateDescriptionForIds(
        ids: List<Int>,
        newDescription: String,
    )

    fun getTransactionCountsByOriginalDescription(): Flow<Map<String, Int>>

    fun getTransactionsByOriginalDescription(originalDesc: String): Flow<List<TransactionDetails>>

    suspend fun updateDescriptionByOriginalDescription(
        originalDesc: String,
        newDescription: String,
        oldDescription: String? = null,
    ): Int

    suspend fun syncDescriptionsWithRenameRules(): Int

    fun getDailySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<DailyTotal>>

    suspend fun addTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    )

    suspend fun removeTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    )

    fun getTransactionsByTagId(tagId: Int): Flow<List<TransactionDetails>>

    suspend fun removeAllTransactionsForTag(tagId: Int)

    fun getRecentManualTransactions(limit: Int): Flow<List<TransactionDetails>>

    fun getReimbursementsForExpense(expenseId: Int): Flow<List<TransactionDetails>>

    fun getCandidateReimbursements(excludeExpenseId: Int): Flow<List<TransactionDetails>>

    fun getLinkedExpenseForReimbursement(incomeId: Int): Flow<TransactionDetails?>

    @Deprecated(
        message = "Use ManageReimbursementUseCase directly from presentation/domain layer.",
        replaceWith = ReplaceWith("manageReimbursementUseCase.linkReimbursement(incomeId, expenseId)"),
    )
    suspend fun linkReimbursement(
        incomeId: Int,
        expenseId: Int,
    )

    @Deprecated(
        message = "Use ManageReimbursementUseCase directly from presentation/domain layer.",
        replaceWith = ReplaceWith("manageReimbursementUseCase.unlinkReimbursement(incomeId)"),
    )
    suspend fun unlinkReimbursement(incomeId: Int)

    suspend fun findRecentTransactionForMerge(
        merchant: String,
        accountId: Int,
        transactionType: TransactionType,
        timeWindowStart: Long,
        newTxnId: Int,
    ): Transaction?

    suspend fun dismissMerge(id: Int)

    suspend fun findPotentialTransfers(
        amount: Double,
        accountId: Int,
        transactionType: TransactionType,
        startTime: Long,
        endTime: Long,
    ): List<Transaction>

    suspend fun linkTransfer(
        primaryTxnId: Int,
        secondaryTxnId: Int,
    )

    /**
     * Observes all draft pending transactions requiring user review, sorted by date descending.
     *
     * @return A cold [Flow] emitting the current list of [Transaction]s with [TransactionStatus.PENDING].
     */
    fun getPendingTransactionsFlow(): Flow<List<Transaction>>

    /**
     * Atomically confirms a draft transaction, optionally updating its amount beforehand
     * (e.g., when the user adjusts a variable bill amount before confirming).
     *
     * Both the optional amount update and status change to [TransactionStatus.COMPLETED]
     * are executed atomically inside a database transaction on the IO dispatcher.
     *
     * @param transactionId The ID of the pending draft transaction to confirm.
     * @param confirmedAmount The verified transaction amount if modified by the user, or null to keep existing draft amount.
     */
    suspend fun confirmTransaction(
        transactionId: Int,
        confirmedAmount: Double? = null,
    )

    /**
     * Marks a draft pending transaction as skipped ([TransactionStatus.SKIPPED]) without deleting it.
     *
     * Executed on the IO dispatcher.
     *
     * @param transactionId The ID of the pending draft transaction to skip.
     */
    suspend fun skipTransaction(transactionId: Int)
}
