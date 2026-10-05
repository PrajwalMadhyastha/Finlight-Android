package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IRecurringTransactionRepository {
    fun getAll(): Flow<List<RecurringTransaction>>

    fun getById(id: Int): Flow<RecurringTransaction?>

    suspend fun insert(recurringTransaction: RecurringTransaction)

    suspend fun update(recurringTransaction: RecurringTransaction)

    suspend fun delete(recurringTransaction: RecurringTransaction)

    /**
     * Retrieves a recurring transaction rule by its unique identifier.
     *
     * @param id The primary key ID of the recurring transaction rule.
     * @return The [RecurringTransaction] matching [id], or null if not found.
     */
    suspend fun getRuleById(id: Int): RecurringTransaction?

    /**
     * Updates the last executed timestamp of a recurring transaction rule without altering
     * its skip count or schedule configuration.
     *
     * @param ruleId The primary key ID of the recurring transaction rule.
     * @param lastRunDate The epoch millisecond timestamp representing when the rule was last executed or snoozed.
     */
    suspend fun updateLastRunDate(
        ruleId: Int,
        lastRunDate: Long,
    )

    /**
     * Atomically updates both the last executed timestamp and skip count of a recurring transaction rule
     * in a single database update.
     *
     * @param ruleId The primary key ID of the recurring transaction rule.
     * @param lastRunDate The epoch millisecond timestamp representing the new last run date.
     * @param skipCount The updated consecutive skip counter for this rule.
     */
    suspend fun updateLastRunAndSkipCount(
        ruleId: Int,
        lastRunDate: Long,
        skipCount: Int,
    )
}
