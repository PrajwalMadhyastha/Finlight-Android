// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ReportsViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ReportsViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionRepository: TransactionRepository
    @Mock
    private lateinit var categoryDao: CategoryDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: ReportsViewModel

    @Before
    override fun setup() {
        super.setup()

        // Setup default mocks for initialization
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
        `when`(transactionRepository.getTopSpendingCategoriesForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))
        `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        // --- ADDED: Mock the new dependency for consistency flows ---
        `when`(transactionRepository.getMonthlyConsistencyData(anyInt(), anyInt())).thenReturn(flowOf(emptyList()))


        viewModel = ReportsViewModel(transactionRepository, categoryDao, settingsRepository)
    }

    private fun initializeViewModel() {
        viewModel = ReportsViewModel(transactionRepository, categoryDao, settingsRepository)
    }

    @Test
    fun `allCategories flow emits categories from DAO`() = runTest {
        // Arrange
        val categories = listOf(Category(1, "Food", "icon", "color"))
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(categories))
        viewModel = ReportsViewModel(transactionRepository, categoryDao, settingsRepository)

        // Assert
        viewModel.allCategories.test {
            assertEquals(categories, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reportData correctly processes data from repositories`() = runTest {
        // Arrange
        val spendingList = listOf(CategorySpending("Food", 100.0, "red", "restaurant"))
        val trends = listOf(MonthlyTrend("2025-10", 200.0, 100.0))
        val currentSummary = FinancialSummary(200.0, 100.0)
        val previousSummary = FinancialSummary(100.0, 50.0)
        val topCategory = spendingList.first()

        `when`(transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(spendingList))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(trends))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong()))
            .thenReturn(flowOf(currentSummary)) // First call for current
            .thenReturn(flowOf(previousSummary)) // Second call for previous
        `when`(transactionRepository.getTopSpendingCategoriesForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(topCategory))

        viewModel = ReportsViewModel(transactionRepository, categoryDao, settingsRepository)

        // Act
        viewModel.selectPeriod(ReportPeriod.MONTH) // Trigger the flatMapLatest
        advanceUntilIdle() // Ensure all flows have emitted

        // Assert
        viewModel.reportData.test {
            val data = awaitItem()
            assertNotNull(data.pieData)
            assertEquals(1, data.pieData?.entryCount)
            assertNotNull(data.trendData)
            // --- FIX: Corrected the assertions for the trend data ---
            // The BarData contains two datasets (income and expense).
            assertEquals(2, data.trendData?.first?.dataSetCount)
            // Each dataset should have one entry, corresponding to the one month of trend data.
            assertEquals(1, data.trendData?.first?.getDataSetByIndex(0)?.entryCount) // Income
            assertEquals(1, data.trendData?.first?.getDataSetByIndex(1)?.entryCount) // Expense
            assertEquals("This Month", data.periodTitle)
            assertNotNull(data.insights)
            assertEquals(100, data.insights?.percentageChange) // (100 - 50) / 50 * 100
            assertEquals(topCategory, data.insights?.topCategory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setReportView updates view type and displayed stats`() = runTest {
        // Arrange
        val yearlyData: List<CalendarDayStatus> = listOf(CalendarDayStatus(Calendar.getInstance().time, SpendingStatus.OVER_LIMIT, 120, 100))
        val monthlyData: List<CalendarDayStatus> = listOf(CalendarDayStatus(Calendar.getInstance().time, SpendingStatus.WITHIN_LIMIT, 80, 100))

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // Mock all 12 calls for the YEARLY view. One month (current) returns monthlyData, the rest return yearlyData.
        `when`(transactionRepository.getMonthlyConsistencyData(eq(currentYear), anyInt())).thenAnswer { invocation ->
            val month = invocation.getArgument<Int>(1)
            if (month == currentMonth) {
                flowOf(monthlyData)
            } else {
                flowOf(yearlyData)
            }
        }
        // This mock is for the MONTHLY view (detailedMonthData flow)
        `when`(transactionRepository.getMonthlyConsistencyData(eq(currentYear), eq(currentMonth))).thenReturn(flowOf(monthlyData))

        // Act
        initializeViewModel()
        advanceUntilIdle() // Let the init block's flows finish

        // Assert
        viewModel.displayedConsistencyStats.test {
            // 1. Consume the initial value (0,0,0,0).
            assertEquals(ConsistencyStats(0, 0, 0, 0), awaitItem())

            // 2. Await the first calculated value (default YEARLY view).
            // It will have 1 good day (from the current month) and 11 bad days.
            val yearlyStats = awaitItem()
            assertEquals(1, yearlyStats.goodDays)
            assertEquals(11, yearlyStats.badDays)

            // 3. Switch to MONTHLY and check stats.
            viewModel.setReportView(ReportViewType.MONTHLY)
            advanceUntilIdle()
            val monthlyStats = awaitItem() // This should now emit a new value
            // The monthlyData has 1 good day.
            assertEquals(1, monthlyStats.goodDays)
            assertEquals(0, monthlyStats.badDays)

            // 4. Switch back to YEARLY.
            viewModel.setReportView(ReportViewType.YEARLY)
            advanceUntilIdle()
            val yearlyStats2 = awaitItem() // This will be distinct from monthlyStats
            // It should be the same as the first yearly stats.
            assertEquals(1, yearlyStats2.goodDays)
            assertEquals(11, yearlyStats2.badDays)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectPeriod updates selectedPeriod state`() = runTest {
        // Arrange
        initializeViewModel()

        // Assert
        viewModel.selectedPeriod.test {
            assertEquals(ReportPeriod.MONTH, awaitItem()) // Default

            // Act
            viewModel.selectPeriod(ReportPeriod.WEEK)
            assertEquals(ReportPeriod.WEEK, awaitItem())

            viewModel.selectPeriod(ReportPeriod.ALL_TIME)
            assertEquals(ReportPeriod.ALL_TIME, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectPreviousMonth updates selectedMonth correctly`() = runTest {
        // Arrange
        initializeViewModel()
        val initialMonth = viewModel.selectedMonth.value.get(Calendar.MONTH)
        val expectedMonth = (initialMonth - 1 + 12) % 12

        // Act
        viewModel.selectPreviousMonth()
        advanceUntilIdle()

        // Assert
        val newMonth = viewModel.selectedMonth.value.get(Calendar.MONTH)
        assertEquals(expectedMonth, newMonth)
    }

    @Test
    fun `selectNextMonth updates selectedMonth correctly`() = runTest {
        // Arrange
        initializeViewModel()
        val initialMonth = viewModel.selectedMonth.value.get(Calendar.MONTH)
        val expectedMonth = (initialMonth + 1) % 12

        // Act
        viewModel.selectNextMonth()
        advanceUntilIdle()

        // Assert
        val newMonth = viewModel.selectedMonth.value.get(Calendar.MONTH)
        assertEquals(expectedMonth, newMonth)
    }

    @Test
    fun `reportData uses same-period comparison for MONTH period`() = runTest {
        // Arrange - Mock data for mid-month scenario (Jan 15, 2026)
        val spendingList = listOf(CategorySpending("Food", 150.0, "red", "restaurant"))
        val trends = listOf(MonthlyTrend("2026-01", 200.0, 150.0))
        
        // Current period (Jan 1-15, 2026): 150.0 expenses
        val currentSummary = FinancialSummary(0.0, 150.0)
        // Previous period (Jan 1-15, 2025): 100.0 expenses
        val previousSummary = FinancialSummary(0.0, 100.0)
        val topCategory = spendingList.first()

        var currentStartCaptured = 0L
        var currentEndCaptured = 0L
        var previousStartCaptured = 0L
        var previousEndCaptured = 0L
        var summaryCallCount = 0

        `when`(transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(spendingList))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(trends))
        
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenAnswer { invocation ->
            val start = invocation.getArgument<Long>(0)
            val end = invocation.getArgument<Long>(1)
            
            summaryCallCount++
            if (summaryCallCount == 1) {
                currentStartCaptured = start
                currentEndCaptured = end
                flowOf(currentSummary)
            } else {
                previousStartCaptured = start
                previousEndCaptured = end
                flowOf(previousSummary)
            }
        }
        
        `when`(transactionRepository.getTopSpendingCategoriesForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(topCategory))

        // Act
        viewModel = ReportsViewModel(transactionRepository, categoryDao, settingsRepository)
        viewModel.selectPeriod(ReportPeriod.MONTH)
        advanceUntilIdle()

        // Assert
        viewModel.reportData.test {
            val data = awaitItem()
            
            // Verify percentage change calculation (50% increase)
            assertEquals(50, data.insights?.percentageChange) // (150 - 100) / 100 * 100 = 50%
            
            // Verify current period is from start of current month to today
            val currentStartCal = Calendar.getInstance().apply { timeInMillis = currentStartCaptured }
            val currentEndCal = Calendar.getInstance().apply { timeInMillis = currentEndCaptured }
            assertEquals(1, currentStartCal.get(Calendar.DAY_OF_MONTH))
            
            // Verify previous period is same day range from previous year
            val previousStartCal = Calendar.getInstance().apply { timeInMillis = previousStartCaptured }
            val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
            
            val today = Calendar.getInstance()
            val previousYear = today.get(Calendar.YEAR) - 1
            
            assertEquals(previousYear, previousStartCal.get(Calendar.YEAR))
            assertEquals(today.get(Calendar.MONTH), previousStartCal.get(Calendar.MONTH))
            assertEquals(1, previousStartCal.get(Calendar.DAY_OF_MONTH))
            
            // Previous end should be same day of month as today (or max day if today exceeds it)
            assertEquals(previousYear, previousEndCal.get(Calendar.YEAR))
            assertEquals(today.get(Calendar.MONTH), previousEndCal.get(Calendar.MONTH))
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}

