// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/DashboardViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class has been updated to extend the new
// `BaseViewModelTest`. All boilerplate for JUnit rules, coroutine dispatchers,
// and Mockito initialization has been removed and is now inherited from the base
// class. The `setup` method is now an override that calls `super.setup()`.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import kotlin.math.roundToLong

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DashboardViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var accountRepository: AccountRepository

    @Mock
    private lateinit var budgetDao: BudgetDao

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: DashboardViewModel

    @Before
    override fun setup() {
        super.setup() // Initializes mocks and sets the main dispatcher

        // --- Set up default mock behaviors BEFORE ViewModel initialization ---
        `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
        `when`(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
        `when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getDashboardVisibleCards()).thenReturn(flowOf(emptySet()))
        `when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(FinancialSummary(0.0, 0.0)))
        runTest {
            `when`(transactionRepository.getTotalExpensesSince(Mockito.anyLong())).thenReturn(0.0)
        }
        `when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(0f))
        `when`(accountRepository.accountsWithBalance).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.recentTransactions).thenReturn(flowOf(emptyList()))
        `when`(
            budgetDao.getBudgetsWithSpendingForMonth(
                Mockito.anyString(),
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        `when`(
            transactionRepository.getDailySpendingForDateRange(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(emptyList()))


        // Initialize the ViewModel with mocked dependencies
        viewModel = DashboardViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            budgetDao = budgetDao,
            settingsRepository = settingsRepository,
        )
    }

    @Test
    fun `test monthly income and expenses are loaded correctly`() = runTest {
        // ARRANGE: Mock the repository to return a specific financial summary for this test
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 1500.0)
        `when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(summary))

        // ACT: Re-initialize ViewModel to pick up the new mock setup
        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        advanceUntilIdle() // Let flows emit

        // ASSERT: Check if the StateFlows in the ViewModel reflect the mocked data
        assertEquals(5000L, viewModel.monthlyIncome.first())
        assertEquals(1500L, viewModel.monthlyExpenses.first())
    }

    @Test
    fun `test amount remaining calculation is correct`() = runTest {
        // ARRANGE
        val budget = 3000f
        val expenses = 1200.0
        `when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(budget))
        `when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(FinancialSummary(0.0, expenses)))

        // ACT
        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        advanceUntilIdle()

        // ASSERT
        val expectedRemaining = budget.roundToLong() - expenses.roundToLong()
        assertEquals(expectedRemaining, viewModel.amountRemaining.first())
    }

    @Test
    fun `budgetHealthSummary shows 'Set a budget' when budget is zero`() = runTest {
        // ARRANGE
        `when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(0f))
        `when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(FinancialSummary(0.0, 1000.0)))

        // ACT
        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        advanceUntilIdle()

        // ASSERT
        assertEquals("Set a budget to see insights", viewModel.budgetHealthSummary.first())
    }

    @Test
    fun `budgetHealthSummary shows 'over budget' message when expenses exceed budget`() = runTest {
        // ARRANGE
        `when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(1000f))
        `when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(FinancialSummary(0.0, 1200.0)))
        `when`(transactionRepository.getTotalExpensesSince(Mockito.anyLong())).thenReturn(200.0)


        // ACT
        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        viewModel.refreshBudgetSummary()
        advanceUntilIdle()

        val possibleMessages = listOf(
            "Pacing a bit high for this month",
            "Time to ease up on spending",
            "Still trending over budget",
            "Let's try to slow things down",
            "Watch the spending for a bit",
            "Heads up: pacing is still high",
            "A bit too fast for this month",
            "Let's pump the brakes slightly",
            "Budget is feeling the pressure",
            "Trending to overspend"
        )
        assertTrue(
            "Summary message should be one of the 'over budget' phrases.",
            viewModel.budgetHealthSummary.first() in possibleMessages
        )
    }


    @Test
    fun `updateCardOrder calls settingsRepository to save layout`() = runTest {
        // ARRANGE
        val initialOrder = listOf(
            DashboardCardType.HERO_BUDGET,
            DashboardCardType.QUICK_ACTIONS,
            DashboardCardType.RECENT_TRANSACTIONS
        )
        val visibleCards = setOf(
            DashboardCardType.HERO_BUDGET,
            DashboardCardType.QUICK_ACTIONS,
            DashboardCardType.RECENT_TRANSACTIONS
        )

        `when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(initialOrder))
        `when`(settingsRepository.getDashboardVisibleCards())
            .thenReturn(flowOf(visibleCards))

        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        advanceUntilIdle()

        // ACT
        viewModel.updateCardOrder(from = 1, to = 2)
        advanceUntilIdle()

        // ASSERT
        val expectedNewOrder = listOf(
            DashboardCardType.HERO_BUDGET,
            DashboardCardType.RECENT_TRANSACTIONS,
            DashboardCardType.QUICK_ACTIONS
        )
        // Verify that the repository's save method was called with the new order
        verify(settingsRepository).saveDashboardLayout(expectedNewOrder, visibleCards)
    }

    @Test
    fun `toggleCardVisibility calls settingsRepository to save layout`() = runTest {
        // ARRANGE
        val initialOrder = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS)
        val initialVisible = setOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS)

        `when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(initialOrder))
        `when`(settingsRepository.getDashboardVisibleCards())
            .thenReturn(flowOf(initialVisible))

        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        advanceUntilIdle()

        // ACT
        viewModel.toggleCardVisibility(DashboardCardType.QUICK_ACTIONS) // Toggle it off
        advanceUntilIdle()

        // ASSERT
        val expectedNewVisible = setOf(DashboardCardType.HERO_BUDGET)
        // Verify that the repository's save method was called with the updated visible set
        verify(settingsRepository).saveDashboardLayout(initialOrder, expectedNewVisible)
    }

    @Test
    fun `monthlyIncomeAndExpenses are converted to Long and rounded correctly`() = runTest {
        // ARRANGE
        val summaryWithDecimals = FinancialSummary(totalIncome = 5000.75, totalExpenses = 1500.25)
        `when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(summaryWithDecimals))

        // ACT
        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        advanceUntilIdle()

        // ASSERT
        val expectedIncome = 5000.75.roundToLong() // Should be 5001
        val expectedExpenses = 1500.25.roundToLong() // Should be 1500

        assertEquals(expectedIncome, viewModel.monthlyIncome.first())
        assertEquals(expectedExpenses, viewModel.monthlyExpenses.first())
    }
}