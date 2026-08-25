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
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
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
    private lateinit var transactionQueryDao: TransactionQueryDao
    private lateinit var transactionWriteDao: TransactionWriteDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Enable the recurring transaction feature for tests
        context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("recurring_transactions_enabled", true)
            .apply()

        db = mockk(relaxed = true)
        recurringTransactionDao = mockk(relaxed = true)

        mockkObject(AppDatabase)
        transactionQueryDao = mockk<TransactionQueryDao>(relaxed = true)
        transactionWriteDao = mockk<TransactionWriteDao>(relaxed = true)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.recurringTransactionDao() } returns recurringTransactionDao
        every { db.transactionQueryDao() } returns transactionQueryDao
        every { db.transactionWriteDao() } returns transactionWriteDao
        coEvery { transactionWriteDao.insert(any<Transaction>()) } returns 101L
        coEvery { transactionQueryDao.getPendingTransactionForRule(any()) } returns null

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)
        every { NotificationHelper.showRecurringTransactionDueNotification(any(), any(), any()) } just runs
        coEvery { ReminderManager.scheduleRecurringTransactionWorker(any()) } returns Unit
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `doWork triggers notification for due rule and skips others`() =
        runTest {
            // Arrange
            val now = Calendar.getInstance().timeInMillis
            val yesterday = now - 86400000
            val tomorrow = now + 86400000

            val dueRule =
                RecurringTransaction(id = 1, description = "Due", amount = 10.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Daily", startDate = 0L, lastRunDate = yesterday, accountId = 1, categoryId = null)
            val notDueRule =
                RecurringTransaction(id = 2, description = "Not Due", amount = 20.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Daily", startDate = 0L, lastRunDate = now, accountId = 1, categoryId = null)
            val futureRule =
                RecurringTransaction(
                    id = 3,
                    description = "Future",
                    amount = 30.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Daily",
                    startDate = tomorrow,
                    accountId = 1,
                    categoryId = null,
                )

            coEvery { recurringTransactionDao.getAllRulesList() } returns listOf(dueRule, notDueRule, futureRule)
            val capturedRule = slot<RecurringTransaction>()
            val capturedDraftId = slot<Int>()

            val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { ReminderManager.scheduleRecurringTransactionWorker(context) }
            verify(exactly = 1) { NotificationHelper.showRecurringTransactionDueNotification(any(), capture(capturedRule), capture(capturedDraftId)) }

            assertEquals("Due", capturedRule.captured.description)
            assertEquals(1, capturedRule.captured.id)
            assertEquals(101, capturedDraftId.captured)
        }

    @Test
    fun `doWork returns success when no rules are due`() =
        runTest {
            // Arrange
            coEvery { recurringTransactionDao.getAllRulesList() } returns emptyList()
            val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showRecurringTransactionDueNotification(any(), any(), any()) }
            coVerify(exactly = 1) { ReminderManager.scheduleRecurringTransactionWorker(context) }
        }

    @Test
    fun `doWork skips expired rules`() =
        runTest {
            val now = System.currentTimeMillis()
            val past = now - 86400000
            val rule = RecurringTransaction(id = 1, description = "Expired", amount = 10.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Daily", startDate = 0L, endDate = past, lastRunDate = null, accountId = 1, categoryId = 1)

            coEvery { recurringTransactionDao.getAllRulesList() } returns listOf(rule)
            val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()

            worker.doWork()

            coVerify(exactly = 0) { transactionWriteDao.insert(any<Transaction>()) }
        }

    @Test
    fun `doWork skips rule if pending draft already exists`() =
        runTest {
            val now = System.currentTimeMillis()
            val rule = RecurringTransaction(id = 1, description = "Draft Exists", amount = 10.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Daily", startDate = 0L, lastRunDate = null, accountId = 1, categoryId = 1)
            val draft = Transaction(id = 100, description = "Draft", amount = 10.0, transactionType = TransactionType.EXPENSE, date = now, accountId = 1, categoryId = 1, notes = null, status = TransactionStatus.PENDING)

            coEvery { recurringTransactionDao.getAllRulesList() } returns listOf(rule)
            coEvery { transactionQueryDao.getPendingTransactionForRule(1) } returns draft

            val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()
            worker.doWork()

            coVerify(exactly = 0) { transactionWriteDao.insert(any<Transaction>()) }
        }

    @Test
    fun `doWork creates CONFIRMED transaction and updates lastRunDate if autoApprove is true`() =
        runTest {
            val now = System.currentTimeMillis()
            val rule = RecurringTransaction(id = 1, description = "Auto Approve", amount = 10.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Daily", startDate = 0L, lastRunDate = null, accountId = 1, categoryId = 1, autoApprove = true)

            coEvery { recurringTransactionDao.getAllRulesList() } returns listOf(rule)
            val capturedTxn = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(capturedTxn)) } returns 101L
            every { NotificationHelper.showAutoApprovedPaymentNotification(any(), any()) } just runs

            val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context).build()
            worker.doWork()

            coVerify(exactly = 1) { transactionWriteDao.insert(any<Transaction>()) }
            coVerify(exactly = 1) { recurringTransactionDao.updateLastRunDate(1, any()) }
            verify(exactly = 1) { NotificationHelper.showAutoApprovedPaymentNotification(any(), any()) }

            assertEquals(TransactionStatus.CONFIRMED, capturedTxn.captured.status)
            assertEquals(TransactionType.EXPENSE, capturedTxn.captured.transactionType)
            assertEquals("Auto Approve", capturedTxn.captured.description)
        }

    @Test
    fun `doWork returns retry on failure`() =
        runTest {
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
