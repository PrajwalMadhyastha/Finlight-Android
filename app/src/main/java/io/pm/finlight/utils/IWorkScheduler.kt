package io.pm.finlight.utils

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy

/**
 * Interface contract for scheduling and managing background workers.
 */
interface IWorkScheduler {
    suspend fun scheduleDailyReport(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE)

    suspend fun scheduleWeeklySummary(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE)

    suspend fun scheduleMonthlySummary(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE)

    fun scheduleAutoBackup(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE)

    fun scheduleSmsRecoveryWorker(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE)

    fun cancelSmsRecoveryWorker()

    fun scheduleGoalSurplusWorker(
        forceNextMonth: Boolean = false,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    )

    fun scheduleRecurringTransactionWorker(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE)

    fun scheduleRecurringPatternWorker(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE)

    fun cancelRecurringTransactionWorkers()

    fun cancelDailyReport()

    fun cancelWeeklySummary()

    fun cancelMonthlySummary()

    fun cancelAutoBackup()

    fun scheduleTransactionNotification(transactionId: Long)

    suspend fun rescheduleAllWork()

    suspend fun reconcileWorkOnStartup()
}
