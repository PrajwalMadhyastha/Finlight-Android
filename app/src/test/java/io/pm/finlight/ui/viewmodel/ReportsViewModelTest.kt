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
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(3000f))
        // Provide a valid start date for transaction history
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)))

        `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenAnswer { invocation ->
            val startDate = invocation.getArgument<Long>(0)
            val endDate = invocation.getArgument<Long>(1)
            val durationDays = TimeUnit.MILLISECONDS.toDays(endDate - startDate)

            if (durationDays > 31) { // Heuristic: A long range is for the yearly calendar
                flowOf(listOf(DailyTotal("2025-10-07", 120.0))) // Causes 1 bad day
            } else { // A short range is for the monthly calendar
                flowOf(listOf(DailyTotal("2025-10-07", 80.0))) // Causes 1 good day
            }
        }

        initializeViewModel()

        // Act & Assert
        viewModel.displayedConsistencyStats.test {
            // 1. Consume the initial value from stateIn which is always (0,0,0,0).
            assertEquals("Initial value should be empty stats", ConsistencyStats(0, 0, 0, 0), awaitItem())

            // 2. Await the first calculated value for the default YEARLY view.
            // This is emitted after the ViewModel's init block coroutines complete.
            val yearlyStats1 = awaitItem()
            assertEquals("Initial YEARLY stats should have 0 good days", 0, yearlyStats1.goodDays)
            assertEquals("Initial YEARLY stats should have 1 bad day", 1, yearlyStats1.badDays)

            // 3. Switch to MONTHLY and check stats. This triggers a new emission.
            viewModel.setReportView(ReportViewType.MONTHLY)
            val monthlyStats = awaitItem()
            // Daily safe to spend = 3000 / days_in_month ~= 96. Spend = 80. So, 1 good day.
            assertEquals("MONTHLY stats should have 1 good day", 1, monthlyStats.goodDays)
            assertEquals("MONTHLY stats should have 0 bad days", 0, monthlyStats.badDays)

            // 4. Switch back to YEARLY and check stats.
            viewModel.setReportView(ReportViewType.YEARLY)
            val yearlyStats2 = awaitItem()
            // The data for the yearly view hasn't changed. It should still be a bad day.
            assertEquals("Second YEARLY stats should have 0 good days", 0, yearlyStats2.goodDays)
            assertEquals("Second YEARLY stats should have 1 bad day", 1, yearlyStats2.badDays)

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
}