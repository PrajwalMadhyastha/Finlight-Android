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
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CategorySpending
import io.pm.finlight.FinancialSummary
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.WeeklySummaryWorker
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class WeeklySummaryWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var transactionRepository: ITransactionRepository
    private lateinit var workScheduler: IWorkScheduler
    private lateinit var settingsRepository: ISettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        transactionRepository = SummaryWorkerTestFixtures.mockTransactionRepository()
        workScheduler = SummaryWorkerTestFixtures.mockWorkScheduler()
        settingsRepository = SummaryWorkerTestFixtures.mockSettingsRepository()

        ServiceLocator.setTransactionRepository(transactionRepository)
        ServiceLocator.setWorkScheduler(workScheduler)
        ServiceLocator.setSettingsRepository(settingsRepository)

        every { settingsRepository.getWeeklySummaryEnabled() } returns flowOf(true)

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        every { NotificationHelper.showWeeklySummaryNotification(any(), any(), any(), any()) } just runs
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `doWork calls repository with correct date ranges`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns null
            coEvery { transactionRepository.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)

            val now = Calendar.getInstance()
            val thisWeekEnd =
                (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            val thisWeekStart =
                (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            val lastWeekEnd =
                (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -8)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            val lastWeekStart =
                (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -14)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)

            coVerifyOrder {
                transactionRepository.getFinancialSummaryForRange(thisWeekStart, thisWeekEnd)
                transactionRepository.getFinancialSummaryForRange(lastWeekStart, lastWeekEnd)
                transactionRepository.getTopSpendingCategoriesForRange(thisWeekStart, thisWeekEnd)
            }
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
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify(exactly = 0) { workScheduler.scheduleWeeklySummary(any()) }
        }

    @Test
    fun `doWork returns failure on final attempt 2 and reschedules`() =
        runTest {
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context, runAttemptCount = 2)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 1) { workScheduler.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE) }
        }

    @Test
    fun `doWork returns failure immediately when runAttemptCount is 3 or more`() =
        runTest {
            val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context, runAttemptCount = 3)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 0) { transactionRepository.getFinancialSummaryForRange(any(), any()) }
            coVerify(exactly = 0) { workScheduler.scheduleWeeklySummary(any()) }
        }

    @Test
    fun `doWork rethrows CancellationException`() {
        coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws CancellationException("Worker cancelled")
        val worker = SummaryWorkerTestFixtures.createWorker<WeeklySummaryWorker>(context)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                worker.doWork()
            }
        }
    }
}
