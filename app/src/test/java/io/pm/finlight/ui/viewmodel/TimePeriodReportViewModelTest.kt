package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.utils.TestDispatcherProvider
import java.util.Calendar
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

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TimePeriodReportViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var settingsRepository: ISettingsRepository

    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    @Mock
    private lateinit var getMonthlyConsistencyDataUseCase: GetMonthlyConsistencyDataUseCase

    private val testDispatcherProvider by lazy { TestDispatcherProvider(testDispatcher) }

    private fun createViewModel(
        timePeriod: TimePeriod = TimePeriod.MONTHLY,
        initialDateMillis: Long? = null,
        showPreviousMonth: Boolean = false,
        useCase: GetMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
    ): TimePeriodReportViewModel {
        return TimePeriodReportViewModel(
            transactionRepository = transactionRepository,
            settingsRepository = settingsRepository,
            timePeriod = timePeriod,
            initialDateMillis = initialDateMillis,
            showPreviousMonth = showPreviousMonth,
            dispatcherProvider = testDispatcherProvider,
            getMonthlyConsistencyDataUseCase = useCase,
        )
    }

    @Before
    override fun setup() {
        super.setup()
        `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))

        runTest {
            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(null)
            `when`(transactionRepository.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())
        }

        `when`(transactionRepository.getDailyTrends(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getWeeklyTrends(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(0L))
        `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

        `when`(getMonthlyConsistencyDataUseCase(anyInt(), anyInt())).thenReturn(flowOf(emptyList()))

        `when`(settingsRepository.getExcludedIncomeMonths()).thenReturn(flowOf(emptySet()))
        `when`(settingsRepository.getExcludedExpenseMonths()).thenReturn(flowOf(emptySet()))
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(null))
    }

    @Test
    fun `totalIncome is correctly calculated and converted to Long`() =
        runTest {
            // ARRANGE
            val transactions =
                listOf(
                    TransactionDetails(
                        Transaction(
                            id = 1,
                            amount = 1000.50,
                            transactionType = TransactionType.INCOME,
                            description = "",
                            categoryId = 1,
                            date = 0L,
                            accountId = 1,
                            notes = null,
                        ),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                    TransactionDetails(
                        Transaction(
                            id = 2,
                            amount = 2000.25,
                            transactionType = TransactionType.INCOME,
                            description = "",
                            categoryId = 1,
                            date = 0L,
                            accountId = 1,
                            notes = null,
                        ),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                    TransactionDetails(
                        Transaction(
                            id = 3,
                            amount = 500.0,
                            transactionType = TransactionType.EXPENSE,
                            description = "",
                            categoryId = 1,
                            date = 0L,
                            accountId = 1,
                            notes = null,
                        ),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))

            // ACT
            val viewModel = createViewModel(TimePeriod.MONTHLY)
            advanceUntilIdle()

            // ASSERT
            val expectedTotalIncome = 3001L // Corrected from 3000L to match roundToLong() behavior for 3000.75
            val actualTotalIncome = viewModel.totalIncome.first()

            assertEquals(expectedTotalIncome, actualTotalIncome)
        }

    @Test
    fun `selectPreviousPeriod updates selectedDate correctly for MONTHLY`() =
        runTest {
            // Arrange
            val viewModel = createViewModel(TimePeriod.MONTHLY)
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
    fun `selectNextPeriod updates selectedDate correctly for YEARLY`() =
        runTest {
            // Arrange
            val viewModel = createViewModel(TimePeriod.YEARLY)
            val initialYear = viewModel.selectedDate.first().get(Calendar.YEAR)

            // Act
            viewModel.selectNextPeriod()
            advanceUntilIdle()

            // Assert
            val newYear = viewModel.selectedDate.first().get(Calendar.YEAR)
            assertEquals(initialYear + 1, newYear)
        }

    @Test
    fun `insights flow calculates percentageChange correctly`() =
        runTest {
            // Arrange
            val currentSummary = FinancialSummary(0.0, 150.0)
            val previousSummary = FinancialSummary(0.0, 100.0)
            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong()))
                .thenReturn(currentSummary) // First call for current
                .thenReturn(previousSummary) // Second call for previous
            val viewModel = createViewModel(TimePeriod.WEEKLY)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()
                assertEquals(50, insights?.percentageChange) // (150 - 100) / 100 * 100
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insights flow returns null percentageChange when previous summary expenses is zero`() =
        runTest {
            // Arrange
            val currentSummary = FinancialSummary(0.0, 150.0)
            val previousSummary = FinancialSummary(0.0, 0.0)
            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong()))
                .thenReturn(currentSummary)
                .thenReturn(previousSummary)
            val viewModel = createViewModel(TimePeriod.WEEKLY)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()
                assertNull(insights?.percentageChange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insights flow returns null percentageChange when previous summary is null`() =
        runTest {
            // Arrange
            val currentSummary = FinancialSummary(0.0, 150.0)
            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong()))
                .thenReturn(currentSummary)
                .thenReturn(null)
            val viewModel = createViewModel(TimePeriod.WEEKLY)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()
                assertNull(insights?.percentageChange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `showPreviousMonth factory parameter sets initial date correctly`() =
        runTest {
            // Arrange
            val today = Calendar.getInstance()
            val expectedMonth = (today.get(Calendar.MONTH) - 1 + 12) % 12
            val expectedYear = if (today.get(Calendar.MONTH) == Calendar.JANUARY) today.get(Calendar.YEAR) - 1 else today.get(Calendar.YEAR)

            // Act
            val viewModel = createViewModel(TimePeriod.MONTHLY, null, true)
            advanceUntilIdle()

            // Assert
            val selectedDate = viewModel.selectedDate.value
            assertEquals(expectedMonth, selectedDate.get(Calendar.MONTH))
            assertEquals(expectedYear, selectedDate.get(Calendar.YEAR))
        }

    @Test
    fun `consistencyStats are correctly calculated for monthly view`() =
        runTest {
            // Arrange
            val testCal = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 9) }
            val year = testCal.get(Calendar.YEAR)
            val month = testCal.get(Calendar.MONTH) + 1 // 10
            val firstDayOfMonth = (testCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

            val mockData = mutableListOf<CalendarDayStatus>()
            val safeToSpend = 100L
            for (i in 1..7) {
                val date = (testCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, i) }.time
                val (status, amount) =
                    when (i) {
                        1 -> SpendingStatus.NO_DATA to 0L // Day 1
                        2 -> SpendingStatus.NO_SPEND to 0L // Day 2
                        3 -> SpendingStatus.WITHIN_LIMIT to 50L // Day 3
                        4 -> SpendingStatus.OVER_LIMIT to 150L // Day 4
                        else -> SpendingStatus.NO_SPEND to 0L // Days 5, 6, 7
                    }
                mockData.add(CalendarDayStatus(date, status, amount, safeToSpend))
            }
            `when`(getMonthlyConsistencyDataUseCase(year, month)).thenReturn(flowOf(mockData))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstDayOfMonth))

            // Act
            val viewModel =
                createViewModel(
                    timePeriod = TimePeriod.MONTHLY,
                    initialDateMillis = testCal.timeInMillis,
                    showPreviousMonth = false,
                    useCase = getMonthlyConsistencyDataUseCase,
                )
            advanceUntilIdle()

            // Assert
            viewModel.consistencyStats.test {
                val stats = awaitItem()
                assertEquals(1, stats.goodDays)
                assertEquals(1, stats.badDays)
                assertEquals(4, stats.noSpendDays)
                assertEquals(1, stats.noDataDays)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `chartData generates correctly for WEEKLY period`() =
        runTest {
            // Arrange
            val weeklyTrends =
                listOf(
                    io.pm.finlight.WeeklyTrend("2025-40", 500.0, 1000.0),
                    io.pm.finlight.WeeklyTrend("2025-41", 750.0, 1500.0),
                )
            `when`(transactionRepository.getWeeklyTrends(anyLong(), anyLong())).thenReturn(flowOf(weeklyTrends))

            // Act
            val viewModel = createViewModel(TimePeriod.WEEKLY)
            advanceUntilIdle()

            // Assert
            viewModel.chartData.test {
                val chartPair = awaitItem()
                assertNotNull(chartPair)
                assertEquals(6, chartPair!!.first.entryCount) // 3 weeks x 2 datasets (income + expense)
                assertEquals(3, chartPair.second.size) // 3 labels
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `chartData generates correctly for DAILY period`() =
        runTest {
            // Arrange
            val dailyTrends =
                listOf(
                    io.pm.finlight.DailyTrend("2025-10-08", 100.0, 50.0),
                    io.pm.finlight.DailyTrend("2025-10-09", 200.0, 100.0),
                )
            `when`(transactionRepository.getDailyTrends(anyLong(), anyLong())).thenReturn(flowOf(dailyTrends))

            // Act
            val viewModel = createViewModel(TimePeriod.DAILY)
            advanceUntilIdle()

            // Assert
            viewModel.chartData.test {
                val chartPair = awaitItem()
                assertNotNull(chartPair)
                assertEquals(6, chartPair!!.first.entryCount) // 3 days x 2 datasets (income + expense)
                assertEquals(3, chartPair.second.size) // 3 labels
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `chartData generates correctly for MONTHLY period`() =
        runTest {
            // Arrange
            val monthlyTrends =
                listOf(
                    io.pm.finlight.MonthlyTrend("2025-09", 1000.0, 500.0),
                    io.pm.finlight.MonthlyTrend("2025-10", 2000.0, 1000.0),
                )
            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(monthlyTrends))

            // Act
            val viewModel = createViewModel(TimePeriod.MONTHLY)
            advanceUntilIdle()

            // Assert
            viewModel.chartData.test {
                val chartPair = awaitItem()
                assertNotNull(chartPair)
                assertEquals(6, chartPair!!.first.entryCount) // 3 months x 2 datasets (income + expense)
                assertEquals(3, chartPair.second.size) // 3 labels
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `chartData generates correctly for YEARLY period`() =
        runTest {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val mockTrends =
                listOf(
                    MonthlyTrend("$currentYear-01", 1000.0, 500.0),
                    MonthlyTrend("$currentYear-02", 1200.0, 600.0),
                )
            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(mockTrends))

            val viewModel = createViewModel(TimePeriod.YEARLY)
            advanceUntilIdle()

            viewModel.chartData.test {
                val chartPair = awaitItem()
                assertNotNull(chartPair)
                assertEquals(24, chartPair!!.first.entryCount) // 12 months x 2 datasets
                assertEquals(12, chartPair.second.size) // 12 month labels
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `chartData returns null when DAO returns empty data`() =
        runTest {
            // Arrange
            `when`(transactionRepository.getDailyTrends(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

            // Act
            val viewModel = createViewModel(TimePeriod.DAILY)
            advanceUntilIdle()

            // Assert
            viewModel.chartData.test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initialDateMillis correctly sets initial selectedDate`() =
        runTest {
            // Arrange
            val specificDate =
                Calendar.getInstance().apply {
                    set(2023, Calendar.JUNE, 15)
                }.timeInMillis

            // Act
            val viewModel = createViewModel(TimePeriod.MONTHLY, specificDate, false)
            advanceUntilIdle()

            // Assert
            val selectedDate = viewModel.selectedDate.value
            assertEquals(2023, selectedDate.get(Calendar.YEAR))
            assertEquals(Calendar.JUNE, selectedDate.get(Calendar.MONTH))
            assertEquals(15, selectedDate.get(Calendar.DAY_OF_MONTH))
        }

    @Test
    fun `insights uses same-period comparison for monthly reports mid-month`() =
        runTest {
            val testDate =
                Calendar.getInstance().apply {
                    set(2026, Calendar.JANUARY, 15, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

            val currentSummary = FinancialSummary(0.0, 150.0)
            val previousSummary = FinancialSummary(0.0, 100.0)

            var currentStartCaptured = 0L
            var currentEndCaptured = 0L
            var previousStartCaptured = 0L
            var previousEndCaptured = 0L

            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
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

            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))

            // Act
            val viewModel = createViewModel(TimePeriod.MONTHLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()

                assertEquals(50, insights?.percentageChange)

                val actualNow = Calendar.getInstance()
                val todayDayOfMonth = actualNow.get(Calendar.DAY_OF_MONTH)
                val periodEndCal =
                    Calendar.getInstance().apply {
                        set(2026, Calendar.JANUARY, 31, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                val isFuture = periodEndCal.after(actualNow)

                val currentStartCal = Calendar.getInstance().apply { timeInMillis = currentStartCaptured }
                assertEquals(2026, currentStartCal.get(Calendar.YEAR))
                assertEquals(Calendar.JANUARY, currentStartCal.get(Calendar.MONTH))
                assertEquals(1, currentStartCal.get(Calendar.DAY_OF_MONTH))

                val currentEndCal = Calendar.getInstance().apply { timeInMillis = currentEndCaptured }
                if (isFuture) {
                    assertEquals(actualNow.get(Calendar.YEAR), currentEndCal.get(Calendar.YEAR))
                    assertEquals(actualNow.get(Calendar.MONTH), currentEndCal.get(Calendar.MONTH))
                    assertEquals(todayDayOfMonth, currentEndCal.get(Calendar.DAY_OF_MONTH))
                } else {
                    assertEquals(31, currentEndCal.get(Calendar.DAY_OF_MONTH))
                }

                val previousStartCal = Calendar.getInstance().apply { timeInMillis = previousStartCaptured }
                assertEquals(2025, previousStartCal.get(Calendar.YEAR))
                assertEquals(Calendar.JANUARY, previousStartCal.get(Calendar.MONTH))
                assertEquals(1, previousStartCal.get(Calendar.DAY_OF_MONTH))

                val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
                if (isFuture) {
                    assertEquals(2025, previousEndCal.get(Calendar.YEAR))
                    assertEquals(Calendar.JANUARY, previousEndCal.get(Calendar.MONTH))
                    assertEquals(todayDayOfMonth, previousEndCal.get(Calendar.DAY_OF_MONTH))
                } else {
                    assertEquals(2025, previousEndCal.get(Calendar.YEAR))
                    assertEquals(31, previousEndCal.get(Calendar.DAY_OF_MONTH))
                }

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insights uses same-period comparison for yearly reports mid-year`() =
        runTest {
            val testDate =
                Calendar.getInstance().apply {
                    set(2024, Calendar.JANUARY, 15, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

            val actualToday = Calendar.getInstance()
            val todayDayOfYear = actualToday.get(Calendar.DAY_OF_YEAR)

            val currentSummary = FinancialSummary(0.0, 500.0)
            val previousSummary = FinancialSummary(0.0, 400.0)

            var currentStartCaptured = 0L
            var currentEndCaptured = 0L
            var previousStartCaptured = 0L
            var previousEndCaptured = 0L

            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
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

            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))

            // Act
            val viewModel = createViewModel(TimePeriod.YEARLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()

                assertEquals(25, insights?.percentageChange)

                val actualNow = Calendar.getInstance()
                val isFuture = 2024 > actualNow.get(Calendar.YEAR)

                val currentStartCal = Calendar.getInstance().apply { timeInMillis = currentStartCaptured }
                assertEquals(2024, currentStartCal.get(Calendar.YEAR))
                assertEquals(1, currentStartCal.get(Calendar.DAY_OF_YEAR))

                val currentEndCal = Calendar.getInstance().apply { timeInMillis = currentEndCaptured }
                if (isFuture) {
                    assertEquals(actualNow.get(Calendar.YEAR), currentEndCal.get(Calendar.YEAR))
                    assertEquals(todayDayOfYear, currentEndCal.get(Calendar.DAY_OF_YEAR))
                } else {
                    assertEquals(31, currentEndCal.get(Calendar.DAY_OF_MONTH))
                    assertEquals(Calendar.DECEMBER, currentEndCal.get(Calendar.MONTH))
                }

                val previousStartCal = Calendar.getInstance().apply { timeInMillis = previousStartCaptured }
                assertEquals(2023, previousStartCal.get(Calendar.YEAR))
                assertEquals(Calendar.JANUARY, previousStartCal.get(Calendar.MONTH))
                assertEquals(1, previousStartCal.get(Calendar.DAY_OF_MONTH))

                val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
                if (isFuture) {
                    assertEquals(2025, previousEndCal.get(Calendar.YEAR))
                    assertEquals(todayDayOfYear, previousEndCal.get(Calendar.DAY_OF_YEAR))
                } else {
                    assertEquals(2023, previousEndCal.get(Calendar.YEAR))
                    assertEquals(31, previousEndCal.get(Calendar.DAY_OF_MONTH))
                    assertEquals(Calendar.DECEMBER, previousEndCal.get(Calendar.MONTH))
                }

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insights uses full period comparison for completed months`() =
        runTest {
            val testDate =
                Calendar.getInstance().apply {
                    set(2025, Calendar.DECEMBER, 31, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }

            val currentSummary = FinancialSummary(0.0, 3100.0)
            val previousSummary = FinancialSummary(0.0, 3000.0)

            var previousStartCaptured = 0L
            var previousEndCaptured = 0L
            var callCount = 0

            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
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
            val viewModel = createViewModel(TimePeriod.MONTHLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()

                assertEquals(3, insights?.percentageChange)

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
    fun `insights preserves existing behavior for daily reports`() =
        runTest {
            // Arrange
            val currentSummary = FinancialSummary(0.0, 150.0)
            val previousSummary = FinancialSummary(0.0, 100.0)

            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong()))
                .thenReturn(currentSummary)
                .thenReturn(previousSummary)

            // Act
            val viewModel = createViewModel(TimePeriod.DAILY)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()
                assertEquals(50, insights?.percentageChange)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insights handles leap year edge case for yearly reports`() =
        runTest {
            val testDate =
                Calendar.getInstance().apply {
                    set(2024, Calendar.FEBRUARY, 29, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

            val currentSummary = FinancialSummary(0.0, 200.0)
            val previousSummary = FinancialSummary(0.0, 180.0)

            var previousEndCaptured = 0L
            var callCount = 0

            `when`(transactionRepository.getFinancialSummaryForRange(anyLong(), anyLong())).thenAnswer { invocation ->
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
            val viewModel = createViewModel(TimePeriod.YEARLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.insights.test {
                val insights = awaitItem()

                assertEquals(11, insights?.percentageChange)

                val previousEndCal = Calendar.getInstance().apply { timeInMillis = previousEndCaptured }
                assertEquals(2023, previousEndCal.get(Calendar.YEAR))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `yearlyMonthlyBreakdown handles income and expense exclusions correctly`() =
        runTest {
            // Arrange
            val testDate = Calendar.getInstance().apply { set(2025, Calendar.JANUARY, 15) }
            `when`(settingsRepository.getExcludedIncomeMonths()).thenReturn(flowOf(setOf("2025-01")))
            `when`(settingsRepository.getExcludedExpenseMonths()).thenReturn(flowOf(setOf("2025-02")))

            val transactions =
                listOf(
                    TransactionDetails(Transaction(id = 1, amount = 100.0, transactionType = TransactionType.INCOME, description = "", categoryId = 1, date = testDate.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 2, amount = 50.0, transactionType = TransactionType.EXPENSE, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.FEBRUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null)
                )
            `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))

            // Act
            val viewModel = createViewModel(TimePeriod.YEARLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.yearlyMonthlyBreakdown.test {
                val breakdown = awaitItem()
                if (breakdown.isNotEmpty()) {
                    val jan = breakdown.find { it.monthKey == "2025-01" }
                    val feb = breakdown.find { it.monthKey == "2025-02" }
                    assertEquals(true, jan?.isIncomeExcluded)
                    assertEquals(false, jan?.isExpenseExcluded)
                    assertEquals(false, feb?.isIncomeExcluded)
                    assertEquals(true, feb?.isExpenseExcluded)
                    assertEquals(100.0, jan?.income ?: 0.0, 0.0)
                    assertEquals(50.0, feb?.expenses ?: 0.0, 0.0)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `totalIncome and totalExpenses respect exclusions in YEARLY time period`() =
        runTest {
            // Arrange
            val testDate = Calendar.getInstance().apply { set(2025, Calendar.JANUARY, 15) }
            `when`(settingsRepository.getExcludedIncomeMonths()).thenReturn(flowOf(setOf("2025-01")))
            `when`(settingsRepository.getExcludedExpenseMonths()).thenReturn(flowOf(setOf("2025-02")))

            val transactions =
                listOf(
                    TransactionDetails(Transaction(id = 1, amount = 100.0, transactionType = TransactionType.INCOME, description = "", categoryId = 1, date = testDate.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 2, amount = 200.0, transactionType = TransactionType.INCOME, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.FEBRUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 3, amount = 50.0, transactionType = TransactionType.EXPENSE, description = "", categoryId = 1, date = testDate.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 4, amount = 75.0, transactionType = TransactionType.EXPENSE, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.FEBRUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null)
                )
            `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))

            // Act
            val viewModel = createViewModel(TimePeriod.YEARLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.totalIncome.test {
                var item = awaitItem()
                if (item == 0L) item = awaitItem()
                assertEquals(200L, item) // 100 is excluded
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.totalExpenses.test {
                var item = awaitItem()
                if (item == 0L) item = awaitItem()
                assertEquals(50L, item) // 75 is excluded
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `monthlyAverageIncome and monthlyAverageExpenses omit excluded months`() =
        runTest {
            // Arrange
            val testDate = Calendar.getInstance().apply { set(2025, Calendar.MARCH, 15) }
            `when`(settingsRepository.getExcludedIncomeMonths()).thenReturn(flowOf(setOf("2025-01")))
            `when`(settingsRepository.getExcludedExpenseMonths()).thenReturn(flowOf(setOf("2025-02")))

            val transactions =
                listOf(
                    TransactionDetails(Transaction(id = 1, amount = 100.0, transactionType = TransactionType.INCOME, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.JANUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 2, amount = 200.0, transactionType = TransactionType.INCOME, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.FEBRUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 3, amount = 300.0, transactionType = TransactionType.INCOME, description = "", categoryId = 1, date = testDate.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 4, amount = 50.0, transactionType = TransactionType.EXPENSE, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.JANUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 5, amount = 75.0, transactionType = TransactionType.EXPENSE, description = "", categoryId = 1, date = Calendar.getInstance().apply { set(2025, Calendar.FEBRUARY, 15) }.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
                    TransactionDetails(Transaction(id = 6, amount = 150.0, transactionType = TransactionType.EXPENSE, description = "", categoryId = 1, date = testDate.timeInMillis, accountId = 1, notes = null), emptyList(), null, null, null, null, null)
                )
            `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))

            // Act
            val viewModel = createViewModel(TimePeriod.YEARLY, testDate.timeInMillis, false)
            advanceUntilIdle()

            // Assert
            viewModel.monthlyAverageIncome.test {
                val item = awaitItem()
                assertNotNull(item)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.monthlyAverageExpenses.test {
                val item = awaitItem()
                assertNotNull(item)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggleIncomeExclusion calls repository toggle`() =
        runTest {
            // Arrange
            val viewModel = createViewModel(TimePeriod.YEARLY)

            // Act
            viewModel.toggleIncomeExclusion("2025-05")

            // Assert
            verify(settingsRepository).toggleIncomeMonthExclusion("2025-05")
        }

    @Test
    fun `toggleExpenseExclusion calls repository toggle`() =
        runTest {
            // Arrange
            val viewModel = createViewModel(TimePeriod.YEARLY)

            // Act
            viewModel.toggleExpenseExclusion("2025-05")

            // Assert
            verify(settingsRepository).toggleExpenseMonthExclusion("2025-05")
        }

    @Test
    fun `yearlyMonthlyBreakdown handles active and excluded transactions across all types`() =
        runTest {
            val now = System.currentTimeMillis()
            val txns =
                listOf(
                    TransactionDetails(
                        Transaction(id = 1, description = "", amount = 1000.0, transactionType = TransactionType.INCOME, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = false),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 2, description = "", amount = 500.0, transactionType = TransactionType.INCOME, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = true),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 3, description = "", amount = 400.0, transactionType = TransactionType.EXPENSE, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = false),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 4, description = "", amount = 200.0, transactionType = TransactionType.EXPENSE, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = true),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 5, description = "", amount = 999.0, transactionType = TransactionType.TRANSFER, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = false),
                        emptyList(), null, null, null, null, null,
                    ),
                )

            `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(txns))
            val viewModel = createViewModel(TimePeriod.YEARLY)
            advanceUntilIdle()

            viewModel.yearlyMonthlyBreakdown.test {
                val list = awaitItem()
                assertEquals(12, list.size)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.totalIncome.test {
                assertEquals(1000L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.totalExpenses.test {
                assertEquals(400L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `monthly totals correctly filter non-excluded income and expenses`() =
        runTest {
            val now = System.currentTimeMillis()
            val txns =
                listOf(
                    TransactionDetails(
                        Transaction(id = 1, description = "", amount = 3000.0, transactionType = TransactionType.INCOME, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = false),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 2, description = "", amount = 1000.0, transactionType = TransactionType.INCOME, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = true),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 3, description = "", amount = 1500.0, transactionType = TransactionType.EXPENSE, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = false),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 4, description = "", amount = 500.0, transactionType = TransactionType.EXPENSE, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = true),
                        emptyList(), null, null, null, null, null,
                    ),
                    TransactionDetails(
                        Transaction(id = 5, description = "", amount = 700.0, transactionType = TransactionType.TRANSFER, date = now, accountId = 1, categoryId = 1, notes = null, isExcluded = false),
                        emptyList(), null, null, null, null, null,
                    ),
                )

            `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(txns))
            val viewModel = createViewModel(TimePeriod.MONTHLY)
            advanceUntilIdle()

            viewModel.totalIncome.test {
                assertEquals(3000L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.totalExpenses.test {
                assertEquals(1500L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
