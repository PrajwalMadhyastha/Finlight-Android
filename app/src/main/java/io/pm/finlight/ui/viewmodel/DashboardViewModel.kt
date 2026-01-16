// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/DashboardViewModel.kt
// REASON: REFACTOR (Consistency) - The `yearlyConsistencyData` flow has been
// rewritten to use the new centralized `transactionRepository
// .getMonthlyConsistencyData` function. It now combines the data for all 12
// months of the current year, ensuring the dashboard heatmap uses the same
// "Monthly-First" logic as all other consistency views.
// FIX (Bug) - The old, buggy `generateYearlyConsistencyData` function has been
// completely removed, eliminating the data inconsistency and truncation bug.
//
// REASON: FIX (Consistency) - The `overallMonthlyBudget` collector is updated
// to handle the new nullable `Float?` from `SettingsRepository`. It now uses
// `map { (it ?: 0f).roundToLong() }`. This safely displays "₹0" in the hero card
// if no budget is set for the current month, preventing a crash.
//
// REASON: FIX (Flaky Test) - The `_summaryRefreshTrigger` is now a `Long`
// counter instead of a `System.currentTimeMillis()` timestamp. This fixes a
// race condition in unit tests where the trigger could be updated with the
// same millisecond value, preventing the StateFlow from emitting a new value
// and causing the test to time out.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
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
    // --- FIX: Use a counter instead of a timestamp to avoid race conditions in tests ---
    private val _summaryRefreshTrigger = MutableStateFlow(0L)

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
                // --- UPDATED: Handle nullable Float? and map null to 0f for this display ---
                .map { (it ?: 0f).roundToLong() }
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
                            "Pacing high this month",
                            "Time to ease up on spending",
                            "Still trending over budget",
                            "Let's slow things down",
                            "Watch the spending a bit",
                            "Pacing is still high",
                            "A bit too fast this month",
                            "Time to pump the brakes",
                            "Budget feeling the pressure",
                            "Trending to overspend",
                            "Ease up for a bit",
                            "Spending's running hot"
                        ).random()
                    }
                    // Scenario D: Recently Slipped Up (OK total spend, but high recent spend)
                    percentOfBudgetSpent <= percentOfMonthPassed && forecastedSpend > budget -> {
                        listOf(
                            "Spending picked up recently",
                            "Careful, trending over",
                            "Watch the recent spending",
                            "Pace increased lately",
                            "A recent slip-up",
                            "Let's get back on track",
                            "Trending high lately",
                            "A small adjustment helps",
                            "Avoid a spending spree",
                            "Back on the brakes",
                            "Recent uptick in spending",
                            "Tighten up a bit"
                        ).random()
                    }
                    // Scenario A: Back on Track (High total spend, but low recent spend)
                    percentOfBudgetSpent > percentOfMonthPassed && forecastedSpend <= budget -> {
                        listOf(
                            "Nice recovery! On pace now",
                            "Spending slowed nicely",
                            "Back on track!",
                            "Great spending adjustment",
                            "You've course-corrected!",
                            "Well done reining it in",
                            "Pacing is now under control",
                            "Rest of month looks good",
                            "Good save! Keep it up",
                            "Back within your plan",
                            "Perfect correction",
                            "Nicely rebalanced"
                        ).random()
                    }
                    // Scenario B: Consistently Good
                    else -> {
                        listOf(
                            "Excellent pacing!",
                            "On track with room to spare",
                            "Well within budget!",
                            "Budget looking healthy",
                            "Consistently great spending",
                            "Keep this momentum going!",
                            "Smooth sailing this month",
                            "Building a nice buffer",
                            "Perfectly on track",
                            "Another great spending day",
                            "Crushing it this month",
                            "Budget in great shape"
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
        // --- FIX: Use a counter to guarantee a new value ---
        _summaryRefreshTrigger.value = _summaryRefreshTrigger.value + 1
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
