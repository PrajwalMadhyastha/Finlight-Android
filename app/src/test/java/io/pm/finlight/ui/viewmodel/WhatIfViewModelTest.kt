package io.pm.finlight.ui.viewmodel

import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.FinancialSummary
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class WhatIfViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: WhatIfViewModel

    private val testCalendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private val mockTimeProvider =
        object : TimeProvider {
            override fun now(): Calendar = testCalendar.clone() as Calendar
        }

    @Before
    override fun setup() {
        super.setup()

        // Mock settings
        `when`(settingsRepository.getOverallBudgetForMonth(2026, 6)).thenReturn(flowOf(5000f))
        `when`(settingsRepository.getSimulatorPrivacyModeEnabled()).thenReturn(flowOf(false))

        // Mock financial summary
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong()))
            .thenReturn(flowOf(FinancialSummary(totalIncome = 8000.0, totalExpenses = 2000.0)))

        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(testCalendar.timeInMillis - 86400000L))
    }

    private fun createViewModel() {
        viewModel =
            WhatIfViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timeProvider = mockTimeProvider
            )
    }

    @Test
    fun `initial state is empty hypothetical expenses and loaded actuals`() =
        runTest {
            createViewModel()

            val expenses = viewModel.hypotheticalExpenses.first()
            assertTrue(expenses.isEmpty())
            assertEquals(8000L, viewModel.actualMonthlyIncome.first())
            assertEquals(2000L, viewModel.actualMonthlyExpenses.first())
            assertEquals(5000L, viewModel.overallMonthlyBudget.first())
            assertEquals(2000L, viewModel.simulatedExpenses.first())
            assertEquals(3000L, viewModel.simulatedAmountRemaining.first())
            assertFalse(viewModel.isDeficitSpending.first())
        }

    @Test
    fun `addHypotheticalExpense adds an expense correctly`() =
        runTest {
            createViewModel()

            viewModel.addHypotheticalExpense("New Phone", 800L)

            val expenses = viewModel.hypotheticalExpenses.first()
            assertEquals(1, expenses.size)
            assertEquals("New Phone", expenses[0].name)
            assertEquals(800L, expenses[0].amount)
        }

    @Test
    fun `addHypotheticalExpense ignores invalid expense`() =
        runTest {
            createViewModel()

            viewModel.addHypotheticalExpense("", 800L)
            viewModel.addHypotheticalExpense("   ", 800L)
            viewModel.addHypotheticalExpense("Valid", 0L)
            viewModel.addHypotheticalExpense("Invalid", -100L)

            val expenses = viewModel.hypotheticalExpenses.first()
            assertTrue(expenses.isEmpty())
        }

    @Test
    fun `removeHypotheticalExpense removes an expense correctly`() =
        runTest {
            createViewModel()

            viewModel.addHypotheticalExpense("New Phone", 800L)
            val addedExpense = viewModel.hypotheticalExpenses.first().first()

            viewModel.removeHypotheticalExpense(addedExpense.id)

            val expenses = viewModel.hypotheticalExpenses.first()
            assertTrue(expenses.isEmpty())
        }

    @Test
    fun `togglePrivacyMode toggles simulator privacy mode`() =
        runTest {
            createViewModel()

            viewModel.togglePrivacyMode()

            verify(settingsRepository).saveSimulatorPrivacyModeEnabled(true)
        }

    @Test
    fun `simulatedSafeToSpendPerDay calculates positive amount when within budget`() =
        runTest {
            createViewModel()

            // Budget = 5000, Actual Expenses = 2000, Remaining = 3000
            // June has 30 days. Today is June 15. Remaining days = 30 - 15 + 1 = 16 days.
            // 3000.0 / 16 = 187.5 -> roundToLong = 188L
            val safeToSpend = viewModel.simulatedSafeToSpendPerDay.first()
            assertEquals(188L, safeToSpend)
            assertFalse(viewModel.isDeficitSpending.first())
        }

    @Test
    fun `WhatIfViewModel accurately signals deficit spending when simulated expenses exceed budget`() =
        runTest {
            createViewModel()

            // Budget = 5000, Actual Expenses = 2000
            // Add hypothetical expense of 4600 -> Simulated Expenses = 6600, Remaining = -1600
            viewModel.addHypotheticalExpense("Large Purchase", 4600L)

            assertEquals(6600L, viewModel.simulatedExpenses.first())
            assertEquals(-1600L, viewModel.simulatedAmountRemaining.first())
            assertTrue(viewModel.isDeficitSpending.first())

            // Remaining days = 16. -1600.0 / 16 = -100L.
            // Should accurately reflect negative rate instead of clamping to 0L.
            val safeToSpend = viewModel.simulatedSafeToSpendPerDay.first()
            assertEquals(-100L, safeToSpend)
        }

    @Test
    fun `break-even simulation results in zero safe to spend and no deficit`() =
        runTest {
            createViewModel()

            // Budget = 5000, Actual Expenses = 2000
            // Add expense of 3000 -> Remaining = 0
            viewModel.addHypotheticalExpense("Exact Budget Filler", 3000L)

            assertEquals(5000L, viewModel.simulatedExpenses.first())
            assertEquals(0L, viewModel.simulatedAmountRemaining.first())
            assertFalse(viewModel.isDeficitSpending.first())
            assertEquals(0L, viewModel.simulatedSafeToSpendPerDay.first())
        }

    @Test
    fun `initialization queries financial summary with exact month boundaries`() =
        runTest {
            val startCaptor = ArgumentCaptor.forClass(Long::class.java)
            val endCaptor = ArgumentCaptor.forClass(Long::class.java)

            createViewModel()
            viewModel.actualMonthlyIncome.first()

            verify(transactionRepository).getFinancialSummaryForRangeFlow(
                startCaptor.capture(),
                endCaptor.capture()
            )

            val startCal = Calendar.getInstance().apply { timeInMillis = startCaptor.value }
            assertEquals(2026, startCal.get(Calendar.YEAR))
            assertEquals(Calendar.JUNE, startCal.get(Calendar.MONTH))
            assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, startCal.get(Calendar.MINUTE))
            assertEquals(0, startCal.get(Calendar.SECOND))
            assertEquals(0, startCal.get(Calendar.MILLISECOND))

            val endCal = Calendar.getInstance().apply { timeInMillis = endCaptor.value }
            assertEquals(2026, endCal.get(Calendar.YEAR))
            assertEquals(Calendar.JUNE, endCal.get(Calendar.MONTH))
            assertEquals(30, endCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, endCal.get(Calendar.MINUTE))
            assertEquals(59, endCal.get(Calendar.SECOND))
            assertEquals(999, endCal.get(Calendar.MILLISECOND))
        }
}
