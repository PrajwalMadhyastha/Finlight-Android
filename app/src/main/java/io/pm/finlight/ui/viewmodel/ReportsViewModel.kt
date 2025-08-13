// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ReportsViewModel.kt
// REASON: FIX - The consistency calendar flows (`consistencyCalendarData` and
// `detailedMonthData`) are now fully reactive to budget changes. They now
// combine flows from the SettingsRepository with transaction data, ensuring the
// heatmaps update instantly when the user modifies the monthly budget.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Enum to manage the state of the view toggle on the reports screen.
 */
enum class ReportViewType {
    YEARLY,
    MONTHLY
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val transactionRepository: TransactionRepository
    private val categoryDao: CategoryDao
    private val settingsRepository: SettingsRepository

    val allCategories: StateFlow<List<Category>>

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.MONTH)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()

    private val _reportViewType = MutableStateFlow(ReportViewType.YEARLY)
    val reportViewType: StateFlow<ReportViewType> = _reportViewType.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth: StateFlow<Calendar> = _selectedMonth.asStateFlow()

    val reportData: StateFlow<ReportScreenData>

    val consistencyCalendarData: StateFlow<List<CalendarDayStatus>>

    val detailedMonthData: StateFlow<List<CalendarDayStatus>>

    val displayedConsistencyStats: StateFlow<ConsistencyStats>

    init {
        val db = AppDatabase.getInstance(application)
        transactionRepository = TransactionRepository(db.transactionDao())
        categoryDao = db.categoryDao()
        settingsRepository = SettingsRepository(application)


        allCategories = categoryDao.getAllCategories()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        reportData = _selectedPeriod.flatMapLatest { period ->
            val (currentStartDate, currentEndDate) = calculateDateRange(period)
            val (previousStartDate, previousEndDate) = calculatePreviousDateRange(period)
            val trendStartDate = calculateTrendStartDate(period)

            val categorySpendingFlow = transactionRepository.getSpendingByCategoryForMonth(
                startDate = currentStartDate,
                endDate = currentEndDate,
                keyword = null, accountId = null, categoryId = null
            )

            val monthlyTrendFlow = transactionRepository.getMonthlyTrends(trendStartDate)

            val currentSummaryFlow = transactionRepository.getFinancialSummaryForRangeFlow(currentStartDate, currentEndDate)
            val previousSummaryFlow = transactionRepository.getFinancialSummaryForRangeFlow(previousStartDate, previousEndDate)
            val topCategoryFlow = transactionRepository.getTopSpendingCategoriesForRangeFlow(currentStartDate, currentEndDate)

            combine(
                categorySpendingFlow,
                monthlyTrendFlow,
                currentSummaryFlow,
                previousSummaryFlow,
                topCategoryFlow
            ) { spendingList, trends, currentSummary, previousSummary, topCategory ->
                // Create PieData
                val pieEntries = spendingList.map {
                    PieEntry(it.totalAmount.toFloat(), it.categoryName, it.categoryName)
                }
                val pieColors = spendingList.map {
                    (CategoryIconHelper.getIconBackgroundColor(it.colorKey ?: "gray_light")).toArgb()
                }
                val pieDataSet = PieDataSet(pieEntries, "Spending by Category").apply {
                    this.colors = pieColors
                    valueTextSize = 12f
                }
                val finalPieData = if (pieEntries.isEmpty()) null else PieData(pieDataSet)

                // Create BarData for trends
                val incomeEntries = ArrayList<BarEntry>()
                val expenseEntries = ArrayList<BarEntry>()
                val labels = ArrayList<String>()
                trends.forEachIndexed { index, trend ->
                    incomeEntries.add(BarEntry(index.toFloat(), trend.totalIncome.toFloat()))
                    expenseEntries.add(BarEntry(index.toFloat(), trend.totalExpenses.toFloat()))
                    val date = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(trend.monthYear)
                    labels.add(SimpleDateFormat("MMM", Locale.getDefault()).format(date ?: Date()))
                }
                val incomeDataSet = BarDataSet(incomeEntries, "Income").apply { color = android.graphics.Color.rgb(102, 187, 106) }
                val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply { color = android.graphics.Color.rgb(239, 83, 80) }
                val finalTrendData = if (trends.isEmpty()) null else Pair(BarData(incomeDataSet, expenseDataSet), labels)

                // Calculate insights
                val percentageChange = if (previousSummary?.totalExpenses != null && previousSummary.totalExpenses > 0) {
                    val currentExpenses = currentSummary?.totalExpenses ?: 0.0
                    ((currentExpenses - previousSummary.totalExpenses) / previousSummary.totalExpenses * 100).roundToInt()
                } else {
                    null
                }
                val insights = ReportInsights(percentageChange, topCategory)

                val periodTitle = when (period) {
                    ReportPeriod.WEEK -> "This Week"
                    ReportPeriod.MONTH -> "This Month"
                    ReportPeriod.QUARTER -> "Last 3 Months"
                    ReportPeriod.ALL_TIME -> "All Time"
                }

                ReportScreenData(finalPieData, finalTrendData, periodTitle, insights)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReportScreenData(null, null, "This Month", null)
        )

        // --- UPDATED: Make yearly consistency data reactive to budget changes ---
        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)
        val currentMonthIndex = today.get(Calendar.MONTH)
        val monthlyBudgetFlows = (0..currentMonthIndex).map { month ->
            settingsRepository.getOverallBudgetForMonth(year, month + 1)
        }
        val totalBudgetSoFarFlow = combine(monthlyBudgetFlows) { budgets ->
            budgets.sum()
        }

        consistencyCalendarData = combine(
            totalBudgetSoFarFlow,
            transactionRepository.getDailySpendingForDateRange(
                Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis,
                today.timeInMillis
            ),
            transactionRepository.getFirstTransactionDate()
        ) { totalBudget, dailyTotals, firstTransactionDate ->
            generateConsistencyCalendarData(totalBudget, dailyTotals, firstTransactionDate)
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // --- UPDATED: Make detailed monthly data reactive to budget changes ---
        detailedMonthData = _selectedMonth.flatMapLatest { monthCal ->
            val month = monthCal.get(Calendar.MONTH) + 1
            val year = monthCal.get(Calendar.YEAR)
            val monthStartCal = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }
            val monthEndCal = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23) }

            combine(
                settingsRepository.getOverallBudgetForMonth(year, month),
                transactionRepository.getDailySpendingForDateRange(monthStartCal.timeInMillis, monthEndCal.timeInMillis),
                transactionRepository.getFirstTransactionDate()
            ) { budget, dailyTotals, firstTransactionDate ->
                generateConsistencyDataForMonth(monthCal, budget, dailyTotals, firstTransactionDate)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


        displayedConsistencyStats = combine(
            reportViewType,
            consistencyCalendarData,
            detailedMonthData
        ) { viewType, yearlyData, monthlyData ->
            val today = Calendar.getInstance()
            val dataToProcess = if (viewType == ReportViewType.YEARLY) {
                yearlyData
            } else {
                monthlyData.filter { !it.date.after(today.time) }
            }

            val goodDays = dataToProcess.count { it.status == SpendingStatus.WITHIN_LIMIT }
            val badDays = dataToProcess.count { it.status == SpendingStatus.OVER_LIMIT }
            val noSpendDays = dataToProcess.count { it.status == SpendingStatus.NO_SPEND }
            val noDataDays = dataToProcess.count { it.status == SpendingStatus.NO_DATA }
            ConsistencyStats(goodDays, badDays, noSpendDays, noDataDays)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConsistencyStats(0, 0, 0, 0)
        )
    }

    private suspend fun generateConsistencyCalendarData(
        totalBudgetSoFar: Float,
        dailyTotals: List<DailyTotal>,
        firstTransactionDate: Long?
    ): List<CalendarDayStatus> = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)

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

        val daysSoFar = today.get(Calendar.DAY_OF_YEAR)
        val yearlySafeToSpend = if (totalBudgetSoFar > 0 && daysSoFar > 0) (totalBudgetSoFar / daysSoFar).toDouble() else 0.0

        val firstDataCal = Calendar.getInstance().apply { timeInMillis = firstTransactionDate }
        val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })

        val resultList = mutableListOf<CalendarDayStatus>()
        val dayIterator = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
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

    private suspend fun generateConsistencyDataForMonth(
        calendar: Calendar,
        budget: Float,
        dailyTotals: List<DailyTotal>,
        firstTransactionDate: Long?
    ): List<CalendarDayStatus> = withContext(Dispatchers.IO) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        val firstDataCal = firstTransactionDate?.let { Calendar.getInstance().apply { timeInMillis = it } }
        val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val safeToSpend = if (budget > 0) (budget.toDouble() / daysInMonth) else 0.0

        val resultList = mutableListOf<CalendarDayStatus>()
        val dayIterator = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val today = Calendar.getInstance()

        for (i in 1..daysInMonth) {
            dayIterator.set(Calendar.DAY_OF_MONTH, i)
            val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)

            if (dayIterator.after(today) || (firstDataCal != null && dayIterator.before(firstDataCal))) {
                resultList.add(CalendarDayStatus(dayIterator.time, SpendingStatus.NO_DATA, 0.0, 0.0))
                continue
            }

            val amountSpent = spendingMap[dateKey] ?: 0.0
            val status = when {
                amountSpent == 0.0 -> SpendingStatus.NO_SPEND
                safeToSpend > 0 && amountSpent > safeToSpend -> SpendingStatus.OVER_LIMIT
                else -> SpendingStatus.WITHIN_LIMIT
            }
            resultList.add(CalendarDayStatus(dayIterator.time, status, amountSpent, safeToSpend))
        }
        resultList
    }


    fun selectPeriod(period: ReportPeriod) {
        _selectedPeriod.value = period
    }

    fun setReportView(viewType: ReportViewType) {
        _reportViewType.value = viewType
    }

    fun selectPreviousMonth() {
        _selectedMonth.update {
            (it.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        }
    }

    fun selectNextMonth() {
        _selectedMonth.update {
            (it.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        }
    }


    private fun calculateDateRange(period: ReportPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        val startDate = when (period) {
            ReportPeriod.WEEK -> (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            ReportPeriod.MONTH -> (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            ReportPeriod.QUARTER -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -3) }.timeInMillis
            ReportPeriod.ALL_TIME -> 0L
        }
        return Pair(startDate, endDate)
    }

    private fun calculatePreviousDateRange(period: ReportPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        return when (period) {
            ReportPeriod.WEEK -> {
                val endDate = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
                val startDate = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -14) }.timeInMillis
                Pair(startDate, endDate)
            }
            ReportPeriod.MONTH -> {
                val endDate = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
                val startDate = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                Pair(startDate, endDate)
            }
            ReportPeriod.QUARTER -> {
                val endDate = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -3) }.timeInMillis
                val startDate = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -6) }.timeInMillis
                Pair(startDate, endDate)
            }
            ReportPeriod.ALL_TIME -> Pair(0L, 0L) // No previous period for "All Time"
        }
    }

    private fun calculateTrendStartDate(period: ReportPeriod): Long {
        val calendar = Calendar.getInstance()
        return when (period) {
            ReportPeriod.WEEK, ReportPeriod.MONTH -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -6) }.timeInMillis
            ReportPeriod.QUARTER -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -6) }.timeInMillis
            ReportPeriod.ALL_TIME -> 0L
        }
    }
}
