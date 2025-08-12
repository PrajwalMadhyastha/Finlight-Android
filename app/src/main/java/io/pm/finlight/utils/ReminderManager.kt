// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/ReminderManager.kt
// REASON: REFACTOR - The scheduling logic for all reports (Daily, Weekly,
// Monthly) has been updated to use the precise `AlarmManager` instead of
// `WorkManager`'s inexact initial delays. This significantly increases the
// reliability of time-sensitive notifications, especially on devices with
// aggressive battery optimization. The old worker-scheduling logic in these
// functions has been replaced by calls to the new `scheduleExactAlarm` helper.
// =================================================================================
package io.pm.finlight.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.pm.finlight.BackupWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.receiver.AlarmReceiver
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderManager {
    private const val DAILY_EXPENSE_REPORT_WORK_TAG = "daily_expense_report_work"
    private const val WEEKLY_SUMMARY_WORK_TAG = "weekly_summary_work"
    private const val MONTHLY_SUMMARY_WORK_TAG = "monthly_summary_work"
    private const val RECURRING_TRANSACTION_WORK_TAG = "recurring_transaction_work"
    private const val RECURRING_PATTERN_WORK_TAG = "recurring_pattern_work"
    private const val AUTO_BACKUP_WORK_TAG = "auto_backup_work"

    fun rescheduleAllWork(context: Context) {
        Log.d("ReminderManager", "Rescheduling all background work...")
        val settings = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)

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

        scheduleRecurringTransactionWorker(context)
        scheduleRecurringPatternWorker(context)
    }

    private fun scheduleExactAlarm(context: Context, triggerAtMillis: Long, workTag: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_WORK_TAG, workTag)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.d("ReminderManager", "Exact alarm scheduled for $workTag at ${Calendar.getInstance().apply { timeInMillis = triggerAtMillis }.time}")
        } else {
            Log.w("ReminderManager", "Cannot schedule exact alarms. App may not deliver notifications on time.")
            // As a fallback, you could schedule an inexact alarm, but for now, we'll rely on the permission.
        }
    }

    private fun cancelExactAlarm(context: Context, workTag: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_WORK_TAG, workTag)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("ReminderManager", "Cancelled alarm for $workTag")
    }

    fun scheduleAutoBackup(context: Context) {
        val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        val hour = prefs.getInt("auto_backup_hour", 2)
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
        Log.d("ReminderManager", "Auto backup (WorkManager) scheduled for ${nextRun.time}")
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

        scheduleExactAlarm(context, nextRun.timeInMillis, DAILY_EXPENSE_REPORT_WORK_TAG, 1)
    }

    fun cancelDailyReport(context: Context) {
        cancelExactAlarm(context, DAILY_EXPENSE_REPORT_WORK_TAG, 1)
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

        scheduleExactAlarm(context, nextRun.timeInMillis, WEEKLY_SUMMARY_WORK_TAG, 2)
    }


    fun cancelWeeklySummary(context: Context) {
        cancelExactAlarm(context, WEEKLY_SUMMARY_WORK_TAG, 2)
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

        scheduleExactAlarm(context, nextRun.timeInMillis, MONTHLY_SUMMARY_WORK_TAG, 3)
    }

    fun cancelMonthlySummary(context: Context) {
        cancelExactAlarm(context, MONTHLY_SUMMARY_WORK_TAG, 3)
    }
}
