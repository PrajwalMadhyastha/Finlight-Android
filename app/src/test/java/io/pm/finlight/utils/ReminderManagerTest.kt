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
import io.pm.finlight.*
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var settingsRepository: SettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        runTest {
            context.financeSettingsDataStore.edit { it.clear() }
        }
        settingsRepository = SettingsRepository(context)

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
    }

    private fun assertWorkIsEnqueued(
        uniqueWorkName: String,
        workerClass: Class<out ListenableWorker>,
    ) {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertEquals("Work request should be enqueued for $uniqueWorkName", 1, workInfos.size)
        val workInfo = workInfos[0]
        // Periodic work can be ENQUEUED or RUNNING immediately in tests
        assertTrue(
            "Work state should be ENQUEUED or RUNNING, but was ${workInfo.state}",
            workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING,
        )
        assertTrue(
            "Worker class name should contain ${workerClass.simpleName}",
            workInfo.tags.any { it.contains(workerClass.simpleName) },
        )
    }

    private fun assertWorkIsCancelled(uniqueWorkName: String) {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertTrue(
            "Work request for $uniqueWorkName should be cancelled or finished",
            workInfos.all {
                it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.SUCCEEDED
            },
        )
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

    @org.junit.Ignore("Temporarily disabled (Issue #105)")
    @Test
    fun `rescheduleAllWork schedules enabled workers`() =
        runTest {
            // Arrange: Enable everything
            settingsRepository.saveDailyReportEnabled(true)
            settingsRepository.saveWeeklySummaryEnabled(true)
            settingsRepository.saveMonthlySummaryEnabled(true)
            settingsRepository.saveAutoBackupEnabled(true)
            settingsRepository.saveRecurringTransactionsEnabled(true)

            // Act
            ReminderManager.rescheduleAllWork(context)

            // Assert
            assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java)
            assertWorkIsEnqueued("weekly_summary_work", WeeklySummaryWorker::class.java)
            assertWorkIsEnqueued("monthly_summary_work", MonthlySummaryWorker::class.java)
            assertWorkIsEnqueued("auto_backup_work", BackupWorker::class.java)
        }

    @Test
    fun `rescheduleAllWork does not schedule disabled workers`() =
        runTest {
            // Arrange: Disable everything including recurring transactions
            settingsRepository.saveDailyReportEnabled(false)
            settingsRepository.saveWeeklySummaryEnabled(false)
            settingsRepository.saveMonthlySummaryEnabled(false)
            settingsRepository.saveAutoBackupEnabled(false)
            settingsRepository.saveRecurringTransactionsEnabled(false)

            // Act
            ReminderManager.rescheduleAllWork(context)

            // Assert: All workers are cancelled/not present when disabled
            assertWorkIsCancelled("daily_expense_report_work")
            assertWorkIsCancelled("weekly_summary_work")
            assertWorkIsCancelled("monthly_summary_work")
            assertWorkIsCancelled("auto_backup_work")
            assertWorkIsCancelled("recurring_transaction_work")
            assertWorkIsCancelled("recurring_pattern_work")
        }
}
