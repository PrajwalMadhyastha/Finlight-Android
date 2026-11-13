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
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ProcessDueRecurringTransactionsUseCase
import io.pm.finlight.domain.usecase.ProcessedRecurringResult
import io.pm.finlight.utils.IWorkScheduler
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var workScheduler: IWorkScheduler
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var useCase: ProcessDueRecurringTransactionsUseCase

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        workScheduler = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        useCase = mockk(relaxed = true)

        ServiceLocator.setWorkScheduler(workScheduler)
        ServiceLocator.setSettingsRepository(settingsRepository)
        ServiceLocator.setProcessDueRecurringTransactionsUseCase(useCase)
        ServiceLocator.setDispatcherProvider(TestDispatcherProvider(testDispatcher))

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        every { NotificationHelper.showAutoApprovedPaymentNotification(any(), any()) } returns Unit
        every { NotificationHelper.showRecurringTransactionDueNotification(any(), any(), any()) } returns Unit
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
    }

    private fun createDummyRule(
        id: Int = 1,
        description: String = "Test Subscription",
        amount: Double = 50.0,
        autoApprove: Boolean = false,
    ): RecurringTransaction =
        RecurringTransaction(
            id = id,
            description = description,
            amount = amount,
            transactionType = TransactionType.EXPENSE,
            recurrenceInterval = "Monthly",
            startDate = 1_000_000L,
            accountId = 1,
            categoryId = 1,
            autoApprove = autoApprove,
        )

    @Test
    fun `doWork when feature is disabled halts work chain, reschedules, and returns success`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(false)

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { useCase(any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringTransactionWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork when feature is enabled delegates to useCase and dispatches auto approved notification`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val rule = createDummyRule(id = 1, description = "Internet Bill", autoApprove = true)
            coEvery { useCase(any()) } returns listOf(ProcessedRecurringResult.AutoApproved(rule, 101L))

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { useCase(any()) }
            verify(exactly = 1) { NotificationHelper.showAutoApprovedPaymentNotification(any(), rule) }
            verify(exactly = 0) { NotificationHelper.showRecurringTransactionDueNotification(any(), any(), any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringTransactionWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork when feature is enabled delegates to useCase and dispatches pending draft notification`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val rule = createDummyRule(id = 2, description = "Rent", autoApprove = false)
            coEvery { useCase(any()) } returns listOf(ProcessedRecurringResult.PendingDraftCreated(rule, 202L))

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { useCase(any()) }
            verify(exactly = 1) { NotificationHelper.showRecurringTransactionDueNotification(any(), rule, 202) }
            verify(exactly = 0) { NotificationHelper.showAutoApprovedPaymentNotification(any(), any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringTransactionWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork when useCase returns skipped result does not dispatch notifications`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val rule = createDummyRule(id = 3, description = "Gym")
            coEvery { useCase(any()) } returns listOf(ProcessedRecurringResult.Skipped(rule, "Already processed"))

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showAutoApprovedPaymentNotification(any(), any()) }
            verify(exactly = 0) { NotificationHelper.showRecurringTransactionDueNotification(any(), any(), any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringTransactionWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork on attempt 0 failure returns retry and does not reschedule preserving exponential backoff`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws RuntimeException("Engine error")

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context, runAttemptCount = 0)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(exactly = 0) { workScheduler.scheduleRecurringTransactionWorker(any()) }
        }

    @Test
    fun `doWork on attempt 1 failure returns retry and does not reschedule`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws RuntimeException("Engine error")

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context, runAttemptCount = 1)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(exactly = 0) { workScheduler.scheduleRecurringTransactionWorker(any()) }
        }

    @Test
    fun `doWork on final attempt 2 returns failure and reschedules`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws RuntimeException("Engine error")

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context, runAttemptCount = 2)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            verify(exactly = 1) {
                workScheduler.scheduleRecurringTransactionWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork rethrows CancellationException cleanly without swallowing`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws CancellationException("Worker cancelled")

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringTransactionWorker>(context)

            assertThrows(CancellationException::class.java) {
                runBlocking {
                    worker.doWork()
                }
            }
            verify(exactly = 0) { workScheduler.scheduleRecurringTransactionWorker(any()) }
        }
}
