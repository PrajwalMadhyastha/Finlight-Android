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
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MonthlySummaryWorkerTest : BaseViewModelTest() {

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
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        `when`(db.transactionDao()).thenReturn(transactionDao)

        // Initialize WorkManager for testing.
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
        unmockkAll() // Clears all MockK mocks
        super.tearDown()
    }

    @Test
    fun `doWork calculates percentage change and shows notification`() = runTest {
        // Arrange
        val lastMonthSummary = FinancialSummary(0.0, 150.0)
        val prevMonthSummary = FinancialSummary(0.0, 100.0)
        val topCategories = listOf(CategorySpending("Food", 50.0, "red", "icon"))

        // Mock DAO to return summary for last month, then for the month before
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong()))
            .thenReturn(lastMonthSummary)
            .thenReturn(prevMonthSummary)
        `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(topCategories)

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
        val lastMonthSummary = FinancialSummary(0.0, 150.0)
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong()))
            .thenReturn(lastMonthSummary)
            .thenReturn(null) // No data for the previous-to-last month
        `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())

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
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenThrow(RuntimeException("DB error"))
        val worker = TestListenableWorkerBuilder<MonthlySummaryWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleMonthlySummary(any()) }
    }
}