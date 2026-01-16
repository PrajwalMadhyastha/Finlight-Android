// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/utils/ReminderManagerTest.kt
// REASON: CLEANUP - Removed the import for the deleted `SnapshotWorker`.
// - Removed the test case `scheduleSnapshotWorker schedules SnapshotWorker`.
// - Removed assertions for `snapshot_worker_tag` from both
//   `rescheduleAllWork` tests, as this worker is no longer scheduled
//   by this method.
//
// REASON: FIX (Test) - The `assertWorkIsEnqueued` assertion is now more
// flexible, accepting either ENQUEUED or RUNNING. This fixes a test
// failure caused by the SynchronousExecutor running periodic work immediately.
// =================================================================================
package io.pm.finlight.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.pm.finlight.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ReminderManagerTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var prefs: SharedPreferences

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Initialize WorkManager for testing
        val config = Configuration.Builder()
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

    private fun assertWorkIsEnqueued(uniqueWorkName: String, workerClass: Class<out ListenableWorker>) {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertEquals("Work request should be enqueued for $uniqueWorkName", 1, workInfos.size)
        val workInfo = workInfos[0]
        // --- FIX: Periodic work can be ENQUEUED or RUNNING immediately in tests ---
        assertTrue(
            "Work state should be ENQUEUED or RUNNING, but was ${workInfo.state}",
            workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        )
        assertTrue(
            "Worker class name should contain ${workerClass.simpleName}",
            workInfo.tags.any { it.contains(workerClass.simpleName) }
        )
    }

    private fun assertWorkIsCancelled(uniqueWorkName: String) {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertTrue("Work request for $uniqueWorkName should be cancelled or finished", workInfos.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.SUCCEEDED })
    }

    @Test
    fun `scheduleDailyReport schedules DailyReportWorker correctly`() {
        // Arrange: Set time for 2 hours from now
        val nextRun = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) }
        prefs.edit()
            .putInt("daily_report_hour", nextRun.get(Calendar.HOUR_OF_DAY))
            .putInt("daily_report_minute", nextRun.get(Calendar.MINUTE))
            .apply()

        // Act
        ReminderManager.scheduleDailyReport(context)

        // Assert
        assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java)
    }

    @Test
    fun `cancelDailyReport cancels the work`() {
        // Arrange
        ReminderManager.scheduleDailyReport(context)
        assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java) // Pre-condition

        // Act
        ReminderManager.cancelDailyReport(context)

        // Assert
        assertWorkIsCancelled("daily_expense_report_work")
    }

    @Test
    fun `scheduleWeeklySummary schedules WeeklySummaryWorker correctly`() {
        // Arrange
        prefs.edit()
            .putInt("weekly_report_day", Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
            .putInt("weekly_report_hour", Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1) // 1 hour in the future
            .putInt("weekly_report_minute", Calendar.getInstance().get(Calendar.MINUTE))
            .apply()

        // Act
        ReminderManager.scheduleWeeklySummary(context)

        // Assert
        assertWorkIsEnqueued("weekly_summary_work", WeeklySummaryWorker::class.java)
    }

    @Test
    fun `scheduleMonthlySummary schedules MonthlySummaryWorker correctly`() {
        // Arrange
        prefs.edit()
            .putInt("monthly_report_day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
            .putInt("monthly_report_hour", Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1)
            .putInt("monthly_report_minute", Calendar.getInstance().get(Calendar.MINUTE))
            .apply()

        // Act
        ReminderManager.scheduleMonthlySummary(context)

        // Assert
        assertWorkIsEnqueued("monthly_summary_work", MonthlySummaryWorker::class.java)
    }

    @Test
    fun `scheduleAutoBackup schedules BackupWorker correctly`() {
        // Arrange
        // --- UPDATED: No longer need to set prefs, time is hardcoded ---

        // Act
        ReminderManager.scheduleAutoBackup(context)

        // Assert
        assertWorkIsEnqueued("auto_backup_work", BackupWorker::class.java)
    }

    @Test
    fun `rescheduleAllWork schedules enabled workers`() {
        // Arrange: Enable everything
        prefs.edit()
            .putBoolean("daily_report_enabled", true)
            .putBoolean("weekly_summary_enabled", true)
            .putBoolean("monthly_summary_enabled", true)
            .putBoolean("auto_backup_enabled", true)
            .putBoolean("recurring_transactions_enabled", true)
            .apply()

        // Act
        ReminderManager.rescheduleAllWork(context)

        // Assert
        assertWorkIsEnqueued("daily_expense_report_work", DailyReportWorker::class.java)
        assertWorkIsEnqueued("weekly_summary_work", WeeklySummaryWorker::class.java)
        assertWorkIsEnqueued("monthly_summary_work", MonthlySummaryWorker::class.java)
        assertWorkIsEnqueued("auto_backup_work", BackupWorker::class.java)
        // Recurring transaction workers (when enabled)
        assertWorkIsEnqueued("recurring_transaction_work", RecurringTransactionWorker::class.java)
        assertWorkIsEnqueued("recurring_pattern_work", RecurringPatternWorker::class.java)
    }

    @Test
    fun `rescheduleAllWork does not schedule disabled workers`() {
        // Arrange: Disable everything including recurring transactions
        prefs.edit()
            .putBoolean("daily_report_enabled", false)
            .putBoolean("weekly_summary_enabled", false)
            .putBoolean("monthly_summary_enabled", false)
            .putBoolean("auto_backup_enabled", false)
            .putBoolean("recurring_transactions_enabled", false)
            .apply()

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
