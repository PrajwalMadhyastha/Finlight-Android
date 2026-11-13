package io.pm.finlight.utils

import android.content.Context
import io.pm.finlight.di.ServiceLocator

/**
 * Backward-compatible delegating wrapper around [IWorkScheduler].
 */
@Deprecated(
    message = "Use IWorkScheduler injected via ServiceLocator.provideWorkScheduler(context) instead.",
    replaceWith = ReplaceWith("ServiceLocator.provideWorkScheduler(context)"),
)
object ReminderManager {
    suspend fun rescheduleAllWork(context: Context) {
        ServiceLocator.provideWorkScheduler(context).rescheduleAllWork()
    }

    fun scheduleSmsRecoveryWorker(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleSmsRecoveryWorker()
    }

    fun cancelSmsRecoveryWorker(context: Context) {
        ServiceLocator.provideWorkScheduler(context).cancelSmsRecoveryWorker()
    }

    fun scheduleAutoBackup(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleAutoBackup()
    }

    fun cancelAutoBackup(context: Context) {
        ServiceLocator.provideWorkScheduler(context).cancelAutoBackup()
    }

    fun scheduleRecurringPatternWorker(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleRecurringPatternWorker()
    }

    fun scheduleRecurringTransactionWorker(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleRecurringTransactionWorker()
    }

    fun cancelRecurringTransactionWorkers(context: Context) {
        ServiceLocator.provideWorkScheduler(context).cancelRecurringTransactionWorkers()
    }

    suspend fun scheduleDailyReport(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleDailyReport()
    }

    fun cancelDailyReport(context: Context) {
        ServiceLocator.provideWorkScheduler(context).cancelDailyReport()
    }

    suspend fun scheduleWeeklySummary(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleWeeklySummary()
    }

    fun cancelWeeklySummary(context: Context) {
        ServiceLocator.provideWorkScheduler(context).cancelWeeklySummary()
    }

    suspend fun scheduleMonthlySummary(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleMonthlySummary()
    }

    fun cancelMonthlySummary(context: Context) {
        ServiceLocator.provideWorkScheduler(context).cancelMonthlySummary()
    }

    fun scheduleGoalSurplusWorker(context: Context) {
        ServiceLocator.provideWorkScheduler(context).scheduleGoalSurplusWorker()
    }
}
