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
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import io.pm.finlight.utils.ReminderManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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
        // --- FIX: Manually initialize WorkManager for testing ---
        // This is required because the OnboardingViewModel now triggers WorkManager
        // tasks via ReminderManager.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(application, config)

        viewModel = OnboardingViewModel(application, categoryRepository, settingsRepository)
    }

    @After
    override fun tearDown() {
        // Clean up static mocks after tests
        unmockkObject(ReminderManager)
        super.tearDown()
    }


    @Test
    fun `detects currency from network country iso`() = runTest {
        // Arrange
        val shadowTelephonyManager = org.robolectric.Shadows.shadowOf(
            application.getSystemService(android.content.Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        )
        shadowTelephonyManager.setNetworkCountryIso("us") // United States -> USD

        // Act
        // Re-init viewModel to trigger init block
        viewModel = OnboardingViewModel(application, categoryRepository, settingsRepository)

        // Assert
        viewModel.homeCurrency.test {
            val currencyInfo = awaitItem()
            assertNotNull(currencyInfo)
            assertEquals("USD", currencyInfo?.currencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `detects currency from resource configuration locale when network iso missing`() = runTest {
        // Arrange
        val shadowTelephonyManager = org.robolectric.Shadows.shadowOf(
            application.getSystemService(android.content.Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        )
        shadowTelephonyManager.setNetworkCountryIso("") // Missing Network ISO

        // Set configuration locale to Japan (JPY)
        val res = application.resources
        val config = res.configuration
        config.setLocale(java.util.Locale.JAPAN)
        res.updateConfiguration(config, res.displayMetrics)

        // Act
        viewModel = OnboardingViewModel(application, categoryRepository, settingsRepository)

        // Assert
        viewModel.homeCurrency.test {
            val currencyInfo = awaitItem()
            assertNotNull(currencyInfo)
            assertEquals("JPY", currencyInfo?.currencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `detects currency from default locale when others missing`() = runTest {
        // Arrange
        val shadowTelephonyManager = org.robolectric.Shadows.shadowOf(
            application.getSystemService(android.content.Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        )
        shadowTelephonyManager.setNetworkCountryIso("") // Missing Network ISO

        // Clear configuration locale (roughly speaking, or set to something neutral if possible, but Robolectric keeps defaults)
        // Ideally we set Default Locale and ensure Config is empty or ignored.
        // However, OnboardingViewModel prioritizes Config over Default.
        // So we must ensure Config is "invalid" or "empty" country to fall through?
        // Code: if (configLocale != null && configLocale.country.isNotBlank())
        
        // Let's set Config to a locale with no country, e.g. just "en"
        val res = application.resources
        val config = res.configuration
        config.setLocale(java.util.Locale("en", "")) // No country
        res.updateConfiguration(config, res.displayMetrics)

        // Set Default Locale to UK (GBP)
        java.util.Locale.setDefault(java.util.Locale.UK)

        // Act
        viewModel = OnboardingViewModel(application, categoryRepository, settingsRepository)

        // Assert
        viewModel.homeCurrency.test {
            val currencyInfo = awaitItem()
            assertNotNull(currencyInfo)
            assertEquals("GBP", currencyInfo?.currencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `falls back to INR on exception`() = runTest {
        // Arrange
        // Mock application to throw exception when accessing system service
        val mockApp = mock(Application::class.java)
        `when`(mockApp.getSystemService(anyString())).thenThrow(RuntimeException("System Service Error"))

        // Act
        viewModel = OnboardingViewModel(mockApp, categoryRepository, settingsRepository)

        // Assert
        viewModel.homeCurrency.test {
            val currencyInfo = awaitItem()
            assertNotNull(currencyInfo)
            assertEquals("INR", currencyInfo?.currencyCode) // Fallback
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

    @Test
    fun `finishOnboarding schedules default workers via ReminderManager`() = runTest {
        // Arrange
        mockkObject(ReminderManager)
        every { ReminderManager.rescheduleAllWork(any()) } just runs

        // Act
        viewModel.finishOnboarding()
        advanceUntilIdle() // Ensure the coroutine in finishOnboarding completes

        // Assert
        verify(exactly = 1) { ReminderManager.rescheduleAllWork(application) }
    }
}