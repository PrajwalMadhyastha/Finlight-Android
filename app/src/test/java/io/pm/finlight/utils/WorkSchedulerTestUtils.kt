package io.pm.finlight.utils

import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.pm.finlight.BackupWorker
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.workers.GoalSurplusWorker
import io.pm.finlight.workers.SmsCatchupWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object WorkSchedulerTestUtils {
    fun assertWorkEnqueued(
        workManager: WorkManager,
        uniqueWorkName: String,
        workerClass: Class<out ListenableWorker>,
    ) {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertEquals("Work request should be enqueued for $uniqueWorkName", 1, workInfos.size)
        val workInfo = workInfos[0]
        val isActive = workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        assertTrue("Work state should be ENQUEUED or RUNNING for $uniqueWorkName, but was ${workInfo.state}", isActive)
        assertTrue(
            "Worker class name should contain ${workerClass.simpleName}",
            workInfo.tags.any { it.contains(workerClass.simpleName) },
        )
    }

    fun assertWorkCancelled(
        workManager: WorkManager,
        uniqueWorkName: String,
    ) {
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        val isInactive =
            workInfos.all {
                it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.SUCCEEDED
            }
        assertTrue("Work request for $uniqueWorkName should be cancelled or finished", isInactive)
    }

    fun assertAllStandardWorkersEnqueued(workManager: WorkManager) {
        assertWorkEnqueued(workManager, DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG, DailyReportWorker::class.java)
        assertWorkEnqueued(workManager, DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG, WeeklySummaryWorker::class.java)
        assertWorkEnqueued(workManager, DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG, MonthlySummaryWorker::class.java)
        assertWorkEnqueued(workManager, DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG, BackupWorker::class.java)
        assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG,
            RecurringTransactionWorker::class.java,
        )
        assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG,
            RecurringPatternWorker::class.java,
        )
        assertWorkEnqueued(workManager, DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG, GoalSurplusWorker::class.java)
        assertWorkEnqueued(workManager, DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG, SmsCatchupWorker::class.java)
    }

    fun assertConfigurableWorkersCancelled(workManager: WorkManager) {
        assertWorkCancelled(workManager, DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG)
        assertWorkCancelled(workManager, DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG)
        assertWorkCancelled(workManager, DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG)
        assertWorkCancelled(workManager, DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG)
        assertWorkCancelled(workManager, DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG)
        assertWorkCancelled(workManager, DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG)
    }

    suspend fun configureAllStandardSettings(
        settingsRepository: ISettingsRepository,
        enabled: Boolean,
    ) {
        settingsRepository.saveDailyReportEnabled(enabled)
        settingsRepository.saveWeeklySummaryEnabled(enabled)
        settingsRepository.saveMonthlySummaryEnabled(enabled)
        settingsRepository.saveAutoBackupEnabled(enabled)
        settingsRepository.saveRecurringTransactionsEnabled(enabled)
    }
}
