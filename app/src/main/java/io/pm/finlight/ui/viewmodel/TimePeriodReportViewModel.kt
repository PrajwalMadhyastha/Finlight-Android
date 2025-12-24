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
            val (currentStart, currentEnd) = getPeriodDateRange(calendar)

            val previousPeriodEndCal = (calendar.clone() as Calendar).apply {
                when (timePeriod) {
                    TimePeriod.DAILY -> add(Calendar.HOUR_OF_DAY, -24)
                    TimePeriod.WEEKLY -> add(Calendar.DAY_OF_YEAR, -7)
                    TimePeriod.MONTHLY -> add(Calendar.MONTH, -1)
                    TimePeriod.YEARLY -> add(Calendar.YEAR, -1)
                }
            }
            val (previousStart, previousEnd) = getPeriodDateRange(previousPeriodEndCal)

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

                transactionDao.getDailySpendingForDateRange(startCal.timeInMillis, endCal.timeInMillis).map { dailyTotals ->
                    if (dailyTotals.isEmpty()) return@map null

                    val entries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    dailyTotals.forEachIndexed { index, dailyTotal ->
                        entries.add(BarEntry(index.toFloat(), dailyTotal.totalAmount.toFloat()))
                        val date = fullDateFormat.parse(dailyTotal.date)
                        labels.add(dayFormat.format(date!!))
                    }

                    val dataSet = BarDataSet(entries, "Daily Spending").apply {
                        color = 0xFF81D4FA.toInt() // Light Blue
                        setDrawValues(true)
                        valueTextColor = 0xFFFFFFFF.toInt()
                    }
                    Pair(BarData(dataSet), labels)
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

                transactionDao.getWeeklySpendingForDateRange(startCal.timeInMillis, endCal.timeInMillis).map { weeklyTotals ->
                    if (weeklyTotals.isEmpty()) return@map null

                    val entries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val totalsMap = weeklyTotals.associateBy { it.period }

                    for (i in 0..7) {
                        val weekCal = (startCal.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, i) }
                        val yearWeek = "${weekCal.get(Calendar.YEAR)}-${weekCal.get(Calendar.WEEK_OF_YEAR).toString().padStart(2, '0')}"

                        val total = totalsMap[yearWeek]?.totalAmount?.toFloat() ?: 0f
                        entries.add(BarEntry(i.toFloat(), total))
                        labels.add("W${weekCal.get(Calendar.WEEK_OF_YEAR)}")
                    }

                    val dataSet = BarDataSet(entries, "Weekly Spending").apply {
                        color = 0xFF9575CD.toInt() // Deep Purple
                        setDrawValues(true)
                        valueTextColor = 0xFFFFFFFF.toInt()
                    }
                    Pair(BarData(dataSet), labels)
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

                transactionDao.getMonthlySpendingForDateRange(startCal.timeInMillis, endCal.timeInMillis).map { monthlyTotals ->
                    if (monthlyTotals.isEmpty()) return@map null

                    val entries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                    val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val totalsMap = monthlyTotals.associateBy { it.period }

                    for (i in 0..5) {
                        val monthCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, i) }
                        val yearMonth = yearMonthFormat.format(monthCal.time)

                        val total = totalsMap[yearMonth]?.totalAmount?.toFloat() ?: 0f
                        entries.add(BarEntry(i.toFloat(), total))
                        labels.add(monthFormat.format(monthCal.time))
                    }

                    val dataSet = BarDataSet(entries, "Monthly Spending").apply {
                        color = 0xFF4DB6AC.toInt() // Teal
                        setDrawValues(true)
                        valueTextColor = 0xFFFFFFFF.toInt()
                    }
                    Pair(BarData(dataSet), labels)
                }
            }
            TimePeriod.YEARLY -> {
                val endCal = (calendar.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                }
                val startCal = (calendar.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                }

                transactionDao.getMonthlySpendingForDateRange(startCal.timeInMillis, endCal.timeInMillis).map { monthlyTotals ->
                    val entries = mutableListOf<BarEntry>()
                    val labels = mutableListOf<String>()
                    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                    val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val totalsMap = monthlyTotals.associateBy { it.period }

                    for (i in 0..11) {
                        val monthCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, i) }
                        val yearMonth = yearMonthFormat.format(monthCal.time)

                        val total = totalsMap[yearMonth]?.totalAmount?.toFloat() ?: 0f
                        entries.add(BarEntry(i.toFloat(), total))
                        labels.add(monthFormat.format(monthCal.time))
                    }

                    if (entries.all { it.y == 0f }) return@map null

                    val dataSet = BarDataSet(entries, "Yearly Spending").apply {
                        color = 0xFF4DD0E1.toInt() // Cyan
                        setDrawValues(true)
                        valueTextColor = 0xFFFFFFFF.toInt()
                    }
                    Pair(BarData(dataSet), labels)
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
}
