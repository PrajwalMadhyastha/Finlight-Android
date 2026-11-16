// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/utils/ReminderManagerTest.kt
// =================================================================================
package io.pm.finlight.utils

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.workers.GoalSurplusWorker
import io.pm.finlight.workers.SmsCatchupWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ReminderManagerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var settingsRepository: io.pm.finlight.ISettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(any()) } returns true
        runTest {
            context.financeSettingsDataStore.edit { it.clear() }
        }
        io.pm.finlight.di.ServiceLocator.reset()
        settingsRepository = io.pm.finlight.di.ServiceLocator.provideSettingsRepository(context)

        // Initialize WorkManager for testing
        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Cancel all work after each test to ensure a clean state
        workManager.cancelAllWork()
        unmockkObject(DataExportService)
        io.pm.finlight.di.ServiceLocator.reset()
    }

    private fun assertWorkIsEnqueued(
        uniqueWorkName: String,
        workerClass: Class<out ListenableWorker>,
    ) {
        WorkSchedulerTestUtils.assertWorkEnqueued(workManager, uniqueWorkName, workerClass)
    }

    private fun assertWorkIsCancelled(uniqueWorkName: String) {
        WorkSchedulerTestUtils.assertWorkCancelled(workManager, uniqueWorkName)
    }

    @Test
    fun `scheduleDailyReport schedules DailyReportWorker correctly`() =
        runTest {
            // Arrange: Set time for 2 hours from now
            val nextRun = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) }
            settingsRepository.saveDailyReportTime(
                nextRun.get(Calendar.HOUR_OF_DAY),
                nextRun.get(Calendar.MINUTE),
            )

            // Act
            ReminderManager.scheduleDailyReport(context)

            // Assert
            assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java)
        }

    @Test
    fun `cancelDailyReport cancels the work`() =
        runTest {
            // Arrange
            ReminderManager.scheduleDailyReport(context)
            assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java) // Pre-condition

            // Act
            ReminderManager.cancelDailyReport(context)

            // Assert
            assertWorkIsCancelled("daily_expense_report_work")
        }

    @Test
    fun `scheduleWeeklySummary schedules WeeklySummaryWorker correctly`() =
        runTest {
            // Arrange
            settingsRepository.saveWeeklyReportTime(
                Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1,
                Calendar.getInstance().get(Calendar.MINUTE),
            )

            // Act
            ReminderManager.scheduleWeeklySummary(context)

            // Assert
            assertWorkIsEnqueued("weekly_summary_work", WeeklySummaryWorker::class.java)
        }

    @Test
    fun `scheduleMonthlySummary schedules MonthlySummaryWorker correctly`() =
        runTest {
            // Arrange
            settingsRepository.saveMonthlyReportTime(
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1,
                Calendar.getInstance().get(Calendar.MINUTE),
            )

            // Act
            ReminderManager.scheduleMonthlySummary(context)

            // Assert
            assertWorkIsEnqueued("monthly_summary_work", MonthlySummaryWorker::class.java)
        }

    @Test
    fun `scheduleAutoBackup schedules BackupWorker correctly`() {
        // Act
        ReminderManager.scheduleAutoBackup(context)

        // Assert
        assertWorkIsEnqueued("auto_backup_work", BackupWorker::class.java)
    }

    @Test
    fun `rescheduleAllWork schedules enabled workers`() =
        runTest {
            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = true)

            // Act
            ReminderManager.rescheduleAllWork(context)

            // Assert
            assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java)
            assertWorkIsEnqueued("weekly_summary_work", WeeklySummaryWorker::class.java)
            assertWorkIsEnqueued("monthly_summary_work", MonthlySummaryWorker::class.java)
            assertWorkIsEnqueued("auto_backup_work", BackupWorker::class.java)
            assertWorkIsEnqueued("recurring_transaction_work", RecurringTransactionWorker::class.java)
            assertWorkIsEnqueued("recurring_pattern_work", RecurringPatternWorker::class.java)
            assertWorkIsEnqueued("sms_catchup_work", SmsCatchupWorker::class.java)
            assertWorkIsEnqueued("goal_surplus_work", GoalSurplusWorker::class.java)
        }

    @Test
    fun `rescheduleAllWork does not schedule disabled workers`() =
        runTest {
            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = false)

            // Act
            ReminderManager.rescheduleAllWork(context)

            // Assert: All workers are cancelled/not present when disabled
            WorkSchedulerTestUtils.assertConfigurableWorkersCancelled(workManager)
        }

    @Test
    fun `scheduleSmsRecoveryWorker schedules SmsCatchupWorker with batteryNotLow constraint`() {
        ReminderManager.scheduleSmsRecoveryWorker(context)

        assertWorkIsEnqueued("sms_catchup_work", SmsCatchupWorker::class.java)
        val workInfos = workManager.getWorkInfosForUniqueWork("sms_catchup_work").get()
        assertEquals(1, workInfos.size)
        assertTrue(
            "Worker must have requiresBatteryNotLow constraint",
            workInfos[0].constraints.requiresBatteryNotLow(),
        )
    }

    @Test
    fun `scheduleSmsRecoveryWorker updates existing work with new constraints`() {
        // Enqueue an initial work request without constraints to simulate an older app version
        val oldRequest =
            PeriodicWorkRequestBuilder<SmsCatchupWorker>(4, java.util.concurrent.TimeUnit.HOURS)
                .build()
        workManager.enqueueUniquePeriodicWork(
            "sms_catchup_work",
            ExistingPeriodicWorkPolicy.KEEP,
            oldRequest,
        ).result.get()

        var workInfos = workManager.getWorkInfosForUniqueWork("sms_catchup_work").get()
        assertEquals(1, workInfos.size)
        assertFalse(workInfos[0].constraints.requiresBatteryNotLow())

        // Call scheduleSmsRecoveryWorker which uses UPDATE policy
        ReminderManager.scheduleSmsRecoveryWorker(context)

        workInfos = workManager.getWorkInfosForUniqueWork("sms_catchup_work").get()
        assertEquals(1, workInfos.size)
        assertTrue(
            "Worker must update to have requiresBatteryNotLow constraint",
            workInfos[0].constraints.requiresBatteryNotLow(),
        )
    }

    @Test
    fun `cancelSmsRecoveryWorker cancels SmsCatchupWorker`() {
        ReminderManager.scheduleSmsRecoveryWorker(context)
        ReminderManager.cancelSmsRecoveryWorker(context)

        assertWorkIsCancelled("sms_catchup_work")
    }

    @Test
    fun `cancelWeeklySummary cancels weekly work`() =
        runTest {
            ReminderManager.scheduleWeeklySummary(context)
            ReminderManager.cancelWeeklySummary(context)

            assertWorkIsCancelled("weekly_summary_work")
        }

    @Test
    fun `cancelMonthlySummary cancels monthly work`() =
        runTest {
            ReminderManager.scheduleMonthlySummary(context)
            ReminderManager.cancelMonthlySummary(context)

            assertWorkIsCancelled("monthly_summary_work")
        }

    @Test
    fun `cancelAutoBackup cancels backup work`() {
        ReminderManager.scheduleAutoBackup(context)
        ReminderManager.cancelAutoBackup(context)

        assertWorkIsCancelled("auto_backup_work")
    }

    @Test
    fun `scheduleRecurringPatternWorker and cancelRecurringTransactionWorkers delegate properly`() {
        ReminderManager.scheduleRecurringTransactionWorker(context)
        ReminderManager.scheduleRecurringPatternWorker(context)
        assertWorkIsEnqueued("recurring_transaction_work", RecurringTransactionWorker::class.java)
        assertWorkIsEnqueued("recurring_pattern_work", RecurringPatternWorker::class.java)

        ReminderManager.cancelRecurringTransactionWorkers(context)
        assertWorkIsCancelled("recurring_transaction_work")
        assertWorkIsCancelled("recurring_pattern_work")
    }

    @Test
    fun `scheduleGoalSurplusWorker delegates properly`() {
        ReminderManager.scheduleGoalSurplusWorker(context)
        assertWorkIsEnqueued("goal_surplus_work", io.pm.finlight.workers.GoalSurplusWorker::class.java)
    }
}
