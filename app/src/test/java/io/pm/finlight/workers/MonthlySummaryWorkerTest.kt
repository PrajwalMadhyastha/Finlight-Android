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
import io.pm.finlight.MonthlySummaryWorker
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
class MonthlySummaryWorkerTest : BaseSummaryWorkerTest() {
    @Before
    override fun setup() {
        super.setup()
        every { settingsRepository.getMonthlySummaryEnabled() } returns flowOf(true)
        every { NotificationHelper.showMonthlySummaryNotification(any(), any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun `doWork calculates correct date ranges and calls repository`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns null
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<MonthlySummaryWorker>(context)

            val lastMonthStartSlot = slot<Long>()
            val lastMonthEndSlot = slot<Long>()
            val prevMonthStartSlot = slot<Long>()
            val prevMonthEndSlot = slot<Long>()
            val topCategoriesStartSlot = slot<Long>()
            val topCategoriesEndSlot = slot<Long>()

            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)

            coVerifyOrder {
                transactionRepository.getFinancialSummaryForRange(capture(lastMonthStartSlot), capture(lastMonthEndSlot))
                transactionRepository.getFinancialSummaryForRange(capture(prevMonthStartSlot), capture(prevMonthEndSlot))
                transactionRepository.getTopSpendingCategoriesForRange(capture(topCategoriesStartSlot), capture(topCategoriesEndSlot))
            }

            val lastMonthStartCal = Calendar.getInstance().apply { timeInMillis = lastMonthStartSlot.captured }
            val lastMonthEndCal = Calendar.getInstance().apply { timeInMillis = lastMonthEndSlot.captured }
            val prevMonthStartCal = Calendar.getInstance().apply { timeInMillis = prevMonthStartSlot.captured }
            val prevMonthEndCal = Calendar.getInstance().apply { timeInMillis = prevMonthEndSlot.captured }

            // Last month start: 1st day of month at 00:00:00.000
            assertEquals(1, lastMonthStartCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, lastMonthStartCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, lastMonthStartCal.get(Calendar.MINUTE))
            assertEquals(0, lastMonthStartCal.get(Calendar.SECOND))
            assertEquals(0, lastMonthStartCal.get(Calendar.MILLISECOND))

            // Last month end: last day of month at 23:59:59.999
            assertEquals(lastMonthEndCal.getActualMaximum(Calendar.DAY_OF_MONTH), lastMonthEndCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(23, lastMonthEndCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, lastMonthEndCal.get(Calendar.MINUTE))
            assertEquals(59, lastMonthEndCal.get(Calendar.SECOND))
            assertEquals(999, lastMonthEndCal.get(Calendar.MILLISECOND))

            // Prev month start: 1st day of month at 00:00:00.000
            assertEquals(1, prevMonthStartCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, prevMonthStartCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, prevMonthStartCal.get(Calendar.MINUTE))
            assertEquals(0, prevMonthStartCal.get(Calendar.SECOND))
            assertEquals(0, prevMonthStartCal.get(Calendar.MILLISECOND))

            // Prev month end: last day of month at 23:59:59.999
            assertEquals(prevMonthEndCal.getActualMaximum(Calendar.DAY_OF_MONTH), prevMonthEndCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(23, prevMonthEndCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, prevMonthEndCal.get(Calendar.MINUTE))
            assertEquals(59, prevMonthEndCal.get(Calendar.SECOND))
            assertEquals(999, prevMonthEndCal.get(Calendar.MILLISECOND))

            // Last month is continuous with prev month
            assertEquals(1L, lastMonthStartSlot.captured - prevMonthEndSlot.captured)

            // Category range matches last month
            assertEquals(lastMonthStartSlot.captured, topCategoriesStartSlot.captured)
            assertEquals(lastMonthEndSlot.captured, topCategoriesEndSlot.captured)
            coVerify(exactly = 1) { workScheduler.scheduleMonthlySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork calculates percentage change and shows notification`() =
        runTest {
            val lastMonthSummary = FinancialSummary(0.0, 150.0)
            val prevMonthSummary = FinancialSummary(0.0, 100.0)
            val topCategories = listOf(CategorySpending("Food", 50.0, "red", "icon"))

            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns lastMonthSummary andThen prevMonthSummary
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns topCategories

            val worker = SummaryWorkerTestFixtures.createWorker<MonthlySummaryWorker>(context)

            val percentageCaptor = slot<Int?>()
            val expensesCaptor = slot<Double>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showMonthlySummaryNotification(
                    context = any(),
                    calendar = any(),
                    totalExpenses = capture(expensesCaptor),
                    percentageChange = captureNullable(percentageCaptor),
                    topCategories = any(),
                )
            }

            assertEquals(50, percentageCaptor.captured)
            assertEquals(150.0, expensesCaptor.captured, 0.0)
            coVerify(exactly = 1) { workScheduler.scheduleMonthlySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork with no previous data calculates null percentage change`() =
        runTest {
            val lastMonthSummary = FinancialSummary(0.0, 150.0)
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns lastMonthSummary andThen null
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<MonthlySummaryWorker>(context)
            val percentageCaptor = slot<Int?>()
            val expensesCaptor = slot<Double>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showMonthlySummaryNotification(
                    context = any(),
                    calendar = any(),
                    totalExpenses = capture(expensesCaptor),
                    percentageChange = captureNullable(percentageCaptor),
                    topCategories = any(),
                )
            }
            assertNull(percentageCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleMonthlySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork skips summary generation and reschedules when feature is disabled`() =
        runTest {
            every { settingsRepository.getMonthlySummaryEnabled() } returns flowOf(false)

            val worker = SummaryWorkerTestFixtures.createWorker<MonthlySummaryWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) {
                NotificationHelper.showMonthlySummaryNotification(any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) { workScheduler.scheduleMonthlySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork returns retry on failure on attempt 0 and does not reschedule`() =
        runTest {
            assertRetryOnFirstAttempt<MonthlySummaryWorker> {
                coVerify(exactly = 0) { it.scheduleMonthlySummary(any()) }
            }
        }

    @Test
    fun `doWork returns failure on final attempt 2 and reschedules`() =
        runTest {
            assertFailureOnFinalAttemptAndReschedules<MonthlySummaryWorker> {
                coVerify(exactly = 1) { it.scheduleMonthlySummary(ExistingWorkPolicy.REPLACE) }
            }
        }

    @Test
    fun `doWork returns failure immediately when runAttemptCount is 3 or more`() =
        runTest {
            assertImmediateFailureWhenAttemptCapExceeded<MonthlySummaryWorker> {
                coVerify(exactly = 0) { it.scheduleMonthlySummary(any()) }
            }
        }

    @Test
    fun `doWork rethrows CancellationException`() {
        assertRethrowsCancellationException<MonthlySummaryWorker>()
    }
}
