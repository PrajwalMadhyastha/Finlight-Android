// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/BudgetViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.content.Context
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Budget
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.Category
import io.pm.finlight.CategoryRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TestApplication
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BudgetViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var budgetRepository: BudgetRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    private lateinit var viewModel: BudgetViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        assertEquals("The budget should be carried over from two months prior.", augustBudget, result)
    }
}