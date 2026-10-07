package io.pm.finlight.domain.usecase

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IBudgetSettingsRepository
import io.pm.finlight.CalendarDayStatus
import io.pm.finlight.DailyTotal
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.SpendingStatus
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class GetMonthlyConsistencyDataUseCaseTest : BaseViewModelTest() {
    private val budgetSettingsRepository: IBudgetSettingsRepository = mockk()
    private val transactionRepository: ITransactionRepository = mockk()

    private fun getDayOfMonth(dayStatus: CalendarDayStatus): Int {
        val cal = Calendar.getInstance()
        cal.time = dayStatus.date
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun getDateKey(
        year: Int,
        month: Int,
        day: Int,
    ): String {
        return String.format(Locale.ROOT, "%d-%02d-%02d", year, month, day)
    }

    private fun getTimestamp(
        month: Int,
        day: Int,
        year: Int = 2025,
    ): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `returns NO_DATA for all past days if budget is null`() =
        runTest(testDispatcher) {
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )

            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            every { budgetSettingsRepository.getOverallBudgetForMonth(year, month) } returns flowOf(null)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    DailyTotal(getDateKey(year, month, 3), 0.0),
                )
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(dailyTotals)

            val results = useCase(year, month).first()

            val day1 = results.find { getDayOfMonth(it) == 1 }
            val day2 = results.find { getDayOfMonth(it) == 2 }
            val day3 = results.find { getDayOfMonth(it) == 3 }

            assertNotNull(day1)
            assertEquals(SpendingStatus.NO_DATA, day1?.status)
            assertEquals(0L, day1?.amountSpent)

            assertNotNull(day2)
            assertEquals(SpendingStatus.NO_DATA, day2?.status)
            assertEquals(100L, day2?.amountSpent)

            assertNotNull(day3)
            assertEquals(SpendingStatus.NO_DATA, day3?.status)
            assertEquals(0L, day3?.amountSpent)
        }

    @Test
    fun `handles 0f budget correctly`() =
        runTest(testDispatcher) {
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )

            val year = 2025
            val month = 9
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            every { budgetSettingsRepository.getOverallBudgetForMonth(year, month) } returns flowOf(0f)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    DailyTotal(getDateKey(year, month, 3), 0.0),
                )
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(dailyTotals)

            val results = useCase(year, month).first()

            val day1 = results.find { getDayOfMonth(it) == 1 }
            val day2 = results.find { getDayOfMonth(it) == 2 }
            val day3 = results.find { getDayOfMonth(it) == 3 }

            // Spent 0 on 0 budget -> WITHIN_LIMIT
            assertEquals(SpendingStatus.WITHIN_LIMIT, day1?.status)
            assertEquals(0L, day1?.amountSpent)
            assertEquals(0L, day1?.safeToSpend)

            // Spent 100 on 0 budget -> OVER_LIMIT
            assertEquals(SpendingStatus.OVER_LIMIT, day2?.status)
            assertEquals(100L, day2?.amountSpent)
            assertEquals(0L, day2?.safeToSpend)

            // Spent 0 on 0 budget -> WITHIN_LIMIT
            assertEquals(SpendingStatus.WITHIN_LIMIT, day3?.status)
            assertEquals(0L, day3?.amountSpent)
            assertEquals(0L, day3?.safeToSpend)
        }

    @Test
    fun `handles positive budget correctly`() =
        runTest(testDispatcher) {
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )

            val year = 2025
            val month = 9 // 30 days
            val totalBudget = 3000f // 100 per day
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            every { budgetSettingsRepository.getOverallBudgetForMonth(year, month) } returns flowOf(totalBudget)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 1), 0.0),
                    DailyTotal(getDateKey(year, month, 2), 50.0),
                    DailyTotal(getDateKey(year, month, 3), 200.0),
                )
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(dailyTotals)

            val results = useCase(year, month).first()

            val day1 = results.find { getDayOfMonth(it) == 1 }
            val day2 = results.find { getDayOfMonth(it) == 2 }
            val day3 = results.find { getDayOfMonth(it) == 3 }

            assertEquals(SpendingStatus.NO_SPEND, day1?.status)
            assertEquals(0L, day1?.amountSpent)
            assertEquals(100L, day1?.safeToSpend)

            assertEquals(SpendingStatus.WITHIN_LIMIT, day2?.status)
            assertEquals(50L, day2?.amountSpent)
            assertEquals(103L, day2?.safeToSpend)

            assertEquals(SpendingStatus.OVER_LIMIT, day3?.status)
            assertEquals(200L, day3?.amountSpent)
            assertEquals(105L, day3?.safeToSpend)
        }

    @Test
    fun `returns NO_DATA before first transaction`() =
        runTest(testDispatcher) {
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )

            val year = 2025
            val month = 9
            // First transaction on September 15
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 15)

            every { budgetSettingsRepository.getOverallBudgetForMonth(year, month) } returns flowOf(1000f)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(emptyList())

            val results = useCase(year, month).first()

            val day1 = results.find { getDayOfMonth(it) == 1 }
            val day14 = results.find { getDayOfMonth(it) == 14 }
            val day15 = results.find { getDayOfMonth(it) == 15 }

            assertEquals(SpendingStatus.NO_DATA, day1?.status)
            assertEquals(SpendingStatus.NO_DATA, day14?.status)

            // Day 15 is on/after firstTxDate
            assertEquals(SpendingStatus.NO_SPEND, day15?.status)
        }

    @Test
    fun `returns NO_DATA for future days in future month`() =
        runTest(testDispatcher) {
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )

            val futureYear = Calendar.getInstance().get(Calendar.YEAR) + 1
            val month = 1
            val firstTxDate = getTimestamp(Calendar.JANUARY, 1, year = 2020)

            every { budgetSettingsRepository.getOverallBudgetForMonth(futureYear, month) } returns flowOf(1000f)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(emptyList())

            val results = useCase(futureYear, month).first()

            assertTrue(results.isNotEmpty())
            for (day in results) {
                assertEquals(SpendingStatus.NO_DATA, day.status)
                assertEquals(0L, day.safeToSpend)
                assertEquals(0L, day.amountSpent)
            }
        }

    @Test
    fun `handles null firstTransactionDate gracefully`() =
        runTest(testDispatcher) {
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )

            val year = 2025
            val month = 9
            val totalBudget = 3000f

            every { budgetSettingsRepository.getOverallBudgetForMonth(year, month) } returns flowOf(totalBudget)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(null)
            val dailyTotals = listOf(DailyTotal(getDateKey(year, month, 1), 50.0))
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(dailyTotals)

            val results = useCase(year, month).first()

            val day1 = results.find { getDayOfMonth(it) == 1 }
            val day2 = results.find { getDayOfMonth(it) == 2 }

            assertNotNull(day1)
            assertEquals(SpendingStatus.WITHIN_LIMIT, day1?.status)
            assertEquals(50L, day1?.amountSpent)

            assertNotNull(day2)
            assertEquals(SpendingStatus.NO_SPEND, day2?.status)
            assertEquals(0L, day2?.amountSpent)
        }

    @Test
    fun `useCase works with DispatcherProvider constructor`() =
        runTest(testDispatcher) {
            val testDispatcherProvider = TestDispatcherProvider(testDispatcher)
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                    dispatcherProvider = testDispatcherProvider,
                )

            val year = 2025
            val month = 9
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            every { budgetSettingsRepository.getOverallBudgetForMonth(year, month) } returns flowOf(null)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(emptyList())

            val results = useCase(year, month).first()
            assertNotNull(results)
            assertEquals(testDispatcherProvider, useCase.dispatcherProvider)
        }

    @Test
    fun `useCase primary constructor works properly`() =
        runTest(testDispatcher) {
            val testDispatcherProvider = TestDispatcherProvider(testDispatcher)
            val useCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetProvider = { _, _ -> flowOf(null) },
                    transactionRepository = transactionRepository,
                    dispatcherProvider = testDispatcherProvider,
                )

            val year = 2025
            val month = 9
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(emptyList())

            val results = useCase(year, month).first()
            assertNotNull(results)
            assertEquals(testDispatcherProvider, useCase.dispatcherProvider)
        }

    @Test
    fun `useCase constructors with ISettingsRepository work properly`() =
        runTest(testDispatcher) {
            val mockSettingsRepo = mockk<ISettingsRepository>()
            val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

            val useCase1 =
                GetMonthlyConsistencyDataUseCase(
                    settingsRepository = mockSettingsRepo,
                    transactionRepository = transactionRepository,
                    dispatcherProvider = testDispatcherProvider,
                )
            assertEquals(testDispatcherProvider, useCase1.dispatcherProvider)

            val useCase2 =
                GetMonthlyConsistencyDataUseCase(
                    settingsRepository = mockSettingsRepo,
                    transactionRepository = transactionRepository,
                    dispatcher = testDispatcher,
                )
            assertNotNull(useCase2.dispatcherProvider)

            val useCase3 =
                GetMonthlyConsistencyDataUseCase(
                    settingsRepository = mockSettingsRepo,
                    transactionRepository = transactionRepository,
                )
            assertTrue(useCase3.dispatcherProvider is DefaultDispatcherProvider)

            val defaultUseCase =
                GetMonthlyConsistencyDataUseCase(
                    budgetSettingsRepository = budgetSettingsRepository,
                    transactionRepository = transactionRepository,
                )
            assertTrue(defaultUseCase.dispatcherProvider is DefaultDispatcherProvider)

            // Verify execution and flow collection via ISettingsRepository constructor
            val year = 2025
            val month = 9
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            every { mockSettingsRepo.getOverallBudgetForMonth(year, month) } returns flowOf(3000f)
            every { transactionRepository.getFirstTransactionDate() } returns flowOf(firstTxDate)
            val dailyTotals = listOf(DailyTotal(getDateKey(year, month, 1), 50.0))
            every { transactionRepository.getDailySpendingForDateRange(any(), any()) } returns flowOf(dailyTotals)

            val results = useCase1(year, month).first()
            assertNotNull(results)
            val day1 = results.find { getDayOfMonth(it) == 1 }
            assertEquals(SpendingStatus.WITHIN_LIMIT, day1?.status)
            assertEquals(50L, day1?.amountSpent)
            verify(exactly = 1) { mockSettingsRepo.getOverallBudgetForMonth(year, month) }

            val results2 = useCase2(year, month).first()
            assertNotNull(results2)
            val day1UseCase2 = results2.find { getDayOfMonth(it) == 1 }
            assertEquals(SpendingStatus.WITHIN_LIMIT, day1UseCase2?.status)
        }
}
