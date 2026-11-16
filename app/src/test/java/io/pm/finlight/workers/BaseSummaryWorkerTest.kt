package io.pm.finlight.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.IWorkScheduler
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
abstract class BaseSummaryWorkerTest : BaseViewModelTest() {
    protected lateinit var context: Context
    protected lateinit var transactionRepository: ITransactionRepository
    protected lateinit var workScheduler: IWorkScheduler
    protected lateinit var settingsRepository: ISettingsRepository

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

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
    }

    protected suspend inline fun <reified T : CoroutineWorker> assertRetryOnFirstAttempt(
        crossinline verifyNoSchedule: (IWorkScheduler) -> Unit,
    ) {
        coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
        val worker = SummaryWorkerTestFixtures.createWorker<T>(context)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verifyNoSchedule(workScheduler)
    }

    protected suspend inline fun <reified T : CoroutineWorker> assertFailureOnFinalAttemptAndReschedules(
        crossinline verifyReschedule: (IWorkScheduler) -> Unit,
    ) {
        coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
        val worker = SummaryWorkerTestFixtures.createWorker<T>(context, runAttemptCount = 2)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verifyReschedule(workScheduler)
    }

    protected suspend inline fun <reified T : CoroutineWorker> assertImmediateFailureWhenAttemptCapExceeded(
        crossinline verifyNoSchedule: (IWorkScheduler) -> Unit,
    ) {
        val worker = SummaryWorkerTestFixtures.createWorker<T>(context, runAttemptCount = 3)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { transactionRepository.getFinancialSummaryForRange(any(), any()) }
        verifyNoSchedule(workScheduler)
    }

    protected inline fun <reified T : CoroutineWorker> assertRethrowsCancellationException() {
        coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } throws CancellationException("Worker cancelled")
        val worker = SummaryWorkerTestFixtures.createWorker<T>(context)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                worker.doWork()
            }
        }
    }
}
