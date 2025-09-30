// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/BudgetViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
}