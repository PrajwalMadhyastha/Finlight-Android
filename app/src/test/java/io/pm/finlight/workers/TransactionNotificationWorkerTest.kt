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
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionNotificationWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var transactionRepository: ITransactionRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        transactionRepository = mockk()
        ServiceLocator.setTransactionRepository(transactionRepository)

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkObject(NotificationHelper)
        every { NotificationHelper.showRichTransactionNotification(any(), any(), any(), any(), any()) } just runs
    }

    @After
    override fun tearDown() {
        ServiceLocator.setTransactionRepository(null)
        unmockkAll()
        super.tearDown()
    }

    private fun createTestTransactionDetails(
        id: Int,
        type: TransactionType = TransactionType.EXPENSE,
        amount: Double = 100.0,
        description: String = "Test",
    ): TransactionDetails {
        return TransactionDetails(
            transaction =
                Transaction(
                    id = id,
                    description = description,
                    amount = amount,
                    transactionType = type,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    originalDescription = description,
                ),
            images = emptyList(),
            accountName = "Account",
            categoryName = "Category",
            categoryIconKey = "icon",
            categoryColorKey = "color",
            tagNames = null,
        )
    }

    @Test
    fun `doWork success case calls NotificationHelper with correct data`() =
        runTest {
            val transactionId = 1
            val details = createTestTransactionDetails(transactionId, TransactionType.EXPENSE, 100.0, "Test")
            val summary = FinancialSummary(0.0, 1500.0)
            val visitCount = 5

            every { transactionRepository.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns summary
            every { transactionRepository.getTransactionCountForMerchant("Test") } returns flowOf(visitCount)
            every { transactionRepository.getTotalSpentForMerchant("Test") } returns flowOf(500.0)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val detailsCaptor = slot<TransactionDetails>()
            val totalCaptor = slot<Double>()
            val visitCaptor = slot<Int>()
            val merchantTotalCaptor = slot<Double>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showRichTransactionNotification(
                    context = any(),
                    details = capture(detailsCaptor),
                    monthlyTotal = capture(totalCaptor),
                    visitCount = capture(visitCaptor),
                    merchantTotalSpent = capture(merchantTotalCaptor),
                )
            }
            assertEquals(details, detailsCaptor.captured)
            assertEquals(summary.totalExpenses, totalCaptor.captured, 0.0)
            assertEquals(visitCount, visitCaptor.captured)
            assertEquals(500.0, merchantTotalCaptor.captured, 0.0)
        }

    @Test
    fun `doWork returns failure for invalid transactionId`() =
        runTest {
            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to -1)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            verify(exactly = 0) { transactionRepository.getTransactionDetailsById(any()) }
        }

    @Test
    fun `doWork returns failure if transaction details not found`() =
        runTest {
            val transactionId = 999
            every { transactionRepository.getTransactionDetailsById(transactionId) } returns flowOf(null)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `doWork returns retry on unexpected exception`() =
        runTest {
            val transactionId = 1
            every { transactionRepository.getTransactionDetailsById(transactionId) } throws RuntimeException("DB Error")
            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `doWork handles income transaction and null summary correctly`() =
        runTest {
            val transactionId = 2
            val details = createTestTransactionDetails(transactionId, TransactionType.INCOME, 5000.0, "Salary")

            every { transactionRepository.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns null

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
                    merchantTotalSpent = any(),
                )
            }
            assertEquals(0.0, totalCaptor.captured, 0.0)
            assertEquals(0, visitCaptor.captured)
        }

    @Test
    fun `doWork handles expense transaction and null summary correctly`() =
        runTest {
            val transactionId = 5
            val details = createTestTransactionDetails(transactionId, TransactionType.EXPENSE, 100.0, "Coffee")

            every { transactionRepository.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns null
            every { transactionRepository.getTransactionCountForMerchant("Coffee") } returns flowOf(1)
            every { transactionRepository.getTotalSpentForMerchant("Coffee") } returns flowOf(100.0)

            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .build()

            val totalCaptor = slot<Double>()
            val visitCaptor = slot<Int>()
            val merchantTotalCaptor = slot<Double>()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify {
                NotificationHelper.showRichTransactionNotification(
                    context = any(),
                    details = any(),
                    monthlyTotal = capture(totalCaptor),
                    visitCount = capture(visitCaptor),
                    merchantTotalSpent = capture(merchantTotalCaptor),
                )
            }
            assertEquals(0.0, totalCaptor.captured, 0.0)
            assertEquals(1, visitCaptor.captured)
            assertEquals(100.0, merchantTotalCaptor.captured, 0.0)
        }

    @Test
    fun `doWork handles income transaction with non-null summary and uses totalIncome`() =
        runTest {
            val transactionId = 3
            val details = createTestTransactionDetails(transactionId, TransactionType.INCOME, 50000.0, "Salary")
            val summary = FinancialSummary(totalIncome = 50000.0, totalExpenses = 12000.0)

            every { transactionRepository.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns summary

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
                    merchantTotalSpent = any(),
                )
            }
            assertEquals(50000.0, totalCaptor.captured, 0.0)
            assertEquals(0, visitCaptor.captured)
        }

    @Test
    fun `doWork handles transfer transaction with non-null summary and gets visit count`() =
        runTest {
            val transactionId = 4
            val details = createTestTransactionDetails(transactionId, TransactionType.TRANSFER, 1000.0, "Transfer")
            val summary = FinancialSummary(totalIncome = 10000.0, totalExpenses = 5000.0)

            every { transactionRepository.getTransactionDetailsById(transactionId) } returns flowOf(details)
            coEvery { transactionRepository.getFinancialSummaryForRange(any(), any()) } returns summary
            every { transactionRepository.getTransactionCountForMerchant("Transfer") } returns flowOf(3)
            every { transactionRepository.getTotalSpentForMerchant("Transfer") } returns flowOf(3000.0)

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
                    merchantTotalSpent = any(),
                )
            }
            assertEquals(5000.0, totalCaptor.captured, 0.0)
            assertEquals(3, visitCaptor.captured)
        }

    @Test
    fun `doWork returns failure when runAttemptCount is 3 or more`() =
        runTest {
            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to 1)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .setRunAttemptCount(3)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            verify(exactly = 0) { transactionRepository.getTransactionDetailsById(any()) }
        }

    @Test
    fun `doWork returns failure when unexpected exception thrown and runAttemptCount is 2`() =
        runTest {
            val transactionId = 1
            every { transactionRepository.getTransactionDetailsById(transactionId) } throws RuntimeException("DB Error")
            val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
            val worker =
                TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                    .setInputData(inputData)
                    .setRunAttemptCount(2)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `doWork rethrows CancellationException`() {
        val transactionId = 1
        every { transactionRepository.getTransactionDetailsById(transactionId) } throws CancellationException("Cancelled")
        val inputData = workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to transactionId)
        val worker =
            TestListenableWorkerBuilder<TransactionNotificationWorker>(context)
                .setInputData(inputData)
                .build()

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                worker.doWork()
            }
        }
    }
}
