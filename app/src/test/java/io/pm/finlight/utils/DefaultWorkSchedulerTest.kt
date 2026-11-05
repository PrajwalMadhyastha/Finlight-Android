package io.pm.finlight.utils

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.pm.finlight.BackupWorker
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionNotificationWorker
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.workers.GoalSurplusWorker
import io.pm.finlight.workers.SmsCatchupWorker
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
class DefaultWorkSchedulerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var scheduler: DefaultWorkScheduler

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        runTest {
            context.financeSettingsDataStore.edit { it.clear() }
        }
        ServiceLocator.reset()
        settingsRepository = ServiceLocator.provideSettingsRepository(context)

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        scheduler =
            DefaultWorkScheduler(
                context = context,
                settingsRepository = settingsRepository,
                workManagerProvider = { workManager },
            )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        workManager.cancelAllWork()
        ServiceLocator.reset()
    }

    private fun checkUniqueWorkEnqueued(
        uniqueTag: String,
        targetWorker: Class<out ListenableWorker>,
    ) {
        val infos = workManager.getWorkInfosForUniqueWork(uniqueTag).get()
        assertEquals("Expected single work for tag: $uniqueTag", 1, infos.size)
        val info = infos.first()
        val isActiveState = info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
        assertTrue("Expected active state for $uniqueTag but was ${info.state}", isActiveState)
        assertTrue(
            "Expected tag matching worker class ${targetWorker.simpleName}",
            info.tags.any { it.contains(targetWorker.simpleName) },
        )
    }

    private fun checkUniqueWorkCancelledOrAbsent(uniqueTag: String) {
        val infos = workManager.getWorkInfosForUniqueWork(uniqueTag).get()
        val isInactive = infos.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.SUCCEEDED }
        assertTrue("Expected work for $uniqueTag to be cancelled or done", isInactive)
    }

    @Test
    fun `scheduleDailyReport enqueues worker when target time is later today`() =
        runTest {
            val later = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 2) }
            settingsRepository.saveDailyReportTime(
                later.get(Calendar.HOUR_OF_DAY),
                later.get(Calendar.MINUTE),
            )

            scheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE)

            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG,
                DailyReportWorker::class.java,
            )
        }

    @Test
    fun `scheduleDailyReport enqueues worker when target time was earlier today`() =
        runTest {
            val earlier = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -2) }
            settingsRepository.saveDailyReportTime(
                earlier.get(Calendar.HOUR_OF_DAY),
                earlier.get(Calendar.MINUTE),
            )

            scheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE)

            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG,
                DailyReportWorker::class.java,
            )
        }

    @Test
    fun `cancelDailyReport cancels pending report work`() =
        runTest {
            scheduler.scheduleDailyReport()
            scheduler.cancelDailyReport()

            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG)
        }

    @Test
    fun `scheduleWeeklySummary enqueues WeeklySummaryWorker for future day`() =
        runTest {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_WEEK, 2) }
            settingsRepository.saveWeeklyReportTime(
                cal.get(Calendar.DAY_OF_WEEK),
                14,
                30,
            )

            scheduler.scheduleWeeklySummary()

            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG,
                WeeklySummaryWorker::class.java,
            )
        }

    @Test
    fun `scheduleWeeklySummary enqueues WeeklySummaryWorker when time is earlier this week`() =
        runTest {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_WEEK, -2) }
            settingsRepository.saveWeeklyReportTime(
                cal.get(Calendar.DAY_OF_WEEK),
                10,
                0,
            )

            scheduler.scheduleWeeklySummary()

            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG,
                WeeklySummaryWorker::class.java,
            )
        }

    @Test
    fun `cancelWeeklySummary cancels weekly summary work`() =
        runTest {
            scheduler.scheduleWeeklySummary()
            scheduler.cancelWeeklySummary()

            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG)
        }

    @Test
    fun `scheduleMonthlySummary enqueues MonthlySummaryWorker with day clamp and next month roll`() =
        runTest {
            // Day 31 will be clamped by minOf to actual max days in month, and rolled if earlier
            settingsRepository.saveMonthlyReportTime(31, 8, 0)

            scheduler.scheduleMonthlySummary()

            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG,
                MonthlySummaryWorker::class.java,
            )
        }

    @Test
    fun `cancelMonthlySummary cancels monthly summary work`() =
        runTest {
            scheduler.scheduleMonthlySummary()
            scheduler.cancelMonthlySummary()

            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG)
        }

    @Test
    fun `scheduleAutoBackup enqueues periodic BackupWorker`() {
        scheduler.scheduleAutoBackup(ExistingPeriodicWorkPolicy.UPDATE)

        checkUniqueWorkEnqueued(
            DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG,
            BackupWorker::class.java,
        )
    }

    @Test
    fun `cancelAutoBackup cancels periodic auto backup`() {
        scheduler.scheduleAutoBackup()
        scheduler.cancelAutoBackup()

        checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG)
    }

    @Test
    fun `scheduleSmsRecoveryWorker enqueues SmsCatchupWorker with battery requirement`() {
        scheduler.scheduleSmsRecoveryWorker(ExistingPeriodicWorkPolicy.UPDATE)

        checkUniqueWorkEnqueued(
            DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG,
            SmsCatchupWorker::class.java,
        )
        val info = workManager.getWorkInfosForUniqueWork(DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG).get().first()
        assertTrue("Sms recovery must enforce battery not low", info.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `cancelSmsRecoveryWorker cancels sms catchup work`() {
        scheduler.scheduleSmsRecoveryWorker()
        scheduler.cancelSmsRecoveryWorker()

        checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG)
    }

    @Test
    fun `scheduleGoalSurplusWorker schedules with forceNextMonth true and false`() {
        scheduler.scheduleGoalSurplusWorker(forceNextMonth = false)
        checkUniqueWorkEnqueued(
            DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG,
            GoalSurplusWorker::class.java,
        )

        scheduler.scheduleGoalSurplusWorker(forceNextMonth = true)
        checkUniqueWorkEnqueued(
            DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG,
            GoalSurplusWorker::class.java,
        )
    }

    @Test
    fun `scheduleRecurringTransactionWorker schedules RecurringTransactionWorker`() {
        scheduler.scheduleRecurringTransactionWorker()

        checkUniqueWorkEnqueued(
            DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG,
            RecurringTransactionWorker::class.java,
        )
    }

    @Test
    fun `scheduleRecurringPatternWorker schedules RecurringPatternWorker`() {
        scheduler.scheduleRecurringPatternWorker()

        checkUniqueWorkEnqueued(
            DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG,
            RecurringPatternWorker::class.java,
        )
    }

    @Test
    fun `cancelRecurringTransactionWorkers cancels transaction and pattern workers`() {
        scheduler.scheduleRecurringTransactionWorker()
        scheduler.scheduleRecurringPatternWorker()

        scheduler.cancelRecurringTransactionWorkers()

        checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG)
        checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG)
    }

    @Test
    fun `scheduleTransactionNotification enqueues worker with transactionId parameter`() {
        val txId = 4289L
        scheduler.scheduleTransactionNotification(txId)

        val uniqueName = "tx_notif_$txId"
        checkUniqueWorkEnqueued(uniqueName, TransactionNotificationWorker::class.java)

        val info = workManager.getWorkInfosForUniqueWork(uniqueName).get().first()
        assertTrue(info.tags.any { it.contains("TransactionNotificationWorker") })
    }

    @Test
    fun `reconcileWorkOnStartup catches uninitialized WorkManager gracefully`() =
        runTest {
            val uninitializedScheduler =
                DefaultWorkScheduler(
                    context = context,
                    settingsRepository = settingsRepository,
                    workManagerProvider = { throw IllegalStateException("WorkManager not initialized") },
                )

            // Must not throw IllegalStateException
            uninitializedScheduler.reconcileWorkOnStartup()
        }

    @Test
    fun `reconcileWorkOnStartup reconciles all 8 workers when enabled`() =
        runTest {
            settingsRepository.saveDailyReportEnabled(true)
            settingsRepository.saveWeeklySummaryEnabled(true)
            settingsRepository.saveMonthlySummaryEnabled(true)
            settingsRepository.saveAutoBackupEnabled(true)
            settingsRepository.saveRecurringTransactionsEnabled(true)

            scheduler.reconcileWorkOnStartup()

            checkUniqueWorkEnqueued(DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG, DailyReportWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG, WeeklySummaryWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG, MonthlySummaryWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG, BackupWorker::class.java)
            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG,
                RecurringTransactionWorker::class.java,
            )
            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG,
                RecurringPatternWorker::class.java,
            )
            checkUniqueWorkEnqueued(DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG, GoalSurplusWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG, SmsCatchupWorker::class.java)
        }

    @Test
    fun `reconcileWorkOnStartup skips disabled workers but keeps essential workers`() =
        runTest {
            settingsRepository.saveDailyReportEnabled(false)
            settingsRepository.saveWeeklySummaryEnabled(false)
            settingsRepository.saveMonthlySummaryEnabled(false)
            settingsRepository.saveAutoBackupEnabled(false)
            settingsRepository.saveRecurringTransactionsEnabled(false)

            scheduler.reconcileWorkOnStartup()

            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG)
            // Goal surplus and SMS recovery are always reconciled under KEEP
            checkUniqueWorkEnqueued(DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG, GoalSurplusWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG, SmsCatchupWorker::class.java)
        }

    @Test
    fun `rescheduleAllWork schedules all enabled features`() =
        runTest {
            settingsRepository.saveDailyReportEnabled(true)
            settingsRepository.saveWeeklySummaryEnabled(true)
            settingsRepository.saveMonthlySummaryEnabled(true)
            settingsRepository.saveAutoBackupEnabled(true)
            settingsRepository.saveRecurringTransactionsEnabled(true)

            scheduler.rescheduleAllWork()

            checkUniqueWorkEnqueued(DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG, DailyReportWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG, WeeklySummaryWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG, MonthlySummaryWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG, BackupWorker::class.java)
            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG,
                RecurringTransactionWorker::class.java,
            )
            checkUniqueWorkEnqueued(
                DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG,
                RecurringPatternWorker::class.java,
            )
            checkUniqueWorkEnqueued(DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG, GoalSurplusWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG, SmsCatchupWorker::class.java)
        }

    @Test
    fun `rescheduleAllWork explicitly cancels all disabled features`() =
        runTest {
            // First enqueue all
            settingsRepository.saveDailyReportEnabled(true)
            settingsRepository.saveWeeklySummaryEnabled(true)
            settingsRepository.saveMonthlySummaryEnabled(true)
            settingsRepository.saveAutoBackupEnabled(true)
            settingsRepository.saveRecurringTransactionsEnabled(true)
            scheduler.rescheduleAllWork()

            // Now disable all and reschedule
            settingsRepository.saveDailyReportEnabled(false)
            settingsRepository.saveWeeklySummaryEnabled(false)
            settingsRepository.saveMonthlySummaryEnabled(false)
            settingsRepository.saveAutoBackupEnabled(false)
            settingsRepository.saveRecurringTransactionsEnabled(false)
            scheduler.rescheduleAllWork()

            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG)
            checkUniqueWorkCancelledOrAbsent(DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG)
            // Goal surplus and SMS recovery remain active
            checkUniqueWorkEnqueued(DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG, GoalSurplusWorker::class.java)
            checkUniqueWorkEnqueued(DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG, SmsCatchupWorker::class.java)
        }
}
