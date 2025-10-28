// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/ReportsViewModel.kt
// REASON: REFACTOR (Consistency) - This ViewModel now uses the new, centralized
// `transactionRepository.getMonthlyConsistencyData` function for both its monthly
// and yearly calendar flows. The old, duplicated, and buggy local functions
// (`generateConsistencyCalendarData`, `generateConsistencyDataForMonth`) have been
// completely removed.
// =================================================================================
package io.pm.finlight

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
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
import kotlin.math.roundToLong

/**
 * Enum to manage the state of the view toggle on the reports screen.
 */
enum class ReportViewType {
    YEARLY,
    MONTHLY
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

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

        // --- REFACTORED: Use the new "Monthly-First" centralized logic ---
        consistencyCalendarData = flow {
            val today = Calendar.getInstance()
            val year = today.get(Calendar.YEAR)

            // Create a flow for each month of the current year
            // --- FIX: Add explicit types to resolve build error ---
            val monthlyDataFlows: List<Flow<List<CalendarDayStatus>>> = (1..12).map { month ->
                transactionRepository.getMonthlyConsistencyData(year, month)
            }

            // Combine all 12 flows
            // --- FIX: This is the corrected logic ---
            val combinedFlow = combine(monthlyDataFlows) { monthlyDataArray: Array<List<CalendarDayStatus>> ->
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

        // --- REFACTORED: Use the new centralized repository function ---
        detailedMonthData = _selectedMonth.flatMapLatest { monthCal ->
            val month = monthCal.get(Calendar.MONTH) + 1
            val year = monthCal.get(Calendar.YEAR)
            transactionRepository.getMonthlyConsistencyData(year, month)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


        val today = Calendar.getInstance()
        displayedConsistencyStats = combine(
            reportViewType,
            consistencyCalendarData,
            detailedMonthData
        ) { viewType, yearlyData, monthlyData ->
            val dataToProcess = if (viewType == ReportViewType.YEARLY) {
                yearlyData
            } else {
                monthlyData
            }
            // Filter out future days from stats calculation
            val relevantData = dataToProcess.filter { !it.date.after(today.time) }

            val goodDays = relevantData.count { it.status == SpendingStatus.WITHIN_LIMIT }
            val badDays = relevantData.count { it.status == SpendingStatus.OVER_LIMIT }
            val noSpendDays = relevantData.count { it.status == SpendingStatus.NO_SPEND }
            val noDataDays = relevantData.count { it.status == SpendingStatus.NO_DATA }
            ConsistencyStats(goodDays, badDays, noSpendDays, noDataDays)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConsistencyStats(0, 0, 0, 0)
        )
    }

    // --- DELETED: generateConsistencyCalendarData function ---

    // --- DELETED: generateConsistencyDataForMonth function ---


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

