// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: FEATURE - Added `completeTrip` and `cancelTrip` functions to handle
// the different user intentions for ending a travel plan. The `saveTravelModeSettings`
// function is now responsible for both creating and updating trip records,
// including cleaning up old tags when date ranges change.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencyViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
    private val tripRepository = TripRepository(db.tripDao())
    private val transactionRepository = TransactionRepository(db.transactionDao())

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

    fun saveTravelModeSettings(
        newSettings: TravelModeSettings
    ) {
        viewModelScope.launch {
            val oldSettings = travelModeSettings.first()

            // Step 1: Cleanup old tags if dates have changed
            if (oldSettings != null && (oldSettings.startDate != newSettings.startDate || oldSettings.endDate != newSettings.endDate)) {
                val oldTripTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                transactionRepository.removeTagForDateRange(oldTripTag.id, oldSettings.startDate, oldSettings.endDate)
            }

            // Step 2: Save the new settings to SharedPreferences
            settingsRepository.saveTravelModeSettings(newSettings)

            // Step 3: Find or create the tag for the new/updated trip
            val newTripTag = tagRepository.findOrCreateTag(newSettings.tripName)

            // Step 4: Apply the tag to all transactions in the new date range
            transactionRepository.addTagForDateRange(newTripTag.id, newSettings.startDate, newSettings.endDate)

            // Step 5: Create or update the historical trip record
            val existingTrip = oldSettings?.let {
                val oldTag = tagRepository.findOrCreateTag(it.tripName)
                tripRepository.getTripByTagId(oldTag.id)
            }

            val tripRecord = Trip(
                id = existingTrip?.id ?: 0,
                name = newSettings.tripName,
                startDate = newSettings.startDate,
                endDate = newSettings.endDate,
                tagId = newTripTag.id
            )
            tripRepository.insert(tripRecord)
        }
    }

    fun completeTrip() {
        viewModelScope.launch {
            // Simply clear the active settings. History and tags remain.
            settingsRepository.saveTravelModeSettings(null)
        }
    }

    fun cancelTrip(settings: TravelModeSettings) {
        viewModelScope.launch {
            // Find the associated tag and trip
            val tripTag = tagRepository.findOrCreateTag(settings.tripName)
            val tripRecord = tripRepository.getTripByTagId(tripTag.id)

            // Remove all transaction links for this tag
            transactionRepository.removeAllTransactionsForTag(tripTag.id)

            // Delete the historical trip record if it exists
            tripRecord?.let {
                tripRepository.deleteTripById(it.id)
            }

            // Finally, clear the active settings
            settingsRepository.saveTravelModeSettings(null)
        }
    }
}