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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.util.Locale

import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao

class TransactionRepository(
    private val transactionWriteDao: TransactionWriteDao,
    private val transactionQueryDao: TransactionQueryDao,
    private val transactionAnalyticsDao: TransactionAnalyticsDao,
    private val transactionReimbursementDao: TransactionReimbursementDao,
    private val settingsRepository: SettingsRepository,
    private val tagRepository: TagRepository,
    private val db: AppDatabase,
) {
    @Deprecated("Use domain DAO constructor without deletedSmsHashDao/mergeRecordDao", level = DeprecationLevel.WARNING)
    constructor(
        transactionWriteDao: TransactionWriteDao,
        transactionQueryDao: TransactionQueryDao,
        transactionAnalyticsDao: TransactionAnalyticsDao,
        transactionReimbursementDao: TransactionReimbursementDao,
        settingsRepository: SettingsRepository,
        tagRepository: TagRepository,
        deletedSmsHashDao: DeletedSmsHashDao,
        mergeRecordDao: MergeRecordDao,
        db: AppDatabase,
    ) : this(
        transactionWriteDao = transactionWriteDao,
        transactionQueryDao = transactionQueryDao,
        transactionAnalyticsDao = transactionAnalyticsDao,
        transactionReimbursementDao = transactionReimbursementDao,
        settingsRepository = settingsRepository,
        tagRepository = tagRepository,
        db = db,
    )

    @Deprecated("Use domain DAO constructor", level = DeprecationLevel.WARNING)
    constructor(
        transactionDao: TransactionDao,
        settingsRepository: SettingsRepository,
        tagRepository: TagRepository,
        deletedSmsHashDao: DeletedSmsHashDao,
        mergeRecordDao: MergeRecordDao,
        db: AppDatabase,
    ) : this(
        transactionWriteDao = transactionDao,
        transactionQueryDao = transactionDao,
        transactionAnalyticsDao = transactionDao,
        transactionReimbursementDao = transactionDao,
        settingsRepository = settingsRepository,
        tagRepository = tagRepository,
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
        val transactionId = transactionWriteDao.insert(transaction)
        if (finalTags.isNotEmpty()) {
            val crossRefs =
                finalTags.map { tag ->
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
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        transactionWriteDao.update(transaction)
        transactionWriteDao.clearTagsForTransaction(transaction.id)
        if (finalTags.isNotEmpty()) {
            val crossRefs =
                finalTags.map { tag ->
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
        val finalTags = getFinalTagsForTransaction(transaction, tags)
        val newTransactionId = transactionWriteDao.insert(transaction)
        if (finalTags.isNotEmpty()) {
            val crossRefs =
                finalTags.map { tag ->
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
