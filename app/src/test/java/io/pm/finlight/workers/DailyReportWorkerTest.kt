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
//
// REASON: TEST (Bug Fix) - Updated the tests to validate the new rolling
// 24-hour window logic. The `getExpectedTimeRanges` helper now calculates
// the rolling window, and the tests verify that the DAO is called with these
// exact start and end times.
//
// REASON: FIX (Flaky Test) - Replaced strict `eq()` matchers for timestamps
// with `any()` in mocks and `slot()` for verification. This resolves a race
// condition where the test's `Calendar.getInstance()` and the worker's
// `Calendar.getInstance()` would be milliseconds apart, causing mocks
// to fail and the worker to return `Retry`.
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
import kotlin.test.assertTrue

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

        // Mock static helpers
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

    // --- REMOVED: racy getExpectedTimeRanges() function ---

    @Test
    fun `doWork with higher spending generates 'higher than usual' title and uses correct rolling 24h time-bounds`() = runTest {
        // Arrange
        // --- USE GENERIC MATCHERS ---
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 200.0)
        coEvery { transactionDao.getAverageDailySpendingForRange(any(), any()) } returns 100.0
        coEvery { transactionDao.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()
        val titleCaptor = slot<String>()

        // --- SLOTS FOR VERIFICATION ---
        val reportStartSlot = slot<Long>()
        val reportEndSlot = slot<Long>()
        val avgStartSlot = slot<Long>()
        val avgEndSlot = slot<Long>()

        // --- CAPTURE "NOW" IN THE TEST ---
        val testNow = Calendar.getInstance()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)

        // --- VERIFY DAO CALLS WITH CAPTORS ---
        coVerify {
            transactionDao.getFinancialSummaryForRange(capture(reportStartSlot), capture(reportEndSlot))
            transactionDao.getAverageDailySpendingForRange(capture(avgStartSlot), capture(avgEndSlot))
            transactionDao.getTopSpendingCategoriesForRange(capture(reportStartSlot), capture(reportEndSlot))
        }

        // --- VERIFY TIME BOUNDS ---
        // 1. Report Window (Rolling 24h)
        val expectedReportEnd = testNow.timeInMillis
        val expectedReportStart = (testNow.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, -24) }.timeInMillis

        // Allow a small delta (e.g., 100ms) for execution time difference between test and SUT
        val timeDelta = 100L
        // --- FIX: Correct argument order for assertTrue ---
        assertTrue(
            kotlin.math.abs(expectedReportEnd - reportEndSlot.captured) < timeDelta,
            "Report end time (${reportEndSlot.captured}) should be close to expected ($expectedReportEnd)"
        )
        assertTrue(
            kotlin.math.abs(expectedReportStart - reportStartSlot.captured) < timeDelta,
            "Report start time (${reportStartSlot.captured}) should be close to expected ($expectedReportStart)"
        )

        // 2. Average Window (7 days prior, full days)
        val expectedAvgStart = (testNow.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -8)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expectedAvgEnd = (testNow.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -2)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        // These should be exact, as their calculation is deterministic relative to 'now'
        // --- FIX: Correct argument order for assertTrue ---
        assertTrue(
            kotlin.math.abs(expectedAvgStart - avgStartSlot.captured) < timeDelta,
            "Average start time (${avgStartSlot.captured}) should be close to expected ($expectedAvgStart)"
        )
        assertTrue(
            kotlin.math.abs(expectedAvgEnd - avgEndSlot.captured) < timeDelta,
            "Average end time (${avgEndSlot.captured}) should be close to expected ($expectedAvgEnd)"
        )


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
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 10.0)
        coEvery { transactionDao.getAverageDailySpendingForRange(any(), any()) } returns 100.0
        coEvery { transactionDao.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

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
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } returns FinancialSummary(0.0, 0.0)
        coEvery { transactionDao.getAverageDailySpendingForRange(any(), any()) } returns 100.0
        coEvery { transactionDao.getTopSpendingCategoriesForRange(any(), any()) } returns emptyList()

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
        coEvery { transactionDao.getFinancialSummaryForRange(any(), any()) } throws RuntimeException("DB error")
        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleDailyReport(any()) }
    }
}

