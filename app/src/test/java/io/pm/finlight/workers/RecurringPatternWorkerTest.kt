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
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringPatternWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var patternDao: RecurringPatternDao
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
        transactionDao = mockk()
        patternDao = mockk()
        recurringTransactionDao = mockk()

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao
        every { db.recurringPatternDao() } returns patternDao
        every { db.recurringTransactionDao() } returns recurringTransactionDao

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)
        every { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) } just runs
        coEvery { ReminderManager.scheduleRecurringPatternWorker(any()) } returns Unit
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `doWork correctly detects monthly pattern and creates rule`() = runTest {
        // Arrange
        val signature = "monthly_sig"
        val pattern = RecurringPattern(signature, "Netflix", 149.0, "expense", 1, 1, 4, 0L, 0L)
        val transactions = listOf(
            Transaction(id = 1, smsSignature = signature, date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90), description = "", amount = 0.0, accountId = 0, categoryId = 0, notes = null),
            Transaction(id = 2, smsSignature = signature, date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60), description = "", amount = 0.0, accountId = 0, categoryId = 0, notes = null),
            Transaction(id = 3, smsSignature = signature, date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30), description = "", amount = 0.0, accountId = 0, categoryId = 0, notes = null)
        )

        coEvery { transactionDao.getTransactionsWithSignatureSince(any()) } returns emptyList()
        coEvery { patternDao.getAllPatterns() } returns listOf(pattern)
        coEvery { transactionDao.getTransactionsBySignature(signature) } returns transactions
        coEvery { recurringTransactionDao.insert(any()) } returns 1L
        coEvery { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) } just runs
        coEvery { patternDao.deleteBySignature(signature) } just runs

        val worker = TestListenableWorkerBuilder<RecurringPatternWorker>(context).build()
        val ruleCaptor = slot<RecurringTransaction>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { recurringTransactionDao.insert(capture(ruleCaptor)) }
        assertEquals("Monthly", ruleCaptor.captured.recurrenceInterval)
        coVerify { NotificationHelper.showRecurringPatternDetectedNotification(any(), any()) }
        coVerify { patternDao.deleteBySignature(signature) }
        coVerify { ReminderManager.scheduleRecurringPatternWorker(context) }
    }

    @Test
    fun `doWork returns retry on failure`() = runTest {
        // Arrange
        coEvery { transactionDao.getTransactionsWithSignatureSince(any()) } throws RuntimeException("DB Error")
        val worker = TestListenableWorkerBuilder<RecurringPatternWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleRecurringPatternWorker(any()) }
    }
}
