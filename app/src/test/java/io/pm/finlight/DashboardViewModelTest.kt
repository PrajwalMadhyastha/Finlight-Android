// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/DashboardViewModelTest.kt
// REASON: FIX(test) - Moved the SQLCipher library loading logic from an `init`
// block to a `@BeforeClass` annotated function. This ensures the native
// libraries are loaded before Robolectric instantiates the MainApplication,
// resolving the UnsatisfiedLinkError during test setup.
// =================================================================================
package io.pm.finlight

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sqlcipher.database.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
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

    companion object {
        // --- NEW: Use @BeforeClass to load libraries before the test class is even instantiated ---
        @BeforeClass
        @JvmStatic
        fun setupClass() {
            SQLiteDatabase.loadLibs(ApplicationProvider.getApplicationContext())
        }
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // --- FIX: Set up default mock behaviors BEFORE ViewModel initialization ---
        // These are required for the ViewModel's init block to succeed.
        // Individual tests can override these with more specific `when` clauses.
        `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
        `when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getDashboardVisibleCards()).thenReturn(flowOf(emptySet()))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, 0.0)))
        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(0f))
        `when`(accountRepository.accountsWithBalance).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.recentTransactions).thenReturn(flowOf(emptyList()))
        `when`(budgetDao.getBudgetsWithSpendingForMonth(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        `when`(transactionRepository.getDailySpendingForDateRange(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(emptyList()))


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
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(summary))

        // ACT: Re-initialize ViewModel to pick up the new mock setup
        viewModel = DashboardViewModel(transactionRepository, accountRepository, budgetDao, settingsRepository)
        advanceUntilIdle() // Let flows emit

        // ASSERT: Check if the StateFlows in the ViewModel reflect the mocked data
        assertEquals(5000.0, viewModel.monthlyIncome.first(), 0.0)
        assertEquals(1500.0, viewModel.monthlyExpenses.first(), 0.0)
    }

    @Test
    fun `test amount remaining calculation is correct`() = runTest {
        // ARRANGE
        val budget = 3000f
        val expenses = 1200.0
        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(budget))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, expenses)))

        // ACT
        viewModel = DashboardViewModel(transactionRepository, accountRepository, budgetDao, settingsRepository)
        advanceUntilIdle()

        // ASSERT
        val expectedRemaining = budget - expenses.toFloat()
        assertEquals(expectedRemaining, viewModel.amountRemaining.first(), 0.01f)
    }

    @Test
    fun `budgetHealthSummary shows 'Set a budget' when budget is zero`() = runTest {
        // ARRANGE
        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(0f))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, 1000.0)))

        // ACT
        viewModel = DashboardViewModel(transactionRepository, accountRepository, budgetDao, settingsRepository)
        advanceUntilIdle()

        // ASSERT
        assertEquals("Set a budget to see insights", viewModel.budgetHealthSummary.first())
    }

    @Test
    fun `budgetHealthSummary shows 'over budget' message when expenses exceed budget`() = runTest {
        // ARRANGE
        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(1000f))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, 1200.0)))

        // ACT
        viewModel = DashboardViewModel(transactionRepository, accountRepository, budgetDao, settingsRepository)
        viewModel.refreshBudgetSummary() // Trigger a new phrase selection
        advanceUntilIdle()

        // ASSERT
        val possibleMessages = listOf(
            "You've gone over for ${viewModel.monthYear}.",
            "Let's get back on track next month.",
            "Budget exceeded for the month."
        )
        assertTrue(
            "Summary message should be one of the 'over budget' phrases.",
            viewModel.budgetHealthSummary.first() in possibleMessages
        )
    }

    @Test
    fun `updateCardOrder calls settingsRepository to save layout`() = runTest {
        // ARRANGE
        val initialOrder = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS, DashboardCardType.RECENT_TRANSACTIONS)
        val visibleCards = setOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS, DashboardCardType.RECENT_TRANSACTIONS)

        `when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(initialOrder))
        `when`(settingsRepository.getDashboardVisibleCards()).thenReturn(flowOf(visibleCards))

        viewModel = DashboardViewModel(transactionRepository, accountRepository, budgetDao, settingsRepository)
        advanceUntilIdle()

        // ACT
        viewModel.updateCardOrder(from = 1, to = 2)
        advanceUntilIdle()

        // ASSERT
        val expectedNewOrder = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.RECENT_TRANSACTIONS, DashboardCardType.QUICK_ACTIONS)
        // Verify that the repository's save method was called with the new order
        verify(settingsRepository).saveDashboardLayout(expectedNewOrder, visibleCards)
    }

    @Test
    fun `toggleCardVisibility calls settingsRepository to save layout`() = runTest {
        // ARRANGE
        val initialOrder = listOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS)
        val initialVisible = setOf(DashboardCardType.HERO_BUDGET, DashboardCardType.QUICK_ACTIONS)

        `when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(initialOrder))
        `when`(settingsRepository.getDashboardVisibleCards()).thenReturn(flowOf(initialVisible))

        viewModel = DashboardViewModel(transactionRepository, accountRepository, budgetDao, settingsRepository)
        advanceUntilIdle()

        // ACT
        viewModel.toggleCardVisibility(DashboardCardType.QUICK_ACTIONS) // Toggle it off
        advanceUntilIdle()

        // ASSERT
        val expectedNewVisible = setOf(DashboardCardType.HERO_BUDGET)
        // Verify that the repository's save method was called with the updated visible set
        verify(settingsRepository).saveDashboardLayout(initialOrder, expectedNewVisible)
    }
}
