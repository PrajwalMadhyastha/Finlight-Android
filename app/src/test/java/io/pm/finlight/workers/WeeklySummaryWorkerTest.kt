package io.pm.finlight.workers

import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import io.pm.finlight.CategorySpending
import io.pm.finlight.FinancialSummary
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@ExperimentalCoroutinesApi
class WeeklySummaryWorkerTest : BaseSummaryWorkerTest() {
    @Before
    override fun setup() {
        super.setup()
        every { settingsRepository.getWeeklySummaryEnabled() } returns flowOf(true)
        every { NotificationHelper.showWeeklySummaryNotification(any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun `doWork calls repository with correct date ranges`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns null
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)

            val thisWeekStartSlot = slot<Long>()
            val thisWeekEndSlot = slot<Long>()
            val lastWeekStartSlot = slot<Long>()
            val lastWeekEndSlot = slot<Long>()
            val topCategoriesStartSlot = slot<Long>()
            val topCategoriesEndSlot = slot<Long>()

            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)

            coVerifyOrder {
                transactionRepository.getFinancialSummaryForRange(capture(thisWeekStartSlot), capture(thisWeekEndSlot))
                transactionRepository.getFinancialSummaryForRange(capture(lastWeekStartSlot), capture(lastWeekEndSlot))
                transactionRepository.getTopSpendingCategoriesForRange(capture(topCategoriesStartSlot), capture(topCategoriesEndSlot))
            }

            val thisWeekEndCal = Calendar.getInstance().apply { timeInMillis = thisWeekEndSlot.captured }
            assertEquals(23, thisWeekEndCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, thisWeekEndCal.get(Calendar.MINUTE))
            assertEquals(59, thisWeekEndCal.get(Calendar.SECOND))
            assertEquals(999, thisWeekEndCal.get(Calendar.MILLISECOND))

            val thisWeekStartCal = Calendar.getInstance().apply { timeInMillis = thisWeekStartSlot.captured }
            assertEquals(0, thisWeekStartCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, thisWeekStartCal.get(Calendar.MINUTE))
            assertEquals(0, thisWeekStartCal.get(Calendar.SECOND))
            assertEquals(0, thisWeekStartCal.get(Calendar.MILLISECOND))

            val lastWeekEndCal = Calendar.getInstance().apply { timeInMillis = lastWeekEndSlot.captured }
            assertEquals(23, lastWeekEndCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, lastWeekEndCal.get(Calendar.MINUTE))
            assertEquals(59, lastWeekEndCal.get(Calendar.SECOND))
            assertEquals(999, lastWeekEndCal.get(Calendar.MILLISECOND))

            val lastWeekStartCal = Calendar.getInstance().apply { timeInMillis = lastWeekStartSlot.captured }
            assertEquals(0, lastWeekStartCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, lastWeekStartCal.get(Calendar.MINUTE))
            assertEquals(0, lastWeekStartCal.get(Calendar.SECOND))
            assertEquals(0, lastWeekStartCal.get(Calendar.MILLISECOND))

            // Last week end is exactly 1 ms prior to this week start (contiguous ranges)
            assertEquals(1L, thisWeekStartSlot.captured - lastWeekEndSlot.captured)

            // Duration checks: this week spans 8 days (day 0 through -7), last week spans 7 days (day -8 through -14)
            val dayMillis = 24L * 60 * 60 * 1000
            assertEquals(8L * dayMillis - 1, thisWeekEndSlot.captured - thisWeekStartSlot.captured)
            assertEquals(7L * dayMillis - 1, lastWeekEndSlot.captured - lastWeekStartSlot.captured)

            assertEquals(thisWeekStartSlot.captured, topCategoriesStartSlot.captured)
            assertEquals(thisWeekEndSlot.captured, topCategoriesEndSlot.captured)
            coVerify(exactly = 1) { workScheduler.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork calculates percentage change correctly and shows notification`() =
        runTest {
            val thisWeekSummary = FinancialSummary(0.0, 300.0)
            val lastWeekSummary = FinancialSummary(0.0, 200.0)
            val topCategories = listOf(CategorySpending("Travel", 150.0, "blue", "icon"))

            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns thisWeekSummary andThen lastWeekSummary
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns topCategories

            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)

            val expensesCaptor = slot<Double>()
            val percentageCaptor = slot<Int?>()
            val categoriesCaptor = slot<List<CategorySpending>>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showWeeklySummaryNotification(
                    context = any(),
                    totalExpenses = capture(expensesCaptor),
                    percentageChange = captureNullable(percentageCaptor),
                    topCategories = capture(categoriesCaptor),
                )
            }

            assertEquals(300.0, expensesCaptor.captured, 0.0)
            assertEquals(50, percentageCaptor.captured)
            assertEquals(topCategories, categoriesCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork with zero last week expenses calculates null percentage change`() =
        runTest {
            val thisWeekSummary = FinancialSummary(0.0, 150.0)
            val lastWeekSummary = FinancialSummary(0.0, 0.0)

            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns thisWeekSummary andThen lastWeekSummary
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)
            val percentageCaptor = slot<Int?>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showWeeklySummaryNotification(
                    context = any(),
                    totalExpenses = 150.0,
                    percentageChange = captureNullable(percentageCaptor),
                    topCategories = any(),
                )
            }
            assertNull(percentageCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork skips summary generation and reschedules when feature is disabled`() =
        runTest {
            every { settingsRepository.getWeeklySummaryEnabled() } returns flowOf(false)

            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) {
                NotificationHelper.showWeeklySummaryNotification(any(), any(), any(), any())
            }
            coVerify(exactly = 1) { workScheduler.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork returns retry on failure on attempt 0 and does not reschedule`() =
        runTest {
            assertRetryOnFirstAttempt<WeeklySummaryWorker> {
                coVerify(exactly = 0) { it.scheduleWeeklySummary(any()) }
            }
        }

    @Test
    fun `doWork returns failure on final attempt 2 and reschedules`() =
        runTest {
            assertFailureOnFinalAttemptAndReschedules<WeeklySummaryWorker> {
                coVerify(exactly = 1) { it.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE) }
            }
        }

    @Test
    fun `doWork returns failure immediately when runAttemptCount is 3 or more`() =
        runTest {
            assertImmediateFailureWhenAttemptCapExceeded<WeeklySummaryWorker> {
                coVerify(exactly = 0) { it.scheduleWeeklySummary(any()) }
            }
        }

    @Test
    fun `doWork rethrows CancellationException`() {
        assertRethrowsCancellationException<WeeklySummaryWorker>()
    }
}
