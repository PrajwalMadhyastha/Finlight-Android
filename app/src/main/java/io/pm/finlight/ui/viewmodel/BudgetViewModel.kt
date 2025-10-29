// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/BudgetViewModel.kt
// REASON: FEATURE (Historical Budgets) - The `saveOverallBudget` function has
// been refactored. It now takes a `Calendar` object, extracts the year and
// month, and calls the new `settingsRepository.saveOverallBudgetForMonth`
// function. This allows the ViewModel to save a budget for *any* month the
// user has selected.
//
// REASON: FIX (Consistency) - The `overallBudget` collector is updated to
// handle the new nullable `Float?` from `SettingsRepository`. It now uses
// `map { (it ?: 0f).roundToLong() }` to safely convert the nullable value to a
// non-null `Long` for the UI, resolving a build error.
//
// REASON: REFACTOR (Dynamic Budget) - The ViewModel is refactored to be dynamic.
// - It now holds a `selectedMonth` state, just like `TransactionViewModel`.
// - All data flows (`budgetsForSelectedMonth`, `overallBudgetForSelectedMonth`, etc.)
//   are now `flatMapLatest` streams that react to changes in `selectedMonth`.
// - `overallBudgetForSelectedMonth` is now a `StateFlow<Float?>`, correctly
//   emitting `null` when no budget is set for the selected period.
// - Adds `monthlySummaries` flow to power the new month navigation header.
//
// REASON: FIX (Build) - Corrected `receiveAsStateFlow()` to `receiveAsFlow()`
// to resolve an "Unresolved reference" compilation error.
//
// REASON: FIX (Bug) - The `monthlySummaries` flow was incorrectly showing
// total *spent* for each month. It has been rewritten to be a
// `StateFlow<List<Pair<Calendar, Float?>>>` that correctly fetches the
// *budget* for each month from the SettingsRepository, including carry-over logic.
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
    categoryRepository: CategoryRepository,
    // --- NEW: Add TransactionRepository dependency ---
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    // --- FIX: Corrected typo from receiveAsStateFlow to receiveAsFlow ---
    val uiEvent = _uiEvent.receiveAsFlow()

    // --- NEW: Add dynamic month selection ---
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth: StateFlow<Calendar> = _selectedMonth.asStateFlow()

    val monthlySummaries: StateFlow<List<Pair<Calendar, Float?>>>

    // --- REFACTORED: All flows are now dynamic based on selectedMonth ---
    val budgetsForSelectedMonth: StateFlow<List<BudgetWithSpending>>
    val overallBudgetForSelectedMonth: StateFlow<Float?>
    val allCategories: Flow<List<Category>>
    val availableCategoriesForNewBudget: Flow<List<Category>>
    val totalSpendingForSelectedMonth: StateFlow<Long>

    init {
        // --- REFACTORED: Logic to fetch monthly budgets for the scroller ---
        monthlySummaries = transactionRepository.getFirstTransactionDate().flatMapLatest { firstTransactionDate ->
            val startDate = firstTransactionDate ?: System.currentTimeMillis()
            val monthList = mutableListOf<Calendar>()
            val startCal = Calendar.getInstance().apply { timeInMillis = startDate; set(Calendar.DAY_OF_MONTH, 1) }
            val endCal = Calendar.getInstance()

            while (startCal.before(endCal) || (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH))) {
                monthList.add(startCal.clone() as Calendar)
                startCal.add(Calendar.MONTH, 1)
            }

            // Create a list of flows, one for each month's budget
            val budgetFlows: List<Flow<Pair<Calendar, Float?>>> = monthList.map { cal ->
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                settingsRepository.getOverallBudgetForMonth(year, month).map { budget ->
                    Pair(cal, budget) // Pair the calendar with its fetched budget
                }
            }

            if (budgetFlows.isEmpty()) {
                flowOf(emptyList())
            } else {
                // Combine all budget flows into a single flow that emits the full list
                combine(budgetFlows) { summaries ->
                    summaries.toList().reversed() // Reverse to show most recent first
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


        // --- REFACTORED: Use flatMapLatest on _selectedMonth ---
        budgetsForSelectedMonth = _selectedMonth.flatMapLatest { calendar ->
            val yearMonthString = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            budgetRepository.getBudgetsForMonthWithSpending(yearMonthString, month, year)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allCategories = categoryRepository.allCategories

        // --- REFACTORED: Use flatMapLatest on _selectedMonth and return nullable Float ---
        overallBudgetForSelectedMonth = _selectedMonth.flatMapLatest {
            val month = it.get(Calendar.MONTH) + 1
            val year = it.get(Calendar.YEAR)
            settingsRepository.getOverallBudgetForMonth(year, month)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

        // --- REFACTORED: Combine dynamic flows ---
        availableCategoriesForNewBudget =
            combine(allCategories, budgetsForSelectedMonth) { categories, budgetsWithSpending ->
                val budgetedCategoryNames = budgetsWithSpending.map { it.budget.categoryName }.toSet()
                categories.filter { category -> category.name !in budgetedCategoryNames }
            }

        // --- REFACTORED: Use flatMapLatest on dynamic budgetsForSelectedMonth ---
        totalSpendingForSelectedMonth = budgetsForSelectedMonth.flatMapLatest { budgets ->
            if (budgets.isEmpty()) {
                flowOf(0L)
            } else {
                val spendingFlows = budgets.map {
                    // Use getActualSpending for the selected month
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

    // --- NEW: Add function to update selected month ---
    fun setSelectedMonth(calendar: Calendar) {
        _selectedMonth.value = calendar
    }

    // --- REFACTORED: Use selectedMonth state ---
    fun getActualSpending(categoryName: String): Flow<Long> {
        val month = _selectedMonth.value.get(Calendar.MONTH) + 1
        val year = _selectedMonth.value.get(Calendar.YEAR)
        return budgetRepository.getActualSpendingForCategory(categoryName, month, year)
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
                // --- REFACTORED: Use selectedMonth state ---
                val month = _selectedMonth.value.get(Calendar.MONTH) + 1
                val year = _selectedMonth.value.get(Calendar.YEAR)
                val newBudget =
                    Budget(
                        categoryName = categoryName,
                        amount = amount,
                        month = month,
                        year = year,
                    )
                budgetRepository.insert(newBudget)
                _uiEvent.send("Budget for '$categoryName' added.")
            } catch (e: Exception) {
                _uiEvent.send("Error adding budget: ${e.message}")
            }
        }
    }

    // --- REFACTORED: Function signature changed and logic updated ---
    fun saveOverallBudget(budgetStr: String, forCalendar: Calendar) {
        val budgetFloat = budgetStr.toFloatOrNull() ?: 0f
        val year = forCalendar.get(Calendar.YEAR)
        val month = forCalendar.get(Calendar.MONTH) + 1

        // Call the new repository function to save for the specific month
        settingsRepository.saveOverallBudgetForMonth(year, month, budgetFloat)
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
