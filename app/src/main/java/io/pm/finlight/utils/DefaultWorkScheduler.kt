package io.pm.finlight.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.pm.finlight.BackupWorker
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.TransactionNotificationWorker
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.workers.GoalSurplusWorker
import io.pm.finlight.workers.SmsCatchupWorker
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DefaultWorkScheduler(
    private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val workManagerProvider: () -> WorkManager = { WorkManager.getInstance(context) },
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : IWorkScheduler {
    companion object {
        private const val TAG = "DefaultWorkScheduler"
        const val DAILY_EXPENSE_REPORT_WORK_TAG = "daily_expense_report_work"
        const val WEEKLY_SUMMARY_WORK_TAG = "weekly_summary_work"
        const val MONTHLY_SUMMARY_WORK_TAG = "monthly_summary_work"
        const val RECURRING_TRANSACTION_WORK_TAG = "recurring_transaction_work"
        const val RECURRING_PATTERN_WORK_TAG = "recurring_pattern_work"
        const val AUTO_BACKUP_WORK_TAG = "auto_backup_work"
        const val SMS_CATCHUP_WORK_TAG = "sms_catchup_work"
        const val GOAL_SURPLUS_WORK_TAG = "goal_surplus_work"
    }

    private val workManager: WorkManager by lazy { workManagerProvider() }

    override suspend fun scheduleDailyReport(policy: ExistingWorkPolicy) {
        val (hour, minute) = settingsRepository.getDailyReportTime().first()
        val now = Calendar.getInstance()
        val nextRun =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
        val delay = nextRun.timeInMillis - now.timeInMillis
        val request =
            OneTimeWorkRequestBuilder<DailyReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(DAILY_EXPENSE_REPORT_WORK_TAG, policy, request)
    }

    override suspend fun scheduleWeeklySummary(policy: ExistingWorkPolicy) {
        val (dayOfWeek, hour, minute) = settingsRepository.getWeeklyReportTime().first()
        val now = Calendar.getInstance()
        val nextRun =
            (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
        val delay = nextRun.timeInMillis - now.timeInMillis
        val request =
            OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(WEEKLY_SUMMARY_WORK_TAG, policy, request)
    }

    override suspend fun scheduleMonthlySummary(policy: ExistingWorkPolicy) {
        val (dayOfMonth, hour, minute) = settingsRepository.getMonthlyReportTime().first()
        val now = Calendar.getInstance()
        val nextRun =
            (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
            }
        val delay = nextRun.timeInMillis - now.timeInMillis
        val request =
            OneTimeWorkRequestBuilder<MonthlySummaryWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(MONTHLY_SUMMARY_WORK_TAG, policy, request)
    }

    override fun scheduleAutoBackup(policy: ExistingPeriodicWorkPolicy) {
        val request =
            PeriodicWorkRequestBuilder<BackupWorker>(8, TimeUnit.HOURS)
                .build()
        workManager.enqueueUniquePeriodicWork(AUTO_BACKUP_WORK_TAG, policy, request)
    }

    override fun scheduleSmsRecoveryWorker(policy: ExistingPeriodicWorkPolicy) {
        val constraints =
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        val request =
            PeriodicWorkRequestBuilder<SmsCatchupWorker>(4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        workManager.enqueueUniquePeriodicWork(SMS_CATCHUP_WORK_TAG, policy, request)
    }

    override fun cancelSmsRecoveryWorker() {
        workManager.cancelUniqueWork(SMS_CATCHUP_WORK_TAG)
    }

    override fun scheduleGoalSurplusWorker(
        forceNextMonth: Boolean,
        policy: ExistingWorkPolicy,
    ) {
        val now = Calendar.getInstance()
        val targetYear =
            if (forceNextMonth) {
                if (now.get(Calendar.MONTH) == Calendar.DECEMBER) now.get(Calendar.YEAR) + 1 else now.get(Calendar.YEAR)
            } else {
                now.get(Calendar.YEAR)
            }
        val targetMonth =
            if (forceNextMonth) {
                (now.get(Calendar.MONTH) + 1) % 12
            } else {
                now.get(Calendar.MONTH)
            }
        val nextRun =
            (now.clone() as Calendar).apply {
                set(Calendar.YEAR, targetYear)
                set(Calendar.MONTH, targetMonth)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            }
        val delay = maxOf(0L, nextRun.timeInMillis - now.timeInMillis)
        val request =
            OneTimeWorkRequestBuilder<GoalSurplusWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(GOAL_SURPLUS_WORK_TAG, policy, request)
    }

    override fun scheduleRecurringTransactionWorker(policy: ExistingWorkPolicy) {
        val now = Calendar.getInstance()
        val nextRun =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
        val delay = nextRun.timeInMillis - now.timeInMillis
        val request =
            OneTimeWorkRequestBuilder<RecurringTransactionWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(RECURRING_TRANSACTION_WORK_TAG, policy, request)
    }

    override fun scheduleRecurringPatternWorker(policy: ExistingWorkPolicy) {
        val now = Calendar.getInstance()
        val nextRun =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
        val delay = nextRun.timeInMillis - now.timeInMillis
        val request =
            OneTimeWorkRequestBuilder<RecurringPatternWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniqueWork(RECURRING_PATTERN_WORK_TAG, policy, request)
    }

    override fun cancelRecurringTransactionWorkers() {
        workManager.cancelUniqueWork(RECURRING_TRANSACTION_WORK_TAG)
        workManager.cancelUniqueWork(RECURRING_PATTERN_WORK_TAG)
    }

    override fun cancelDailyReport() {
        workManager.cancelUniqueWork(DAILY_EXPENSE_REPORT_WORK_TAG)
    }

    override fun cancelWeeklySummary() {
        workManager.cancelUniqueWork(WEEKLY_SUMMARY_WORK_TAG)
    }

    override fun cancelMonthlySummary() {
        workManager.cancelUniqueWork(MONTHLY_SUMMARY_WORK_TAG)
    }

    override fun cancelAutoBackup() {
        workManager.cancelUniqueWork(AUTO_BACKUP_WORK_TAG)
    }

    override fun scheduleTransactionNotification(transactionId: Long) {
        val workRequest =
            OneTimeWorkRequestBuilder<TransactionNotificationWorker>()
                .setInputData(workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId.toInt()))
                .build()
        workManager.enqueueUniqueWork(
            "tx_notif_$transactionId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    override suspend fun reconcileWorkOnStartup() {
        try {
            // Verify WorkManager is initialized in this environment (guards against Robolectric IllegalStateException)
            workManagerProvider()
        } catch (e: IllegalStateException) {
            Log.w(
                TAG,
                "WorkManager not initialized in current environment. Skipping startup reconciliation.",
                e,
            )
            return
        }
        if (settingsRepository.getDailyReportEnabled().first()) {
            scheduleDailyReport(policy = ExistingWorkPolicy.KEEP)
        }
        if (settingsRepository.getWeeklySummaryEnabled().first()) {
            scheduleWeeklySummary(policy = ExistingWorkPolicy.KEEP)
        }
        if (settingsRepository.getMonthlySummaryEnabled().first()) {
            scheduleMonthlySummary(policy = ExistingWorkPolicy.KEEP)
        }
        if (settingsRepository.getAutoBackupEnabled().first()) {
            scheduleAutoBackup(policy = ExistingPeriodicWorkPolicy.KEEP)
        }
        if (settingsRepository.getRecurringTransactionsEnabled().first()) {
            scheduleRecurringTransactionWorker(policy = ExistingWorkPolicy.KEEP)
            scheduleRecurringPatternWorker(policy = ExistingWorkPolicy.KEEP)
        }
        scheduleGoalSurplusWorker(forceNextMonth = false, policy = ExistingWorkPolicy.KEEP)
        scheduleSmsRecoveryWorker(policy = ExistingPeriodicWorkPolicy.KEEP)
    }

    override suspend fun rescheduleAllWork() {
        if (settingsRepository.getDailyReportEnabled().first()) {
            scheduleDailyReport(ExistingWorkPolicy.REPLACE)
        } else {
            cancelDailyReport()
        }

        if (settingsRepository.getWeeklySummaryEnabled().first()) {
            scheduleWeeklySummary(ExistingWorkPolicy.REPLACE)
        } else {
            cancelWeeklySummary()
        }

        if (settingsRepository.getMonthlySummaryEnabled().first()) {
            scheduleMonthlySummary(ExistingWorkPolicy.REPLACE)
        } else {
            cancelMonthlySummary()
        }

        if (settingsRepository.getAutoBackupEnabled().first()) {
            scheduleAutoBackup(ExistingPeriodicWorkPolicy.UPDATE)
        } else {
            cancelAutoBackup()
        }

        val recurringEnabled = settingsRepository.getRecurringTransactionsEnabled().first()
        if (recurringEnabled) {
            scheduleRecurringTransactionWorker(ExistingWorkPolicy.REPLACE)
            scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
        } else {
            cancelRecurringTransactionWorkers()
        }
        scheduleGoalSurplusWorker(forceNextMonth = false, policy = ExistingWorkPolicy.REPLACE)
        scheduleSmsRecoveryWorker(policy = ExistingPeriodicWorkPolicy.UPDATE)
    }
}
