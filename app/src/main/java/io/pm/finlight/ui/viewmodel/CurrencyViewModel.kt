// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: FIX - The `cancelTrip` function has been corrected. It now calls
// `removeTagForDateRange` instead of the destructive `removeAllTransactionsForTag`.
// This ensures that canceling a trip only untags transactions within that
// specific trip's date range, preserving the integrity of historical trips
// that may share the same name/tag.
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
                id = existingTrip?.id ?: _tripToEdit.value?.tripId ?: 0,
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

            // --- FIX: Use the date-ranged removal to preserve history ---
            // This was the source of the critical bug.
            transactionRepository.removeTagForDateRange(tripTag.id, settings.startDate, settings.endDate)

            // Delete the historical trip record ONLY IF it matches the dates.
            // This is a safety check in case of tag name reuse.
            if (tripRecord != null && tripRecord.startDate == settings.startDate && tripRecord.endDate == settings.endDate) {
                tripRepository.deleteTripById(tripRecord.id)
            }

            // Finally, clear the active settings
            settingsRepository.saveTravelModeSettings(null)
        }
    }
}
