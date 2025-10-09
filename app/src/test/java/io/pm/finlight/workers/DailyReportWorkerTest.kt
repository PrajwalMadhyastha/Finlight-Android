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
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DailyReportWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context

    @Mock
    private lateinit var db: AppDatabase
    @Mock
    private lateinit var transactionDao: TransactionDao

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Mock the static AppDatabase companion object to return our mock db instance.
        // This is the key to preventing the UnsatisfiedLinkError.
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        `when`(db.transactionDao()).thenReturn(transactionDao)

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

    @Test
    fun `doWork with higher spending generates 'higher than usual' title`() = runTest {
        // Arrange
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(FinancialSummary(0.0, 200.0)) // Yesterday's expense
        `when`(transactionDao.getAverageDailySpendingForRange(anyLong(), anyLong())).thenReturn(100.0) // 7-day average
        `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())

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
        assertEquals("Heads up: Spending was higher than usual yesterday.", titleCaptor.captured)
        coVerify { ReminderManager.scheduleDailyReport(context) }
    }

    @Test
    fun `doWork with lower spending generates 'well below average' title`() = runTest {
        // Arrange
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(FinancialSummary(0.0, 10.0)) // Yesterday's expense
        `when`(transactionDao.getAverageDailySpendingForRange(anyLong(), anyLong())).thenReturn(100.0) // 7-day average
        `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())

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
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(FinancialSummary(0.0, 0.0))
        `when`(transactionDao.getAverageDailySpendingForRange(anyLong(), anyLong())).thenReturn(100.0)
        `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())

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
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenThrow(RuntimeException("DB error"))
        val worker = TestListenableWorkerBuilder<DailyReportWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleDailyReport(any()) }
    }
}