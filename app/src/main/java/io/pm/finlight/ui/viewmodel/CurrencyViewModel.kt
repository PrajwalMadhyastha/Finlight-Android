// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: FIX - The ViewModel now implements the full retrospective tagging
// logic. `saveTravelModeSettings` and `disableTravelMode` now perform a
// "cleanup and re-apply" operation: they remove the old trip tag from
// transactions in the previous date range before applying the new trip tag to
// transactions in the new date range, ensuring data consistency.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencyViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository
    private val transactionRepository: TransactionRepository
    private val tagRepository: TagRepository

    val homeCurrency: StateFlow<String>
    val travelModeSettings: StateFlow<TravelModeSettings?>

    init {
        val db = AppDatabase.getInstance(application)
        settingsRepository = SettingsRepository(application)
        transactionRepository = TransactionRepository(db.transactionDao())
        tagRepository = TagRepository(db.tagDao(), db.transactionDao())

        homeCurrency = settingsRepository.getHomeCurrency()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = "INR"
            )

        travelModeSettings = settingsRepository.getTravelModeSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }

    fun saveHomeCurrency(currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.saveHomeCurrency(currencyCode)
        }
    }

    fun saveTravelModeSettings(settings: TravelModeSettings) {
        viewModelScope.launch {
            // --- Step 1: Get the current settings for cleanup ---
            val oldSettings = travelModeSettings.first()

            // --- Step 2: Clean up old tags if a previous plan existed ---
            if (oldSettings?.isEnabled == true) {
                val oldTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                transactionRepository.removeTagForDateRange(oldTag.id, oldSettings.startDate, oldSettings.endDate)
            }

            // --- Step 3: Save the new settings to the repository ---
            settingsRepository.saveTravelModeSettings(settings)

            // --- Step 4: Apply new tags if the new plan is enabled ---
            if (settings.isEnabled) {
                val newTag = tagRepository.findOrCreateTag(settings.tripName)
                transactionRepository.addTagForDateRange(newTag.id, settings.startDate, settings.endDate)
            }
        }
    }

    fun disableTravelMode() {
        viewModelScope.launch {
            // --- Step 1: Get the current settings for cleanup ---
            val oldSettings = travelModeSettings.first()

            // --- Step 2: Clean up old tags if a plan was active ---
            if (oldSettings?.isEnabled == true) {
                val oldTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                transactionRepository.removeTagForDateRange(oldTag.id, oldSettings.startDate, oldSettings.endDate)
            }

            // --- Step 3: Save null to disable the mode ---
            settingsRepository.saveTravelModeSettings(null)
        }
    }
}
