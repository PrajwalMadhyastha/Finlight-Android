// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/BudgetRepository.kt
// REASON: FEATURE - Added the `getBudgetsForMonth` function. This is needed by
// the BudgetViewModel to correctly determine which categories are available for
// a new budget, respecting the new carry-over logic.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) : IBudgetRepository {
    // --- NEW: Function to get budgets for a specific month ---
    override fun getBudgetsForMonth(
        month: Int,
        year: Int,
    ): Flow<List<Budget>> {
        return budgetDao.getBudgetsForMonth(month, year)
    }

    override fun getBudgetsForMonthWithSpending(
        yearMonth: String,
        month: Int,
        year: Int,
    ): Flow<List<BudgetWithSpending>> {
        return budgetDao.getBudgetsWithSpendingForMonth(yearMonth, month, year)
    }

    override fun getActualSpendingForCategory(
        categoryName: String,
        month: Int,
        year: Int,
    ): Flow<Double?> {
        return budgetDao.getActualSpendingForCategory(categoryName, month, year)
    }

    override suspend fun update(budget: Budget) {
        budgetDao.update(budget)
    }

    override suspend fun insert(budget: Budget) {
        budgetDao.insert(budget)
    }

    override suspend fun insertAll(budgets: List<Budget>) {
        budgetDao.insertAll(budgets)
    }

    override suspend fun getBudgetsForCategoryAndYear(
        categoryName: String,
        year: Int,
    ): List<Budget> {
        return budgetDao.getBudgetsForCategoryAndYear(categoryName, year)
    }

    override suspend fun delete(budget: Budget) {
        budgetDao.delete(budget)
    }

    override fun getBudgetById(id: Int): Flow<Budget?> {
        return budgetDao.getById(id)
    }
}
