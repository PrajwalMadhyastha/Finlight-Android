// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ReportsViewModelTest.kt
// REASON: NEW FILE - Unit tests for ReportsViewModel, covering state
// observation from its various repository and DAO dependencies.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ReportsViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var transactionRepository: TransactionRepository
    @Mock
    private lateinit var categoryDao: CategoryDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: ReportsViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
}