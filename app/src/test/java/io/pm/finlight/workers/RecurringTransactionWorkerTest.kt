package io.pm.finlight.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var recurringTransactionDao: RecurringTransactionDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Enable the recurring transaction feature for tests
        context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("recurring_transactions_enabled", true)
            .apply()

        db = mockk()
        recurringTransactionDao = mockk()

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.recurringTransactionDao() } returns recurringTransactionDao

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)
        every { NotificationHelper.showRecurringTransactionDueNotification(any(), any()) } just runs
        coEvery { ReminderManager.scheduleRecurringTransactionWorker(any()) } returns Unit
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `doWork triggers notification for due rule and skips others`() = runTest {
        // Arrange
        val now = Calendar.getInstance().timeInMillis
        val yesterday = now - 86400000
        val tomorrow = now + 86400000

        val dueRule = RecurringTransaction(id = 1, description = "Due", amount = 10.0, transactionType = "expense", recurrenceInterval = "Daily", startDate = 0L, lastRunDate = yesterday, accountId = 1, categoryId = null)
        val notDueRule = RecurringTransaction(id = 2, description = "Not Due", amount = 20.0, transactionType = "expense", recurrenceInterval = "Daily", startDate = 0L, lastRunDate = now, accountId = 1, categoryId = null)
        val futureRule = RecurringTransaction(id = 3, description = "Future", amount = 30.0, transactionType = "expense", recurrenceInterval = "Daily", startDate = tomorrow, accountId = 1, categoryId = null)

        coEvery { recurringTransactionDao.getAllRulesList() } returns listOf(dueRule, notDueRule, futureRule)
        val capturedTxn = slot<PotentialTransaction>()

        val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { ReminderManager.scheduleRecurringTransactionWorker(context) }
        verify(exactly = 1) { NotificationHelper.showRecurringTransactionDueNotification(any(), capture(capturedTxn)) }

        assertEquals("Due", capturedTxn.captured.merchantName)
        assertEquals(1, capturedTxn.captured.sourceSmsId)
    }

    @Test
    fun `doWork returns success when no rules are due`() = runTest {
        // Arrange
        coEvery { recurringTransactionDao.getAllRulesList() } returns emptyList()
        val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { NotificationHelper.showRecurringTransactionDueNotification(any(), any()) }
        coVerify(exactly = 1) { ReminderManager.scheduleRecurringTransactionWorker(context) }
    }

    @Test
    fun `doWork returns retry on failure`() = runTest {
        // Arrange
        coEvery { recurringTransactionDao.getAllRulesList() } throws RuntimeException("DB Error")
        val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleRecurringTransactionWorker(context) }
    }
}