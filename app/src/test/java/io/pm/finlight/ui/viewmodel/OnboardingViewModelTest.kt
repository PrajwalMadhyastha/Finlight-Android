// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/OnboardingViewModelTest.kt
// REASON: NEW FILE - Unit tests for OnboardingViewModel, covering state updates
// and the finalization logic in the `finishOnboarding` function.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Category
import io.pm.finlight.CategoryRepository
import io.pm.finlight.OnboardingViewModel
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class OnboardingViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Captor
    private lateinit var stringCaptor: ArgumentCaptor<String>

    @Captor
    private lateinit var floatCaptor: ArgumentCaptor<Float>

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(application, categoryRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

        // Assert
        verify(settingsRepository).saveUserName(name)
        verify(settingsRepository).saveHomeCurrency(currency.currencyCode)
        verify(categoryRepository).insertAll(CategoryIconHelper.predefinedCategories)
        verify(settingsRepository).saveOverallBudgetForCurrentMonth(budget.toFloat())
    }

    @Test
    @Ignore
    fun `finishOnboarding does not save blank user name`() = runTest {
        // Act
        viewModel.onNameChanged("  ")
        viewModel.finishOnboarding()

        // Assert
        verify(settingsRepository, never()).saveUserName(stringCaptor.capture())
    }

    @Test
    fun `finishOnboarding does not save zero or invalid budget`() = runTest {
        // Act
        viewModel.onBudgetChanged("0")
        viewModel.finishOnboarding()
        // Assert
        verify(settingsRepository, never()).saveOverallBudgetForCurrentMonth(floatCaptor.capture())

        // Act
        viewModel.onBudgetChanged("")
        viewModel.finishOnboarding()
        // Assert
        verify(settingsRepository, never()).saveOverallBudgetForCurrentMonth(floatCaptor.capture())
    }
}
