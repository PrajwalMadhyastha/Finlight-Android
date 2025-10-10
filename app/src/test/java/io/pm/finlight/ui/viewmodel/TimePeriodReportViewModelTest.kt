package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TimePeriodReportViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Before
    override fun setup() {
        super.setup()
        // Setup default mocks that apply to most tests in this file
        `when`(transactionDao.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))

        runTest {
            `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(null)
            `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())
            `when`(settingsRepository.getOverallBudgetForMonthBlocking(anyInt(), anyInt())).thenReturn(0f)
        }

        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getWeeklySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getMonthlySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(0L))
    }

    @Test
    fun `totalIncome is correctly calculated and converted to Long`() = runTest {
        // ARRANGE
        val transactions = listOf(
            TransactionDetails(Transaction(id = 1, amount = 1000.50, transactionType = "income", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 2, amount = 2000.25, transactionType = "income", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 3, amount = 500.0, transactionType = "expense", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null)
        )
        `when`(transactionDao.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))


        // ACT
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, false)
        advanceUntilIdle()

        // ASSERT
        val expectedTotalIncome = (1000.50 + 2000.25).roundToLong() // 3001
        val actualTotalIncome = viewModel.totalIncome.first()

        assertEquals(expectedTotalIncome, actualTotalIncome)
    }

    @Test
    fun `selectPreviousPeriod updates selectedDate correctly for MONTHLY`() = runTest {
        // Arrange
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, false)
        val initialMonth = viewModel.selectedDate.first().get(Calendar.MONTH)

        // Act
        viewModel.selectPreviousPeriod()
        advanceUntilIdle()

        // Assert
        val newMonth = viewModel.selectedDate.first().get(Calendar.MONTH)
        val expectedMonth = (initialMonth - 1 + 12) % 12
        assertEquals(expectedMonth, newMonth)
    }

    @Test
    fun `selectNextPeriod updates selectedDate correctly for YEARLY`() = runTest {
        // Arrange
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.YEARLY, null, false)
        val initialYear = viewModel.selectedDate.first().get(Calendar.YEAR)

        // Act
        viewModel.selectNextPeriod()
        advanceUntilIdle()

        // Assert
        val newYear = viewModel.selectedDate.first().get(Calendar.YEAR)
        assertEquals(initialYear + 1, newYear)
    }

    @Test
    fun `insights flow calculates percentageChange correctly`() = runTest {
        // Arrange
        val currentSummary = FinancialSummary(0.0, 150.0)
        val previousSummary = FinancialSummary(0.0, 100.0)
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong()))
            .thenReturn(currentSummary) // First call for current
            .thenReturn(previousSummary) // Second call for previous
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.WEEKLY, null, false)
        advanceUntilIdle()

        // Assert
        viewModel.insights.test {
            val insights = awaitItem()
            assertEquals(50, insights?.percentageChange) // (150 - 100) / 100 * 100
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showPreviousMonth factory parameter sets initial date correctly`() = runTest {
        // Arrange
        val today = Calendar.getInstance()
        val expectedMonth = (today.get(Calendar.MONTH) - 1 + 12) % 12
        val expectedYear = if (today.get(Calendar.MONTH) == Calendar.JANUARY) today.get(Calendar.YEAR) - 1 else today.get(Calendar.YEAR)

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, true)
        advanceUntilIdle()

        // Assert
        val selectedDate = viewModel.selectedDate.value
        assertEquals(expectedMonth, selectedDate.get(Calendar.MONTH))
        assertEquals(expectedYear, selectedDate.get(Calendar.YEAR))
    }

    @Test
    fun `consistencyStats are correctly calculated for monthly view`() = runTest {
        // Arrange
        val testCal = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 9) }
        val year = testCal.get(Calendar.YEAR)
        val month = testCal.get(Calendar.MONTH) + 1
        val daysInMonth = testCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfMonth = (testCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

        // Budget: 3100 -> SafeToSpend: 100/day
        `when`(settingsRepository.getOverallBudgetForMonthBlocking(year, month)).thenReturn(3100f)
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstDayOfMonth))

        val dailyTotals = listOf(
            DailyTotal(date = "2025-10-01", totalAmount = 0.0),      // No Spend
            DailyTotal(date = "2025-10-02", totalAmount = 50.0),     // Good Day
            DailyTotal(date = "2025-10-03", totalAmount = 150.0)     // Bad Day
        )
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, testCal.timeInMillis, false)
        advanceUntilIdle()

        // Assert
        viewModel.consistencyStats.test {
            // The flow first emits its initial value (0,0,0,0) from stateIn. We consume it.
            val initialStats = awaitItem()
            assertEquals(0, initialStats.noSpendDays)

            // Await the second, calculated value.
            val stats = awaitItem()

            // The production code loops through all days in the month (31 for October).
            // Days not in `dailyTotals` are counted as NO_SPEND.
            // Day 1 is explicit NO_SPEND. Days 4-31 are implicit NO_SPEND.
            val expectedNoSpendDays = 1 + (daysInMonth - dailyTotals.size) // 1 + (31 - 3) = 29

            assertEquals(expectedNoSpendDays, stats.noSpendDays)
            assertEquals(1, stats.goodDays)
            assertEquals(1, stats.badDays)
            assertEquals(0, stats.noDataDays) // First transaction is on day 1.
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chartData generates correctly for WEEKLY period`() = runTest {
        // Arrange
        val weeklyTotals = listOf(
            PeriodTotal("2025-40", 1000.0),
            PeriodTotal("2025-41", 1500.0)
        )
        `when`(transactionDao.getWeeklySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(weeklyTotals))

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.WEEKLY, null, false)
        advanceUntilIdle()

        // Assert
        viewModel.chartData.test {
            val chartPair = awaitItem()
            assertNotNull(chartPair)
            assertEquals(8, chartPair!!.first.entryCount) // 8 weeks total
            assertEquals(8, chartPair.second.size) // 8 labels
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chartData returns null when DAO returns empty data`() = runTest {
        // Arrange
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.DAILY, null, false)
        advanceUntilIdle()

        // Assert
        viewModel.chartData.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initialDateMillis correctly sets initial selectedDate`() = runTest {
        // Arrange
        val specificDate = Calendar.getInstance().apply {
            set(2023, Calendar.JUNE, 15)
        }.timeInMillis

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, specificDate, false)
        advanceUntilIdle()

        // Assert
        val selectedDate = viewModel.selectedDate.value
        assertEquals(2023, selectedDate.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, selectedDate.get(Calendar.MONTH))
        assertEquals(15, selectedDate.get(Calendar.DAY_OF_MONTH))
    }
}
