package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IFeatureSettingsRepository {
    suspend fun saveRecurringTransactionsEnabled(isEnabled: Boolean)

    fun getRecurringTransactionsEnabled(): Flow<Boolean>

    suspend fun saveGoalIncomeThreshold(amount: Int)

    fun getGoalIncomeThreshold(): Flow<Int>

    suspend fun saveGoalNudgesEnabled(isEnabled: Boolean)

    fun getGoalNudgesEnabled(): Flow<Boolean>

    fun getExcludedIncomeMonths(): Flow<Set<String>>

    fun getExcludedExpenseMonths(): Flow<Set<String>>

    suspend fun toggleIncomeMonthExclusion(monthKey: String)

    suspend fun toggleExpenseMonthExclusion(monthKey: String)
}
