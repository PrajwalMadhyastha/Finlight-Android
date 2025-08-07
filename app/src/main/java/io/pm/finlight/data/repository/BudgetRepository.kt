// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/BudgetRepository.kt
// REASON: FEATURE - Added the `getBudgetsForMonth` function. This is needed by
// the BudgetViewModel to correctly determine which categories are available for
// a new budget, respecting the new carry-over logic.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) {

    // --- NEW: Function to get budgets for a specific month ---
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>> {
        return budgetDao.getBudgetsForMonth(month, year)
    }

    fun getBudgetsForMonthWithSpending(yearMonth: String, month: Int, year: Int): Flow<List<BudgetWithSpending>> {
        return budgetDao.getBudgetsWithSpendingForMonth(yearMonth, month, year)
    }

    fun getActualSpendingForCategory(
        categoryName: String,
        month: Int,
        year: Int,
    ): Flow<Double?> {
        return budgetDao.getActualSpendingForCategory(categoryName, month, year)
    }

    suspend fun update(budget: Budget) {
        budgetDao.update(budget)
    }

    suspend fun insert(budget: Budget) {
        budgetDao.insert(budget)
    }

    suspend fun delete(budget: Budget) {
        budgetDao.delete(budget)
    }

    fun getBudgetById(id: Int): Flow<Budget?> {
        return budgetDao.getById(id)
    }
}
