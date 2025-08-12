// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/ReminderManager.kt
// REASON: REFACTOR - Added a new public `rescheduleAllWork` function. This
// centralizes the logic for re-scheduling all background tasks, which is
// essential for the BootReceiver to correctly re-initialize all workers after
// a device restart.
// =================================================================================
package io.pm.finlight.utils

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.pm.finlight.BackupWorker
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.WeeklySummaryWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderManager {
    private const val DAILY_EXPENSE_REPORT_WORK_TAG = "daily_expense_report_work"
    private const val WEEKLY_SUMMARY_WORK_TAG = "weekly_summary_work"
    private const val MONTHLY_SUMMARY_WORK_TAG = "monthly_summary_work"
    private const val RECURRING_TRANSACTION_WORK_TAG = "recurring_transaction_work"
    private const val RECURRING_PATTERN_WORK_TAG = "recurring_pattern_work"
    private const val AUTO_BACKUP_WORK_TAG = "auto_backup_work"


    /**
     * Re-schedules all background workers based on their current settings.
     * This is intended to be called after a device reboot to ensure all
     * scheduled tasks persist.
     */
    fun rescheduleAllWork(context: Context) {
        Log.d("ReminderManager", "Rescheduling all background work...")
        val settings = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)

        // Reschedule reports and backups only if they are enabled
        if (settings.getBoolean("daily_report_enabled", false)) {
            scheduleDailyReport(context)
        }
        if (settings.getBoolean("weekly_summary_enabled", true)) {
            scheduleWeeklySummary(context)
        }
        if (settings.getBoolean("monthly_summary_enabled", true)) {
            scheduleMonthlySummary(context)
        }
        if (settings.getBoolean("auto_backup_enabled", true)) {
            scheduleAutoBackup(context)
        }

        // These workers don't have user-facing toggles, so they should always be rescheduled
        scheduleRecurringTransactionWorker(context)
        scheduleRecurringPatternWorker(context)
    }


    fun scheduleAutoBackup(context: Context) {
        val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        val hour = prefs.getInt("auto_backup_hour", 2) // Default to 2 AM
        val minute = prefs.getInt("auto_backup_minute", 0)

        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val backupRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AUTO_BACKUP_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            backupRequest,
        )
        Log.d("ReminderManager", "Auto backup scheduled for ${nextRun.time}")
    }

    fun cancelAutoBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_TAG)
        Log.d("ReminderManager", "Auto backup cancelled.")
    }

    fun scheduleRecurringPatternWorker(context: Context) {
        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis

        val recurringRequest = OneTimeWorkRequestBuilder<RecurringPatternWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RECURRING_PATTERN_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            recurringRequest
        )
        Log.d("ReminderManager", "Recurring pattern worker scheduled for ${nextRun.time}")
    }


    fun scheduleRecurringTransactionWorker(context: Context) {
        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis

        val recurringRequest = OneTimeWorkRequestBuilder<RecurringTransactionWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RECURRING_TRANSACTION_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            recurringRequest
        )
        Log.d("ReminderManager", "Recurring transaction worker scheduled for ${nextRun.time}")
    }


    fun scheduleDailyReport(context: Context) {
        val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        val hour = prefs.getInt("daily_report_hour", 9)
        val minute = prefs.getInt("daily_report_minute", 0)

        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val dailyReportRequest = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_EXPENSE_REPORT_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            dailyReportRequest,
        )
        Log.d("ReminderManager", "Daily report scheduled for ${nextRun.time}")
    }

    fun cancelDailyReport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_EXPENSE_REPORT_WORK_TAG)
    }

    fun scheduleWeeklySummary(context: Context) {
        val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        val dayOfWeek = prefs.getInt("weekly_report_day", Calendar.MONDAY)
        val hour = prefs.getInt("weekly_report_hour", 9)
        val minute = prefs.getInt("weekly_report_minute", 0)

        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.WEEK_OF_YEAR, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val weeklyReportRequest = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WEEKLY_SUMMARY_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            weeklyReportRequest,
        )
        Log.d("ReminderManager", "Weekly summary scheduled for ${nextRun.time}")
    }


    fun cancelWeeklySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_SUMMARY_WORK_TAG)
    }

    fun scheduleMonthlySummary(context: Context) {
        val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        val dayOfMonth = prefs.getInt("monthly_report_day", 1)
        val hour = prefs.getInt("monthly_report_hour", 9)
        val minute = prefs.getInt("monthly_report_minute", 0)

        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.MONTH, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val monthlyReportRequest = OneTimeWorkRequestBuilder<MonthlySummaryWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MONTHLY_SUMMARY_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            monthlyReportRequest,
        )
        Log.d("ReminderManager", "Monthly summary scheduled for ${nextRun.time}")
    }

    fun cancelMonthlySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MONTHLY_SUMMARY_WORK_TAG)
    }
}
