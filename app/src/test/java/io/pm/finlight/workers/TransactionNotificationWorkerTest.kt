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
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.utils.NotificationHelper
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
    private lateinit var transactionQueryDao: TransactionQueryDao
    private lateinit var transactionAnalyticsDao: TransactionAnalyticsDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        db = mockk()
        transactionQueryDao = mockk()
        transactionAnalyticsDao = mockk()

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionQueryDao() } returns transactionQueryDao
        every { db.transactionAnalyticsDao() } returns transactionAnalyticsDao

        val config =
            Configuration.Builder()
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
    fun `doWork success case calls NotificationHelper with correct data`() =
        runTest {
            // Arrange
            val transactionId = 1
            val details =
                TransactionDetails(
                    Transaction(id = transactionId, description = "Test", amount = 100.0, transactionType = TransactionType.EXPENSE, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null, originalDescription = "Test"),
                    emptyList(),
                    "Account",
                    "Category",
                    "icon",
                    "color",
                    null,
                )
            val summary = FinancialSummary(0.0, 1500.0)
            val visitCount = 5

            coEvery { transactionQueryDao.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionAnalyticsDao.getFinancialSummaryForRange(any(), any()) } returns summary
            every { transactionQueryDao.getTransactionCountForMerchant("Test") } returns flowOf(visitCount)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
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
                    visitCount = capture(visitCaptor),
                )
            }
            assertEquals(details, detailsCaptor.captured)
            assertEquals(summary.totalExpenses, totalCaptor.captured, 0.0)
            assertEquals(visitCount, visitCaptor.captured)
        }

    @Test
    fun `doWork returns failure for invalid transactionId`() =
        runTest {
            // Arrange
            coEvery { transactionQueryDao.getTransactionDetailsById(any()) } returns flowOf(null)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to -1)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `doWork returns failure if transaction details not found`() =
        runTest {
            // Arrange
            val transactionId = 999
            coEvery { transactionQueryDao.getTransactionDetailsById(transactionId) } returns flowOf(null)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `doWork returns retry on unexpected exception`() =
        runTest {
            // Arrange
            val transactionId = 1
            coEvery { transactionQueryDao.getTransactionDetailsById(transactionId) } throws RuntimeException("DB Error")
            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `doWork handles income transaction and null summary correctly`() =
        runTest {
            // Arrange
            val transactionId = 2
            val details =
                TransactionDetails(
                    Transaction(id = transactionId, description = "Salary", amount = 5000.0, transactionType = TransactionType.INCOME, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null, originalDescription = null),
                    emptyList(),
                    "Account",
                    "Category",
                    "icon",
                    "color",
                    null,
                )

            coEvery { transactionQueryDao.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionAnalyticsDao.getFinancialSummaryForRange(any(), any()) } returns null

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val totalCaptor = slot<Double>()
            val visitCaptor = slot<Int>()

            // Act
            val result = worker.doWork()

            // Assert
            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showRichTransactionNotification(
                    context = any(),
                    details = any(),
                    monthlyTotal = capture(totalCaptor),
                    visitCount = capture(visitCaptor),
                )
            }
            assertEquals(0.0, totalCaptor.captured, 0.0) // null summary fallback to 0.0
            assertEquals(0, visitCaptor.captured) // income transaction has 0 visit count
        }

    @Test
    fun `doWork handles income transaction with non-null summary and uses totalIncome`() =
        runTest {
            val transactionId = 3
            val details =
                TransactionDetails(
                    Transaction(id = transactionId, description = "Salary", amount = 50000.0, transactionType = TransactionType.INCOME, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null),
                    emptyList(),
                    "Account",
                    "Category",
                    "icon",
                    "color",
                    null,
                )
            val summary = FinancialSummary(totalIncome = 50000.0, totalExpenses = 12000.0)

            coEvery { transactionQueryDao.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionAnalyticsDao.getFinancialSummaryForRange(any(), any()) } returns summary

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val totalCaptor = slot<Double>()
            val visitCaptor = slot<Int>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showRichTransactionNotification(
                    context = any(),
                    details = any(),
                    monthlyTotal = capture(totalCaptor),
                    visitCount = capture(visitCaptor),
                )
            }
            assertEquals(50000.0, totalCaptor.captured, 0.0)
            assertEquals(0, visitCaptor.captured)
        }

    @Test
    fun `doWork handles transfer transaction with non-null summary and gets visit count`() =
        runTest {
            val transactionId = 4
            val details =
                TransactionDetails(
                    Transaction(id = transactionId, description = "Transfer", amount = 1000.0, transactionType = TransactionType.TRANSFER, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null),
                    emptyList(),
                    "Account",
                    "Category",
                    "icon",
                    "color",
                    null,
                )
            val summary = FinancialSummary(totalIncome = 10000.0, totalExpenses = 5000.0)

            coEvery { transactionQueryDao.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionAnalyticsDao.getFinancialSummaryForRange(any(), any()) } returns summary
            every { transactionQueryDao.getTransactionCountForMerchant("Transfer") } returns flowOf(3)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val totalCaptor = slot<Double>()
            val visitCaptor = slot<Int>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showRichTransactionNotification(
                    context = any(),
                    details = any(),
                    monthlyTotal = capture(totalCaptor),
                    visitCount = capture(visitCaptor),
                )
            }
            assertEquals(5000.0, totalCaptor.captured, 0.0)
            assertEquals(3, visitCaptor.captured)
        }
}
