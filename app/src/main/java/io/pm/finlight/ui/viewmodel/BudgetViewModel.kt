// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/BudgetViewModel.kt
// REASON: FEATURE - The logic for `availableCategoriesForNewBudget` has been
// updated. It now correctly identifies categories that do not have a budget set
// for the current month, allowing users to override a carried-over budget by
// creating a new one.
// REFACTOR (Testing) - The ViewModel now uses constructor dependency injection,
// accepting its repository dependencies instead of creating them internally. This
// decouples it from direct database access and allows for easier unit testing
// with mock repositories, resolving the AndroidKeyStore crash in tests.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val calendar: Calendar = Calendar.getInstance()
    private val currentMonth: Int
    private val currentYear: Int

    val budgetsForCurrentMonth: StateFlow<List<BudgetWithSpending>>
    val overallBudget: StateFlow<Float>
    val allCategories: Flow<List<Category>>
    val availableCategoriesForNewBudget: Flow<List<Category>>
    val totalSpending: StateFlow<Double>

    init {
        currentMonth = calendar.get(Calendar.MONTH) + 1
        currentYear = calendar.get(Calendar.YEAR)

        val yearMonthString = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

        budgetsForCurrentMonth = budgetRepository.getBudgetsForMonthWithSpending(yearMonthString, currentMonth, currentYear)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        allCategories = categoryRepository.allCategories

        overallBudget =
            settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0f,
                )

        // --- UPDATED: This logic now correctly determines which categories can have a NEW budget ---
        // It checks against budgets set specifically for the current month, allowing users
        // to override a carried-over budget.
        val budgetsSetInCurrentMonth = budgetRepository.getBudgetsForMonth(currentMonth, currentYear)
        availableCategoriesForNewBudget =
            combine(allCategories, budgetsSetInCurrentMonth) { categories, budgets ->
                val budgetedCategoryNames = budgets.map { it.categoryName }.toSet()
                categories.filter { category -> category.name !in budgetedCategoryNames }
            }

        totalSpending = budgetsForCurrentMonth.flatMapLatest { budgets ->
            if (budgets.isEmpty()) {
                flowOf(0.0)
            } else {
                val spendingFlows = budgets.map {
                    getActualSpending(it.budget.categoryName)
                }
                combine(spendingFlows) { amounts ->
                    amounts.sum()
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )
    }

    fun getActualSpending(categoryName: String): Flow<Double> {
        return budgetRepository.getActualSpendingForCategory(categoryName, currentMonth, currentYear)
            .map { spending -> spending ?: 0.0 }
    }

    fun addCategoryBudget(
        categoryName: String,
        amountStr: String,
    ) {
        val amount = amountStr.toDoubleOrNull() ?: return
        if (amount <= 0 || categoryName.isBlank()) {
            return
        }
        val newBudget =
            Budget(
                categoryName = categoryName,
                amount = amount,
                month = currentMonth,
                year = currentYear,
            )
        viewModelScope.launch {
            budgetRepository.insert(newBudget)
        }
    }

    fun saveOverallBudget(budgetStr: String) {
        val budgetFloat = budgetStr.toFloatOrNull() ?: 0f
        settingsRepository.saveOverallBudgetForCurrentMonth(budgetFloat)
    }

    fun getBudgetById(id: Int): Flow<Budget?> {
        return budgetRepository.getBudgetById(id)
    }

    fun updateBudget(budget: Budget) =
        viewModelScope.launch {
            budgetRepository.update(budget)
        }

    fun deleteBudget(budget: Budget) =
        viewModelScope.launch {
            budgetRepository.delete(budget)
        }
}
