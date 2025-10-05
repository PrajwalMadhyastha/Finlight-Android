// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/BudgetViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
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
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
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
    }

    @Test
    fun `availableCategoriesForNewBudget only shows categories without a current month budget`() = runTest {
        // ARRANGE
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        val allCategories = listOf(
            Category(1, "Food", "restaurant", "red_light"),
            Category(2, "Shopping", "shopping_bag", "blue_light"),
            Category(3, "Travel", "travel_explore", "green_light")
        )

        // "Food" already has a budget specifically for this month
        val budgetsForCurrentMonth = listOf(
            Budget(101, "Food", 5000.0, currentMonth, currentYear)
        )

        // Mock the repository calls needed for the ViewModel's init block
        `when`(categoryRepository.allCategories).thenReturn(flowOf(allCategories))
        `when`(budgetRepository.getBudgetsForMonth(currentMonth, currentYear)).thenReturn(flowOf(budgetsForCurrentMonth))
        `when`(settingsRepository.getOverallBudgetForMonth(currentMonth, currentYear)).thenReturn(flowOf(10000f))
        `when`(budgetRepository.getBudgetsForMonthWithSpending(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getActualSpendingForCategory(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(flowOf(0.0))


        // ACT
        // ViewModel logic runs in the init block. We instantiate it directly with mocks.
        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository)

        // ASSERT
        val availableCategories = viewModel.availableCategoriesForNewBudget.first()
        val availableCategoryNames = availableCategories.map { it.name }

        assertEquals(2, availableCategories.size)
        assertTrue("'Shopping' should be available as it has no budget this month", availableCategoryNames.contains("Shopping"))
        assertTrue("'Travel' should be available as it has no budget this month", availableCategoryNames.contains("Travel"))
        assertTrue("'Food' should NOT be available as it already has a budget this month", !availableCategoryNames.contains("Food"))
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
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(budgetRepository.getBudgetsForMonth(anyInt(), anyInt())).thenReturn(flowOf(emptyList()))


        // ACT
        viewModel = BudgetViewModel(budgetRepository, settingsRepository, categoryRepository)
        advanceUntilIdle()

        // ASSERT
        val actualBudget = viewModel.overallBudget.first()
        val actualSpending = viewModel.totalSpending.first()

        assertEquals(overallBudgetFloat.roundToLong(), actualBudget)
        assertEquals(spendingPerCategory.roundToLong(), actualSpending)
    }
}