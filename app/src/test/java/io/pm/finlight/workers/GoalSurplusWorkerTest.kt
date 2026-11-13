package io.pm.finlight.workers

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Goal
import io.pm.finlight.GoalDao
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.IWorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApplication::class)
class GoalSurplusWorkerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionWriteDao: TransactionWriteDao
    private lateinit var goalDao: GoalDao
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var workScheduler: IWorkScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            context.financeSettingsDataStore.edit { it.clear() }
        }
        ServiceLocator.reset()
        settingsRepository = ServiceLocator.provideSettingsRepository(context)

        workScheduler = mockk(relaxed = true)
        ServiceLocator.setWorkScheduler(workScheduler)

        // Use an unencrypted in-memory database!
        db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        transactionWriteDao = db.transactionWriteDao()
        goalDao = db.goalDao()

        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db
    }

    @After
    fun teardown() {
        db.close()
        ServiceLocator.reset()
        unmockkAll()
    }

    private fun getPreviousYearAndMonth(): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        return Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private fun getPostedNotifications(): List<Notification> {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return shadowOf(notificationManager).allNotifications
    }

    private fun getPreviousMonthTimestamp(): Long {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        return cal.timeInMillis
    }

    @Test
    fun testDoWorkWithSurplus() =
        runTest {
            ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

            val (prevYear, prevMonth) = getPreviousYearAndMonth()

            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 5000f)

            // Insert required data
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(Category(id = 1, name = "Salary", iconKey = "work", colorKey = "green"))
            db.categoryDao().insert(Category(id = 2, name = "Food", iconKey = "food", colorKey = "red"))

            val activeGoal = Goal(id = 1, name = "Trip", targetAmount = 1000.0, accountId = 1, targetDate = null, notes = null, iconEmoji = "🎯", priority = 0)
            goalDao.insert(activeGoal)

            val txDate = getPreviousMonthTimestamp()

            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 5000.0, date = txDate, notes = "", description = "Salary", transactionType = TransactionType.INCOME)
            )
            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 2, amount = 2000.0, date = txDate, notes = "", description = "Food", transactionType = TransactionType.EXPENSE)
            )

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue("Notification should be sent", getPostedNotifications().isNotEmpty())

            val lastPrompt = settingsRepository.getLastSurplusPrompt().first()
            assertEquals(Pair(prevYear, prevMonth), lastPrompt)

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_alreadyPromptedThisMonth_skipsDuplicatePrompt() =
        runTest {
            ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

            val (prevYear, prevMonth) = getPreviousYearAndMonth()

            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 5000f)
            settingsRepository.saveLastSurplusPrompt(prevYear, prevMonth)

            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(Category(id = 1, name = "Salary", iconKey = "work", colorKey = "green"))
            goalDao.insert(Goal(id = 1, name = "Trip", targetAmount = 1000.0, accountId = 1, targetDate = null, notes = null, iconEmoji = "🎯", priority = 0))

            val txDate = getPreviousMonthTimestamp()
            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 5000.0, date = txDate, notes = "", description = "Salary", transactionType = TransactionType.INCOME)
            )

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue("No duplicate notification should be sent if already prompted", getPostedNotifications().isEmpty())

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_nudgesDisabled() =
        runTest {
            settingsRepository.saveGoalNudgesEnabled(false)

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue("No notification should be sent when nudges are disabled", getPostedNotifications().isEmpty())

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_noBudgetSet() =
        runTest {
            settingsRepository.saveGoalNudgesEnabled(true)
            // No budget is set in prefs

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue("No notification should be sent when no budget is set", getPostedNotifications().isEmpty())

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_noSurplus() =
        runTest {
            val (prevYear, prevMonth) = getPreviousYearAndMonth()

            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 1000f)

            // Insert expenses greater than budget
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(Category(id = 1, name = "Food", iconKey = "food", colorKey = "red"))

            val txDate = getPreviousMonthTimestamp()

            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 1500.0, date = txDate, notes = "", description = "Food", transactionType = TransactionType.EXPENSE)
            )

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue("No notification should be sent when expenses exceed budget", getPostedNotifications().isEmpty())

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_noActiveGoals() =
        runTest {
            val (prevYear, prevMonth) = getPreviousYearAndMonth()

            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 5000f)

            // Insert only income, no expenses, so there is surplus
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(Category(id = 1, name = "Salary", iconKey = "work", colorKey = "green"))

            val txDate = getPreviousMonthTimestamp()

            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 5000.0, date = txDate, notes = "", description = "Salary", transactionType = TransactionType.INCOME)
            )

            // DO NOT insert any active goals

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue("No notification should be sent when no active goals exist", getPostedNotifications().isEmpty())

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_noTransactionsInPreviousMonth() =
        runTest {
            val (prevYear, prevMonth) = getPreviousYearAndMonth()

            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 1000f)

            // Insert account and goal, but zero transactions
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            goalDao.insert(Goal(id = 1, name = "Trip", targetAmount = 5000.0, savedAmount = 100.0, targetDate = System.currentTimeMillis() + 100000000L, accountId = 1))

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)

            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_retryOnNonFinalAttempt_preservesBackoffWithoutRescheduling() =
        runTest {
            val (prevYear, prevMonth) = getPreviousYearAndMonth()
            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 1000f)

            val mockTxnRepo = mockk<ITransactionRepository>()
            every { mockTxnRepo.getFinancialSummaryForRangeFlow(any(), any()) } returns
                flow { throw RuntimeException("Simulated failure") }
            ServiceLocator.setTransactionRepository(mockTxnRepo)

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context, runAttemptCount = 0)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(exactly = 0) {
                workScheduler.scheduleGoalSurplusWorker(any(), any())
            }
        }

    @Test
    fun testDoWork_failureOnFinalAttempt_reschedulesNextMonth() =
        runTest {
            val (prevYear, prevMonth) = getPreviousYearAndMonth()
            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 1000f)

            val mockTxnRepo = mockk<ITransactionRepository>()
            every { mockTxnRepo.getFinancialSummaryForRangeFlow(any(), any()) } returns
                flow { throw RuntimeException("Simulated persistent failure") }
            ServiceLocator.setTransactionRepository(mockTxnRepo)

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context, runAttemptCount = 2)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            verify(exactly = 1) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
        }

    @Test
    fun testDoWork_rethrowsCancellationException() =
        runTest {
            val (prevYear, prevMonth) = getPreviousYearAndMonth()
            settingsRepository.saveGoalNudgesEnabled(true)
            settingsRepository.saveOverallBudgetForMonth(prevYear, prevMonth, 1000f)

            val mockTxnRepo = mockk<ITransactionRepository>()
            every { mockTxnRepo.getFinancialSummaryForRangeFlow(any(), any()) } returns
                flow { throw CancellationException("Worker cancelled") }
            ServiceLocator.setTransactionRepository(mockTxnRepo)

            val worker = SummaryWorkerTestFixtures.createWorker<GoalSurplusWorker>(context, runAttemptCount = 0)

            assertThrows(CancellationException::class.java) {
                runBlocking {
                    worker.doWork()
                }
            }

            verify(exactly = 0) {
                workScheduler.scheduleGoalSurplusWorker(any(), any())
            }
        }
}
