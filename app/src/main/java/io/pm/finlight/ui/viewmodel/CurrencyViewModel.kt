// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel has been updated to use constructor
// dependency injection for its repository dependencies. This decouples it from
// direct instantiation of data sources, making it fully unit-testable.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.*
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CurrencyViewModel(
    private val settingsRepository: ISettingsRepository,
    private val tripRepository: ITripRepository,
    private val transactionRepository: ITransactionRepository,
    private val tagRepository: ITagRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val mutex = Mutex()

    val homeCurrency: StateFlow<String> =
        settingsRepository.getHomeCurrency()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = "INR",
            )

    val travelModeSettings: StateFlow<TravelModeSettings?> =
        settingsRepository.getTravelModeSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    private val _tripToEdit = MutableStateFlow<TripWithStats?>(null)
    val tripToEdit: StateFlow<TripWithStats?> = _tripToEdit.asStateFlow()

    val allTags: StateFlow<List<Tag>> =
        tagRepository.allTags.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    // --- NEW: This flow gets ALL trips from the database ---
    private val allTrips: StateFlow<List<TripWithStats>> =
        tripRepository.getAllTripsWithStats()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // --- NEW: This flow filters out the active trip for the UI ---
    val historicTrips: StateFlow<List<TripWithStats>> =
        combine(
            allTrips,
            travelModeSettings,
        ) { trips, activeSettings ->
            if (activeSettings == null) {
                // If no trip is active, all trips are considered historic
                trips
            } else {
                // If a trip is active, filter it out from the history list
                trips.filterNot { trip ->
                    trip.tripName == activeSettings.tripName &&
                        trip.startDate == activeSettings.startDate &&
                        trip.endDate == activeSettings.endDate
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun loadTripForEditing(tripId: Int) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                tripRepository.getTripWithStatsById(tripId).collect {
                    _tripToEdit.value = it
                }
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }

    fun clearTripToEdit() {
        _tripToEdit.value = null
    }

    fun deleteTrip(
        tripId: Int,
        tagId: Int,
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                transactionRepository.removeAllTransactionsForTag(tagId)
                tripRepository.deleteTripById(tripId)
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }

    fun saveHomeCurrency(currencyCode: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                settingsRepository.saveHomeCurrency(currencyCode)
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }

    fun saveActiveTravelPlan(newSettings: TravelModeSettings) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                mutex.withLock {
                    val oldSettings = travelModeSettings.first()

                    if (oldSettings != null && (oldSettings.tripName != newSettings.tripName || oldSettings.startDate != newSettings.startDate || oldSettings.endDate != newSettings.endDate)) {
                        val oldTripTag = tagRepository.findOrCreateTag(oldSettings.tripName)
                        transactionRepository.removeTagForDateRange(oldTripTag.id, oldSettings.startDate, oldSettings.endDate)

                        val isOldTagUsedByOtherTrips = tripRepository.isTagUsedByTrip(oldTripTag.id)
                        val isOldTagUsedByOtherTxns = transactionRepository.getTransactionsByTagId(oldTripTag.id).first().isNotEmpty()
                        if (!isOldTagUsedByOtherTrips && !isOldTagUsedByOtherTxns) {
                            tagRepository.delete(oldTripTag)
                        }
                    }

                    settingsRepository.saveTravelModeSettings(newSettings)
                    val newTripTag = tagRepository.findOrCreateTag(newSettings.tripName)
                    transactionRepository.addTagForDateRange(newTripTag.id, newSettings.startDate, newSettings.endDate)
                    val oldTag = oldSettings?.let { tagRepository.findOrCreateTag(it.tripName) }
                    val existingTrip = oldTag?.let { tripRepository.getTripByTagId(it.id) }

                    val tripRecord =
                        Trip(
                            id = existingTrip?.id ?: 0,
                            name = newSettings.tripName,
                            startDate = newSettings.startDate,
                            endDate = newSettings.endDate,
                            tagId = newTripTag.id,
                            tripType = newSettings.tripType,
                            currencyCode = newSettings.currencyCode,
                            conversionRate = newSettings.conversionRate,
                        )
                    tripRepository.insert(tripRecord)
                }
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }

    fun updateHistoricTrip(updatedSettings: TravelModeSettings) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val originalTrip = _tripToEdit.value ?: return@launch

                val originalTag = tagRepository.findTagById(originalTrip.tagId) ?: return@launch
                val newTag = tagRepository.findOrCreateTag(updatedSettings.tripName)

                if (originalTrip.startDate != updatedSettings.startDate || originalTrip.endDate != updatedSettings.endDate || originalTag.id != newTag.id) {
                    transactionRepository.removeTagForDateRange(originalTag.id, originalTrip.startDate, originalTrip.endDate)
                }

                transactionRepository.addTagForDateRange(newTag.id, updatedSettings.startDate, updatedSettings.endDate)

                if (originalTag.id != newTag.id) {
                    val isOldTagUsedByOtherTrips = tripRepository.isTagUsedByTrip(originalTag.id)
                    val isOldTagUsedByOtherTxns = transactionRepository.getTransactionsByTagId(originalTag.id).first().isNotEmpty()
                    if (!isOldTagUsedByOtherTrips && !isOldTagUsedByOtherTxns) {
                        tagRepository.delete(originalTag)
                    }
                }

                val updatedTripRecord =
                    Trip(
                        id = originalTrip.tripId,
                        name = updatedSettings.tripName,
                        startDate = updatedSettings.startDate,
                        endDate = updatedSettings.endDate,
                        tagId = newTag.id,
                        tripType = updatedSettings.tripType,
                        currencyCode = updatedSettings.currencyCode,
                        conversionRate = updatedSettings.conversionRate,
                    )
                tripRepository.insert(updatedTripRecord)
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }

    fun completeTrip() {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                settingsRepository.saveTravelModeSettings(null)
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }

    fun cancelTrip(settings: TravelModeSettings) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val tripTag = tagRepository.findOrCreateTag(settings.tripName)
                val tripRecord = tripRepository.getTripByTagId(tripTag.id)

                transactionRepository.removeTagForDateRange(tripTag.id, settings.startDate, settings.endDate)

                if (tripRecord != null && tripRecord.startDate == settings.startDate && tripRecord.endDate == settings.endDate) {
                    tripRepository.deleteTripById(tripRecord.id)
                }

                settingsRepository.saveTravelModeSettings(null)
            } catch (_: Exception) {
                // Ignore or handle
            }
        }
    }
}
