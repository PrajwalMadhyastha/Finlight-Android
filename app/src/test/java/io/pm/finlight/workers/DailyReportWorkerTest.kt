package io.pm.finlight.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.FinancialSummary
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.IWorkScheduler
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.math.abs

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DailyReportWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var transactionRepository: ITransactionRepository
    private lateinit var workScheduler: IWorkScheduler
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var travelSettingsRepository: ITravelSettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        transactionRepository = SummaryWorkerTestFixtures.mockTransactionRepository()
        workScheduler = SummaryWorkerTestFixtures.mockWorkScheduler()
        settingsRepository = SummaryWorkerTestFixtures.mockSettingsRepository()
        travelSettingsRepository = SummaryWorkerTestFixtures.mockTravelSettingsRepository()

        ServiceLocator.setTransactionRepository(transactionRepository)
        ServiceLocator.setWorkScheduler(workScheduler)
        ServiceLocator.setSettingsRepository(settingsRepository)
        ServiceLocator.setTravelSettingsRepository(travelSettingsRepository)

        every { settingsRepository.getDailyReportEnabled() } returns flowOf(true)
        coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns null

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        every { NotificationHelper.showDailyReportNotification(any(), any(), any(), any(), any()) } just runs
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
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
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify(exactly = 0) { workScheduler.scheduleDailyReport(any()) }
        }

    @Test
    fun `doWork returns failure on final attempt 2 and reschedules`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context, runAttemptCount = 2)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 1) { workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork returns failure immediately when runAttemptCount is 3 or more`() =
        runTest {
            val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context, runAttemptCount = 3)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 0) { transactionRepository.getFinancialSummaryForRange(any(), any()) }
            coVerify(exactly = 0) { workScheduler.scheduleDailyReport(any()) }
        }

    @Test
    fun `doWork rethrows CancellationException`() {
        coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws CancellationException("Worker cancelled")
        val worker = SummaryWorkerTestFixtures.createWorker<DailyReportWorker>(context)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                worker.doWork()
            }
        }
    }
}
