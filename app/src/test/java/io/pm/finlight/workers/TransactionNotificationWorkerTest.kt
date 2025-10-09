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
import androidx.work.workDataOf
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.NotificationHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionNotificationWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        db = mockk()
        transactionDao = mockk()

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        every { NotificationHelper.showRichTransactionNotification(any(), any(), any(), any()) } just runs
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `doWork success case calls NotificationHelper with correct data`() = runTest {
        // Arrange
        val transactionId = 1
        val details = TransactionDetails(
            Transaction(id = transactionId, description = "Test", amount = 100.0, transactionType = "expense", date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null, originalDescription = "Test"),
            emptyList(), "Account", "Category", "icon", "color", null
        )
        val summary = FinancialSummary(0.0, 1500.0)
        val visitCount = 5

        coEvery { transactionDao.getTransactionDetailsById(transactionId) } returns flowOf(details)
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns summary
        coEvery { transactionDao.getTransactionCountForMerchantSuspend("Test") } returns visitCount

        val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
        val worker = TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
            .setInputData(inputData)
            .build()

        val detailsCaptor = slot<TransactionDetails>()
        val totalCaptor = slot<Double>()
        val visitCaptor = slot<Int>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify {
            NotificationHelper.showRichTransactionNotification(
                context = any(),
                details = capture(detailsCaptor),
                monthlyTotal = capture(totalCaptor),
                visitCount = capture(visitCaptor)
            )
        }
        assertEquals(details, detailsCaptor.captured)
        assertEquals(summary.totalExpenses, totalCaptor.captured, 0.0)
        assertEquals(visitCount, visitCaptor.captured)
    }

    @Test
    fun `doWork returns failure for invalid transactionId`() = runTest {
        // Arrange
        coEvery { transactionDao.getTransactionDetailsById(any()) } returns flowOf(null)

        val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to -1)
        val worker = TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
            .setInputData(inputData)
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns failure if transaction details not found`() = runTest {
        // Arrange
        val transactionId = 999
        coEvery { transactionDao.getTransactionDetailsById(transactionId) } returns flowOf(null)

        val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
        val worker = TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
            .setInputData(inputData)
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns retry on unexpected exception`() = runTest {
        // Arrange
        val transactionId = 1
        coEvery { transactionDao.getTransactionDetailsById(transactionId) } throws RuntimeException("DB Error")
        val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
        val worker = TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
            .setInputData(inputData)
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
