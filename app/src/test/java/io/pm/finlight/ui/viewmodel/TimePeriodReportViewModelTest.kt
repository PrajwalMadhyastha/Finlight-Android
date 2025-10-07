// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TimePeriodReportViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.math.roundToLong

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TimePeriodReportViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Before
    override fun setup() {
        super.setup()
        // Setup default mocks that apply to most tests in this file
        `when`(transactionDao.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))

        // FIX: Wrap mocking of suspend functions in a coroutine scope to resolve build error.
        runTest {
            `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(null)
            `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())
            `when`(settingsRepository.getOverallBudgetForMonthBlocking(anyInt(), anyInt())).thenReturn(0f)
        }

        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getWeeklySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getMonthlySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(0L))
    }

    @Test
    fun `totalIncome is correctly calculated and converted to Long`() = runTest {
        // ARRANGE
        val transactions = listOf(
            TransactionDetails(Transaction(id = 1, amount = 1000.50, transactionType = "income", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 2, amount = 2000.25, transactionType = "income", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 3, amount = 500.0, transactionType = "expense", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null)
        )
        `when`(transactionDao.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))


        // ACT
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, false)
        advanceUntilIdle()

        // ASSERT
        val expectedTotalIncome = (1000.50 + 2000.25).roundToLong() // 3001
        val actualTotalIncome = viewModel.totalIncome.first()

        assertEquals(expectedTotalIncome, actualTotalIncome)
    }

    @Test
    fun `selectPreviousPeriod updates selectedDate correctly for MONTHLY`() = runTest {
        // Arrange
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, false)
        val initialMonth = viewModel.selectedDate.first().get(Calendar.MONTH)

        // Act
        viewModel.selectPreviousPeriod()
        advanceUntilIdle()

        // Assert
        val newMonth = viewModel.selectedDate.first().get(Calendar.MONTH)
        val expectedMonth = (initialMonth - 1 + 12) % 12
        assertEquals(expectedMonth, newMonth)
    }

    @Test
    fun `selectNextPeriod updates selectedDate correctly for YEARLY`() = runTest {
        // Arrange
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.YEARLY, null, false)
        val initialYear = viewModel.selectedDate.first().get(Calendar.YEAR)

        // Act
        viewModel.selectNextPeriod()
        advanceUntilIdle()

        // Assert
        val newYear = viewModel.selectedDate.first().get(Calendar.YEAR)
        assertEquals(initialYear + 1, newYear)
    }

    @Test
    fun `insights flow calculates percentageChange correctly`() = runTest {
        // Arrange
        val currentSummary = FinancialSummary(0.0, 150.0)
        val previousSummary = FinancialSummary(0.0, 100.0)
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong()))
            .thenReturn(currentSummary) // First call for current
            .thenReturn(previousSummary) // Second call for previous
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.WEEKLY, null, false)
        advanceUntilIdle()

        // Assert
        viewModel.insights.test {
            val insights = awaitItem()
            assertEquals(50, insights?.percentageChange) // (150 - 100) / 100 * 100
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showPreviousMonth factory parameter sets initial date correctly`() = runTest {
        // Arrange
        val today = Calendar.getInstance()
        val expectedMonth = (today.get(Calendar.MONTH) - 1 + 12) % 12
        val expectedYear = if (today.get(Calendar.MONTH) == Calendar.JANUARY) today.get(Calendar.YEAR) - 1 else today.get(Calendar.YEAR)

        // Act
        val viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, true)
        advanceUntilIdle()

        // Assert
        val selectedDate = viewModel.selectedDate.value
        assertEquals(expectedMonth, selectedDate.get(Calendar.MONTH))
        assertEquals(expectedYear, selectedDate.get(Calendar.YEAR))
    }
}