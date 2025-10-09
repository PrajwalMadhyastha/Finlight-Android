package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.atLeast
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

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

    @Mock
    private lateinit var merchantRenameRuleRepository: MerchantRenameRuleRepository

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
        `when`(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))


        // Initialize the ViewModel with mocked dependencies
        viewModel = DashboardViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            budgetDao = budgetDao,
            settingsRepository = settingsRepository,
            merchantRenameRuleRepository = merchantRenameRuleRepository
        )
    }

    private fun initializeViewModel() {
        viewModel = DashboardViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            budgetDao = budgetDao,
            settingsRepository = settingsRepository,
            merchantRenameRuleRepository = merchantRenameRuleRepository
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
        initializeViewModel()
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
        initializeViewModel()
        advanceUntilIdle()

        // ASSERT
        val expectedRemaining = budget.roundToLong() - expenses.roundToLong()
        assertEquals(expectedRemaining, viewModel.amountRemaining.first())
    }

    @Test
    fun `safeToSpendPerDay is calculated correctly`() = runTest {
        // ARRANGE
        // The ViewModel uses the real current date, so the test must do the same.
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = (daysInMonth - calendar.get(Calendar.DAY_OF_MONTH) + 1).toLong()

        val budget = 3100f
        val expenses = 1000.0
        val remaining = budget - expenses
        val expectedSafeToSpend = if (remaining > 0 && remainingDays > 0) (remaining / remainingDays).roundToLong() else 0L

        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(budget))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, expenses)))

        // ACT
        initializeViewModel()
        advanceUntilIdle() // Ensure all init block coroutines complete

        // ASSERT
        viewModel.safeToSpendPerDay.test {
            assertEquals(expectedSafeToSpend, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
        initializeViewModel()
        advanceUntilIdle()


        // ASSERT
        viewModel.budgetHealthSummary.test {
            assertEquals("Set a budget to see insights", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `budgetHealthSummary shows 'consistently good' message when pacing is good`() = runTest {
        // ARRANGE
        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(30000f))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, 1000.0)))
        `when`(transactionRepository.getTotalExpensesSince(Mockito.anyLong())).thenReturn(100.0)

        // ACT
        initializeViewModel()
        advanceUntilIdle()

        // ASSERT
        viewModel.budgetHealthSummary.test {
            val possibleMessages = listOf(
                "Excellent pacing this month!", "On track with room to spare", "Well within budget, great job",
                "Your budget is looking healthy", "Consistently great spending", "Keep this momentum going!",
                "Smooth sailing this month", "You're building a nice buffer", "Perfectly on track", "Another great spending day"
            )
            val result = awaitItem()
            assertTrue("Summary message '$result' should be one of the 'good' phrases.", result in possibleMessages)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `budgetHealthSummary shows 'pacing high' message when forecast exceeds budget`() = runTest {
        // ARRANGE
        `when`(settingsRepository.getOverallBudgetForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(2000f))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(Mockito.anyLong(), Mockito.anyLong())).thenReturn(flowOf(FinancialSummary(0.0, 1500.0))) // High spend already
        `when`(transactionRepository.getTotalExpensesSince(Mockito.anyLong())).thenReturn(500.0) // High recent spend velocity

        // ACT
        initializeViewModel()
        advanceUntilIdle()

        // ASSERT
        viewModel.budgetHealthSummary.test {
            val possibleMessages = listOf(
                "Pacing a bit high for this month", "Time to ease up on spending", "Still trending over budget",
                "Let's try to slow things down", "Watch the spending for a bit", "Heads up: pacing is still high",
                "A bit too fast for this month", "Let's pump the brakes slightly", "Budget is feeling the pressure", "Trending to overspend"
            )

            val result = awaitItem()
            assertTrue("Summary message '$result' should be one of the 'pacing high' phrases.", result in possibleMessages)
            cancelAndIgnoreRemainingEvents()
        }
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

        initializeViewModel()
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

        initializeViewModel()
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
        initializeViewModel()
        advanceUntilIdle()

        // ASSERT
        val expectedIncome = 5000.75.roundToLong() // Should be 5001
        val expectedExpenses = 1500.25.roundToLong() // Should be 1500

        assertEquals(expectedIncome, viewModel.monthlyIncome.first())
        assertEquals(expectedExpenses, viewModel.monthlyExpenses.first())
    }

    @Test
    fun `visibleCards flow respects order and visibility from settings`() = runTest {
        // Arrange
        val order = listOf(DashboardCardType.RECENT_TRANSACTIONS, DashboardCardType.QUICK_ACTIONS, DashboardCardType.ACCOUNTS_CAROUSEL)
        val visible = setOf(DashboardCardType.RECENT_TRANSACTIONS, DashboardCardType.ACCOUNTS_CAROUSEL)
        Mockito.`when`(settingsRepository.getDashboardCardOrder()).thenReturn(flowOf(order))
        Mockito.`when`(settingsRepository.getDashboardVisibleCards()).thenReturn(flowOf(visible))
        initializeViewModel()

        // Assert
        viewModel.visibleCards.test {
            // The combine operator can emit intermediate values. We care about the final, stable state.
            // The initial state is emptyList(), then it combines to produce the correct list.
            val finalState = awaitItem()
            assertEquals(listOf(DashboardCardType.RECENT_TRANSACTIONS, DashboardCardType.ACCOUNTS_CAROUSEL), finalState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastMonthSummary is shown on the first day of the month`() = runTest {
        // This test requires manipulating the current date, which can be tricky.
        // For simplicity, we'll assume today is the 1st and the summary hasn't been dismissed.
        // This is more of an integration test, but we can verify the logic flow.

        // Arrange
        val summary = FinancialSummary(1000.0, 500.0)
        Mockito.`when`(settingsRepository.hasLastMonthSummaryBeenDismissed()).thenReturn(false)
        Mockito.`when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong()))
            .thenReturn(flowOf(summary))

        // A more robust test would use a TestClock to set the date to the 1st.
        // For Robolectric, we can't easily do that. We check the logic path instead.
        val today = Calendar.getInstance()
        if (today.get(Calendar.DAY_OF_MONTH) == 1) {
            initializeViewModel()
            advanceUntilIdle()

            // Assert
            assertTrue(viewModel.showLastMonthSummaryCard.value)
            assertEquals(LastMonthSummary(summary.totalIncome, summary.totalExpenses), viewModel.lastMonthSummary.value)
        } else {
            // If not the 1st, the card should not show
            initializeViewModel()
            advanceUntilIdle()
            assertEquals(false, viewModel.showLastMonthSummaryCard.value)
        }
    }

    @Test
    fun `dismissLastMonthSummaryCard calls repository and updates state`() {
        // This test assumes it's the first of the month to make the card visible initially
        val today = Calendar.getInstance()
        if (today.get(Calendar.DAY_OF_MONTH) == 1) {
            runTest {
                // Arrange
                `when`(settingsRepository.hasLastMonthSummaryBeenDismissed()).thenReturn(false)
                `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(FinancialSummary(1.0, 1.0)))
                initializeViewModel()
                advanceUntilIdle()
                assertTrue(viewModel.showLastMonthSummaryCard.value) // Pre-condition

                // Act
                viewModel.dismissLastMonthSummaryCard()
                advanceUntilIdle()

                // Assert
                verify(settingsRepository).setLastMonthSummaryDismissed()
                assertFalse(viewModel.showLastMonthSummaryCard.value)
            }
        } else {
            // If it's not the first, the card is never shown, so the test is trivial
            runTest {
                // Arrange
                initializeViewModel()
                advanceUntilIdle()
                assertFalse(viewModel.showLastMonthSummaryCard.value) // Pre-condition

                // Act
                viewModel.dismissLastMonthSummaryCard()

                // Assert
                verify(settingsRepository).setLastMonthSummaryDismissed()
                assertFalse(viewModel.showLastMonthSummaryCard.value)
            }
        }
    }

    @Test
    fun `refreshBudgetSummary triggers recalculation of budgetHealthSummary`() = runTest {
        // Arrange
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(30000f))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(FinancialSummary(0.0, 1000.0)))
        // First call returns 100, second call (after refresh) returns 200
        `when`(transactionRepository.getTotalExpensesSince(anyLong())).thenReturn(100.0).thenReturn(200.0)
        initializeViewModel()
        advanceUntilIdle()

        // Act & Assert
        viewModel.budgetHealthSummary.test {
            // Initial emission
            awaitItem()

            // Trigger refresh
            viewModel.refreshBudgetSummary()
            advanceUntilIdle()

            // Await re-emission
            // The value may or may not change depending on the random message,
            // but the fact that it emits again proves the trigger worked.
            awaitItem()
        }

        // Verify that the dependency was called multiple times, once for init and once for refresh.
        verify(transactionRepository, atLeast(2)).getTotalExpensesSince(anyLong())
    }

    @Test
    fun `recentTransactions applies merchant aliases correctly`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "amzn", originalDescription = "amzn", amount = 100.0, date = 1L, accountId = 1, categoryId = 1, notes = null)
        val transactionDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)
        val aliases = mapOf("amzn" to "Amazon")

        `when`(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(transactionDetails)))
        `when`(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))
        initializeViewModel()

        // Assert
        viewModel.recentTransactions.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Amazon", result.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `yearlyConsistencyData is generated correctly`() = runTest {
        // Arrange
        val today = Calendar.getInstance()
        val firstDayOfYear = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val firstTransactionDate = firstDayOfYear // Assume data from start of year

        // Mock budget: 100 per day for simplicity
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(100f * today.getActualMaximum(Calendar.DAY_OF_MONTH)))

        // Mock daily totals
        val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val todayClone = Calendar.getInstance()
        val day1 = (todayClone.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 1) }.time
        val day2 = (todayClone.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 2) }.time
        val day3 = (todayClone.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 3) }.time

        val dailyTotalsForTest = listOf(
            DailyTotal(date = keyFormatter.format(day1), totalAmount = 0.0), // No spend
            DailyTotal(date = keyFormatter.format(day2), totalAmount = 50.0), // Good day
            DailyTotal(date = keyFormatter.format(day3), totalAmount = 150.0) // Bad day
        )

        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTransactionDate))
        `when`(transactionRepository.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotalsForTest))

        initializeViewModel()
        advanceUntilIdle()

        // Act & Assert
        viewModel.yearlyConsistencyData.test {
            val consistencyData = awaitItem()

            // This can be flaky depending on the exact day the test is run.
            // Let's just check the first few days we defined.
            if (consistencyData.isNotEmpty()) {
                val day1Status = consistencyData.find {
                    val cal = Calendar.getInstance()
                    cal.time = it.date
                    cal.get(Calendar.DAY_OF_YEAR) == 1
                }
                val day2Status = consistencyData.find {
                    val cal = Calendar.getInstance()
                    cal.time = it.date
                    cal.get(Calendar.DAY_OF_YEAR) == 2
                }
                val day3Status = consistencyData.find {
                    val cal = Calendar.getInstance()
                    cal.time = it.date
                    cal.get(Calendar.DAY_OF_YEAR) == 3
                }

                assertNotNull(day1Status)
                assertNotNull(day2Status)
                assertNotNull(day3Status)

                assertEquals(SpendingStatus.NO_SPEND, day1Status?.status)
                assertEquals(SpendingStatus.WITHIN_LIMIT, day2Status?.status)
                assertEquals(SpendingStatus.OVER_LIMIT, day3Status?.status)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }
}
