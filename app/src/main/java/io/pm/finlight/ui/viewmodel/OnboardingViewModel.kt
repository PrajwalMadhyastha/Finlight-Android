// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/OnboardingViewModel.kt
// REASON: FEATURE - The ViewModel is updated to detect the user's home
// currency from their device locale. It exposes this currency and provides a
// function to save the final selection, integrating currency setup into the
// onboarding flow.
// FIX - The currency detection logic is now more robust. It uses a tiered
// approach, prioritizing the network country over the device's locale to
// provide a more accurate suggestion for the user's home currency.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

class OnboardingViewModel(
    private val application: Application,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _userName = MutableStateFlow("")
    val userName = _userName.asStateFlow()

    private val _monthlyBudget = MutableStateFlow("")
    val monthlyBudget = _monthlyBudget.asStateFlow()

    private val _homeCurrency = MutableStateFlow<CurrencyInfo?>(null)
    val homeCurrency = _homeCurrency.asStateFlow()

    init {
        detectHomeCurrency()
    }

    fun onNameChanged(newName: String) {
        _userName.value = newName
    }

    fun onBudgetChanged(newBudget: String) {
        if (newBudget.all { it.isDigit() }) {
            _monthlyBudget.value = newBudget
        }
    }

    private fun detectHomeCurrency() {
        viewModelScope.launch {
            try {
                val telephonyManager = application.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                // 1. Try Network Country ISO (most reliable without extra permissions)
                val networkCountryIso = telephonyManager.networkCountryIso?.uppercase()
                if (!networkCountryIso.isNullOrBlank()) {
                    val locale = Locale("", networkCountryIso)
                    val currency = Currency.getInstance(locale)
                    _homeCurrency.value = CurrencyInfo(
                        countryName = locale.displayCountry,
                        currencyCode = currency.currencyCode,
                        currencySymbol = currency.getSymbol(locale)
                    )
                    return@launch
                }

                // 2. Fallback to Configuration Locale
                val configLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    application.resources.configuration.locales.get(0)
                } else {
                    @Suppress("DEPRECATION")
                    application.resources.configuration.locale
                }
                if (configLocale != null && configLocale.country.isNotBlank()) {
                    val currency = Currency.getInstance(configLocale)
                    _homeCurrency.value = CurrencyInfo(
                        countryName = configLocale.displayCountry,
                        currencyCode = currency.currencyCode,
                        currencySymbol = currency.getSymbol(configLocale)
                    )
                    return@launch
                }

                // 3. Last resort: Default Locale (language preference)
                val defaultLocale = Locale.getDefault()
                val currency = Currency.getInstance(defaultLocale)
                _homeCurrency.value = CurrencyInfo(
                    countryName = defaultLocale.displayCountry,
                    currencyCode = currency.currencyCode,
                    currencySymbol = currency.getSymbol(defaultLocale)
                )

            } catch (e: Exception) {
                // Final fallback to INR if everything fails
                _homeCurrency.value = CurrencyHelper.getCurrencyInfo("INR")
            }
        }
    }


    fun onHomeCurrencyChanged(currencyInfo: CurrencyInfo) {
        _homeCurrency.value = currencyInfo
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            if (_userName.value.isNotBlank()) {
                settingsRepository.saveUserName(_userName.value)
            }

            _homeCurrency.value?.let {
                settingsRepository.saveHomeCurrency(it.currencyCode)
            }

            categoryRepository.insertAll(CategoryIconHelper.predefinedCategories)

            val budgetFloat = _monthlyBudget.value.toFloatOrNull() ?: 0f
            if (budgetFloat > 0) {
                settingsRepository.saveOverallBudgetForCurrentMonth(budgetFloat)
            }

            // --- FIX: Schedule all default-enabled background workers ---
            ReminderManager.rescheduleAllWork(application)
        }
    }
}
