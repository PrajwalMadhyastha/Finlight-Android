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
    @Mock // --- NEW: Add mock for the new dependency ---
    private lateinit var transactionRepository: TransactionRepository

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

        // --- NEW: Mock the new repository dependency ---
        `when`(transactionRepository.getMonthlyConsistencyData(anyInt(), anyInt())).thenReturn(flowOf(emptyList()))
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.MONTHLY, null, false)
        advanceUntilIdle()

        // ASSERT
        val expectedTotalIncome = (1000.50 + 2000.25).roundToLong() // 3001
        val actualTotalIncome = viewModel.totalIncome.first()

        assertEquals(expectedTotalIncome, actualTotalIncome)
    }

    @Test
    fun `selectPreviousPeriod updates selectedDate correctly for MONTHLY`() = runTest {
        // Arrange
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.MONTHLY, null, false)
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.YEARLY, null, false)
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.WEEKLY, null, false)
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.MONTHLY, null, true)
        advanceUntilIdle()

        // Assert
        val selectedDate = viewModel.selectedDate.value
        assertEquals(expectedMonth, selectedDate.get(Calendar.MONTH))
        assertEquals(expectedYear, selectedDate.get(Calendar.YEAR))
    }

    @Test
    fun `consistencyStats are correctly calculated for monthly view`() = runTest {
        // Arrange
        // We MUST use a fixed "today" for a reliable test, as the VM uses Calendar.getInstance()
        // We can't easily mock Calendar.getInstance(), so we'll run the test *as if* it's running
        // on a specific date by mocking the relevant data based on that date.
        // Let's assume today is Oct 9th, 2025 for this test's logic.
        val testCal = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 9) }
        val year = testCal.get(Calendar.YEAR)
        val month = testCal.get(Calendar.MONTH) + 1 // 10
        val firstDayOfMonth = (testCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

        // --- NEW: Mock the repository call for consistency data ---
        val mockData = mutableListOf<CalendarDayStatus>()
        val safeToSpend = 100L
        // We only mock the first 7 days.
        for (i in 1..7) {
            val date = (testCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, i) }.time
            val (status, amount) = when (i) {
                1 -> SpendingStatus.NO_DATA to 0L      // Day 1
                2 -> SpendingStatus.NO_SPEND to 0L     // Day 2
                3 -> SpendingStatus.WITHIN_LIMIT to 50L // Day 3
                4 -> SpendingStatus.OVER_LIMIT to 150L  // Day 4
                else -> SpendingStatus.NO_SPEND to 0L // Days 5, 6, 7
            }
            mockData.add(CalendarDayStatus(date, status, amount, safeToSpend))
        }
        `when`(transactionRepository.getMonthlyConsistencyData(year, month)).thenReturn(flowOf(mockData))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstDayOfMonth)) // Mock this for legacy code paths

        // Act
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.MONTHLY, testCal.timeInMillis, false)
        advanceUntilIdle()

        // Assert
        viewModel.consistencyStats.test {
            // Await the calculated value.
            val stats = awaitItem()

            // The ViewModel's `consistencyStats` flow uses the *real* `Calendar.getInstance()`
            // to filter `relevantData`. My system time is Oct 28, 2025.
            // The mock data is for Oct 1-7, 2025. All 7 days are before Oct 28, so all are relevant.
            // Stats from the 7 mocked days:
            // 1 GOOD (Day 3)
            // 1 BAD (Day 4)
            // 4 NO_SPEND (Day 2, 5, 6, 7)
            // 1 NO_DATA (Day 1)
            assertEquals(1, stats.goodDays)
            assertEquals(1, stats.badDays)
            // --- FIX: The assertion was 6 (based on old logic), but the new logic + mock data correctly produces 4.
            assertEquals(4, stats.noSpendDays)
            assertEquals(1, stats.noDataDays)
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.WEEKLY, null, false)
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.DAILY, null, false)
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
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, TimePeriod.MONTHLY, specificDate, false)
        advanceUntilIdle()

        // Assert
        val selectedDate = viewModel.selectedDate.value
        assertEquals(2023, selectedDate.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, selectedDate.get(Calendar.MONTH))
        assertEquals(15, selectedDate.get(Calendar.DAY_OF_MONTH))
    }
}
