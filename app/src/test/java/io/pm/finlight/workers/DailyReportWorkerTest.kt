package io.pm.finlight.workers

import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.FinancialSummary
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

@ExperimentalCoroutinesApi
class DailyReportWorkerTest : BaseSummaryWorkerTest() {
    private lateinit var travelSettingsRepository: ITravelSettingsRepository

    @Before
    override fun setup() {
        super.setup()
        travelSettingsRepository = SummaryWorkerTestFixtures.mockTravelSettingsRepository()
        ServiceLocator.setTravelSettingsRepository(travelSettingsRepository)

        every { settingsRepository.getDailyReportEnabled() } returns flowOf(true)
        coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns null

        every { NotificationHelper.showDailyReportNotification(any(), any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun `doWork with higher spending generates 'higher than usual' title and reschedules via workScheduler`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 200.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 100.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val titleCaptor = slot<String>()
            val reportStartSlot = slot<Long>()
            val reportEndSlot = slot<Long>()
            val avgStartSlot = slot<Long>()
            val avgEndSlot = slot<Long>()

            val testNow = Calendar.getInstance()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)

            coVerify {
                transactionRepository.getFinancialSummaryForRange(capture(reportStartSlot), capture(reportEndSlot))
                transactionRepository.getAverageDailySpendingForRange(capture(avgStartSlot), capture(avgEndSlot))
                transactionRepository.getTopSpendingCategoriesForRange(capture(reportStartSlot), capture(reportEndSlot))
            }

            val expectedReportEnd = testNow.timeInMillis
            val expectedReportStart = (testNow.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -24) }.timeInMillis
            val timeDelta = 200L

            assertTrue(
                abs(expectedReportEnd - reportEndSlot.captured) < timeDelta,
            )
            assertTrue(
                abs(expectedReportStart - reportStartSlot.captured) < timeDelta,
            )

            val expectedAvgStart =
                (testNow.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -8)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            val expectedAvgEnd =
                (testNow.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -2)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

            assertTrue(
                abs(expectedAvgStart - avgStartSlot.captured) < timeDelta,
            )
            assertTrue(
                abs(expectedAvgEnd - avgEndSlot.captured) < timeDelta,
            )

            verify {
                NotificationHelper.showDailyReportNotification(
                    context = any(),
                    title = capture(titleCaptor),
                    totalExpenses = 200.0,
                    topCategories = any(),
                    dateMillis = any(),
                )
            }
            assertEquals("Heads up: Spending was higher than usual yesterday.", titleCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork with lower spending generates 'well below average' title`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 10.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 100.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val titleCaptor = slot<String>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showDailyReportNotification(
                    context = any(),
                    title = capture(titleCaptor),
                    totalExpenses = 10.0,
                    topCategories = any(),
                    dateMillis = any(),
                )
            }
            assertEquals("Great job! Spending was well below average yesterday.", titleCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork with on track spending generates 'on track' title`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 100.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 100.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val titleCaptor = slot<String>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showDailyReportNotification(
                    context = any(),
                    title = capture(titleCaptor),
                    totalExpenses = 100.0,
                    topCategories = any(),
                    dateMillis = any(),
                )
            }
            assertEquals("Your spending is on track with your weekly average.", titleCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork with no spending generates 'no spending' title`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 0.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 100.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val titleCaptor = slot<String>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showDailyReportNotification(
                    context = any(),
                    title = capture(titleCaptor),
                    totalExpenses = 0.0,
                    topCategories = any(),
                    dateMillis = any(),
                )
            }
            assertEquals("No spending recorded yesterday. Keep it up!", titleCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork with default spending ratio generates default summary title`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 50.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 0.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val titleCaptor = slot<String>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showDailyReportNotification(
                    context = any(),
                    title = capture(titleCaptor),
                    totalExpenses = 50.0,
                    topCategories = any(),
                    dateMillis = any(),
                )
            }
            assertEquals("Here's your daily summary.", titleCaptor.captured)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork clears expired travel mode settings before report`() =
        runTest {
            val expiredSettings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Past Trip",
                    tripType = TripType.DOMESTIC,
                    startDate = 0L,
                    endDate = 1000L,
                    currencyCode = null,
                    conversionRate = null,
                )
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns expiredSettings
            coEvery { travelSettingsRepository.saveTravelModeSettings(null) } returns Unit

            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 0.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 0.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { travelSettingsRepository.saveTravelModeSettings(null) }
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork preserves active unexpired travel mode settings`() =
        runTest {
            val futureSettings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Future Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = System.currentTimeMillis() - 1000L,
                    endDate = System.currentTimeMillis() + 1000000L,
                    currencyCode = "EUR",
                    conversionRate = 1.1f,
                )
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns futureSettings

            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 0.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 0.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { travelSettingsRepository.saveTravelModeSettings(any()) }
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork handles travel mode check exception gracefully without blocking report`() =
        runTest {
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } throws RuntimeException("Travel mode error")

            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 0.0)
            coEvery { transactionRepository.getAverageDailySpendingForRange(any(), any()) } returns 0.0
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork skips notification and reschedules when feature is disabled`() =
        runTest {
            every { settingsRepository.getDailyReportEnabled() } returns flowOf(false)

            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) {
                NotificationHelper.showDailyReportNotification(any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork returns retry on failure on attempt 0 and does not reschedule`() =
        runTest {
            assertRetryOnFirstAttempt<DailyReportWorker> {
                coVerify(exactly = 0) { it.scheduleDailyReport(any()) }
            }
        }

    @Test
    fun `doWork returns failure on final attempt 2 and reschedules`() =
        runTest {
            assertFailureOnFinalAttemptAndReschedules<DailyReportWorker> {
                coVerify(exactly = 1) { it.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
            }
        }

    @Test
    fun `doWork returns failure immediately when runAttemptCount is 3 or more`() =
        runTest {
            assertImmediateFailureWhenAttemptCapExceeded<DailyReportWorker> {
                coVerify(exactly = 0) { it.scheduleDailyReport(any()) }
            }
        }

    @Test
    fun `doWork rethrows CancellationException`() {
        assertRethrowsCancellationException<DailyReportWorker>()
    }
}
