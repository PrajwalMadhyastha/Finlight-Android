// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DashboardViewModel.kt
// REASON: REFACTOR - The instantiation of AccountRepository has been updated to
// pass the full AppDatabase instance instead of just the DAO. This is required
// to support the new transactional account merging logic.
// FEATURE - The `budgetHealthSummary` logic is now more dynamic and insightful.
// It incorporates the day of the week and days remaining in the month to provide
// more context-aware and personalized financial advice.
// FEATURE - The `budgetHealthSummary` logic has been completely rewritten to use
// a "Spending Velocity" and forecasting model. It now analyzes the user's recent
// spending rate to predict their month-end total, providing much more accurate
// and empathetic advice that correctly handles front-loaded monthly expenses.
// FIX - Corrected the reactive stream logic for `budgetHealthSummary` to resolve
// build errors caused by an incorrect `combine` operator usage.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ConsistencyStats(val goodDays: Int, val badDays: Int, val noSpendDays: Int, val noDataDays: Int)

data class LastMonthSummary(
    val totalIncome: Double,
    val totalExpenses: Double
)

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

    private val _lastMonthSummary = MutableStateFlow<LastMonthSummary?>(null)
    val lastMonthSummary: StateFlow<LastMonthSummary?> = _lastMonthSummary.asStateFlow()

    private val _showLastMonthSummaryCard = MutableStateFlow(false)
    val showLastMonthSummaryCard: StateFlow<Boolean> = _showLastMonthSummaryCard.asStateFlow()

    val privacyModeEnabled: StateFlow<Boolean>


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

        privacyModeEnabled = settingsRepository.getPrivacyModeEnabled()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
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

        checkForLastMonthSummary()

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
        ) { expenses, budget, trigger ->
            Triple(expenses, budget, trigger)
        }.flatMapLatest { (expenses, budget) ->
            if (budget <= 0f) {
                return@flatMapLatest flowOf("Set a budget to see insights")
            }

            flow {
                // --- Spending Velocity & Forecasting Logic ---
                val cal = Calendar.getInstance()
                val lookbackPeriodDays = 3
                val lookbackStartDate = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -lookbackPeriodDays) }.timeInMillis
                val recentSpend = transactionRepository.getTotalExpensesSince(lookbackStartDate)
                val spendingVelocity = if (lookbackPeriodDays > 0) recentSpend / lookbackPeriodDays.toDouble() else 0.0

                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val daysLeft = (daysInMonth - dayOfMonth).coerceAtLeast(0)
                val forecastedSpend = expenses + (spendingVelocity * daysLeft)

                val percentOfMonthPassed = dayOfMonth.toFloat() / daysInMonth.toFloat()
                val percentOfBudgetSpent = if (budget > 0) (expenses.toFloat() / budget) else 0f

                val message = when {
                    // Scenario C: Still Pacing High (High total spend AND high recent spend)
                    percentOfBudgetSpent > percentOfMonthPassed && forecastedSpend > budget -> {
                        listOf(
                            "Pacing a bit high for this month",
                            "Time to ease up on spending",
                            "Still trending over budget",
                            "Let's try to slow things down",
                            "Watch the spending for a bit",
                            "Heads up: pacing is still high",
                            "A bit too fast for this month",
                            "Let's pump the brakes slightly",
                            "Budget is feeling the pressure",
                            "Trending to overspend"
                        ).random()
                    }
                    // Scenario D: Recently Slipped Up (OK total spend, but high recent spend)
                    percentOfBudgetSpent <= percentOfMonthPassed && forecastedSpend > budget -> {
                        listOf(
                            "Spending has picked up recently",
                            "Careful, you're trending over",
                            "Watch the recent spending",
                            "You were on track, pace has increased",
                            "A recent slip-up in spending",
                            "Let's get back to that great pace",
                            "Trending high the last few days",
                            "A little adjustment will help",
                            "Let's avoid a spending spree",
                            "Back on the brakes for a bit"
                        ).random()
                    }
                    // Scenario A: Back on Track (High total spend, but low recent spend)
                    percentOfBudgetSpent > percentOfMonthPassed && forecastedSpend <= budget -> {
                        listOf(
                            "Nice recovery! You're on pace now",
                            "Spending slowed, looking good",
                            "Back on track for the month!",
                            "Great adjustment on spending",
                            "You've course-corrected perfectly",
                            "Well done reining it in",
                            "Pacing is now under control",
                            "The rest of the month looks good",
                            "Good save! Keep it up",
                            "Back within your monthly plan"
                        ).random()
                    }
                    // Scenario B: Consistently Good
                    else -> {
                        listOf(
                            "Excellent pacing this month!",
                            "On track with room to spare",
                            "Well within budget, great job",
                            "Your budget is looking healthy",
                            "Consistently great spending",
                            "Keep this momentum going!",
                            "Smooth sailing this month",
                            "You're building a nice buffer",
                            "Perfectly on track",
                            "Another great spending day"
                        ).random()
                    }
                }
                emit(message)
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

        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)
        val currentMonthIndex = today.get(Calendar.MONTH)

        val monthlyBudgetFlows = (0..currentMonthIndex).map { month ->
            settingsRepository.getOverallBudgetForMonth(year, month + 1)
        }

        val totalBudgetSoFarFlow = combine(monthlyBudgetFlows) { budgets ->
            budgets.sum()
        }

        yearlyConsistencyData = combine(
            totalBudgetSoFarFlow,
            transactionRepository.getDailySpendingForDateRange(
                Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis,
                today.timeInMillis
            ),
            transactionRepository.getFirstTransactionDate()
        ) { totalBudget, dailyTotals, firstTransactionDate ->
            generateYearlyConsistencyData(totalBudget, dailyTotals, firstTransactionDate)
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun dismissLastMonthSummaryCard() {
        settingsRepository.setLastMonthSummaryDismissed()
        _showLastMonthSummaryCard.value = false
    }

    private fun checkForLastMonthSummary() {
        val today = Calendar.getInstance()
        if (today.get(Calendar.DAY_OF_MONTH) == 1 && !settingsRepository.hasLastMonthSummaryBeenDismissed()) {
            _showLastMonthSummaryCard.value = true
            viewModelScope.launch {
                val (start, end) = DateUtils.getPreviousMonthDateRange()
                transactionRepository.getFinancialSummaryForRangeFlow(start, end).collect { summary ->
                    if (summary != null) {
                        _lastMonthSummary.value = LastMonthSummary(
                            totalIncome = summary.totalIncome,
                            totalExpenses = summary.totalExpenses
                        )
                    }
                }
            }
        }
    }


    fun refreshBudgetSummary() {
        _summaryRefreshTrigger.value = System.currentTimeMillis()
    }

    private suspend fun generateYearlyConsistencyData(
        totalBudgetSoFar: Float,
        dailyTotals: List<DailyTotal>,
        firstTransactionDate: Long?
    ): List<CalendarDayStatus> = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()

        if (firstTransactionDate == null) {
            val resultList = mutableListOf<CalendarDayStatus>()
            val dayIterator = Calendar.getInstance().apply {
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.DAY_OF_YEAR, 1)
            }
            while (!dayIterator.after(today)) {
                resultList.add(CalendarDayStatus(dayIterator.time, SpendingStatus.NO_DATA, 0.0, 0.0))
                dayIterator.add(Calendar.DAY_OF_YEAR, 1)
            }
            return@withContext resultList
        }

        val daysSoFar = today.get(Calendar.DAY_OF_YEAR)
        val yearlySafeToSpend = if (totalBudgetSoFar > 0 && daysSoFar > 0) (totalBudgetSoFar / daysSoFar).toDouble() else 0.0

        val firstDataCal = Calendar.getInstance().apply { timeInMillis = firstTransactionDate }
        val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })

        val resultList = mutableListOf<CalendarDayStatus>()
        val dayIterator = Calendar.getInstance().apply {
            set(Calendar.YEAR, today.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, 1)
        }

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