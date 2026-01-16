// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TimePeriodReportViewModel.kt
// REASON: FEATURE (Outlier Adjusted Totals) - Updated `totalIncome` and
// `totalExpenses` to respect outlier exclusions when in `TimePeriod.YEARLY` mode.
// This ensures that the overall summary card reflects the same adjusted values used
// for averages, providing consistent feedback to the user.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import io.pm.finlight.data.model.TimePeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class MonthlyBreakdown(
    val monthKey: String, // yyyy-MM
    val monthName: String,
    val income: Double,
    val expenses: Double,
    val isIncomeExcluded: Boolean,
    val isExpenseExcluded: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimePeriodReportViewModel(
    private val transactionDao: TransactionDao,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val timePeriod: TimePeriod,
    initialDateMillis: Long?,
    showPreviousMonth: Boolean
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(
        Calendar.getInstance().apply {
            if (initialDateMillis != null && initialDateMillis != -1L) {
                timeInMillis = initialDateMillis
            } else if (showPreviousMonth) {
                add(Calendar.MONTH, -1)
            }
        }
    )
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    val transactionsForPeriod: StateFlow<List<TransactionDetails>> = _selectedDate.flatMapLatest { calendar ->
        val (start, end) = getPeriodDateRange(calendar)
        transactionDao.getTransactionDetailsForRange(start, end, null, null, null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val excludedIncomeMonths: StateFlow<Set<String>> = settingsRepository.getExcludedIncomeMonths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val excludedExpenseMonths: StateFlow<Set<String>> = settingsRepository.getExcludedExpenseMonths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val yearlyMonthlyBreakdown: StateFlow<List<MonthlyBreakdown>> = combine(
        transactionsForPeriod,
        excludedIncomeMonths,
        excludedExpenseMonths,
        _selectedDate
    ) { transactions, excludedIncome, excludedExpense, date ->
        if (timePeriod != TimePeriod.YEARLY) return@combine emptyList()

        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val nameSdf = SimpleDateFormat("MMM", Locale.getDefault())
        val breakdownMap = mutableMapOf<String, Pair<Double, Double>>()

        val cal = (date.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.JANUARY) }
        for (i in 0..11) {
            breakdownMap[sdf.format(cal.time)] = Pair(0.0, 0.0)
            cal.add(Calendar.MONTH, 1)
        }

        transactions.forEach { txn ->
            val monthKey = sdf.format(Date(txn.transaction.date))
            val current = breakdownMap[monthKey] ?: Pair(0.0, 0.0)
            if (txn.transaction.transactionType == "income" && !txn.transaction.isExcluded) {
                breakdownMap[monthKey] = current.copy(first = current.first + txn.transaction.amount)
            } else if (txn.transaction.transactionType == "expense" && !txn.transaction.isExcluded) {
                breakdownMap[monthKey] = current.copy(second = current.second + txn.transaction.amount)
            }
        }

        breakdownMap.entries.sortedBy { it.key }.map { (key, totals) ->
            val monthDate = sdf.parse(key)!!
            MonthlyBreakdown(
                monthKey = key,
                monthName = nameSdf.format(monthDate),
                income = totals.first,
                expenses = totals.second,
                isIncomeExcluded = excludedIncome.contains(key),
                isExpenseExcluded = excludedExpense.contains(key)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- UPDATED: Use breakdowns and exclusions for totals and averages ---
    
    val totalIncome: StateFlow<Long> = combine(transactionsForPeriod, yearlyMonthlyBreakdown) { transactions, breakdowns ->
        if (timePeriod == TimePeriod.YEARLY) {
            breakdowns.filter { !it.isIncomeExcluded }.sumOf { it.income }.roundToLong()
        } else {
            transactions
                .filter { it.transaction.transactionType == "income" && !it.transaction.isExcluded }
                .sumOf { it.transaction.amount }.roundToLong()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalExpenses: StateFlow<Long> = combine(transactionsForPeriod, yearlyMonthlyBreakdown) { transactions, breakdowns ->
        if (timePeriod == TimePeriod.YEARLY) {
            breakdowns.filter { !it.isExpenseExcluded }.sumOf { it.expenses }.roundToLong()
        } else {
            transactions
                .filter { it.transaction.transactionType == "expense" && !it.transaction.isExcluded }
                .sumOf { it.transaction.amount }.roundToLong()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val monthlyAverageIncome: StateFlow<Double?> = combine(yearlyMonthlyBreakdown, _selectedDate) { breakdowns, date ->
        if (timePeriod != TimePeriod.YEARLY) return@combine null
        val elapsedMonths = getElapsedMonthsInYear(date)
        val includedBreakdowns = breakdowns.take(elapsedMonths).filter { !it.isIncomeExcluded }
        if (includedBreakdowns.isEmpty()) null else includedBreakdowns.sumOf { it.income } / includedBreakdowns.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthlyAverageExpenses: StateFlow<Double?> = combine(yearlyMonthlyBreakdown, _selectedDate) { breakdowns, date ->
        if (timePeriod != TimePeriod.YEARLY) return@combine null
        val elapsedMonths = getElapsedMonthsInYear(date)
        val includedBreakdowns = breakdowns.take(elapsedMonths).filter { !it.isExpenseExcluded }
        if (includedBreakdowns.isEmpty()) null else includedBreakdowns.sumOf { it.expenses } / includedBreakdowns.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    val insights: StateFlow<ReportInsights?> = _selectedDate.flatMapLatest { calendar ->
        flow {
            // For MONTHLY and YEARLY, use same-period comparison
            // For DAILY and WEEKLY, use full period comparison (existing behavior)
            val (currentStart, currentEnd) = when (timePeriod) {
                TimePeriod.MONTHLY, TimePeriod.YEARLY -> {
                    val periodStart = getPeriodDateRange(calendar).first
                    val actualEnd = getActualPeriodEnd(calendar)
                    Pair(periodStart, actualEnd)
                }
                else -> getPeriodDateRange(calendar)
            }

            val (previousStart, previousEnd) = when (timePeriod) {
                TimePeriod.MONTHLY, TimePeriod.YEARLY -> {
                    getSamePeriodPreviousYearRange(calendar)
                }
                else -> {
                    val previousPeriodEndCal = (calendar.clone() as Calendar).apply {
                        when (timePeriod) {
                            TimePeriod.DAILY -> add(Calendar.HOUR_OF_DAY, -24)
                            TimePeriod.WEEKLY -> add(Calendar.DAY_OF_YEAR, -7)
                            else -> {} // Already handled above
                        }
                    }
                    getPeriodDateRange(previousPeriodEndCal)
                }
            }

            val currentSummary = transactionDao.getFinancialSummaryForRange(currentStart, currentEnd)
            val previousSummary = transactionDao.getFinancialSummaryForRange(previousStart, previousEnd)
            val topCategories = transactionDao.getTopSpendingCategoriesForRange(currentStart, currentEnd)

            val percentageChange = if (previousSummary?.totalExpenses != null && previousSummary.totalExpenses > 0) {
                val currentExpenses = currentSummary?.totalExpenses ?: 0.0
                ((currentExpenses - previousSummary.totalExpenses) / previousSummary.totalExpenses * 100).roundToInt()
            } else {
                null
            }

            emit(ReportInsights(percentageChange, topCategories.firstOrNull()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    val chartData: StateFlow<Pair<BarData, List<String>>?> = _selectedDate.flatMapLatest { calendar ->
        when (timePeriod) {
            TimePeriod.DAILY -> {
                val endCal = (calendar.clone() as Calendar)
                val startCal = (calendar.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -24) }

                transactionDao.getDailyTrends(startCal.timeInMillis, endCal.timeInMillis).map { dailyTrends ->
                    if (dailyTrends.isEmpty()) return@map null

                    val incomeEntries = mutableListOf<BarEntry>()
                    val expenseEntries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    dailyTrends.forEachIndexed { index, dailyTrend ->
                        incomeEntries.add(BarEntry(index.toFloat(), dailyTrend.totalIncome.toFloat()))
                        expenseEntries.add(BarEntry(index.toFloat(), dailyTrend.totalExpenses.toFloat()))
                        val date = fullDateFormat.parse(dailyTrend.date)
                        labels.add(dayFormat.format(date!!))
                    }

                    val incomeDataSet = BarDataSet(incomeEntries, "Income").apply {
                        color = 0xFF66BB6A.toInt() // Green
                    }
                    val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply {
                        color = 0xFFEF5350.toInt() // Red
                    }
                    Pair(BarData(incomeDataSet, expenseDataSet), labels)
                }
            }
            TimePeriod.WEEKLY -> {
                val endCal = (calendar.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    add(Calendar.WEEK_OF_YEAR, 1)
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                val startCal = (calendar.clone() as Calendar).apply {
                    add(Calendar.WEEK_OF_YEAR, -7) // 8 weeks total
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }

                transactionDao.getWeeklyTrends(startCal.timeInMillis, endCal.timeInMillis).map { weeklyTrends ->
                    if (weeklyTrends.isEmpty()) return@map null

                    val incomeEntries = mutableListOf<BarEntry>()
                    val expenseEntries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val trendsMap = weeklyTrends.associateBy { it.period }

                    for (i in 0..7) {
                        val weekCal = (startCal.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, i) }
                        val yearWeek = "${weekCal.get(Calendar.YEAR)}-${weekCal.get(Calendar.WEEK_OF_YEAR).toString().padStart(2, '0')}"

                        val trend = trendsMap[yearWeek]
                        incomeEntries.add(BarEntry(i.toFloat(), trend?.totalIncome?.toFloat() ?: 0f))
                        expenseEntries.add(BarEntry(i.toFloat(), trend?.totalExpenses?.toFloat() ?: 0f))
                        labels.add("W${weekCal.get(Calendar.WEEK_OF_YEAR)}")
                    }

                    val incomeDataSet = BarDataSet(incomeEntries, "Income").apply {
                        color = 0xFF66BB6A.toInt() // Green
                    }
                    val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply {
                        color = 0xFFEF5350.toInt() // Red
                    }
                    Pair(BarData(incomeDataSet, expenseDataSet), labels)
                }
            }
            TimePeriod.MONTHLY -> {
                val endCal = (calendar.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                val startCal = (calendar.clone() as Calendar).apply {
                    add(Calendar.MONTH, -5) // 6 months total
                    set(Calendar.DAY_OF_MONTH, 1)
                }

                transactionDao.getMonthlyTrends(startCal.timeInMillis).map { monthlyTrends ->
                    if (monthlyTrends.isEmpty()) return@map null

                    val incomeEntries = mutableListOf<BarEntry>()
                    val expenseEntries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                    val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val trendsMap = monthlyTrends.associateBy { it.monthYear }

                    for (i in 0..5) {
                        val monthCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, i) }
                        val yearMonth = yearMonthFormat.format(monthCal.time)

                        val trend = trendsMap[yearMonth]
                        incomeEntries.add(BarEntry(i.toFloat(), trend?.totalIncome?.toFloat() ?: 0f))
                        expenseEntries.add(BarEntry(i.toFloat(), trend?.totalExpenses?.toFloat() ?: 0f))
                        labels.add(monthFormat.format(monthCal.time))
                    }

                    val incomeDataSet = BarDataSet(incomeEntries, "Income").apply {
                        color = 0xFF66BB6A.toInt() // Green
                    }
                    val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply {
                        color = 0xFFEF5350.toInt() // Red
                    }
                    Pair(BarData(incomeDataSet, expenseDataSet), labels)
                }
            }
            TimePeriod.YEARLY -> {
                val endCal = (calendar.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }
                val startCal = (calendar.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }

                // Use getMonthlyTrends but filter results to only include current year
                transactionDao.getMonthlyTrends(startCal.timeInMillis).map { monthlyTrends ->
                    val incomeEntries = mutableListOf<BarEntry>()
                    val expenseEntries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                    val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    
                    // Filter trends to only include current year
                    val currentYear = calendar.get(Calendar.YEAR).toString()
                    val trendsMap = monthlyTrends
                        .filter { it.monthYear.startsWith(currentYear) }
                        .associateBy { it.monthYear }

                    for (i in 0..11) {
                        val monthCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, i) }
                        val yearMonth = yearMonthFormat.format(monthCal.time)

                        val trend = trendsMap[yearMonth]
                        incomeEntries.add(BarEntry(i.toFloat(), trend?.totalIncome?.toFloat() ?: 0f))
                        expenseEntries.add(BarEntry(i.toFloat(), trend?.totalExpenses?.toFloat() ?: 0f))
                        labels.add(monthFormat.format(monthCal.time))
                    }

                    if (incomeEntries.all { it.y == 0f } && expenseEntries.all { it.y == 0f }) return@map null

                    val incomeDataSet = BarDataSet(incomeEntries, "Income").apply {
                        color = 0xFF66BB6A.toInt() // Green
                    }
                    val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply {
                        color = 0xFFEF5350.toInt() // Red
                    }
                    Pair(BarData(incomeDataSet, expenseDataSet), labels)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthlyConsistencyData: StateFlow<List<CalendarDayStatus>> = _selectedDate.flatMapLatest { calendar ->
        if (timePeriod != TimePeriod.MONTHLY) return@flatMapLatest flowOf(emptyList())
        transactionRepository.getMonthlyConsistencyData(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yearlyConsistencyData: StateFlow<List<CalendarDayStatus>> = _selectedDate.flatMapLatest { calendar ->
        if (timePeriod != TimePeriod.YEARLY) return@flatMapLatest flowOf(emptyList())

        flow {
            val year = calendar.get(Calendar.YEAR)
            val monthlyDataFlows: List<Flow<List<CalendarDayStatus>>> = (1..12).map { month ->
                transactionRepository.getMonthlyConsistencyData(year, month)
            }

            val combinedFlow = combine(monthlyDataFlows) { monthlyDataArray: Array<List<CalendarDayStatus>> ->
                monthlyDataArray.toList().flatten()
            }

            combinedFlow.collect { combinedYearlyData: List<CalendarDayStatus> ->
                emit(combinedYearlyData)
            }
        }.flowOn(Dispatchers.Default)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val consistencyStats: StateFlow<ConsistencyStats> = combine(
        monthlyConsistencyData,
        yearlyConsistencyData
    ) { monthlyData, yearlyData ->
        val data = if (timePeriod == TimePeriod.MONTHLY) monthlyData else yearlyData
        val today = Calendar.getInstance()
        val relevantData = data.filter { !it.date.after(today.time) }
        val goodDays = relevantData.count { it.status == SpendingStatus.WITHIN_LIMIT }
        val badDays = relevantData.count { it.status == SpendingStatus.OVER_LIMIT }
        val noSpendDays = relevantData.count { it.status == SpendingStatus.NO_SPEND }
        val noDataDays = relevantData.count { it.status == SpendingStatus.NO_DATA }
        ConsistencyStats(goodDays, badDays, noSpendDays, noDataDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConsistencyStats(0, 0, 0, 0))


    fun selectPreviousPeriod() {
        _selectedDate.update {
            (it.clone() as Calendar).apply {
                add(
                    when (timePeriod) {
                        TimePeriod.DAILY -> Calendar.DAY_OF_YEAR
                        TimePeriod.WEEKLY -> Calendar.WEEK_OF_YEAR
                        TimePeriod.MONTHLY -> Calendar.MONTH
                        TimePeriod.YEARLY -> Calendar.YEAR
                    }, -1
                )
            }
        }
    }

    fun selectNextPeriod() {
        _selectedDate.update {
            (it.clone() as Calendar).apply {
                add(
                    when (timePeriod) {
                        TimePeriod.DAILY -> Calendar.DAY_OF_YEAR
                        TimePeriod.WEEKLY -> Calendar.WEEK_OF_YEAR
                        TimePeriod.MONTHLY -> Calendar.MONTH
                        TimePeriod.YEARLY -> Calendar.YEAR
                    }, 1
                )
            }
        }
    }

    fun toggleIncomeExclusion(monthKey: String) {
        settingsRepository.toggleIncomeMonthExclusion(monthKey)
    }

    fun toggleExpenseExclusion(monthKey: String) {
        settingsRepository.toggleExpenseMonthExclusion(monthKey)
    }

    private fun getPeriodDateRange(calendar: Calendar): Pair<Long, Long> {
        val endCal = (calendar.clone() as Calendar)
        val startCal = (endCal.clone() as Calendar)

        when (timePeriod) {
            TimePeriod.DAILY -> startCal.add(Calendar.HOUR_OF_DAY, -24)
            TimePeriod.WEEKLY -> startCal.add(Calendar.DAY_OF_YEAR, -7)
            TimePeriod.MONTHLY -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)

                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
            }
            TimePeriod.YEARLY -> {
                startCal.set(Calendar.DAY_OF_YEAR, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)

                endCal.set(Calendar.DAY_OF_YEAR, endCal.getActualMaximum(Calendar.DAY_OF_YEAR))
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
            }
        }

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    private fun getElapsedMonthsInYear(selectedDate: Calendar): Int {
        val now = Calendar.getInstance()
        return if (selectedDate.get(Calendar.YEAR) < now.get(Calendar.YEAR)) {
            12
        } else if (selectedDate.get(Calendar.YEAR) > now.get(Calendar.YEAR)) {
            0
        } else {
            now.get(Calendar.MONTH) + 1
        }
    }

    /**
     * Returns the actual end date for the current period.
     * For periods in the past, returns the period end.
     * For the current period, returns today (or period end if today is beyond it).
     */
    private fun getActualPeriodEnd(calendar: Calendar): Long {
        val now = Calendar.getInstance()
        val (_, periodEnd) = getPeriodDateRange(calendar)
        val periodEndCal = Calendar.getInstance().apply { timeInMillis = periodEnd }
        
        return if (periodEndCal.after(now)) {
            now.timeInMillis
        } else {
            periodEnd
        }
    }

    /**
     * For MONTHLY and YEARLY periods, returns the same date range from the previous year.
     * For example, if viewing Jan 1-15, 2026, returns Jan 1-15, 2025.
     */
    private fun getSamePeriodPreviousYearRange(calendar: Calendar): Pair<Long, Long> {
        val previousYearCal = (calendar.clone() as Calendar).apply {
            add(Calendar.YEAR, -1)
        }
        
        val periodStart = getPeriodDateRange(previousYearCal).first
        val actualEnd = getActualPeriodEnd(calendar)
        
        // Calculate the offset from period start to actual end in current year
        val currentPeriodStart = getPeriodDateRange(calendar).first
        val currentPeriodStartCal = Calendar.getInstance().apply { timeInMillis = currentPeriodStart }
        val actualEndCal = Calendar.getInstance().apply { timeInMillis = actualEnd }
        
        // Apply the same offset to previous year
        val previousEndCal = Calendar.getInstance().apply {
            timeInMillis = periodStart
            when (timePeriod) {
                TimePeriod.MONTHLY -> {
                    val dayOfMonth = actualEndCal.get(Calendar.DAY_OF_MONTH)
                    val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, maxDay))
                    set(Calendar.HOUR_OF_DAY, actualEndCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, actualEndCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, actualEndCal.get(Calendar.SECOND))
                    set(Calendar.MILLISECOND, actualEndCal.get(Calendar.MILLISECOND))
                }
                TimePeriod.YEARLY -> {
                    val dayOfYear = actualEndCal.get(Calendar.DAY_OF_YEAR)
                    val maxDay = getActualMaximum(Calendar.DAY_OF_YEAR)
                    set(Calendar.DAY_OF_YEAR, minOf(dayOfYear, maxDay))
                    set(Calendar.HOUR_OF_DAY, actualEndCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, actualEndCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, actualEndCal.get(Calendar.SECOND))
                    set(Calendar.MILLISECOND, actualEndCal.get(Calendar.MILLISECOND))
                }
                else -> {} // Not used for DAILY/WEEKLY
            }
        }
        
        return Pair(periodStart, previousEndCal.timeInMillis)
    }
}
