// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransactionRepository.kt
// REASON: FEATURE - The repository has been updated to expose `getById`,
// `update`, and `delete` functions, providing a complete data access layer
// for managing recurring rules. This resolves the "Unresolved reference" errors
// in the ViewModel.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

class RecurringTransactionRepository(
    private val recurringTransactionDao: RecurringTransactionDao,
    private val recurringPatternDao: RecurringPatternDao,
) : IRecurringTransactionRepository {
    override fun getAll(): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.getAllRulesFlow()
    }

    override fun getById(id: Int): Flow<RecurringTransaction?> {
        return recurringTransactionDao.getById(id)
    }

    override suspend fun insert(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.insert(recurringTransaction)
    }

    override suspend fun update(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.update(recurringTransaction)
    }

    override suspend fun delete(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.delete(recurringTransaction)
    }

    override suspend fun getRuleById(id: Int): RecurringTransaction? {
        return recurringTransactionDao.getRuleById(id)
    }

    override suspend fun updateLastRunDate(
        ruleId: Int,
        lastRunDate: Long,
    ) {
        recurringTransactionDao.updateLastRunDate(ruleId, lastRunDate)
    }

    override suspend fun updateLastRunAndSkipCount(
        ruleId: Int,
        lastRunDate: Long,
        skipCount: Int,
    ) {
        recurringTransactionDao.updateLastRunAndSkipCount(ruleId, lastRunDate, skipCount)
    }

    override fun getUnacknowledgedPatterns(): Flow<List<RecurringPattern>> {
        return recurringPatternDao.getUnacknowledgedPatterns()
    }

    override suspend fun dismissPatternSuggestion(pattern: RecurringPattern) {
        recurringPatternDao.update(pattern.copy(isDismissed = true))
    }

    override suspend fun getRuleBySmsSenderId(senderId: String): RecurringTransaction? =
        recurringTransactionDao.getRuleBySmsSenderId(senderId)

    override suspend fun getAllRulesList(): List<RecurringTransaction> =
        recurringTransactionDao.getAllRulesList()

    override suspend fun insertPattern(pattern: RecurringPattern) {
        recurringPatternDao.insert(pattern)
    }

    override suspend fun updatePattern(pattern: RecurringPattern) {
        recurringPatternDao.update(pattern)
    }

    override suspend fun getPatternBySignature(signature: String): RecurringPattern? =
        recurringPatternDao.getPatternBySignature(signature)

    override suspend fun getAllPatterns(): List<RecurringPattern> =
        recurringPatternDao.getAllPatterns()
}
