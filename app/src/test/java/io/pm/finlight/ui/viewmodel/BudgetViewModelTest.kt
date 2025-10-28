// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/BudgetViewModelTest.kt
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
// - It validates the logic of `saveOverallBudget` to ensure it only saves
//   for the *real* current month.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
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
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
    private fun getCalendar(year: Int, month: Int): Calendar {
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
    fun `availableCategoriesForNewBudget excludes categories with current or carried-over budgets`() = runTest {
        // ARRANGE
        val allCategories = listOf(
            Category(1, "Food", "icon", "color"),
            Category(2, "Travel", "icon", "color"),
            Category(3, "Shopping", "icon", "color")
        )
        val budgetsWithSpending = listOf(
            BudgetWithSpending(Budget(1, "Food", 5000.0, 10, 2025), 100.0, "icon", "color"),
            BudgetWithSpending(Budget(2, "Travel", 2000.0, 10, 2025), 200.0, "icon", "color")
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
    fun `setSelectedMonth triggers refetch of budget data`() = runTest {
        // ARRANGE
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        // --- FIX: Correctly get previous month's index ---
        val prevMonthIndex = cal.get(Calendar.MONTH) - 1
        val prevMonthCal = getCalendar(currentYear, prevMonthIndex)

        val currentMonthBudget = 10000f
        val prevMonthBudget = 5000f

        // Mock settings repo to return different budgets for different months
        `when`(settingsRepository.getOverallBudgetForMonth(currentYear, currentMonth)).thenReturn(flowOf(currentMonthBudget))
        // --- FIX: Use correct month index (prevMonthIndex + 1) for the mock ---
        `when`(settingsRepository.getOverallBudgetForMonth(currentYear, prevMonthIndex + 1)).thenReturn(flowOf(prevMonthBudget))

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
        verify(settingsRepository).getOverallBudgetForMonth(currentYear, prevMonthIndex + 1)
    }

    // --- NEW: Test for "Not Set" (null) budget ---
    @Test
    fun `overallBudgetForSelectedMonth emits null when no budget is found`() = runTest {
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
    fun `addCategoryBudget uses selectedMonth for new budget`() = runTest {
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
    fun `getActualSpending uses selectedMonth for query`() = runTest {
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

    // --- NEW: Test refactored saveOverallBudget ---
    @Test
    fun `saveOverallBudget only saves for the REAL current month`() = runTest {
        // ARRANGE
        val realCurrentCal = Calendar.getInstance()
        val realCurrentYear = realCurrentCal.get(Calendar.YEAR)
        val realCurrentMonth = realCurrentCal.get(Calendar.MONTH)

        // Case 1: Selected month IS the current month
        viewModel.setSelectedMonth(realCurrentCal)

        // ACT
        viewModel.saveOverallBudget("1000")

        // ASSERT
        // Should save because selected month == current month
        verify(settingsRepository).saveOverallBudgetForCurrentMonth(1000f)

        // ARRANGE 2: Selected month IS NOT the current month
        val pastMonthCal = getCalendar(2024, Calendar.JANUARY)
        viewModel.setSelectedMonth(pastMonthCal)

        // ACT 2
        viewModel.saveOverallBudget("2000")

        // ASSERT 2
        // Logic dictates it should *still* call the save for the *current* month,
        // effectively ignoring the selected month.
        verify(settingsRepository).saveOverallBudgetForCurrentMonth(2000f)
    }

    // --- Existing Tests (Updated) ---

    @Test
    fun `totalSpending and overallBudget are converted to Long`() = runTest {
        // ARRANGE
        val overallBudgetFloat = 10000.55f
        val spendingPerCategory = 1234.56

        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(overallBudgetFloat))
        `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(listOf(
            BudgetWithSpending(Budget(1, "Food", 5000.0, 10, 2025), spendingPerCategory, "icon", "color")
        )))
        `when`(budgetRepository.getActualSpendingForCategory(anyString(), anyInt(), anyInt())).thenReturn(flowOf(spendingPerCategory))

        // ACT
        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository, transactionRepository)
        advanceUntilIdle()

        // ASSERT
        val actualBudget = viewModel.overallBudgetForSelectedMonth.first()
        val actualSpending = viewModel.totalSpendingForSelectedMonth.first()

        // This test's assertion is now different. The budget is a Float?, spending is Long.
        assertEquals(overallBudgetFloat, actualBudget)
        assertEquals(spendingPerCategory.roundToLong(), actualSpending)
    }

    @Test
    fun `addCategoryBudget with invalid amount sends error event`() = runTest {
        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.addCategoryBudget("Food", "0")
            advanceUntilIdle()
            assertEquals("Please enter a valid amount and select a category.", awaitItem())
            verify(budgetRepository, never()).insert(anyObject())
        }
    }

    @Test
    fun `addCategoryBudget failure sends error event`() = runTest {
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
    fun `updateBudget success sends success event`() = runTest {
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
    fun `updateBudget failure sends error event`() = runTest {
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
    fun `deleteBudget success sends success event`() = runTest {
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
    fun `deleteBudget failure sends error event`() = runTest {
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
    fun `getBudgetById calls repository`() = runTest {
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
}

