package io.pm.finlight.domain.usecase

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.DailyTotal
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SpendingStatus
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class GetMonthlyConsistencyDataUseCaseTest : BaseViewModelTest() {
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var useCase: GetMonthlyConsistencyDataUseCase

    private fun getTimestamp(
        month: Int,
        day: Int,
    ): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getDateKey(
        year: Int,
        month: Int,
        day: Int,
    ): String {
        return String.format(Locale.ROOT, "%d-%02d-%02d", year, month, day)
    }

    @Before
    override fun setup() {
        super.setup()
        useCase = GetMonthlyConsistencyDataUseCase(settingsRepository, transactionRepository)
    }

    @Test
    fun `returns NO_DATA for all past days if budget is null`() =
        runTest {
            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(null))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    DailyTotal(getDateKey(year, month, 3), 0.0),
                )
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

            useCase(year, month).test {
                val results = awaitItem()

                val day1 = results.find { it.date.date == 1 }
                val day2 = results.find { it.date.date == 2 }
                val day3 = results.find { it.date.date == 3 }

                assertEquals(SpendingStatus.NO_DATA, day1?.status)
                assertEquals(0L, day1?.amountSpent)

                assertEquals(SpendingStatus.NO_DATA, day2?.status)
                assertEquals(100L, day2?.amountSpent)

                assertEquals(SpendingStatus.NO_DATA, day3?.status)
                assertEquals(0L, day3?.amountSpent)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `handles 0f budget correctly`() =
        runTest {
            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(0f))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    DailyTotal(getDateKey(year, month, 3), 0.0),
                )
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

            useCase(year, month).test {
                val results = awaitItem()

                val day1 = results.find { it.date.date == 1 }
                val day2 = results.find { it.date.date == 2 }
                val day3 = results.find { it.date.date == 3 }

                assertEquals(SpendingStatus.WITHIN_LIMIT, day1?.status)
                assertEquals(SpendingStatus.OVER_LIMIT, day2?.status)
                assertEquals(SpendingStatus.WITHIN_LIMIT, day3?.status)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `handles positive budget correctly`() =
        runTest {
            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)
            val budget = 3000f

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 2), 0.0),
                    DailyTotal(getDateKey(year, month, 3), 50.0),
                    DailyTotal(getDateKey(year, month, 4), 150.0),
                )
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

            useCase(year, month).test {
                val results = awaitItem()

                val day1 = results.find { it.date.date == 1 }
                val day2 = results.find { it.date.date == 2 }
                val day3 = results.find { it.date.date == 3 }
                val day4 = results.find { it.date.date == 4 }

                assertEquals(SpendingStatus.NO_SPEND, day1?.status)
                assertEquals(SpendingStatus.NO_SPEND, day2?.status)
                assertEquals(SpendingStatus.WITHIN_LIMIT, day3?.status)
                assertEquals(SpendingStatus.OVER_LIMIT, day4?.status)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns NO_DATA before first transaction and for future days`() =
        runTest {
            val year = 2025
            val month = 9 // September
            val budget = 3000f

            val firstTxCal =
                Calendar.getInstance().apply {
                    set(2025, Calendar.SEPTEMBER, 10, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            val firstTxDate = firstTxCal.timeInMillis

            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 11), 50.0),
                )

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

            useCase(year, month).test {
                val results = awaitItem()

                val day9 = results.find { it.date.date == 9 }
                val day10 = results.find { it.date.date == 10 }
                val day11 = results.find { it.date.date == 11 }

                assertEquals(SpendingStatus.NO_DATA, day9?.status)
                assertEquals(SpendingStatus.NO_SPEND, day10?.status)
                assertEquals(SpendingStatus.WITHIN_LIMIT, day11?.status)

                cancelAndIgnoreRemainingEvents()
            }

            val prodToday = Calendar.getInstance()
            val futureYear = prodToday.get(Calendar.YEAR)
            val futureMonth = prodToday.get(Calendar.MONTH) + 1
            val futureBudget = 3000f
            val veryFirstTxDate = getTimestamp(Calendar.JANUARY, 1)

            `when`(settingsRepository.getOverallBudgetForMonth(futureYear, futureMonth)).thenReturn(flowOf(futureBudget))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(veryFirstTxDate))
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

            useCase(futureYear, futureMonth).test {
                val results = awaitItem()

                val todayDay = prodToday.get(Calendar.DAY_OF_MONTH)
                val todayData = results.find { it.date.date == todayDay }
                assertNotNull(todayData)
                assertEquals(SpendingStatus.NO_SPEND, todayData.status)

                val futureDay = prodToday.get(Calendar.DAY_OF_MONTH) + 2
                val daysInCurrentMonth = prodToday.getActualMaximum(Calendar.DAY_OF_MONTH)

                if (futureDay <= daysInCurrentMonth) {
                    val futureDayData = results.find { it.date.date == futureDay }
                    assertNotNull(futureDayData)
                    assertEquals(SpendingStatus.NO_DATA, futureDayData.status)
                }

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `returns status correctly when firstTransactionDate is null`() =
        runTest {
            val year = 2025
            val month = 9 // September
            val budget = 3000f

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

            useCase(year, month).test {
                val results = awaitItem()
                assertEquals(30, results.size)
                // All past days are treated as NO_SPEND since there is no lower bound date restriction
                val day1 = results.find { it.date.date == 1 }
                assertNotNull(day1)
                assertEquals(SpendingStatus.NO_SPEND, day1.status)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `evaluates spending status on exact boundary and decimal rounding values`() =
        runTest {
            val year = 2025
            val month = 9
            val budget = 3000f // 30 days -> 100/day
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            // Day 1 spent 99.4 (rounds to 99L <= 100 -> WITHIN_LIMIT)
            // Day 2 spent 100.0 (exact match -> WITHIN_LIMIT)
            // Day 3 spent 100.6 (rounds to 101L > 100 -> OVER_LIMIT)
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 1), 99.4),
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    DailyTotal(getDateKey(year, month, 3), 100.6),
                )

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

            useCase(year, month).test {
                val results = awaitItem()

                val day1 = results.find { it.date.date == 1 }
                val day2 = results.find { it.date.date == 2 }
                val day3 = results.find { it.date.date == 3 }

                assertEquals(SpendingStatus.WITHIN_LIMIT, day1?.status)
                assertEquals(99L, day1?.amountSpent)

                assertEquals(SpendingStatus.WITHIN_LIMIT, day2?.status)
                assertEquals(100L, day2?.amountSpent)

                assertEquals(SpendingStatus.OVER_LIMIT, day3?.status)
                assertEquals(101L, day3?.amountSpent)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
