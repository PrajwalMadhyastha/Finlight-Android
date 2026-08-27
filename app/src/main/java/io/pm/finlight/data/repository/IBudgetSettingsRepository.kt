package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IBudgetSettingsRepository {
    suspend fun saveOverallBudgetForCurrentMonth(amount: Float)

    suspend fun saveOverallBudgetForMonth(
        year: Int,
        month: Int,
        amount: Float,
    )

    suspend fun getOverallBudgetsForYear(year: Int): Map<Int, Float>

    fun getOverallBudgetForMonth(
        year: Int,
        month: Int,
    ): Flow<Float?>
}
