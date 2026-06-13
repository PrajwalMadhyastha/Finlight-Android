package io.pm.finlight.ui.viewmodel

import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.WhatIfViewModel
import io.pm.finlight.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
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

        // Mock transactions repository
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(testCalendar.timeInMillis - 86400000L))

        // Note: The daily spending calculation inside getMonthlyConsistencyData is complex to mock fully,
        // but for WhatIfViewModel, we only care about base budgets if we are to use safeToSpend directly.
        // Actually, WhatIfViewModel relies on settingsRepository.getOverallBudgetForMonth and transactionDao?
        // Wait, WhatIfViewModel needs transactionRepository.getMonthlyConsistencyData to return a flow.
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
    fun `initial state is empty hypothetical expenses`() =
        runTest {
            // We will just mock getOverallBudgetForMonth since WhatIfViewModel accesses it directly
            // WhatIfViewModel uses `getMonthlyConsistencyData` from TransactionRepository?
            // Actually, let's just initialize and test state.

            // Wait, WhatIfViewModel actually relies on `SettingsRepository.getOverallBudgetForMonth`
            // and doesn't call `getMonthlyConsistencyData`. Let's check WhatIfViewModel implementation.
            createViewModel()

            val expenses = viewModel.hypotheticalExpenses.first()
            assertTrue(expenses.isEmpty())
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

            // Initially false, so toggling should save true
            verify(settingsRepository).saveSimulatorPrivacyModeEnabled(true)
        }
}
