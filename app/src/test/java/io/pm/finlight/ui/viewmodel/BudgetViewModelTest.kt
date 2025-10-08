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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.math.roundToLong

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

    private lateinit var viewModel: BudgetViewModel

    @Before
    override fun setup() {
        super.setup()
        // Setup default mocks for initialization
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getBudgetsForMonth(anyInt(), anyInt())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))
        `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getActualSpendingForCategory(anyString(), anyInt(), anyInt())).thenReturn(flowOf(0.0))

        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository)
    }

    @Test
    fun `availableCategoriesForNewBudget excludes categories with current or carried-over budgets`() = runTest {
        // ARRANGE
        val allCategories = listOf(
            Category(1, "Food", "icon", "color"),
            Category(2, "Travel", "icon", "color"),
            Category(3, "Shopping", "icon", "color")
        )
        // This list simulates the DAO result from getBudgetsWithSpendingForMonth, which includes carried-over budgets.
        // Let's say "Food" and "Travel" both have budgets for the current period (either set this month or carried over).
        val budgetsWithSpending = listOf(
            BudgetWithSpending(Budget(1, "Food", 5000.0, 10, 2025), 100.0, "icon", "color"),
            BudgetWithSpending(Budget(2, "Travel", 2000.0, 10, 2025), 200.0, "icon", "color")
        )

        // Mock the repository calls needed for the ViewModel's init block.
        `when`(categoryRepository.allCategories).thenReturn(flowOf(allCategories))
        `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(budgetsWithSpending))

        // ACT: Re-initialize ViewModel to pick up new mock setup.
        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository)

        // ASSERT: Check that only the "Shopping" category is available for a new budget.
        viewModel.availableCategoriesForNewBudget.test {
            val availableCategories = awaitItem()
            assertEquals(1, availableCategories.size)
            assertEquals("Shopping", availableCategories.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `overallBudget carries forward from older month when immediate previous month is skipped`() = runTest {
        // ARRANGE
        // Use Robolectric's application context to get a real SharedPreferences instance for this test.
        // This allows us to test the actual lookback logic in the real SettingsRepository.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPrefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        testPrefs.edit().clear().apply() // Clean slate for the test

        val calendar = Calendar.getInstance() // Assume current month is October
        calendar.set(Calendar.MONTH, Calendar.OCTOBER)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        val augustBudget = 50000f
        // Set a budget for August (two months prior)
        testPrefs.edit().putFloat("overall_budget_${currentYear}_08", augustBudget).apply()
        // Ensure September has no budget
        testPrefs.edit().remove("overall_budget_${currentYear}_09").apply()

        val realSettingsRepository = SettingsRepository(context)

        // Mock other dependencies that are not under test
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getBudgetsForMonth(Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getBudgetsForMonthWithSpending(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getActualSpendingForCategory(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(0.0))

        // ACT
        // Instantiate the ViewModel with the real SettingsRepository and other mocks
        viewModel = BudgetViewModel(budgetRepository, realSettingsRepository, categoryRepository)
        advanceUntilIdle() // Allow the callbackFlow in SettingsRepository to emit

        // ASSERT
        // The overallBudget flow should find the August budget, skipping September.
        val result = viewModel.overallBudget.first()
        assertEquals("The budget should be carried over from two months prior.", augustBudget.roundToLong(), result)
    }

    @Test
    fun `totalSpending and overallBudget are converted to Long`() = runTest {
        // ARRANGE
        val overallBudgetFloat = 10000.55f
        val spendingPerCategory = 1234.56

        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(overallBudgetFloat))
        `when`(budgetRepository.getBudgetsForMonthWithSpending(anyString(), anyInt(), anyInt())).thenReturn(flowOf(listOf(
            // Mock a single budget item
            BudgetWithSpending(
                Budget(1, "Food", 5000.0, 10, 2025),
                spendingPerCategory,
                "icon",
                "color"
            )
        )))
        `when`(budgetRepository.getActualSpendingForCategory(anyString(), anyInt(), anyInt())).thenReturn(flowOf(spendingPerCategory))


        // ACT
        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository)
        advanceUntilIdle()

        // ASSERT
        val actualBudget = viewModel.overallBudget.first()
        val actualSpending = viewModel.totalSpending.first()

        assertEquals(overallBudgetFloat.roundToLong(), actualBudget)
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
}
