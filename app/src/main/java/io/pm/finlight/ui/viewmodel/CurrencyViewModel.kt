// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: FEATURE - Unified Travel Mode. The `saveTravelModeSettings` function
// has been updated to accept the new `TravelModeSettings` data class, which
// now supports both domestic and international trip details.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencyViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val homeCurrency: StateFlow<String> = settingsRepository.getHomeCurrency()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "INR"
        )

    val travelModeSettings: StateFlow<TravelModeSettings?> = settingsRepository.getTravelModeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun saveHomeCurrency(currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.saveHomeCurrency(currencyCode)
        }
    }

    fun saveTravelModeSettings(settings: TravelModeSettings) {
        viewModelScope.launch {
            settingsRepository.saveTravelModeSettings(settings)
        }
    }

    fun disableTravelMode() {
        viewModelScope.launch {
            settingsRepository.saveTravelModeSettings(null)
        }
    }
}
