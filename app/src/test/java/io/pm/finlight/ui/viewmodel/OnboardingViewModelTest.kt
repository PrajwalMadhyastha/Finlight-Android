// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/OnboardingViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// FIX (Testing) - Replaced ArgumentCaptor.capture() with the appropriate
// `any...()` matchers inside `never()` verifications to prevent a
// NullPointerException.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class OnboardingViewModelTest : BaseViewModelTest() {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: OnboardingViewModel

    @Before
    override fun setup() {
        super.setup()
        viewModel = OnboardingViewModel(application, categoryRepository, settingsRepository)
    }

    @Test
    fun `initial state detects a home currency`() = runTest {
        // Assert
        viewModel.homeCurrency.test {
            assertNotNull("Home currency should be detected and not null on init", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onNameChanged updates userName state`() = runTest {
        // Act
        val newName = "Test User"
        viewModel.onNameChanged(newName)

        // Assert
        assertEquals(newName, viewModel.userName.value)
    }

    @Test
    fun `onBudgetChanged updates monthlyBudget state with valid input`() = runTest {
        // Act
        val newBudget = "5000"
        viewModel.onBudgetChanged(newBudget)

        // Assert
        assertEquals(newBudget, viewModel.monthlyBudget.value)
    }

    @Test
    fun `onBudgetChanged ignores non-digit input`() = runTest {
        // Act
        viewModel.onBudgetChanged("abc")
        // Assert
        assertEquals("", viewModel.monthlyBudget.value)

        // Act
        viewModel.onBudgetChanged("12a34")
        // Assert
        assertEquals("", viewModel.monthlyBudget.value)
    }

    @Test
    fun `onHomeCurrencyChanged updates homeCurrency state`() = runTest {
        // Arrange
        val newCurrency = CurrencyInfo("Testland", "TSD", "T")

        // Act
        viewModel.onHomeCurrencyChanged(newCurrency)

        // Assert
        assertEquals(newCurrency, viewModel.homeCurrency.value)
    }

    @Test
    fun `finishOnboarding saves user name, currency, categories, and budget`() = runTest {
        // Arrange
        val name = "Test User"
        val budget = "25000"
        val currency = CurrencyHelper.getCurrencyInfo("USD")!!

        // Act
        viewModel.onNameChanged(name)
        viewModel.onBudgetChanged(budget)
        viewModel.onHomeCurrencyChanged(currency)
        viewModel.finishOnboarding()
        advanceUntilIdle()


        // Assert
        verify(settingsRepository).saveUserName(name)
        verify(settingsRepository).saveHomeCurrency(currency.currencyCode)
        verify(categoryRepository).insertAll(CategoryIconHelper.predefinedCategories)
        verify(settingsRepository).saveOverallBudgetForCurrentMonth(budget.toFloat())
    }

    @Test
    fun `finishOnboarding does not save blank user name`() = runTest {
        // Act
        viewModel.onNameChanged("  ")
        viewModel.finishOnboarding()
        advanceUntilIdle()


        // Assert
        verify(settingsRepository, never()).saveUserName(anyString())
    }

    @Test
    fun `finishOnboarding does not save zero or invalid budget`() = runTest {
        // Act
        viewModel.onBudgetChanged("0")
        viewModel.finishOnboarding()
        advanceUntilIdle()
        // Assert
        verify(settingsRepository, never()).saveOverallBudgetForCurrentMonth(anyFloat())

        // Act
        viewModel.onBudgetChanged("")
        viewModel.finishOnboarding()
        advanceUntilIdle()
        // Assert
        verify(settingsRepository, never()).saveOverallBudgetForCurrentMonth(anyFloat())
    }
}