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
import io.pm.finlight.IFeatureSettingsRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.RecurringPattern
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.AnalyzeRecurringPatternsUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringPatternWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var workScheduler: IWorkScheduler
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var recurringTransactionRepository: IRecurringTransactionRepository
    private lateinit var featureSettingsRepository: IFeatureSettingsRepository
    private lateinit var useCase: AnalyzeRecurringPatternsUseCase

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        workScheduler = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        recurringTransactionRepository = mockk(relaxed = true)
        featureSettingsRepository = mockk(relaxed = true)
        useCase = mockk(relaxed = true)

        ServiceLocator.setWorkScheduler(workScheduler)
        ServiceLocator.setSettingsRepository(settingsRepository)
        ServiceLocator.setRecurringTransactionRepository(recurringTransactionRepository)
        ServiceLocator.setFeatureSettingsRepository(featureSettingsRepository)
        ServiceLocator.setAnalyzeRecurringPatternsUseCase(useCase)
        ServiceLocator.setDispatcherProvider(TestDispatcherProvider(testDispatcher))

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        every { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) } returns Unit
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
    }

    private fun createPatternFixture(
        signature: String = "sig_spotify",
        description: String = "Spotify",
        amount: Double = 119.0,
        accountId: Int = 1,
        isDismissed: Boolean = false,
    ): RecurringPattern =
        RecurringPattern(
            smsSignature = signature,
            description = description,
            amount = amount,
            transactionType = TransactionType.EXPENSE,
            accountId = accountId,
            categoryId = 2,
            occurrences = 4,
            firstSeen = 1_000_000L,
            lastSeen = 2_000_000L,
            isDismissed = isDismissed,
        )

    private fun createRuleFixture(
        id: Int = 1,
        description: String = "Spotify",
        accountId: Int = 1,
    ): RecurringTransaction =
        RecurringTransaction(
            id = id,
            description = description,
            amount = 119.0,
            transactionType = TransactionType.EXPENSE,
            recurrenceInterval = "Monthly",
            startDate = 1_000_000L,
            accountId = accountId,
            categoryId = 2,
            autoApprove = false,
        )

    @Test
    fun `worker construction does not eagerly initialize database or DAOs`() {
        val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
        assertNotNull(worker)
    }

    @Test
    fun `doWork when feature is disabled halts work chain, reschedules, and returns success`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(false)

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { useCase(any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork when feature is enabled notifies unnotified patterns and updates cache`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val pattern = createPatternFixture(signature = "sig_spotify", description = "Spotify", accountId = 1)
            coEvery { useCase(any()) } returns listOf(pattern)
            coEvery { recurringTransactionRepository.getAllRulesList() } returns emptyList()
            coEvery { featureSettingsRepository.getNotifiedPatternSignatures() } returns emptySet()

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 1) { NotificationHelper.showRecurringPatternDetectedNotification(any(), pattern) }
            coVerify(exactly = 1) { featureSettingsRepository.markPatternNotified("sig_spotify") }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork suppresses notifications for dismissed patterns`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val dismissedPattern = createPatternFixture(signature = "sig_dismissed", isDismissed = true)
            coEvery { useCase(any()) } returns listOf(dismissedPattern)
            coEvery { recurringTransactionRepository.getAllRulesList() } returns emptyList()
            coEvery { featureSettingsRepository.getNotifiedPatternSignatures() } returns emptySet()

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) }
            coVerify(exactly = 0) { featureSettingsRepository.markPatternNotified(any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork suppresses notification when active rule matches by description and account ID`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val pattern = createPatternFixture(signature = "sig_matched", description = "Netflix", accountId = 2)
            val activeRule = createRuleFixture(id = 10, description = "netflix", accountId = 2)
            coEvery { useCase(any()) } returns listOf(pattern)
            coEvery { recurringTransactionRepository.getAllRulesList() } returns listOf(activeRule)
            coEvery { featureSettingsRepository.getNotifiedPatternSignatures() } returns emptySet()

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) }
            coVerify(exactly = 0) { featureSettingsRepository.markPatternNotified(any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork notifies when active rule description matches but account ID differs`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val pattern = createPatternFixture(signature = "sig_diff_acc", description = "Netflix", accountId = 1)
            val activeRule = createRuleFixture(id = 11, description = "Netflix", accountId = 2)
            coEvery { useCase(any()) } returns listOf(pattern)
            coEvery { recurringTransactionRepository.getAllRulesList() } returns listOf(activeRule)
            coEvery { featureSettingsRepository.getNotifiedPatternSignatures() } returns emptySet()

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 1) { NotificationHelper.showRecurringPatternDetectedNotification(any(), pattern) }
            coVerify(exactly = 1) { featureSettingsRepository.markPatternNotified("sig_diff_acc") }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork suppresses notification when signature is already in notified cache`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            val pattern = createPatternFixture(signature = "sig_cached", description = "Prime")
            coEvery { useCase(any()) } returns listOf(pattern)
            coEvery { recurringTransactionRepository.getAllRulesList() } returns emptyList()
            coEvery { featureSettingsRepository.getNotifiedPatternSignatures() } returns setOf("sig_cached")

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) }
            coVerify(exactly = 0) { featureSettingsRepository.markPatternNotified(any()) }
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork on attempt 0 or 1 failure returns retry without rescheduling`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws RuntimeException("Use case failure")

            for (attempt in 0..1) {
                val worker =
                    SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context, runAttemptCount = attempt)
                val result = worker.doWork()
                assertEquals(ListenableWorker.Result.retry(), result)
            }
            verify(exactly = 0) { workScheduler.scheduleRecurringPatternWorker(any()) }
        }

    @Test
    fun `doWork on final attempt 2 returns failure and reschedules`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws RuntimeException("Use case failure")

            val worker =
                SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context, runAttemptCount = 2)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            verify(exactly = 1) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
        }

    @Test
    fun `doWork rethrows CancellationException cleanly without swallowing`() =
        runTest {
            every { settingsRepository.getRecurringTransactionsEnabled() } returns flowOf(true)
            coEvery { useCase(any()) } throws CancellationException("Worker cancelled")

            val worker = SummaryWorkerTestFixtures.createWorker<RecurringPatternWorker>(context)

            assertThrows(CancellationException::class.java) {
                runBlocking {
                    worker.doWork()
                }
            }
            verify(exactly = 0) { workScheduler.scheduleRecurringPatternWorker(any()) }
        }
}
