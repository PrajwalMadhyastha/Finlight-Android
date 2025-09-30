// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/DashboardViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DashboardViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var accountRepository: AccountRepository

    @Mock
    private lateinit var budgetDao: BudgetDao

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: DashboardViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // --- FIX: Set up default mock behaviors BEFORE ViewModel initialization ---
        Mockito.`when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        Mockito.`when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
        Mockito.`when`(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
        Mockito.`when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(emptyList()))
        Mockito.`when`(settingsRepository.getDashboardVisibleCards()).thenReturn(flowOf(emptySet()))
        Mockito.`when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(FinancialSummary(0.0, 0.0)))
        // --- FIX: Added mock for the new dependency, wrapped in runTest ---
        runTest {
            Mockito.`when`(transactionRepository.getTotalExpensesSince(Mockito.anyLong())).thenReturn(0.0)
        }
        Mockito.`when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(0f))
        Mockito.`when`(accountRepository.accountsWithBalance).thenReturn(flowOf(emptyList()))
        Mockito.`when`(transactionRepository.recentTransactions).thenReturn(flowOf(emptyList()))
        Mockito.`when`(
            budgetDao.getBudgetsWithSpendingForMonth(
                Mockito.anyString(),
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(emptyList()))
        Mockito.`when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        Mockito.`when`(
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test monthly income and expenses are loaded correctly`() = runTest {
        // ARRANGE: Mock the repository to return a specific financial summary for this test
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 1500.0)
        Mockito.`when`(
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
        Assert.assertEquals(5000.0, viewModel.monthlyIncome.first(), 0.0)
        Assert.assertEquals(1500.0, viewModel.monthlyExpenses.first(), 0.0)
    }

    @Test
    fun `test amount remaining calculation is correct`() = runTest {
        // ARRANGE
        val budget = 3000f
        val expenses = 1200.0
        Mockito.`when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(budget))
        Mockito.`when`(
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
        val expectedRemaining = budget - expenses.toFloat()
        Assert.assertEquals(expectedRemaining, viewModel.amountRemaining.first(), 0.01f)
    }

    @Test
    fun `budgetHealthSummary shows 'Set a budget' when budget is zero`() = runTest {
        // ARRANGE
        Mockito.`when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(0f))
        Mockito.`when`(
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
        Assert.assertEquals("Set a budget to see insights", viewModel.budgetHealthSummary.first())
    }

    @Test
    fun `budgetHealthSummary shows 'over budget' message when expenses exceed budget`() = runTest {
        // ARRANGE
        Mockito.`when`(
            settingsRepository.getOverallBudgetForMonth(
                Mockito.anyInt(),
                Mockito.anyInt()
            )
        ).thenReturn(flowOf(1000f))
        Mockito.`when`(
            transactionRepository.getFinancialSummaryForRangeFlow(
                Mockito.anyLong(),
                Mockito.anyLong()
            )
        ).thenReturn(flowOf(FinancialSummary(0.0, 1200.0)))
        Mockito.`when`(transactionRepository.getTotalExpensesSince(Mockito.anyLong())).thenReturn(200.0)


        // ACT
        viewModel = DashboardViewModel(
            transactionRepository,
            accountRepository,
            budgetDao,
            settingsRepository
        )
        viewModel.refreshBudgetSummary()
        advanceUntilIdle()

        // --- FIX: Removed the trailing periods to match the ViewModel's actual output ---
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
        Assert.assertTrue(
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

        Mockito.`when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(initialOrder))
        Mockito.`when`(settingsRepository.getDashboardVisibleCards())
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
        Mockito.verify(settingsRepository).saveDashboardLayout(expectedNewOrder, visibleCards)
    }

    @Test
    fun `toggleCardVisibility calls settingsRepository to save layout`() = runTest {
        // ARRANGE
        val initialOrder = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS)
        val initialVisible = setOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS)

        Mockito.`when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(initialOrder))
        Mockito.`when`(settingsRepository.getDashboardVisibleCards())
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
        Mockito.verify(settingsRepository).saveDashboardLayout(initialOrder, expectedNewVisible)
    }
}
