package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IFeatureSettingsRepository {
    suspend fun saveRecurringTransactionsEnabled(isEnabled: Boolean)

    fun getRecurringTransactionsEnabled(): Flow<Boolean>

    suspend fun saveGoalIncomeThreshold(amount: Int)

    fun getGoalIncomeThreshold(): Flow<Int>

    suspend fun saveGoalNudgesEnabled(isEnabled: Boolean)

    fun getGoalNudgesEnabled(): Flow<Boolean>

    suspend fun saveLastSurplusPrompt(
        year: Int,
        month: Int
    )

    fun getLastSurplusPrompt(): Flow<Pair<Int, Int>?>

    fun getExcludedIncomeMonths(): Flow<Set<String>>

    fun getExcludedExpenseMonths(): Flow<Set<String>>

    suspend fun toggleIncomeMonthExclusion(monthKey: String)

    suspend fun toggleExpenseMonthExclusion(monthKey: String)

    suspend fun getNotifiedPatternSignatures(): Set<String>

    suspend fun markPatternNotified(signature: String)

    suspend fun clearNotifiedPatternSignatures()
}
