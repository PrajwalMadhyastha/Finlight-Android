// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TransactionRepository.kt
// REASON: REFACTOR (Issue #242) - Extracted business logic (monthly consistency
// calculations and merge/unmerge operations) into dedicated UseCases
// (`GetMonthlyConsistencyDataUseCase` and `MergeTransactionsUseCase`).
// =================================================================================
package io.pm.finlight

import android.util.Log
import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.MerchantPrediction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.Locale
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao

import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.withContext

class TransactionRepository(
    private val transactionWriteDao: TransactionWriteDao,
    private val transactionQueryDao: TransactionQueryDao,
    private val transactionAnalyticsDao: TransactionAnalyticsDao,
    private val transactionReimbursementDao: TransactionReimbursementDao,
    private val db: AppDatabase,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ITransactionRepository {
    @Deprecated("Use domain DAO constructor", level = DeprecationLevel.WARNING)
    constructor(
        transactionDao: TransactionDao,
        db: AppDatabase,
        dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    ) : this(
        transactionWriteDao = transactionDao,
        transactionQueryDao = transactionDao,
        transactionAnalyticsDao = transactionDao,
        transactionReimbursementDao = transactionDao,
        db = db,
        dispatcherProvider = dispatcherProvider,
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
     * Links [incomeId] as a reimbursement for [expenseId]:
     * - If incomeTxn.amount > expenseTxn.amount (over-repayment):
     *   - Offsets expenseTxn to 0.0 (fully settled).
     *   - Adjusts incomeTxn amount to the offset portion, marks it isExcluded = true.
     *   - Creates an active surplus INCOME transaction for (incomeTxn.amount - offset).
     *   - Links the surplus transaction to the reimbursement income via linkedSurplusTxnId.
     * - Else:
     *   - Deducts the full income amount from the expense.
     *   - Marks incomeTxn as isExcluded = true and parentReimbursementId = expenseId.
     */
    override suspend fun linkReimbursement(
        incomeId: Int,
        expenseId: Int,
    ) {
        val incomeTxn = transactionQueryDao.getTransactionByIdSync(incomeId) ?: return
        val expenseTxn = transactionQueryDao.getTransactionByIdSync(expenseId) ?: return

        if (incomeTxn.amount > expenseTxn.amount) {
            val offset = expenseTxn.amount
            val surplus = incomeTxn.amount - offset

            val surplusTxn =
                Transaction(
                    description = "${incomeTxn.description} (Surplus)",
                    amount = surplus,
                    date = incomeTxn.date,
                    accountId = incomeTxn.accountId,
                    categoryId = incomeTxn.categoryId,
                    transactionType = TransactionType.INCOME,
                    isExcluded = false,
                    notes = "Surplus from repayment for ${expenseTxn.description}",
                    source = "Surplus Allocation",
                    status = TransactionStatus.CONFIRMED,
                )
            val surplusId = transactionWriteDao.insert(surplusTxn).toInt()

            transactionWriteDao.updateAmount(incomeId, offset)
            transactionReimbursementDao.linkReimbursement(incomeId, expenseId, surplusId)
            transactionWriteDao.updateAmount(expenseId, 0.0)
        } else {
            transactionReimbursementDao.linkReimbursement(incomeId, expenseId, null)
            val newExpenseAmount = expenseTxn.amount - incomeTxn.amount
            transactionWriteDao.updateAmount(expenseId, newExpenseAmount)
        }
    }

    /**
     * Removes the reimbursement link from [incomeId]:
     * - If a linked surplus transaction exists, deletes it and merges its amount back.
     * - Clears parentReimbursementId, linkedSurplusTxnId and removes the excluded flag.
     * - Adds the offset amount back onto the parent expense.
     */
    override suspend fun unlinkReimbursement(incomeId: Int) {
        val incomeTxn = transactionQueryDao.getTransactionByIdSync(incomeId) ?: return
        val parentId = incomeTxn.parentReimbursementId ?: return
        val expenseTxn = transactionQueryDao.getTransactionByIdSync(parentId) ?: return

        var totalIncomeToRestore = incomeTxn.amount
        val surplusId = incomeTxn.linkedSurplusTxnId
        if (surplusId != null) {
            val surplusTxn = transactionQueryDao.getTransactionByIdSync(surplusId)
            if (surplusTxn != null) {
                totalIncomeToRestore += surplusTxn.amount
                transactionWriteDao.delete(surplusTxn)
            }
        }

        transactionWriteDao.updateAmount(incomeId, totalIncomeToRestore)
        transactionReimbursementDao.unlinkReimbursement(incomeId)
        val restoredExpenseAmount = expenseTxn.amount + incomeTxn.amount
        transactionWriteDao.updateAmount(parentId, restoredExpenseAmount)
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

    // ─── Self Transfer Detection ──────────────────────────────────────────

    /**
     * Automatically detects if a newly inserted transaction is part of a self-transfer
     * between two user accounts, based on a two-tiered logic:
     * 1. Strict Time (<= 5 mins): Matches exactly on Amount.
     * 2. Loose Time (<= 6 hours): Matches on Amount AND Account Alias/Name text.
     */
    override suspend fun detectAndLinkSelfTransfer(newTxn: Transaction) =
        withContext(dispatcherProvider.io) {
            if (newTxn.sourceSmsId == null || newTxn.linkedTransferId != null || newTxn.isExcluded || newTxn.isSplit) return@withContext

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
                    endTime = endTime,
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
