// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: FEATURE - The ViewModel now saves a record of each trip to the new
// `trips` table whenever travel mode settings are saved. This automatically
// builds a travel history for the user. It uses an insert-or-update logic to
// handle both new trips and modifications to existing ones.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencyViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository
    private val transactionRepository: TransactionRepository
    private val tagRepository: TagRepository
    private val tripRepository: TripRepository

    val homeCurrency: StateFlow<String>
    val travelModeSettings: StateFlow<TravelModeSettings?>

    init {
        val db = AppDatabase.getInstance(application)
        settingsRepository = SettingsRepository(application)
        transactionRepository = TransactionRepository(db.transactionDao())
        tagRepository = TagRepository(db.tagDao(), db.transactionDao())
        tripRepository = TripRepository(db.tripDao())

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
            val oldSettings = travelModeSettings.first()

            if (oldSettings?.isEnabled == true && oldSettings.tripName.isNotBlank()) {
                val oldTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                transactionRepository.removeTagForDateRange(oldTag.id, oldSettings.startDate, oldSettings.endDate)
            }

            settingsRepository.saveTravelModeSettings(settings)

            if (settings.isEnabled) {
                val newTag = tagRepository.findOrCreateTag(settings.tripName)
                transactionRepository.addTagForDateRange(newTag.id, settings.startDate, settings.endDate)

                // Find if a trip with this tag already exists to get its ID for update.
                val existingTrip = tripRepository.getTripByTagId(newTag.id)

                val trip = Trip(
                    id = existingTrip?.id ?: 0, // Use existing ID for update, or 0 for new insert
                    name = settings.tripName,
                    startDate = settings.startDate,
                    endDate = settings.endDate,
                    tagId = newTag.id
                )
                tripRepository.insert(trip)
            }
        }
    }

    fun disableTravelMode() {
        viewModelScope.launch {
            val oldSettings = travelModeSettings.first()

            if (oldSettings?.isEnabled == true && oldSettings.tripName.isNotBlank()) {
                val oldTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                transactionRepository.removeTagForDateRange(oldTag.id, oldSettings.startDate, oldSettings.endDate)
            }

            settingsRepository.saveTravelModeSettings(null)
        }
    }
}