// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/IncomeViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel has been refactored to use
// constructor dependency injection for its repository dependencies. It now extends
// ViewModel instead of AndroidViewModel, decoupling it from the Android framework
// and making it fully unit-testable.
//
// REASON: MODIFIED - Added SettingsRepository dependency and exposed
// `isPrivacyModeEnabled` to allow the UI to hide sensitive amounts.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeViewModel(
    private val transactionRepository: TransactionRepository,
    val accountRepository: AccountRepository,
    val categoryRepository: CategoryRepository,
    // --- NEW: Add SettingsRepository dependency ---
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth: StateFlow<Calendar> = _selectedMonth.asStateFlow()

    private val _filterState = MutableStateFlow(TransactionFilterState())
    val filterState: StateFlow<TransactionFilterState> = _filterState.asStateFlow()

    // --- NEW: Expose privacy mode state ---
    val isPrivacyModeEnabled: StateFlow<Boolean> =
        settingsRepository.getPrivacyModeEnabled()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    private val combinedState: Flow<Pair<Calendar, TransactionFilterState>> =
        _selectedMonth.combine(_filterState) { month, filters ->
            Pair(month, filters)
        }

    val allAccounts: StateFlow<List<Account>>
    val allCategories: Flow<List<Category>>

    val incomeTransactionsForSelectedMonth: StateFlow<List<TransactionDetails>> = combinedState.flatMapLatest { (calendar, filters) ->
        val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
        transactionRepository.getIncomeTransactionsForRange(monthStart, monthEnd, filters.keyword.takeIf { it.isNotBlank() }, filters.account?.id, filters.category?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncomeForSelectedMonth: StateFlow<Long> = incomeTransactionsForSelectedMonth.map { transactions ->
        transactions.sumOf { it.transaction.amount }.roundToLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val incomeByCategoryForSelectedMonth: StateFlow<List<CategorySpending>> = combinedState.flatMapLatest { (calendar, filters) ->
        val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
        transactionRepository.getIncomeByCategoryForMonth(monthStart, monthEnd, filters.keyword.takeIf { it.isNotBlank() }, filters.account?.id, filters.category?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummaries: StateFlow<List<MonthlySummaryItem>>

    init {
        allAccounts = accountRepository.allAccounts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allCategories = categoryRepository.allCategories

        monthlySummaries = transactionRepository.getFirstTransactionDate().flatMapLatest { firstTransactionDate ->
            val startDate = firstTransactionDate ?: System.currentTimeMillis()

            transactionRepository.getMonthlyTrends(startDate)
                .map { trends ->
                    val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val monthMap = trends.associate {
                        val cal = Calendar.getInstance().apply { time = dateFormat.parse(it.monthYear) ?: Date() }
                        (cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)) to it.totalIncome
                    }

                    val monthList = mutableListOf<MonthlySummaryItem>()
                    val startCal = Calendar.getInstance().apply { timeInMillis = startDate }
                    startCal.set(Calendar.DAY_OF_MONTH, 1)
                    val endCal = Calendar.getInstance()

                    while (startCal.before(endCal) || (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH))) {
                        val key = startCal.get(Calendar.YEAR) * 100 + startCal.get(Calendar.MONTH)
                        val income = monthMap[key] ?: 0.0
                        monthList.add(MonthlySummaryItem(calendar = startCal.clone() as Calendar, totalSpent = income))
                        startCal.add(Calendar.MONTH, 1)
                    }
                    monthList.reversed()
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setSelectedMonth(calendar: Calendar) {
        _selectedMonth.value = calendar
    }

    fun updateFilterKeyword(keyword: String) {
        _filterState.update { it.copy(keyword = keyword) }
    }

    fun updateFilterAccount(account: Account?) {
        _filterState.update { it.copy(account = account) }
    }

    fun updateFilterCategory(category: Category?) {
        _filterState.update { it.copy(category = category) }
    }

    fun clearFilters() {
        _filterState.value = TransactionFilterState()
    }
}