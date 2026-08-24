package io.pm.finlight.workers

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionWriteDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

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
        unmockkAll()
    }

    @Test
    fun testDoWorkWithSurplus() =
        runTest {
            ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

            val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("goal_nudges_enabled", true).apply()

            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -1)
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = String.format("%02d", cal.get(Calendar.MONTH) + 1)
            prefs.edit().putFloat("overall_budget_${prevYear}_$prevMonth", 5000f).apply()

            // Insert required data
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(io.pm.finlight.Category(id = 1, name = "Salary", iconKey = "work", colorKey = "green"))
            db.categoryDao().insert(io.pm.finlight.Category(id = 2, name = "Food", iconKey = "food", colorKey = "red"))

            val activeGoal = Goal(id = 1, name = "Trip", targetAmount = 1000.0, accountId = 1, targetDate = null, notes = null, iconEmoji = "🎯", priority = 0)
            goalDao.insert(activeGoal)

            val calDate = Calendar.getInstance()
            calDate.add(Calendar.MONTH, -1)
            val txDate = calDate.timeInMillis

            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 5000.0, date = txDate, notes = "", description = "Salary", transactionType = TransactionType.INCOME)
            )
            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 2, amount = 2000.0, date = txDate, notes = "", description = "Food", transactionType = TransactionType.EXPENSE)
            )

            val worker = TestListenableWorkerBuilder<GoalSurplusWorker>(context).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val shadowNotificationManager = shadowOf(notificationManager)
            val notifications = shadowNotificationManager.allNotifications
            assertTrue("Notification should be sent", notifications.isNotEmpty())
        }

    @Test
    fun testDoWork_nudgesDisabled() =
        runTest {
            val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("goal_nudges_enabled", false).apply()

            val worker = TestListenableWorkerBuilder<GoalSurplusWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = shadowOf(notificationManager).allNotifications
            assertTrue("No notification should be sent when nudges are disabled", notifications.isEmpty())
        }

    @Test
    fun testDoWork_noBudgetSet() =
        runTest {
            val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("goal_nudges_enabled", true).apply()
            // No budget is set in prefs

            val worker = TestListenableWorkerBuilder<GoalSurplusWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = shadowOf(notificationManager).allNotifications
            assertTrue("No notification should be sent when no budget is set", notifications.isEmpty())
        }

    @Test
    fun testDoWork_noSurplus() =
        runTest {
            val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("goal_nudges_enabled", true).apply()

            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -1)
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = String.format("%02d", cal.get(Calendar.MONTH) + 1)
            // Set budget to a low amount
            prefs.edit().putFloat("overall_budget_${prevYear}_$prevMonth", 1000f).apply()

            // Insert expenses greater than budget
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(io.pm.finlight.Category(id = 1, name = "Food", iconKey = "food", colorKey = "red"))

            val calDate = Calendar.getInstance()
            calDate.add(Calendar.MONTH, -1)
            val txDate = calDate.timeInMillis

            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 1500.0, date = txDate, notes = "", description = "Food", transactionType = TransactionType.EXPENSE)
            )

            val worker = TestListenableWorkerBuilder<GoalSurplusWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = shadowOf(notificationManager).allNotifications
            assertTrue("No notification should be sent when expenses exceed budget", notifications.isEmpty())
        }

    @Test
    fun testDoWork_noActiveGoals() =
        runTest {
            val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("goal_nudges_enabled", true).apply()

            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -1)
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = String.format("%02d", cal.get(Calendar.MONTH) + 1)
            prefs.edit().putFloat("overall_budget_${prevYear}_$prevMonth", 5000f).apply()

            // Insert only income, no expenses, so there is surplus
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            db.categoryDao().insert(io.pm.finlight.Category(id = 1, name = "Salary", iconKey = "work", colorKey = "green"))

            val calDate = Calendar.getInstance()
            calDate.add(Calendar.MONTH, -1)
            val txDate = calDate.timeInMillis

            transactionWriteDao.insert(
                Transaction(accountId = 1, categoryId = 1, amount = 5000.0, date = txDate, notes = "", description = "Salary", transactionType = TransactionType.INCOME)
            )

            // DO NOT insert any active goals

            val worker = TestListenableWorkerBuilder<GoalSurplusWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = shadowOf(notificationManager).allNotifications
            // Worker only sends notification when surplus > 0 AND activeGoals.isNotEmpty().
            // Since no goals exist, no notification is sent.
            assertTrue("No notification should be sent when no active goals exist", notifications.isEmpty())
        }

    @Test
    fun testDoWork_noTransactionsInPreviousMonth() =
        runTest {
            val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("goal_nudges_enabled", true).apply()

            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -1)
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = String.format("%02d", cal.get(Calendar.MONTH) + 1)
            prefs.edit().putFloat("overall_budget_${prevYear}_$prevMonth", 1000f).apply()

            // Insert account and goal, but zero transactions
            db.accountDao().insert(Account(id = 1, name = "Cash", type = "Cash"))
            goalDao.insert(Goal(id = 1, name = "Trip", targetAmount = 5000.0, savedAmount = 100.0, targetDate = System.currentTimeMillis() + 100000000L, accountId = 1))

            val worker = TestListenableWorkerBuilder<GoalSurplusWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }
}
