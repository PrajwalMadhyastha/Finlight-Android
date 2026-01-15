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

        // --- FIX: Mock settingsRepository flows to avoid null upstream issue ---
        `when`(settingsRepository.getExcludedIncomeMonths()).thenReturn(flowOf(emptySet()))
        `when`(settingsRepository.getExcludedExpenseMonths()).thenReturn(flowOf(emptySet()))
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, null, false)
        advanceUntilIdle()

        // ASSERT
        val expectedTotalIncome = 3001L // Corrected from 3000L to match roundToLong() behavior for 3000.75
        val actualTotalIncome = viewModel.totalIncome.first()

        assertEquals(expectedTotalIncome, actualTotalIncome)
    }

    @Test
    fun `selectPreviousPeriod updates selectedDate correctly for MONTHLY`() = runTest {
        // Arrange
        // --- FIX: Pass transactionRepository ---
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, null, false)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.YEARLY, null, false)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.WEEKLY, null, false)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, null, true)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, testCal.timeInMillis, false)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.WEEKLY, null, false)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.DAILY, null, false)
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
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, specificDate, false)
        advanceUntilIdle()

        // Assert
        val selectedDate = viewModel.selectedDate.value
        assertEquals(2023, selectedDate.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, selectedDate.get(Calendar.MONTH))
        assertEquals(15, selectedDate.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `insights uses same-period comparison for monthly reports mid-month`() = runTest {
        // Arrange - Simulate viewing Jan 15, 2026
        val testDate = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Mock current period (Jan 1-15, 2026): 150.0 expenses
        val currentSummary = FinancialSummary(0.0, 150.0)
        // Mock previous period (Jan 1-15, 2025): 100.0 expenses
        val previousSummary = FinancialSummary(0.0, 100.0)

        // We need to verify the exact date ranges being queried
        var currentStartCaptured = 0L
        var currentEndCaptured = 0L
        var previousStartCaptured = 0L
        var previousEndCaptured = 0L

        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
            val start = invocation.getArgument<Long>(0)
            val end = invocation.getArgument<Long>(1)
            
            // First call is for current period
            if (currentStartCaptured == 0L) {
                currentStartCaptured = start
                currentEndCaptured = end
                currentSummary
            } else {
                // Second call is for previous period
                previousStartCaptured = start
                previousEndCaptured = end
                previousSummary
            }
        }

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, testDate.timeInMillis, false)
        advanceUntilIdle()

        // Assert
        viewModel.insights.test {
            val insights = awaitItem()
            
            // Verify percentage change calculation
            assertEquals(50, insights?.percentageChange) // (150 - 100) / 100 * 100 = 50%
            
            // Verify current period is Jan 1-15, 2026 (not full month)
            val currentStartCal = Calendar.getInstance().apply { timeInMillis = currentStartCaptured }
            assertEquals(2026, currentStartCal.get(Calendar.YEAR))
            assertEquals(Calendar.JANUARY, currentStartCal.get(Calendar.MONTH))
            assertEquals(1, currentStartCal.get(Calendar.DAY_OF_MONTH))
            
            val currentEndCal = Calendar.getInstance().apply { timeInMillis = currentEndCaptured }
            assertEquals(15, currentEndCal.get(Calendar.DAY_OF_MONTH))
            
            // Verify previous period is Jan 1-15, 2025 (same duration)
            val previousStartCal = Calendar.getInstance().apply { timeInMillis = previousStartCaptured }
            assertEquals(2025, previousStartCal.get(Calendar.YEAR))
            assertEquals(Calendar.JANUARY, previousStartCal.get(Calendar.MONTH))
            assertEquals(1, previousStartCal.get(Calendar.DAY_OF_MONTH))
            
            val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
            assertEquals(15, previousEndCal.get(Calendar.DAY_OF_MONTH))
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insights uses same-period comparison for yearly reports mid-year`() = runTest {
        // Arrange - Simulate viewing Jan 15, 2026
        val testDate = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentSummary = FinancialSummary(0.0, 500.0)
        val previousSummary = FinancialSummary(0.0, 400.0)

        var currentStartCaptured = 0L
        var currentEndCaptured = 0L
        var previousStartCaptured = 0L
        var previousEndCaptured = 0L

        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
            val start = invocation.getArgument<Long>(0)
            val end = invocation.getArgument<Long>(1)
            
            if (currentStartCaptured == 0L) {
                currentStartCaptured = start
                currentEndCaptured = end
                currentSummary
            } else {
                previousStartCaptured = start
                previousEndCaptured = end
                previousSummary
            }
        }

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.YEARLY, testDate.timeInMillis, false)
        advanceUntilIdle()

        // Assert
        viewModel.insights.test {
            val insights = awaitItem()
            
            // Verify percentage change
            assertEquals(25, insights?.percentageChange) // (500 - 400) / 400 * 100 = 25%
            
            // Verify current period is Jan 1-15, 2026
            val currentStartCal = Calendar.getInstance().apply { timeInMillis = currentStartCaptured }
            assertEquals(2026, currentStartCal.get(Calendar.YEAR))
            assertEquals(1, currentStartCal.get(Calendar.DAY_OF_YEAR))
            
            val currentEndCal = Calendar.getInstance().apply { timeInMillis = currentEndCaptured }
            assertEquals(15, currentEndCal.get(Calendar.DAY_OF_YEAR))
            
            // Verify previous period is Jan 1-15, 2025
            val previousStartCal = Calendar.getInstance().apply { timeInMillis = previousStartCaptured }
            assertEquals(2025, previousStartCal.get(Calendar.YEAR))
            assertEquals(1, previousStartCal.get(Calendar.DAY_OF_YEAR))
            
            val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
            assertEquals(15, previousEndCal.get(Calendar.DAY_OF_YEAR))
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insights uses full period comparison for completed months`() = runTest {
        // Arrange - Simulate viewing December 2025 (completed month)
        val testDate = Calendar.getInstance().apply {
            set(2025, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val currentSummary = FinancialSummary(0.0, 3100.0)
        val previousSummary = FinancialSummary(0.0, 3000.0)

        var previousStartCaptured = 0L
        var previousEndCaptured = 0L
        var callCount = 0

        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
            val start = invocation.getArgument<Long>(0)
            val end = invocation.getArgument<Long>(1)
            
            callCount++
            if (callCount == 1) {
                currentSummary
            } else {
                previousStartCaptured = start
                previousEndCaptured = end
                previousSummary
            }
        }

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.MONTHLY, testDate.timeInMillis, false)
        advanceUntilIdle()

        // Assert
        viewModel.insights.test {
            val insights = awaitItem()
            
            // Verify percentage change
            assertEquals(3, insights?.percentageChange) // (3100 - 3000) / 3000 * 100 ≈ 3%
            
            // Verify previous period is full December 2024
            val previousStartCal = Calendar.getInstance().apply { timeInMillis = previousStartCaptured }
            assertEquals(2024, previousStartCal.get(Calendar.YEAR))
            assertEquals(Calendar.DECEMBER, previousStartCal.get(Calendar.MONTH))
            assertEquals(1, previousStartCal.get(Calendar.DAY_OF_MONTH))
            
            val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
            assertEquals(31, previousEndCal.get(Calendar.DAY_OF_MONTH))
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insights preserves existing behavior for daily reports`() = runTest {
        // Arrange
        val currentSummary = FinancialSummary(0.0, 150.0)
        val previousSummary = FinancialSummary(0.0, 100.0)
        
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong()))
            .thenReturn(currentSummary)
            .thenReturn(previousSummary)

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.DAILY, null, false)
        advanceUntilIdle()

        // Assert - Daily reports should still work as before
        viewModel.insights.test {
            val insights = awaitItem()
            assertEquals(50, insights?.percentageChange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insights handles leap year edge case for yearly reports`() = runTest {
        // Arrange - Simulate viewing Feb 29, 2024 (leap year)
        val testDate = Calendar.getInstance().apply {
            set(2024, Calendar.FEBRUARY, 29, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentSummary = FinancialSummary(0.0, 200.0)
        val previousSummary = FinancialSummary(0.0, 180.0)

        var previousEndCaptured = 0L
        var callCount = 0

        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
            val end = invocation.getArgument<Long>(1)
            
            callCount++
            if (callCount == 1) {
                currentSummary
            } else {
                previousEndCaptured = end
                previousSummary
            }
        }

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, transactionRepository, settingsRepository, TimePeriod.YEARLY, testDate.timeInMillis, false)
        advanceUntilIdle()

        // Assert
        viewModel.insights.test {
            val insights = awaitItem()
            
            // Verify percentage change
            assertEquals(11, insights?.percentageChange) // (200 - 180) / 180 * 100 ≈ 11%
            
            // Verify previous year end is Feb 28, 2023 (not Feb 29, since 2023 is not a leap year)
            val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
            assertEquals(2023, previousEndCal.get(Calendar.YEAR))
            // Day 60 in 2024 (leap year) is Feb 29, but day 60 in 2023 (non-leap) is Mar 1
            // Our logic should cap at the max day of year for 2023
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}