// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/MonthlySummaryWorkerTest.kt
// REASON: FIX (Test) - Added a new test `doWork calculates correct date ranges`
// to explicitly validate that the worker is using precise, full-month
// timestamps (00:00:00.000 to 23:59:59.999) for its DAO queries.
// FIX (Test) - Removed Mockito's `@Mock` annotations from `db` and
// `transactionDao` and initialized them with MockK's `mockk()` function
// instead. This resolves the "Missing mocked calls" `MockKException` caused
// by mixing Mockito-initialized mocks with MockK's `coEvery` block.
// =================================================================================
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
import org.mockito.ArgumentMatchers.anyLong
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MonthlySummaryWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context

    // --- FIX: Remove @Mock annotations ---
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // --- FIX: Initialize as MockK mocks ---
        db = mockk()
        transactionDao = mockk()

        // Mock the static AppDatabase companion object to return our mock db instance.
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao

        // Initialize WorkManager for testing. This is required for TestListenableWorkerBuilder.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Mock other static helpers
        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)
        every { NotificationHelper.showMonthlySummaryNotification(any(), any(), any(), any(), any()) } just runs
        coEvery { ReminderManager.scheduleMonthlySummary(any()) } returns Unit
    }

    @After
    override fun tearDown() {
        // Clear all MockK mocks after each test.
        unmockkAll()
        super.tearDown()
    }

    private fun getExpectedTimeRanges(): Map<String, Long> {
        val lastMonthEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val lastMonthStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val prevMonthEnd = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val prevMonthStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, -2)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return mapOf(
            "lastMonthStart" to lastMonthStart,
            "lastMonthEnd" to lastMonthEnd,
            "prevMonthStart" to prevMonthStart,
            "prevMonthEnd" to prevMonthEnd
        )
    }

    @Test
    fun `doWork calculates correct date ranges`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns null
        coEvery { transactionDao.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

        val worker = TestListenableWorkerBuilder<MonthlySummaryWorker>(context).build()

        // Act
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        // Assert
        coVerifyOrder {
            transactionDao.getFinancialSummaryForRange(eq(ranges["lastMonthStart"]!!), eq(ranges["lastMonthEnd"]!!))
            transactionDao.getFinancialSummaryForRange(eq(ranges["prevMonthStart"]!!), eq(ranges["prevMonthEnd"]!!))
            transactionDao.getTopSpendingCategoriesForRange(eq(ranges["lastMonthStart"]!!), eq(ranges["lastMonthEnd"]!!))
        }
    }


    @Test
    fun `doWork calculates percentage change and shows notification`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        val lastMonthSummary = FinancialSummary(0.0, 150.0)
        val prevMonthSummary = FinancialSummary(0.0, 100.0)
        val topCategories = listOf(CategorySpending("Food", 50.0, "red", "icon"))

        // Mock DAO to return summary for last month, then for the month before
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["lastMonthStart"]!!, ranges["lastMonthEnd"]!!) } returns lastMonthSummary
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["prevMonthStart"]!!, ranges["prevMonthEnd"]!!) } returns prevMonthSummary
        coEvery { transactionDao.getTopSpendingCategoriesForRange(ranges["lastMonthStart"]!!, ranges["lastMonthEnd"]!!) } returns topCategories

        val worker = TestListenableWorkerBuilder<MonthlySummaryWorker>(context).build()

        val percentageCaptor = slot<Int?>()
        val expensesCaptor = slot<Double>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify {
            NotificationHelper.showMonthlySummaryNotification(
                context = any(),
                calendar = any(),
                totalExpenses = capture(expensesCaptor),
                percentageChange = captureNullable(percentageCaptor),
                topCategories = any()
            )
        }

        assertEquals(50, percentageCaptor.captured) // (150-100)/100 * 100
        assertEquals(150.0, expensesCaptor.captured, 0.0)
        coVerify { ReminderManager.scheduleMonthlySummary(context) }
    }

    @Test
    fun `doWork with no previous data calculates null percentage change`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        val lastMonthSummary = FinancialSummary(0.0, 150.0)
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["lastMonthStart"]!!, ranges["lastMonthEnd"]!!) } returns lastMonthSummary
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["prevMonthStart"]!!, ranges["prevMonthEnd"]!!) } returns null
        coEvery { transactionDao.getTopSpendingCategoriesForRange(ranges["lastMonthStart"]!!, ranges["lastMonthEnd"]!!) } returns emptyList()

        val worker = TestListenableWorkerBuilder<MonthlySummaryWorker>(context).build()
        val percentageCaptor = slot<Int?>()
        val expensesCaptor = slot<Double>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify {
            NotificationHelper.showMonthlySummaryNotification(
                context = any(),
                calendar = any(),
                totalExpenses = capture(expensesCaptor),
                percentageChange = captureNullable(percentageCaptor),
                topCategories = any()
            )
        }
        assertNull(percentageCaptor.captured)
    }

    @Test
    fun `doWork returns retry on failure`() = runTest {
        // Arrange
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
        val worker = TestListenableWorkerBuilder<MonthlySummaryWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleMonthlySummary(any()) }
    }
}

