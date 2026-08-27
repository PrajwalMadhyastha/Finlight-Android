package io.pm.finlight.utils

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.pm.finlight.BackupWorker
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.workers.SmsCatchupWorker
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderManager {
    private const val DAILY_EXPENSE_REPORT_WORK_TAG = "daily_expense_report_work"
    private const val WEEKLY_SUMMARY_WORK_TAG = "weekly_summary_work"
    private const val MONTHLY_SUMMARY_WORK_TAG = "monthly_summary_work"
    private const val RECURRING_TRANSACTION_WORK_TAG = "recurring_transaction_work"
    private const val RECURRING_PATTERN_WORK_TAG = "recurring_pattern_work"
    private const val AUTO_BACKUP_WORK_TAG = "auto_backup_work"
    private const val SMS_CATCHUP_WORK_TAG = "sms_catchup_work"
    private const val GOAL_SURPLUS_WORK_TAG = "goal_surplus_work"

    suspend fun rescheduleAllWork(context: Context) {
        Log.d("ReminderManager", "Rescheduling all background work...")
        val settings = ServiceLocator.provideSettingsRepository(context)

        val dailyReportEnabled = settings.getDailyReportEnabled().first()
        val weeklySummaryEnabled = settings.getWeeklySummaryEnabled().first()
        val monthlySummaryEnabled = settings.getMonthlySummaryEnabled().first()
        val autoBackupEnabled = settings.getAutoBackupEnabled().first()

        if (dailyReportEnabled) {
            scheduleDailyReport(context)
        }
        if (weeklySummaryEnabled) {
            scheduleWeeklySummary(context)
        }
        if (monthlySummaryEnabled) {
            scheduleMonthlySummary(context)
        }
        if (autoBackupEnabled) {
            scheduleAutoBackup(context)
        }

        Log.d("ReminderManager", "Recurring transaction feature is disabled. Not scheduling workers.")
        cancelRecurringTransactionWorkers(context)

        scheduleSmsRecoveryWorker(context)
        scheduleGoalSurplusWorker(context)
    }

    fun scheduleSmsRecoveryWorker(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<SmsCatchupWorker>(4, TimeUnit.HOURS)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SMS_CATCHUP_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Log.d("ReminderManager", "SMS catch-up worker scheduled (every 4 hours).")
    }

    fun cancelSmsRecoveryWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SMS_CATCHUP_WORK_TAG)
        Log.d("ReminderManager", "SMS catch-up worker cancelled.")
    }

    fun scheduleAutoBackup(context: Context) {
        val backupRequest =
            PeriodicWorkRequestBuilder<BackupWorker>(8, TimeUnit.HOURS)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest,
        )
        Log.d("ReminderManager", "Auto backup (WorkManager) scheduled to run periodically every 8 hours.")
    }

    fun cancelAutoBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_TAG)
        Log.d("ReminderManager", "Auto backup cancelled.")
    }

    fun scheduleRecurringPatternWorker(context: Context) {
        val now = Calendar.getInstance()
        val nextRun =
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis

        val recurringRequest =
            OneTimeWorkRequestBuilder<RecurringPatternWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RECURRING_PATTERN_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            recurringRequest,
        )
        Log.d("ReminderManager", "Recurring pattern worker scheduled for ${nextRun.time}")
    }

    fun scheduleRecurringTransactionWorker(context: Context) {
        val now = Calendar.getInstance()
        val nextRun =
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis

        val recurringRequest =
            OneTimeWorkRequestBuilder<RecurringTransactionWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RECURRING_TRANSACTION_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            recurringRequest,
        )
        Log.d("ReminderManager", "Recurring transaction worker scheduled for ${nextRun.time}")
    }

    fun cancelRecurringTransactionWorkers(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(RECURRING_PATTERN_WORK_TAG)
        WorkManager.getInstance(context).cancelUniqueWork(RECURRING_TRANSACTION_WORK_TAG)
        Log.d("ReminderManager", "Cancelled all recurring transaction workers.")
    }

    suspend fun scheduleDailyReport(context: Context) {
        val settings = ServiceLocator.provideSettingsRepository(context)
        val (hour, minute) = settings.getDailyReportTime().first()

        val now = Calendar.getInstance()
        val nextRun =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val dailyReportRequest =
            OneTimeWorkRequestBuilder<DailyReportWorker>()
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

    suspend fun scheduleWeeklySummary(context: Context) {
        val settings = ServiceLocator.provideSettingsRepository(context)
        val (dayOfWeek, hour, minute) = settings.getWeeklyReportTime().first()

        val now = Calendar.getInstance()
        val nextRun =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        if (nextRun.get(Calendar.DAY_OF_WEEK) == dayOfWeek && nextRun.before(now)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1)
        }

        while (nextRun.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val weeklyReportRequest =
            OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
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

    suspend fun scheduleMonthlySummary(context: Context) {
        val settings = ServiceLocator.provideSettingsRepository(context)
        val (dayOfMonth, hour, minute) = settings.getMonthlyReportTime().first()

        val now = Calendar.getInstance()
        val nextRun =
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.MONTH, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val monthlyReportRequest =
            OneTimeWorkRequestBuilder<MonthlySummaryWorker>()
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

    fun scheduleGoalSurplusWorker(context: Context) {
        val now = Calendar.getInstance()
        val nextRun =
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 10) // 10 AM on the 1st
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.MONTH, 1)
        }

        val initialDelay = nextRun.timeInMillis - now.timeInMillis
        val goalSurplusRequest =
            OneTimeWorkRequestBuilder<io.pm.finlight.workers.GoalSurplusWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            GOAL_SURPLUS_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            goalSurplusRequest,
        )
        Log.d("ReminderManager", "Goal surplus check scheduled for ${nextRun.time}")
    }
}
