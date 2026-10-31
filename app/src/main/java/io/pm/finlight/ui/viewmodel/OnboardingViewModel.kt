package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

class OnboardingViewModel(
    private val application: Application,
    private val categoryRepository: ICategoryRepository,
    private val settingsRepository: ISettingsRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()
    private val _userName = MutableStateFlow("")
    val userName = _userName.asStateFlow()

    private val _isNameValid = MutableStateFlow(true)
    val isNameValid = _isNameValid.asStateFlow()

    private val _monthlyBudget = MutableStateFlow("")
    val monthlyBudget = _monthlyBudget.asStateFlow()

    private val _homeCurrency = MutableStateFlow<CurrencyInfo?>(null)
    val homeCurrency = _homeCurrency.asStateFlow()

    init {
        detectHomeCurrency()
    }

    fun onNameChanged(newName: String) {
        if (newName.length <= 50) {
            _userName.value = newName
            _isNameValid.value = newName.isNotBlank() && newName.all { it.isLetter() || it.isWhitespace() }
        }
    }

    fun onBudgetChanged(newBudget: String) {
        if (newBudget.length <= 12 && newBudget.all { it.isDigit() }) {
            _monthlyBudget.value = newBudget
        }
    }

    private fun detectHomeCurrency() {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val telephonyManager = application.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                // 1. Try Network Country ISO (most reliable without extra permissions)
                val networkCountryIso = telephonyManager.networkCountryIso?.uppercase()
                if (!networkCountryIso.isNullOrBlank()) {
                    val locale = Locale("", networkCountryIso)
                    val currency = Currency.getInstance(locale)
                    _homeCurrency.value =
                        CurrencyInfo(
                            countryName = locale.displayCountry,
                            currencyCode = currency.currencyCode,
                            currencySymbol = currency.getSymbol(locale),
                        )
                    return@launch
                }

                // 2. Fallback to Configuration Locale
                val configLocale =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        application.resources.configuration.locales.get(0)
                    } else {
                        @Suppress("DEPRECATION")
                        application.resources.configuration.locale
                    }
                if (configLocale != null && configLocale.country.isNotBlank()) {
                    val currency = Currency.getInstance(configLocale)
                    _homeCurrency.value =
                        CurrencyInfo(
                            countryName = configLocale.displayCountry,
                            currencyCode = currency.currencyCode,
                            currencySymbol = currency.getSymbol(configLocale),
                        )
                    return@launch
                }

                // 3. Last resort: Default Locale (language preference)
                val defaultLocale = Locale.getDefault()
                val currency = Currency.getInstance(defaultLocale)
                _homeCurrency.value =
                    CurrencyInfo(
                        countryName = defaultLocale.displayCountry,
                        currencyCode = currency.currencyCode,
                        currencySymbol = currency.getSymbol(defaultLocale),
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
        viewModelScope.launch(dispatcherProvider.io) {
            try {
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
            } catch (e: Exception) {
                _uiEvent.send("Error completing onboarding: ${e.message}")
            }
        }
    }
}
