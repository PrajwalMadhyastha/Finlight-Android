package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IBudgetRepository {
    fun getBudgetsForMonth(
        month: Int,
        year: Int,
    ): Flow<List<Budget>>

    fun getBudgetsForMonthWithSpending(
        yearMonth: String,
        month: Int,
        year: Int,
    ): Flow<List<BudgetWithSpending>>

    fun getActualSpendingForCategory(
        categoryName: String,
        month: Int,
        year: Int,
    ): Flow<Double?>

    suspend fun update(budget: Budget)

    suspend fun insert(budget: Budget)

    suspend fun insertAll(budgets: List<Budget>)

    suspend fun getBudgetsForCategoryAndYear(
        categoryName: String,
        year: Int,
    ): List<Budget>

    suspend fun delete(budget: Budget)

    fun getBudgetById(id: Int): Flow<Budget?>
}
