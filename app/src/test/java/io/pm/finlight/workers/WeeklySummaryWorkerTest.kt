// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/WeeklySummaryWorkerTest.kt
// REASON: FIX (Test) - The `doWork calls DAO with correct date ranges` test has
// been rewritten to be precise. It now calculates the *exact* expected
// millisecond timestamps for the date ranges and uses `coVerify` with `eq()`
// to ensure the DAO is called with the correct, zeroed-out time boundaries.
// This fixes the original test's flawed logic.
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
import kotlinx.coroutines.runBlocking
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
class WeeklySummaryWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Create mocks with MockK
        db = mockk()
        transactionDao = mockk()

        // Mock the static AppDatabase to return our mock db instance
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao

        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Mock static helpers
        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)
        every { NotificationHelper.showWeeklySummaryNotification(any(), any(), any(), any()) } just runs
        coEvery { ReminderManager.scheduleWeeklySummary(any()) } returns Unit
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `doWork calls DAO with correct date ranges`() = runTest {
        // Arrange
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns null
        coEvery { transactionDao.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

        val worker = TestListenableWorkerBuilder<WeeklySummaryWorker>(context).build()

        // Calculate expected ranges EXACTLY as the worker does
        val now = Calendar.getInstance()
        val thisWeekEnd = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val thisWeekStart = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val lastWeekEnd = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -8)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val lastWeekStart = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -14)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Act
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        // Assert
        // Verify the DAO was called with the *exact* calculated timestamps
        coVerifyOrder {
            transactionDao.getFinancialSummaryForRange(eq(thisWeekStart), eq(thisWeekEnd))
            transactionDao.getFinancialSummaryForRange(eq(lastWeekStart), eq(lastWeekEnd))
            transactionDao.getTopSpendingCategoriesForRange(eq(thisWeekStart), eq(thisWeekEnd))
        }
    }

    @Test
    fun `doWork calculates percentage and calls notification helper correctly`() = runTest {
        // Arrange
        val thisWeekSummary = FinancialSummary(0.0, 300.0)
        val lastWeekSummary = FinancialSummary(0.0, 200.0)
        val topCategories = listOf(CategorySpending("Travel", 150.0, "blue", "icon"))

        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns thisWeekSummary andThen lastWeekSummary
        coEvery { transactionDao.getTopSpendingCategoriesForRange(any(), any()) } returns topCategories

        val worker = TestListenableWorkerBuilder<WeeklySummaryWorker>(context).build()

        val expensesCaptor = slot<Double>()
        val percentageCaptor = slot<Int?>()
        val categoriesCaptor = slot<List<CategorySpending>>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { ReminderManager.scheduleWeeklySummary(context) }
        verify {
            NotificationHelper.showWeeklySummaryNotification(
                context = any(),
                totalExpenses = capture(expensesCaptor),
                percentageChange = captureNullable(percentageCaptor),
                topCategories = capture(categoriesCaptor)
            )
        }

        assertEquals(300.0, expensesCaptor.captured, 0.0)
        assertEquals(50, percentageCaptor.captured) // (300-200)/200 * 100
        assertEquals(topCategories, categoriesCaptor.captured)
    }

    @Test
    fun `doWork returns retry on failure`() = runTest {
        // Arrange
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB Error")

        val worker = TestListenableWorkerBuilder<WeeklySummaryWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleWeeklySummary(any()) }
    }
}

