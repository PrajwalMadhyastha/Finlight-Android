// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TimePeriodReportViewModel.kt
// REASON: FIX - The ViewModel now accepts a `showPreviousMonth` boolean flag.
// The initialization logic for the `selectedDate` state now checks this flag.
// If true, it sets the initial view to the previous month, ensuring that deep
// links from monthly summary notifications show the correct report.
// FIX - The spending chart logic for the Daily report has been corrected. It
// now fetches and displays data for the relevant 24-hour period, consistent
// with the rest of the screen, instead of incorrectly showing a 7-day summary.
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class TimePeriodReportViewModel(
    private val transactionDao: TransactionDao,
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

    val totalIncome: StateFlow<Double> = transactionsForPeriod.map { transactions ->
        transactions
            .filter { it.transaction.transactionType == "income" && !it.transaction.isExcluded }
            .sumOf { it.transaction.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val insights: StateFlow<ReportInsights?> = _selectedDate.flatMapLatest { calendar ->
        flow {
            val (currentStart, currentEnd) = getPeriodDateRange(calendar)

            val previousPeriodEndCal = (calendar.clone() as Calendar).apply {
                when (timePeriod) {
                    TimePeriod.DAILY -> add(Calendar.HOUR_OF_DAY, -24)
                    TimePeriod.WEEKLY -> add(Calendar.DAY_OF_YEAR, -7)
                    TimePeriod.MONTHLY -> add(Calendar.MONTH, -1)
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthlyConsistencyData: StateFlow<List<CalendarDayStatus>> = _selectedDate.flatMapLatest { calendar ->
        flow {
            emit(generateMonthConsistencyData(calendar))
        }.flowOn(Dispatchers.Default)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consistencyStats: StateFlow<ConsistencyStats> = monthlyConsistencyData.map { data ->
        val goodDays = data.count { it.status == SpendingStatus.WITHIN_LIMIT }
        val badDays = data.count { it.status == SpendingStatus.OVER_LIMIT }
        val noSpendDays = data.count { it.status == SpendingStatus.NO_SPEND }
        val noDataDays = data.count { it.status == SpendingStatus.NO_DATA }
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
                    }, 1
                )
            }
        }
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
        }

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    private suspend fun generateMonthConsistencyData(calendar: Calendar): List<CalendarDayStatus> = withContext(Dispatchers.IO) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        val monthStartCal = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
        }
        val monthEndCal = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
        }

        val firstTransactionDate = transactionDao.getFirstTransactionDate().first()
        val firstDataCal = firstTransactionDate?.let { Calendar.getInstance().apply { timeInMillis = it } }

        val dailyTotals = transactionDao.getDailySpendingForDateRange(monthStartCal.timeInMillis, monthEndCal.timeInMillis).first()
        val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val budget = settingsRepository.getOverallBudgetForMonthBlocking(year, month)
        val safeToSpend = if (budget > 0) (budget.toDouble() / daysInMonth) else 0.0

        val resultList = mutableListOf<CalendarDayStatus>()
        val dayIterator = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }

        for (i in 1..daysInMonth) {
            dayIterator.set(Calendar.DAY_OF_MONTH, i)

            if (firstDataCal != null && dayIterator.before(firstDataCal)) {
                resultList.add(CalendarDayStatus(dayIterator.time, SpendingStatus.NO_DATA, 0.0, 0.0))
                continue
            }

            val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)
            val amountSpent = spendingMap[dateKey] ?: 0.0
            val status = when {
                amountSpent == 0.0 -> SpendingStatus.NO_SPEND
                safeToSpend > 0 && amountSpent > safeToSpend -> SpendingStatus.OVER_LIMIT
                else -> SpendingStatus.WITHIN_LIMIT
            }
            resultList.add(CalendarDayStatus(dayIterator.time, status, amountSpent, safeToSpend))
        }
        return@withContext resultList
    }
}
