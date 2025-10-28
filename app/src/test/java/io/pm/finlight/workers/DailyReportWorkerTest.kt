// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/DailyReportWorkerTest.kt
// REASON: FIX (Test) - The tests in this file have been rewritten to
// correctly validate the worker's time-bound logic. The tests now calculate
// the *exact* expected start/end timestamps (zeroed-out) and use MockK's
// `coVerify` with `eq()` to ensure the DAO is called with those precise values,
// fixing the blind spot that allowed the original bug.
// FIX (Test) - Removed Mockito's `@Mock` annotations from `db` and
// `transactionDao` and initialized them with MockK's `mockk()` function
// instead. This resolves the "Missing mocked calls" `MockKException` caused
// by mixing Mockito-initialized mocks with MockK's `every` block.
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
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DailyReportWorkerTest : BaseViewModelTest() {

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
        // This is the key to preventing the UnsatisfiedLinkError.
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao

        // Initialize WorkManager for testing. This is required for TestListenableWorkerBuilder.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Mock other static objects/singletons that the worker interacts with.
        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)
        every { NotificationHelper.showDailyReportNotification(any(), any(), any(), any(), any()) } just runs
        coEvery { ReminderManager.scheduleDailyReport(any()) } returns Unit
    }

    @After
    override fun tearDown() {
        // Clear all MockK mocks after each test.
        unmockkAll()
        super.tearDown()
    }

    private fun getExpectedTimeRanges(): Map<String, Long> {
        val now = Calendar.getInstance()

        val yesterdayStart = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayEnd = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val sevenDaysAgoStart = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -8)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sevenDaysAgoEnd = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -2)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return mapOf(
            "yesterdayStart" to yesterdayStart,
            "yesterdayEnd" to yesterdayEnd,
            "sevenDaysAgoStart" to sevenDaysAgoStart,
            "sevenDaysAgoEnd" to sevenDaysAgoEnd
        )
    }

    @Test
    fun `doWork with higher spending generates 'higher than usual' title and uses correct time-bounds`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } returns FinancialSummary(0.0, 200.0)
        coEvery { transactionDao.getAverageDailySpendingForRange(ranges["sevenDaysAgoStart"]!!, ranges["sevenDaysAgoEnd"]!!) } returns 100.0
        coEvery { transactionDao.getTopSpendingCategoriesForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } returns emptyList()

        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()
        val titleCaptor = slot<String>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        // Verify all DAO calls used the exact, correct time ranges
        coVerify { transactionDao.getFinancialSummaryForRange(eq(ranges["yesterdayStart"]!!), eq(ranges["yesterdayEnd"]!!)) }
        coVerify { transactionDao.getAverageDailySpendingForRange(eq(ranges["sevenDaysAgoStart"]!!), eq(ranges["sevenDaysAgoEnd"]!!)) }
        coVerify { transactionDao.getTopSpendingCategoriesForRange(eq(ranges["yesterdayStart"]!!), eq(ranges["yesterdayEnd"]!!)) }

        // Verify the notification logic is correct
        verify {
            NotificationHelper.showDailyReportNotification(
                context = any(),
                title = capture(titleCaptor),
                totalExpenses = any(),
                topCategories = any(),
                dateMillis = any()
            )
        }
        assertEquals("Heads up: Spending was higher than usual yesterday.", titleCaptor.captured)
        coVerify { ReminderManager.scheduleDailyReport(context) }
    }

    @Test
    fun `doWork with lower spending generates 'well below average' title`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } returns FinancialSummary(0.0, 10.0)
        coEvery { transactionDao.getAverageDailySpendingForRange(ranges["sevenDaysAgoStart"]!!, ranges["sevenDaysAgoEnd"]!!) } returns 100.0
        coEvery { transactionDao.getTopSpendingCategoriesForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } returns emptyList()

        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()
        val titleCaptor = slot<String>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify {
            NotificationHelper.showDailyReportNotification(
                context = any(),
                title = capture(titleCaptor),
                totalExpenses = any(),
                topCategories = any(),
                dateMillis = any()
            )
        }
        assertEquals("Great job! Spending was well below average yesterday.", titleCaptor.captured)
    }

    @Test
    fun `doWork with no spending generates 'no spending' title`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } returns FinancialSummary(0.0, 0.0)
        coEvery { transactionDao.getAverageDailySpendingForRange(ranges["sevenDaysAgoStart"]!!, ranges["sevenDaysAgoEnd"]!!) } returns 100.0
        coEvery { transactionDao.getTopSpendingCategoriesForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } returns emptyList()

        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()
        val titleCaptor = slot<String>()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        verify {
            NotificationHelper.showDailyReportNotification(
                context = any(),
                title = capture(titleCaptor),
                totalExpenses = any(),
                topCategories = any(),
                dateMillis = any()
            )
        }
        assertEquals("No spending recorded yesterday. Keep it up!", titleCaptor.captured)
    }

    @Test
    fun `doWork returns retry on failure`() = runTest {
        // Arrange
        val ranges = getExpectedTimeRanges()
        coEvery { transactionDao.getFinancialSummaryForRange(ranges["yesterdayStart"]!!, ranges["yesterdayEnd"]!!) } throws RuntimeException("DB error")
        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleDailyReport(any()) }
    }
}

