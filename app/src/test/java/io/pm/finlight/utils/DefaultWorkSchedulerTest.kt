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
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.pm.finlight.BackupWorker
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.RecurringPatternWorker
import io.pm.finlight.RecurringTransactionWorker
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionNotificationWorker
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.di.ServiceLocator
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

    private class SettableTimeProvider(var current: Calendar) : TimeProvider {
        override fun now(): Calendar = current.clone() as Calendar
    }

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(any()) } returns true
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
        unmockkObject(DataExportService)
        ServiceLocator.reset()
    }

    @Test
    fun `calculateNextGoalSurplusRun rolls over to January next year when scheduled in December`() {
        val decDate =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 15)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        val nextRun = DefaultWorkScheduler.calculateNextGoalSurplusRun(decDate, forceNextMonth = true)
        assertEquals(2027, nextRun.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, nextRun.get(Calendar.MONTH))
        assertEquals(1, nextRun.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextRun.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextRun.get(Calendar.MINUTE))
        assertEquals(0, nextRun.get(Calendar.SECOND))
        assertEquals(0, nextRun.get(Calendar.MILLISECOND))
    }

    @Test
    fun `calculateNextGoalSurplusRun runs today when on 1st before 9 AM and forceNextMonth is false`() {
        val morningFirst =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        val nextRun = DefaultWorkScheduler.calculateNextGoalSurplusRun(morningFirst, forceNextMonth = false)
        assertEquals(2026, nextRun.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, nextRun.get(Calendar.MONTH))
        assertEquals(1, nextRun.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextRun.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `calculateNextGoalSurplusRun advances month when on 1st before 9 AM and forceNextMonth is true`() {
        val morningFirst =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        val nextRun = DefaultWorkScheduler.calculateNextGoalSurplusRun(morningFirst, forceNextMonth = true)
        assertEquals(2027, nextRun.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, nextRun.get(Calendar.MONTH))
        assertEquals(1, nextRun.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextRun.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `calculateNextGoalSurplusRun advances month when after 9 AM and forceNextMonth is false`() {
        val midMonth =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.JUNE)
                set(Calendar.DAY_OF_MONTH, 15)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        val nextRun = DefaultWorkScheduler.calculateNextGoalSurplusRun(midMonth, forceNextMonth = false)
        assertEquals(2026, nextRun.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, nextRun.get(Calendar.MONTH))
        assertEquals(1, nextRun.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextRun.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `calculateNextRecurringTransactionRun schedules today when before 2 AM and tomorrow when after 2 AM`() {
        val beforeTwoAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val runToday = DefaultWorkScheduler.calculateNextRecurringTransactionRun(beforeTwoAm)
        assertEquals(beforeTwoAm.get(Calendar.DAY_OF_YEAR), runToday.get(Calendar.DAY_OF_YEAR))
        assertEquals(2, runToday.get(Calendar.HOUR_OF_DAY))

        val afterTwoAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val runTomorrow = DefaultWorkScheduler.calculateNextRecurringTransactionRun(afterTwoAm)
        val expectedDay =
            if (afterTwoAm.get(Calendar.DAY_OF_YEAR) == afterTwoAm.getActualMaximum(Calendar.DAY_OF_YEAR)) {
                1
            } else {
                afterTwoAm.get(Calendar.DAY_OF_YEAR) + 1
            }
        assertEquals(expectedDay, runTomorrow.get(Calendar.DAY_OF_YEAR))
        assertEquals(2, runTomorrow.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `calculateNextRecurringPatternRun schedules today when before 3 AM and tomorrow when after 3 AM`() {
        val beforeThreeAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val runToday = DefaultWorkScheduler.calculateNextRecurringPatternRun(beforeThreeAm)
        assertEquals(beforeThreeAm.get(Calendar.DAY_OF_YEAR), runToday.get(Calendar.DAY_OF_YEAR))
        assertEquals(3, runToday.get(Calendar.HOUR_OF_DAY))

        val afterThreeAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 4)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val runTomorrow = DefaultWorkScheduler.calculateNextRecurringPatternRun(afterThreeAm)
        val expectedDay =
            if (afterThreeAm.get(Calendar.DAY_OF_YEAR) == afterThreeAm.getActualMaximum(Calendar.DAY_OF_YEAR)) {
                1
            } else {
                afterThreeAm.get(Calendar.DAY_OF_YEAR) + 1
            }
        assertEquals(expectedDay, runTomorrow.get(Calendar.DAY_OF_YEAR))
        assertEquals(3, runTomorrow.get(Calendar.HOUR_OF_DAY))
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

            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
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

            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG,
                DailyReportWorker::class.java,
            )
        }

    @Test
    fun `cancelDailyReport cancels pending report work`() =
        runTest {
            scheduler.scheduleDailyReport()
            scheduler.cancelDailyReport()

            WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.DAILY_EXPENSE_REPORT_WORK_TAG)
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

            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
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

            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG,
                WeeklySummaryWorker::class.java,
            )
        }

    @Test
    fun `cancelWeeklySummary cancels weekly summary work`() =
        runTest {
            scheduler.scheduleWeeklySummary()
            scheduler.cancelWeeklySummary()

            WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.WEEKLY_SUMMARY_WORK_TAG)
        }

    @Test
    fun `scheduleMonthlySummary enqueues MonthlySummaryWorker when scheduled earlier this month`() =
        runTest {
            // Day 31 will be clamped by minOf to actual max days in month, and rolled if earlier
            settingsRepository.saveMonthlyReportTime(31, 8, 0)

            scheduler.scheduleMonthlySummary()

            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG,
                MonthlySummaryWorker::class.java,
            )
        }

    @Test
    fun `scheduleMonthlySummary enqueues MonthlySummaryWorker when scheduled later this month`() =
        runTest {
            val futureTime =
                Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 8)
                }
            val customScheduler =
                DefaultWorkScheduler(
                    context = context,
                    settingsRepository = settingsRepository,
                    workManagerProvider = { workManager },
                    timeProvider = SettableTimeProvider(futureTime),
                )
            settingsRepository.saveMonthlyReportTime(20, 10, 0)

            customScheduler.scheduleMonthlySummary()

            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG,
                MonthlySummaryWorker::class.java,
            )
        }

    @Test
    fun `cancelMonthlySummary cancels monthly summary work`() =
        runTest {
            scheduler.scheduleMonthlySummary()
            scheduler.cancelMonthlySummary()

            WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.MONTHLY_SUMMARY_WORK_TAG)
        }

    @Test
    fun `scheduleAutoBackup enqueues periodic BackupWorker`() {
        scheduler.scheduleAutoBackup(ExistingPeriodicWorkPolicy.UPDATE)

        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG,
            BackupWorker::class.java,
        )
    }

    @Test
    fun `cancelAutoBackup cancels periodic auto backup`() {
        scheduler.scheduleAutoBackup()
        scheduler.cancelAutoBackup()

        WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.AUTO_BACKUP_WORK_TAG)
    }

    @Test
    fun `scheduleSmsRecoveryWorker enqueues SmsCatchupWorker with battery requirement`() {
        scheduler.scheduleSmsRecoveryWorker(ExistingPeriodicWorkPolicy.UPDATE)

        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
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

        WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG)
    }

    @Test
    fun `scheduleGoalSurplusWorker schedules with custom time provider before and after threshold`() {
        val morningFirst =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val customScheduler =
            DefaultWorkScheduler(
                context = context,
                settingsRepository = settingsRepository,
                workManagerProvider = { workManager },
                timeProvider = SettableTimeProvider(morningFirst),
            )

        customScheduler.scheduleGoalSurplusWorker(forceNextMonth = false)
        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG,
            GoalSurplusWorker::class.java,
        )

        customScheduler.scheduleGoalSurplusWorker(forceNextMonth = true)
        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG,
            GoalSurplusWorker::class.java,
        )
    }

    @Test
    fun `scheduleRecurringTransactionWorker schedules before 2 AM and after 2 AM`() {
        val beforeTwoAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
        val earlyScheduler =
            DefaultWorkScheduler(
                context = context,
                settingsRepository = settingsRepository,
                workManagerProvider = { workManager },
                timeProvider = SettableTimeProvider(beforeTwoAm),
            )
        earlyScheduler.scheduleRecurringTransactionWorker()
        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG,
            RecurringTransactionWorker::class.java,
        )

        val afterTwoAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
            }
        val lateScheduler =
            DefaultWorkScheduler(
                context = context,
                settingsRepository = settingsRepository,
                workManagerProvider = { workManager },
                timeProvider = SettableTimeProvider(afterTwoAm),
            )
        lateScheduler.scheduleRecurringTransactionWorker()
        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG,
            RecurringTransactionWorker::class.java,
        )
    }

    @Test
    fun `scheduleRecurringPatternWorker schedules before 3 AM and after 3 AM`() {
        val beforeThreeAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
            }
        val earlyScheduler =
            DefaultWorkScheduler(
                context = context,
                settingsRepository = settingsRepository,
                workManagerProvider = { workManager },
                timeProvider = SettableTimeProvider(beforeThreeAm),
            )
        earlyScheduler.scheduleRecurringPatternWorker()
        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG,
            RecurringPatternWorker::class.java,
        )

        val afterThreeAm =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 4)
                set(Calendar.MINUTE, 0)
            }
        val lateScheduler =
            DefaultWorkScheduler(
                context = context,
                settingsRepository = settingsRepository,
                workManagerProvider = { workManager },
                timeProvider = SettableTimeProvider(afterThreeAm),
            )
        lateScheduler.scheduleRecurringPatternWorker()
        WorkSchedulerTestUtils.assertWorkEnqueued(
            workManager,
            DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG,
            RecurringPatternWorker::class.java,
        )
    }

    @Test
    fun `cancelRecurringTransactionWorkers cancels transaction and pattern workers`() {
        scheduler.scheduleRecurringTransactionWorker()
        scheduler.scheduleRecurringPatternWorker()

        scheduler.cancelRecurringTransactionWorkers()

        WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.RECURRING_TRANSACTION_WORK_TAG)
        WorkSchedulerTestUtils.assertWorkCancelled(workManager, DefaultWorkScheduler.RECURRING_PATTERN_WORK_TAG)
    }

    @Test
    fun `scheduleTransactionNotification enqueues worker with transactionId parameter`() {
        val txId = 4289L
        scheduler.scheduleTransactionNotification(txId)

        val uniqueName = "tx_notif_$txId"
        WorkSchedulerTestUtils.assertWorkEnqueued(workManager, uniqueName, TransactionNotificationWorker::class.java)

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
            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = true)
            scheduler.reconcileWorkOnStartup()
            WorkSchedulerTestUtils.assertAllStandardWorkersEnqueued(workManager)
        }

    @Test
    fun `reconcileWorkOnStartup skips disabled workers but keeps essential workers`() =
        runTest {
            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = false)
            scheduler.reconcileWorkOnStartup()
            WorkSchedulerTestUtils.assertConfigurableWorkersCancelled(workManager)
            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG,
                GoalSurplusWorker::class.java,
            )
            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG,
                SmsCatchupWorker::class.java,
            )
        }

    @Test
    fun `rescheduleAllWork schedules all enabled features`() =
        runTest {
            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = true)
            scheduler.rescheduleAllWork()
            WorkSchedulerTestUtils.assertAllStandardWorkersEnqueued(workManager)
        }

    @Test
    fun `rescheduleAllWork explicitly cancels all disabled features`() =
        runTest {
            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = true)
            scheduler.rescheduleAllWork()

            WorkSchedulerTestUtils.configureAllStandardSettings(settingsRepository, enabled = false)
            scheduler.rescheduleAllWork()

            WorkSchedulerTestUtils.assertConfigurableWorkersCancelled(workManager)
            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.GOAL_SURPLUS_WORK_TAG,
                GoalSurplusWorker::class.java,
            )
            WorkSchedulerTestUtils.assertWorkEnqueued(
                workManager,
                DefaultWorkScheduler.SMS_CATCHUP_WORK_TAG,
                SmsCatchupWorker::class.java,
            )
        }
}
