// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/DashboardViewModel.kt
// REASON: REFACTOR (Consistency) - The `yearlyConsistencyData` flow has been
// rewritten to use the new centralized `transactionRepository
// .getMonthlyConsistencyData` function. It now combines the data for all 12
// months of the current year, ensuring the dashboard heatmap uses the same
// "Monthly-First" logic as all other consistency views.
// FIX (Bug) - The old, buggy `generateYearlyConsistencyData` function has been
// completely removed, eliminating the data inconsistency and truncation bug.
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
import kotlin.math.roundToLong

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
    private val merchantRenameRuleRepository: MerchantRenameRuleRepository
) : ViewModel() {
    val userName: StateFlow<String>
    val profilePictureUri: StateFlow<String?>

    val netWorth: StateFlow<Long>
    val monthlyIncome: StateFlow<Long>
    val monthlyExpenses: StateFlow<Long>
    val recentTransactions: StateFlow<List<TransactionDetails>>
    val budgetStatus: StateFlow<List<BudgetWithSpending>>
    val overallMonthlyBudget: StateFlow<Long>
    val amountRemaining: StateFlow<Long>
    val safeToSpendPerDay: StateFlow<Long>
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
    private val merchantAliases: StateFlow<Map<String, String>>


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

        merchantAliases = merchantRenameRuleRepository.getAliasesAsMap()
            .map { it.mapKeys { (key, _) -> key.lowercase(Locale.getDefault()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


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

        monthlyIncome = financialSummaryFlow.map { (it?.totalIncome ?: 0.0).roundToLong() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        monthlyExpenses = financialSummaryFlow.map { (it?.totalExpenses ?: 0.0).roundToLong() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        overallMonthlyBudget =
            settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)
                .map { it.roundToLong() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        amountRemaining =
            combine(overallMonthlyBudget, monthlyExpenses) { budget, expenses ->
                budget - expenses
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        safeToSpendPerDay =
            amountRemaining.map { remaining ->
                val today = Calendar.getInstance()
                val lastDayOfMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
                val remainingDays = (lastDayOfMonth - today.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)

                if (remaining > 0) (remaining.toDouble() / remainingDays).roundToLong() else 0L
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        budgetHealthSummary = combine(
            monthlyExpenses,
            overallMonthlyBudget,
            _summaryRefreshTrigger
        ) { expenses, budget, trigger ->
            Triple(expenses, budget, trigger)
        }.flatMapLatest { (expenses, budget) ->
            if (budget <= 0L) {
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
                val percentOfBudgetSpent = if (budget > 0) (expenses.toFloat() / budget.toFloat()) else 0f

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
                list.sumOf { it.balance }.roundToLong()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        recentTransactions =
            transactionRepository.recentTransactions
                .combine(merchantAliases) { transactions, aliases -> // NEW
                    applyAliases(transactions, aliases) // NEW
                }
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

        // --- REFACTORED: Use the new "Monthly-First" centralized logic ---
        yearlyConsistencyData = flow {
            val today = Calendar.getInstance()
            val year = today.get(Calendar.YEAR)

            // Create a flow for each month of the current year
            // --- FIX: Add explicit types to resolve build error ---
            val monthlyDataFlows: List<Flow<List<CalendarDayStatus>>> = (1..12).map { month ->
                transactionRepository.getMonthlyConsistencyData(year, month)
            }

            // Combine all 12 flows
            // --- FIX: This is the corrected logic ---
            val combinedFlow: Flow<List<CalendarDayStatus>> = combine(monthlyDataFlows) { monthlyDataArray: Array<List<CalendarDayStatus>> ->
                monthlyDataArray.toList().flatten() // Flatten the Array<List> into a single List
            }

            // Collect the combined flow and emit its single list result
            combinedFlow.collect { combinedYearlyData: List<CalendarDayStatus> ->
                emit(combinedYearlyData)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    private fun applyAliases(transactions: List<TransactionDetails>, aliases: Map<String, String>): List<TransactionDetails> {
        return transactions.map { details ->
            val key = (details.transaction.originalDescription ?: details.transaction.description).lowercase(Locale.getDefault())
            val newDescription = aliases[key] ?: details.transaction.description
            details.copy(transaction = details.transaction.copy(description = newDescription))
        }
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

    // --- DELETED: generateYearlyConsistencyData function ---

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

