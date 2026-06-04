// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/BudgetViewModelTest.kt
//
// REASON: FEATURE (Historical Budgets) - The test `saveOverallBudget
// only saves for the REAL current month` is obsolete and has been replaced with
// `saveOverallBudget calls repository with correct year and month from calendar`.
// This new test validates the refactored function that allows saving budgets
// for any selected month.
//
// REASON: REFACTOR (Dynamic Budget) - This test suite is updated to validate
// the refactored `BudgetViewModel`.
// - It now mocks the new `TransactionRepository` dependency.
// - It tests that `setSelectedMonth` correctly updates the dynamic flows
//   like `overallBudgetForSelectedMonth` and `budgetsForSelectedMonth`.
// - It verifies that `overallBudgetForSelectedMonth` correctly emits `null`
//   when no budget is set.
// - It confirms that `addCategoryBudget` and `getActualSpending` use the
//   `selectedMonth` state.
//
// REASON: FIX (Test) - Added new test `monthlySummaries flow emits correct
// list of budgets` to validate the bug fix for the monthly budget scroller.
// This test confirms the flow now emits the set budget, not the spent amount.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.util.*
import kotlin.math.roundToLong
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BudgetViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var budgetRepository: BudgetRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    // --- NEW: Mock for the added dependency ---
    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var viewModel: BudgetViewModel

    // --- Helper to get a calendar for a specific month ---
    private fun getCalendar(
        year: Int,
        month: Int,
    ): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    @Before
    override fun setup() {
        super.setup()
        // Setup default mocks for initialization
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getBudgetsForMonth(anyInt(), anyInt())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(null)) // Default to null
        `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getActualSpendingForCategory(anyString(), anyInt(), anyInt())).thenReturn(flowOf(0.0))

        // --- NEW: Mocks for new dependencies in init ---
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(System.currentTimeMillis()))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))

        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)
    }

    @Test
    fun `availableCategoriesForNewBudget excludes categories with current or carried-over budgets`() =
        runTest {
            // ARRANGE
            val allCategories =
                listOf(
                    Category(1, "Food", "icon", "color"),
                    Category(2, "Travel", "icon", "color"),
                    Category(3, "Shopping", "icon", "color"),
                )
            val budgetsWithSpending =
                listOf(
                    BudgetWithSpending(Budget(1, "Food", 5000.0, 10, 2025), 100.0, "icon", "color"),
                    BudgetWithSpending(Budget(2, "Travel", 2000.0, 10, 2025), 200.0, "icon", "color"),
                )

            `when`(categoryRepository.allCategories).thenReturn(flowOf(allCategories))
            `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(budgetsWithSpending))

            // ACT: Re-initialize ViewModel to pick up new mock setup.
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)

            // ASSERT
            viewModel.availableCategoriesForNewBudget.test {
                val availableCategories = awaitItem()
                assertEquals(1, availableCategories.size)
                assertEquals("Shopping", availableCategories.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- NEW: Test for dynamic month selection ---
    @Test
    fun `setSelectedMonth triggers refetch of budget data`() =
        runTest {
            // ARRANGE
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1

            // --- FIX: Correctly get previous month and its year safely ---
            val prevMonthCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            val prevYear = prevMonthCal.get(Calendar.YEAR)
            val prevMonth = prevMonthCal.get(Calendar.MONTH) + 1

            val currentMonthBudget = 10000f
            val prevMonthBudget = 5000f

            // Mock settings repo to return different budgets for different months
            `when`(settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)).thenReturn(flowOf(currentMonthBudget))
            `when`(settingsRepository.getOverallBudgetForMonth(prevYear, prevMonth)).thenReturn(flowOf(prevMonthBudget))

            // --- Re-initialize ViewModel *after* test-specific mocks are set ---
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)

            // ACT & ASSERT
            viewModel.overallBudgetForSelectedMonth.test {
                // Awaits the initial value for the current month
                assertEquals(currentMonthBudget, awaitItem())

                // Act: Change the selected month
                viewModel.setSelectedMonth(prevMonthCal)

                // Await the new value for the previous month
                assertEquals(prevMonthBudget, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // Verify the repository was called for both months
            verify(settingsRepository).getOverallBudgetForMonth(currentYear, currentMonth)
            verify(settingsRepository).getOverallBudgetForMonth(prevYear, prevMonth)
        }

    // --- NEW: Test for "Not Set" (null) budget ---
    @Test
    fun `overallBudgetForSelectedMonth emits null when no budget is found`() =
        runTest {
            // ARRANGE
            `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(null))

            // ACT
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)

            // ASSERT
            viewModel.overallBudgetForSelectedMonth.test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- NEW: Test refactored addCategoryBudget ---
    @Test
    fun `addCategoryBudget uses selectedMonth for new budget`() =
        runTest {
            // ARRANGE
            val sept2025 = getCalendar(2025, Calendar.SEPTEMBER)
            viewModel.setSelectedMonth(sept2025)
            val budgetCaptor = argumentCaptor<Budget>()

            // ACT
            viewModel.addCategoryBudget("Groceries", "500")
            advanceUntilIdle()

            // ASSERT
            verify(budgetRepository).insert(capture(budgetCaptor))
            val capturedBudget = budgetCaptor.value
            assertEquals("Groceries", capturedBudget.categoryName)
            assertEquals(500.0, capturedBudget.amount, 0.0)
            assertEquals(9, capturedBudget.month) // September is month 9 (index 8 + 1)
            assertEquals(2025, capturedBudget.year)
        }

    // --- NEW: Test refactored getActualSpending ---
    @Test
    fun `getActualSpending uses selectedMonth for query`() =
        runTest {
            // ARRANGE
            val sept2025 = getCalendar(2025, Calendar.SEPTEMBER)
            viewModel.setSelectedMonth(sept2025)
            `when`(budgetRepository.getActualSpendingForCategory("Food", 9, 2025)).thenReturn(flowOf(123.45))

            // ACT & ASSERT
            viewModel.getActualSpending("Food").test {
                assertEquals(123L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(budgetRepository).getActualSpendingForCategory("Food", 9, 2025)
        }

    // --- UPDATED TEST ---
    @Test
    fun `saveOverallBudget calls repository with correct year and month from calendar`() =
        runTest {
            // ARRANGE
            val budgetStr = "12345"
            val budgetFloat = 12345f
            val testCalendar =
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, 2023)
                    set(Calendar.MONTH, Calendar.JULY) // July is 6
                }
            val expectedYear = 2023
            val expectedMonth = 7 // July is 6 + 1

            // ACT
            viewModel.saveOverallBudget(budgetStr, testCalendar)

            // ASSERT
            // Verify the new repository function was called with the *exact* year and month.
            verify(settingsRepository).saveOverallBudgetForMonth(expectedYear, expectedMonth, budgetFloat)
            // Verify the old function was NOT called.
            verify(settingsRepository, never()).saveOverallBudgetForCurrentMonth(any())
        }

    // --- NEW: Test for the bug fix in monthlySummaries ---
    @Test
    fun `monthlySummaries flow emits correct list of budgets`() =
        runTest {
            // Arrange
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1 // e.g., 10 for Oct

            cal.add(Calendar.MONTH, -1)
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = cal.get(Calendar.MONTH) + 1 // e.g., 9 for Sep

            cal.add(Calendar.MONTH, -1)
            val twoMonthsAgoYear = cal.get(Calendar.YEAR)
            val twoMonthsAgoMonth = cal.get(Calendar.MONTH) + 1 // e.g., 8 for Aug
            val firstTxDate = cal.timeInMillis // Start date is 2 months ago

            val currentBudget = 1000f
            val prevBudget = 500f
            // No budget (null) for two months ago

            `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            `when`(settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)).thenReturn(flowOf(currentBudget))
            `when`(settingsRepository.getOverallBudgetForMonth(prevYear, prevMonth)).thenReturn(flowOf(prevBudget))
            `when`(settingsRepository.getOverallBudgetForMonth(twoMonthsAgoYear, twoMonthsAgoMonth)).thenReturn(flowOf(null))

            // Act
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)
            advanceUntilIdle() // Let the flow combine and emit

            // Assert
            viewModel.monthlySummaries.test {
                val summaries = awaitItem()

                // Summaries are reversed (most recent first)
                assertEquals(3, summaries.size)

                // 1. Current Month
                assertEquals(currentYear, summaries[0].first.get(Calendar.YEAR))
                assertEquals(currentMonth - 1, summaries[0].first.get(Calendar.MONTH)) // Calendar month is 0-indexed
                assertEquals(currentBudget, summaries[0].second)

                // 2. Previous Month
                assertEquals(prevYear, summaries[1].first.get(Calendar.YEAR))
                assertEquals(prevMonth - 1, summaries[1].first.get(Calendar.MONTH))
                assertEquals(prevBudget, summaries[1].second)

                // 3. Two Months Ago
                assertEquals(twoMonthsAgoYear, summaries[2].first.get(Calendar.YEAR))
                assertEquals(twoMonthsAgoMonth - 1, summaries[2].first.get(Calendar.MONTH))
                assertNull(summaries[2].second) // Should be null

                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Existing Tests (Updated) ---

    @Test
    fun `totalSpending reflects transactions even when no category budgets are defined`() =
        runTest {
            // ARRANGE: No category budgets in this month
            val totalMonthlyExpenses = 3500.0
            `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(emptyList()))

            // Mock the repository call for total spending
            `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong()))
                .thenReturn(flowOf(FinancialSummary(totalIncome = 0.0, totalExpenses = totalMonthlyExpenses)))

            // ACT
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)
            advanceUntilIdle()

            // ASSERT
            val actualSpending = viewModel.totalSpendingForSelectedMonth.first()

            // It should reflect the total Monthly Expenses even though there were no category budgets
            assertEquals(totalMonthlyExpenses.roundToLong(), actualSpending)
        }

    @Test
    fun `totalSpending and overallBudget are converted to Long`() =
        runTest {
            // ARRANGE
            val overallBudgetFloat = 10000.55f
            val spendingPerCategory = 1234.56
            val totalMonthlyExpenses = 5000.75

            `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(overallBudgetFloat))
            `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(
                flowOf(
                    listOf(
                        BudgetWithSpending(Budget(1, "Food", 5000.0, 10, 2025), spendingPerCategory, "icon", "color"),
                    ),
                ),
            )
            `when`(budgetRepository.getActualSpendingForCategory(anyString(), anyInt(), anyInt())).thenReturn(flowOf(spendingPerCategory))

            // --- FIX: Mock the new repository call for total spending ---
            `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong()))
                .thenReturn(flowOf(FinancialSummary(totalIncome = 0.0, totalExpenses = totalMonthlyExpenses)))

            // ACT
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)
            advanceUntilIdle()

            // ASSERT
            val actualBudget = viewModel.overallBudgetForSelectedMonth.first()
            val actualSpending = viewModel.totalSpendingForSelectedMonth.first()

            assertEquals(overallBudgetFloat, actualBudget)
            // Verify that it now uses the total Monthly Expenses from the repository, not category sum
            assertEquals(totalMonthlyExpenses.roundToLong(), actualSpending)
        }

    @Test
    fun `addCategoryBudget with invalid amount sends error event`() =
        runTest {
            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.addCategoryBudget("Food", "0")
                advanceUntilIdle()
                assertEquals("Please enter a valid amount and select a category.", awaitItem())
                verify(budgetRepository, never()).insert(anyObject())
            }
        }

    @Test
    fun `addCategoryBudget failure sends error event`() =
        runTest {
            // Arrange
            val errorMessage = "DB Error"
            `when`(budgetRepository.insert(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.addCategoryBudget("Food", "100")
                advanceUntilIdle()
                assertEquals("Error adding budget: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `updateBudget success sends success event`() =
        runTest {
            // Arrange
            val budget = Budget(1, "Food", 100.0, 1, 2025)

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.updateBudget(budget)
                advanceUntilIdle()
                verify(budgetRepository).update(budget)
                assertEquals("Budget for '${budget.categoryName}' updated.", awaitItem())
            }
        }

    @Test
    fun `updateBudget failure sends error event`() =
        runTest {
            // Arrange
            val budget = Budget(1, "Food", 100.0, 1, 2025)
            val errorMessage = "DB Error"
            `when`(budgetRepository.update(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.updateBudget(budget)
                advanceUntilIdle()
                assertEquals("Error updating budget: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `deleteBudget success sends success event`() =
        runTest {
            // Arrange
            val budget = Budget(1, "Food", 100.0, 1, 2025)

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.deleteBudget(budget)
                advanceUntilIdle()
                verify(budgetRepository).delete(budget)
                assertEquals("Budget for '${budget.categoryName}' deleted.", awaitItem())
            }
        }

    @Test
    fun `deleteBudget failure sends error event`() =
        runTest {
            // Arrange
            val budget = Budget(1, "Food", 100.0, 1, 2025)
            val errorMessage = "DB Error"
            `when`(budgetRepository.delete(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.deleteBudget(budget)
                advanceUntilIdle()
                assertEquals("Error deleting budget: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `getBudgetById calls repository`() =
        runTest {
            // Arrange
            val budgetId = 1
            val budget = Budget(id = budgetId, categoryName = "Test", amount = 1.0, month = 1, year = 2025)
            `when`(budgetRepository.getBudgetById(budgetId)).thenReturn(flowOf(budget))

            // Act & Assert
            viewModel.getBudgetById(budgetId).test {
                assertEquals(budget, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(budgetRepository).getBudgetById(budgetId)
        }

    // --- NEW: Tests for Annual Budget Planning ---

    @Test
    fun `saveAnnualOverallBudget saves flexible correctly`() =
        runTest {
            val year = 2026
            val target = "12000"

            `when`(settingsRepository.getOverallBudgetsForYear(year)).thenReturn(emptyMap())

            viewModel.setSelectedPlanningYear(year)
            viewModel.saveAnnualOverallBudget(target, false)
            advanceUntilIdle()

            for (month in 1..12) {
                verify(settingsRepository).saveOverallBudgetForMonth(year, month, 1000f)
            }
        }

    @Test
    fun `saveAnnualOverallBudget saves strict correctly`() =
        runTest {
            val year = 2026
            val target = "14000"

            `when`(settingsRepository.getOverallBudgetsForYear(year)).thenReturn(mapOf(1 to 3000f))

            viewModel.setSelectedPlanningYear(year)
            viewModel.saveAnnualOverallBudget(target, true)
            advanceUntilIdle()

            for (month in 2..12) {
                verify(settingsRepository).saveOverallBudgetForMonth(year, month, 1000f)
            }
            verify(settingsRepository, never()).saveOverallBudgetForMonth(year, 1, 3000f)
        }

    @Test
    fun `saveAnnualCategoryBudget saves strict correctly`() =
        runTest {
            val year = 2026
            val categoryName = "Food"
            val target = "1400"

            val existing = listOf(Budget(id = 1, categoryName = categoryName, amount = 300.0, month = 1, year = year))
            `when`(budgetRepository.getBudgetsForCategoryAndYear(categoryName, year)).thenReturn(existing)

            viewModel.setSelectedPlanningYear(year)
            viewModel.saveAnnualCategoryBudget(categoryName, target, true)
            advanceUntilIdle()

            val captor = org.mockito.kotlin.argumentCaptor<List<Budget>>()
            verify(budgetRepository).insertAll(captor.capture())
            val insertedBudgets = captor.firstValue

            assertEquals(11, insertedBudgets.size)
            assertEquals(true, insertedBudgets.all { it.amount == 100.0 })
        }

    @Test
    fun `saveAnnualOverallBudget with invalid amount sends error event`() =
        runTest {
            viewModel.uiEvent.test {
                viewModel.saveAnnualOverallBudget("-100", true)
                assertEquals("Please enter a valid amount.", awaitItem())
                verify(settingsRepository, never()).saveOverallBudgetForMonth(anyInt(), anyInt(), anyFloat())
            }
        }

    @Test
    fun `saveAnnualOverallBudget strict with overrides exceeding target sends error`() =
        runTest {
            val year = 2026
            `when`(settingsRepository.getOverallBudgetsForYear(year)).thenReturn(mapOf(1 to 5000f, 2 to 6000f)) // sum 11000

            viewModel.setSelectedPlanningYear(year)
            viewModel.uiEvent.test {
                viewModel.saveAnnualOverallBudget("10000", true)
                assertEquals("Overrides exceed annual target.", awaitItem())
                verify(settingsRepository, never()).saveOverallBudgetForMonth(anyInt(), anyInt(), anyFloat())
            }
        }

    @Test
    fun `saveAnnualOverallBudget exception sends error event`() =
        runTest {
            val year = 2026
            `when`(settingsRepository.getOverallBudgetsForYear(year)).thenReturn(emptyMap())
            `when`(settingsRepository.saveOverallBudgetForMonth(anyInt(), anyInt(), anyFloat())).thenThrow(RuntimeException("DB Error"))

            viewModel.setSelectedPlanningYear(year)
            viewModel.uiEvent.test {
                viewModel.saveAnnualOverallBudget("10000", true)
                assertEquals("Error saving annual budget: DB Error", awaitItem())
            }
        }

    @Test
    fun `saveAnnualCategoryBudget with invalid amount sends error event`() =
        runTest {
            viewModel.uiEvent.test {
                viewModel.saveAnnualCategoryBudget("Food", "-100", true)
                assertEquals("Please enter a valid amount and select a category.", awaitItem())
                verify(budgetRepository, never()).insertAll(any())
            }
        }

    @Test
    fun `saveAnnualCategoryBudget strict with overrides exceeding target sends error`() =
        runTest {
            val year = 2026
            val categoryName = "Food"
            val existing =
                listOf(
                    Budget(1, categoryName, 5000.0, 1, year),
                    Budget(2, categoryName, 6000.0, 2, year)
                )
            `when`(budgetRepository.getBudgetsForCategoryAndYear(categoryName, year)).thenReturn(existing)

            viewModel.setSelectedPlanningYear(year)
            viewModel.uiEvent.test {
                viewModel.saveAnnualCategoryBudget(categoryName, "10000", true)
                assertEquals("Overrides exceed annual target.", awaitItem())
                verify(budgetRepository, never()).insertAll(any())
            }
        }

    @Test
    fun `saveAnnualCategoryBudget exception sends error event`() =
        runTest {
            val year = 2026
            val categoryName = "Food"
            `when`(budgetRepository.getBudgetsForCategoryAndYear(categoryName, year)).thenReturn(emptyList())
            `when`(budgetRepository.insertAll(any())).thenThrow(RuntimeException("DB Error"))

            viewModel.setSelectedPlanningYear(year)
            viewModel.uiEvent.test {
                viewModel.saveAnnualCategoryBudget(categoryName, "10000", true)
                assertEquals("Error saving annual budget: DB Error", awaitItem())
            }
        }

    @Test
    fun `refreshAnnualSummaries populates overall and category summaries`() =
        runTest {
            val year = 2026
            `when`(settingsRepository.getOverallBudgetsForYear(year)).thenReturn(mapOf(1 to 1000f, 2 to 2000f))

            val categories = listOf(Category(1, "Food", "icon1", "color1"))
            `when`(categoryRepository.allCategories).thenReturn(flowOf(categories))

            val existingCatBudgets = listOf(Budget(1, "Food", 500.0, 1, year))
            `when`(budgetRepository.getBudgetsForCategoryAndYear("Food", year)).thenReturn(existingCatBudgets)

            // Reset view model with the new mock behaviors
            viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)

            viewModel.setSelectedPlanningYear(year)
            advanceUntilIdle()

            viewModel.annualOverallSummary.test {
                val summary = awaitItem()
                assertEquals(3000f, summary?.totalBudget)
                assertEquals(2, summary?.overrideCount)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.annualCategorySummaries.test {
                val summaries = awaitItem()
                assertEquals(1, summaries.size)
                assertEquals("Food", summaries[0].categoryName)
                assertEquals(500.0, summaries[0].totalBudget, 0.0)
                assertEquals(1, summaries[0].overrideCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
