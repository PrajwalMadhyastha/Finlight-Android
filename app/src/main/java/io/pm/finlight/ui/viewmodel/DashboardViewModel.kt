// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DashboardViewModel.kt
// REASON: FIX - The logic for `generateYearlyConsistencyData` now handles the
// edge case where a user has zero transactions. It checks for a null
// `firstTransactionDate` and, if found, returns a list where all past days of
// the year are correctly marked as NO_DATA, preventing them from being
// misclassified as NO_SPEND.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ConsistencyStats(val goodDays: Int, val badDays: Int, val noSpendDays: Int, val noDataDays: Int)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val budgetDao: BudgetDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val userName: StateFlow<String>
    val profilePictureUri: StateFlow<String?>

    val netWorth: StateFlow<Double>
    val monthlyIncome: StateFlow<Double>
    val monthlyExpenses: StateFlow<Double>
    val recentTransactions: StateFlow<List<TransactionDetails>>
    val budgetStatus: StateFlow<List<BudgetWithSpending>>
    val overallMonthlyBudget: StateFlow<Float>
    val amountRemaining: StateFlow<Float>
    val safeToSpendPerDay: StateFlow<Float>
    val accountsSummary: StateFlow<List<AccountWithBalance>>
    val monthYear: String

    val visibleCards: StateFlow<List<DashboardCardType>>
    val allCards: StateFlow<List<DashboardCardType>>

    private val _cardOrder = MutableStateFlow<List<DashboardCardType>>(emptyList())
    private val _visibleCardsSet = MutableStateFlow<Set<DashboardCardType>>(emptySet())

    val yearlyConsistencyData: StateFlow<List<CalendarDayStatus>>
    val budgetHealthSummary: StateFlow<String>
    private val _summaryRefreshTrigger = MutableStateFlow(System.currentTimeMillis())

    init {
        userName = settingsRepository.getUserName()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = "User"
            )

        profilePictureUri = settingsRepository.getProfilePictureUri()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

        viewModelScope.launch {
            settingsRepository.getDashboardCardOrder().collect {
                _cardOrder.value = it
            }
        }
        viewModelScope.launch {
            settingsRepository.getDashboardVisibleCards().collect {
                _visibleCardsSet.value = it
            }
        }

        allCards = _cardOrder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        visibleCards = combine(
            _cardOrder,
            _visibleCardsSet
        ) { order, visible ->
            order.filter { it in visible }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val calendar = Calendar.getInstance()
        monthYear = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)

        val monthStart =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        val monthEnd =
            (calendar.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.DAY_OF_MONTH, -1)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

        val financialSummaryFlow = transactionRepository.getFinancialSummaryForRangeFlow(monthStart, monthEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        monthlyIncome = financialSummaryFlow.map { it?.totalIncome ?: 0.0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyExpenses = financialSummaryFlow.map { it?.totalExpenses ?: 0.0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        overallMonthlyBudget =
            settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        amountRemaining =
            combine(overallMonthlyBudget, monthlyExpenses) { budget, expenses ->
                budget - expenses.toFloat()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        safeToSpendPerDay =
            amountRemaining.map { remaining ->
                val today = Calendar.getInstance()
                val lastDayOfMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
                val remainingDays = (lastDayOfMonth - today.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)

                if (remaining > 0) remaining / remainingDays else 0f
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        budgetHealthSummary = combine(
            monthlyExpenses,
            overallMonthlyBudget,
            _summaryRefreshTrigger
        ) { expenses, budget, _ ->
            if (budget <= 0f) {
                "Set a budget to see insights"
            } else {
                val cal = Calendar.getInstance()
                val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val percentOfMonthPassed = dayOfMonth.toFloat() / daysInMonth.toFloat()
                val percentOfBudgetSpent = (expenses / budget).toFloat()

                when {
                    percentOfBudgetSpent > 1 -> listOf(
                        "You've gone over for $monthName.",
                        "Let's get back on track next month.",
                        "Budget exceeded for the month."
                    ).random()
                    percentOfBudgetSpent > percentOfMonthPassed + 0.2 -> listOf(
                        "A little ahead of schedule.",
                        "Watch your spending for the rest of $monthName.",
                        "Heads up: spending is a bit high."
                    ).random()
                    percentOfBudgetSpent < percentOfMonthPassed - 0.2 -> listOf(
                        "Well under budget so far!",
                        "You're saving more this month.",
                        "Plenty of room in the budget."
                    ).random()
                    else -> listOf(
                        "Looking good for $monthName!",
                        "Great job staying on budget!",
                        "Your spending is right on track."
                    ).random()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Monthly Budget")

        netWorth =
            accountRepository.accountsWithBalance.map { list ->
                list.sumOf { it.balance }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        recentTransactions =
            transactionRepository.recentTransactions
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val yearMonthString = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        budgetStatus = budgetDao.getBudgetsWithSpendingForMonth(yearMonthString, currentMonth, currentYear)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        accountsSummary =
            accountRepository.accountsWithBalance
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        yearlyConsistencyData = flow {
            emit(generateYearlyConsistencyData())
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun refreshBudgetSummary() {
        _summaryRefreshTrigger.value = System.currentTimeMillis()
    }

    private suspend fun generateYearlyConsistencyData(): List<CalendarDayStatus> = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)

        val firstTransactionDate = transactionRepository.getFirstTransactionDate().first()

        // --- FIX: Handle case where there are no transactions at all ---
        if (firstTransactionDate == null) {
            val resultList = mutableListOf<CalendarDayStatus>()
            val dayIterator = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.DAY_OF_YEAR, 1)
            }
            while (!dayIterator.after(today)) {
                resultList.add(CalendarDayStatus(dayIterator.time, SpendingStatus.NO_DATA, 0.0, 0.0))
                dayIterator.add(Calendar.DAY_OF_YEAR, 1)
            }
            return@withContext resultList
        }

        val currentMonthIndex = today.get(Calendar.MONTH)
        val daysSoFar = today.get(Calendar.DAY_OF_YEAR)

        var totalBudgetSoFar = 0f
        for (month in 0..currentMonthIndex) {
            totalBudgetSoFar += settingsRepository.getOverallBudgetForMonthBlocking(year, month + 1)
        }
        val yearlySafeToSpend = if (totalBudgetSoFar > 0 && daysSoFar > 0) (totalBudgetSoFar / daysSoFar).toDouble() else 0.0

        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        val startDate = calendar.timeInMillis

        val firstDataCal = Calendar.getInstance().apply { timeInMillis = firstTransactionDate }

        val dailyTotals = transactionRepository.getDailySpendingForDateRange(startDate, endDate).first()
        val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })

        val resultList = mutableListOf<CalendarDayStatus>()
        val dayIterator = Calendar.getInstance().apply { timeInMillis = startDate }

        while (!dayIterator.after(today)) {
            if (dayIterator.before(firstDataCal)) {
                resultList.add(CalendarDayStatus(dayIterator.time, SpendingStatus.NO_DATA, 0.0, 0.0))
                dayIterator.add(Calendar.DAY_OF_YEAR, 1)
                continue
            }

            val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", dayIterator.get(Calendar.YEAR), dayIterator.get(Calendar.MONTH) + 1, dayIterator.get(Calendar.DAY_OF_MONTH))
            val amountSpent = spendingMap[dateKey] ?: 0.0

            val status = when {
                amountSpent == 0.0 -> SpendingStatus.NO_SPEND
                yearlySafeToSpend > 0 && amountSpent > yearlySafeToSpend -> SpendingStatus.OVER_LIMIT
                else -> SpendingStatus.WITHIN_LIMIT
            }

            resultList.add(CalendarDayStatus(dayIterator.time, status, amountSpent, yearlySafeToSpend))
            dayIterator.add(Calendar.DAY_OF_YEAR, 1)
        }
        resultList
    }

    fun updateCardOrder(from: Int, to: Int) {
        _cardOrder.update { currentList ->
            currentList.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
        viewModelScope.launch {
            settingsRepository.saveDashboardLayout(_cardOrder.value, _visibleCardsSet.value)
        }
    }

    fun toggleCardVisibility(cardType: DashboardCardType) {
        _visibleCardsSet.update { currentSet ->
            if (cardType in currentSet) {
                currentSet - cardType
            } else {
                currentSet + cardType
            }
        }
        viewModelScope.launch {
            settingsRepository.saveDashboardLayout(_cardOrder.value, _visibleCardsSet.value)
        }
    }
}
