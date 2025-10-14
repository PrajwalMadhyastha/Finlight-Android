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
import java.util.concurrent.TimeUnit
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertTrue

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
        val capturedArgs = mutableListOf<Long>()

        // Act
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        // Assert
        coVerify(exactly = 2) {
            transactionDao.getFinancialSummaryForRange(capture(capturedArgs), capture(capturedArgs))
        }

        assertEquals("Should have captured 4 arguments (2 calls * 2 args)", 4, capturedArgs.size)

        val thisWeekStart = capturedArgs[0]
        val thisWeekEnd = capturedArgs[1]
        val lastWeekStart = capturedArgs[2]
        val lastWeekEnd = capturedArgs[3]

        // Verify the first range (last 7 days)
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)
        assertTrue("Start of 'this week' range should be ~7 days ago", kotlin.math.abs(thisWeekStart - sevenDaysAgo) < 1000)
        assertTrue("End of 'this week' range should be ~now", kotlin.math.abs(thisWeekEnd - now) < 1000)

        // Verify the second range (8-14 days ago)
        val eightDaysAgo = now - TimeUnit.DAYS.toMillis(8)
        val fourteenDaysAgo = now - TimeUnit.DAYS.toMillis(14)
        assertTrue("Start of 'last week' range should be ~14 days ago", kotlin.math.abs(lastWeekStart - fourteenDaysAgo) < 1000)
        assertTrue("End of 'last week' range should be ~8 days ago", kotlin.math.abs(lastWeekEnd - eightDaysAgo) < 1000)
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

