// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: FIX - The logic has been fundamentally refactored to fix the travel
// tagging bug. There are now two distinct functions: `saveActiveTravelPlan` for
// managing the current trip (which affects SharedPreferences) and a new
// `updateHistoricTrip` for editing past trips (which only affects the database).
// This separation prevents editing a historic trip from corrupting the active
// trip's settings, thus fixing both retrospective and auto-capture tagging.
// FIX: Added a `clearTripToEdit` function to reset the `tripToEdit` state. This
// is called by the UI when the edit screen is disposed, preventing stale data
// from leaking into the "create new trip" screen.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.*
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

    private val _tripToEdit = MutableStateFlow<TripWithStats?>(null)
    val tripToEdit: StateFlow<TripWithStats?> = _tripToEdit.asStateFlow()

    fun loadTripForEditing(tripId: Int) {
        viewModelScope.launch {
            tripRepository.getTripWithStatsById(tripId).collect {
                _tripToEdit.value = it
            }
        }
    }

    // --- NEW: Function to clear the edit state ---
    fun clearTripToEdit() {
        _tripToEdit.value = null
    }


    fun saveHomeCurrency(currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.saveHomeCurrency(currencyCode)
        }
    }

    /**
     * Manages the active travel plan. This is for creating a NEW plan or updating the CURRENTLY ACTIVE one.
     * This function interacts with SharedPreferences.
     */
    fun saveActiveTravelPlan(newSettings: TravelModeSettings) {
        viewModelScope.launch {
            val oldSettings = travelModeSettings.first()

            // Step 1: Cleanup old tags if the active plan's dates have changed
            if (oldSettings != null && (oldSettings.startDate != newSettings.startDate || oldSettings.endDate != newSettings.endDate)) {
                val oldTripTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                transactionRepository.removeTagForDateRange(oldTripTag.id, oldSettings.startDate, oldSettings.endDate)
            }

            // Step 2: Save the new settings to SharedPreferences to make it the active plan
            settingsRepository.saveTravelModeSettings(newSettings)

            // Step 3: Find or create the tag for the new/updated trip
            val newTripTag = tagRepository.findOrCreateTag(newSettings.tripName)

            // Step 4: Retrospectively apply the tag to all transactions in the new date range
            transactionRepository.addTagForDateRange(newTripTag.id, newSettings.startDate, newSettings.endDate)

            // Step 5: Find the historical record associated with the OLD active plan, if any
            val existingTrip = oldSettings?.let {
                val oldTag = tagRepository.findOrCreateTag(it.tripName)
                tripRepository.getTripByTagId(oldTag.id)
            }

            // Step 6: Create or Update the historical trip record
            val tripRecord = Trip(
                id = existingTrip?.id ?: 0,
                name = newSettings.tripName,
                startDate = newSettings.startDate,
                endDate = newSettings.endDate,
                tagId = newTripTag.id,
                tripType = newSettings.tripType,
                currencyCode = newSettings.currencyCode,
                conversionRate = newSettings.conversionRate
            )
            tripRepository.insert(tripRecord)
        }
    }

    /**
     * Updates a historical trip record. This function does NOT affect the active travel plan
     * and does NOT interact with SharedPreferences.
     */
    fun updateHistoricTrip(updatedSettings: TravelModeSettings) {
        viewModelScope.launch {
            val originalTrip = _tripToEdit.value ?: return@launch

            val originalTag = tagRepository.findTagById(originalTrip.tagId) ?: return@launch
            val newTag = tagRepository.findOrCreateTag(updatedSettings.tripName)

            // Step 1: Cleanup old tags if name or dates have changed
            if (originalTrip.startDate != updatedSettings.startDate || originalTrip.endDate != updatedSettings.endDate || originalTag.id != newTag.id) {
                transactionRepository.removeTagForDateRange(originalTag.id, originalTrip.startDate, originalTrip.endDate)
            }

            // Step 2: Retrospectively apply the tag to all transactions in the new date range
            transactionRepository.addTagForDateRange(newTag.id, updatedSettings.startDate, updatedSettings.endDate)

            // Step 3: Update the historical record in the database
            val updatedTripRecord = Trip(
                id = originalTrip.tripId,
                name = updatedSettings.tripName,
                startDate = updatedSettings.startDate,
                endDate = updatedSettings.endDate,
                tagId = newTag.id,
                tripType = updatedSettings.tripType,
                currencyCode = updatedSettings.currencyCode,
                conversionRate = updatedSettings.conversionRate
            )
            tripRepository.insert(updatedTripRecord)
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
            val tripTag = tagRepository.findOrCreateTag(settings.tripName)
            val tripRecord = tripRepository.getTripByTagId(tripTag.id)

            // This is the crucial fix: only remove tags within the specific trip's date range.
            transactionRepository.removeTagForDateRange(tripTag.id, settings.startDate, settings.endDate)

            // Delete the historical trip record ONLY IF it matches the dates.
            // This prevents accidental deletion if a tag name has been reused.
            if (tripRecord != null && tripRecord.startDate == settings.startDate && tripRecord.endDate == settings.endDate) {
                tripRepository.deleteTripById(tripRecord.id)
            }

            // Finally, clear the active settings
            settingsRepository.saveTravelModeSettings(null)
        }
    }
}