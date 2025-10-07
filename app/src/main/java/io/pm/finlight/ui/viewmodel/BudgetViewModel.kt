// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/BudgetViewModel.kt
// REASON: FEATURE (Error Handling) - Added a UI event channel and wrapped all
// data modification methods (`addCategoryBudget`, `updateBudget`, `deleteBudget`)
// in try-catch blocks. This ensures repository exceptions are handled gracefully
// and communicated to the user, enhancing app stability.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val calendar: Calendar = Calendar.getInstance()
    private val currentMonth: Int
    private val currentYear: Int

    val budgetsForCurrentMonth: StateFlow<List<BudgetWithSpending>>
    val overallBudget: StateFlow<Long>
    val allCategories: Flow<List<Category>>
    val availableCategoriesForNewBudget: Flow<List<Category>>
    val totalSpending: StateFlow<Long>

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
                .map { it.roundToLong() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0L,
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
                flowOf(0L)
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
            initialValue = 0L
        )
    }

    fun getActualSpending(categoryName: String): Flow<Long> {
        return budgetRepository.getActualSpendingForCategory(categoryName, currentMonth, currentYear)
            .map { (it ?: 0.0).roundToLong() }
    }

    fun addCategoryBudget(
        categoryName: String,
        amountStr: String,
    ) {
        viewModelScope.launch {
            try {
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0 || categoryName.isBlank()) {
                    _uiEvent.send("Please enter a valid amount and select a category.")
                    return@launch
                }
                val newBudget =
                    Budget(
                        categoryName = categoryName,
                        amount = amount,
                        month = currentMonth,
                        year = currentYear,
                    )
                budgetRepository.insert(newBudget)
                _uiEvent.send("Budget for '$categoryName' added.")
            } catch (e: Exception) {
                _uiEvent.send("Error adding budget: ${e.message}")
            }
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
            try {
                budgetRepository.update(budget)
                _uiEvent.send("Budget for '${budget.categoryName}' updated.")
            } catch (e: Exception) {
                _uiEvent.send("Error updating budget: ${e.message}")
            }
        }

    fun deleteBudget(budget: Budget) =
        viewModelScope.launch {
            try {
                budgetRepository.delete(budget)
                _uiEvent.send("Budget for '${budget.categoryName}' deleted.")
            } catch (e: Exception) {
                _uiEvent.send("Error deleting budget: ${e.message}")
            }
        }
}