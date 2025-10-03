// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/HistoricTripsViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel has been refactored to use
// constructor dependency injection for TripRepository and TransactionRepository.
// It now extends ViewModel instead of AndroidViewModel, decoupling it from the
// Android framework and making it fully unit-testable.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class HistoricTripsViewModel(
    private val tripRepository: TripRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val historicTrips: StateFlow<List<TripWithStats>> = tripRepository.getAllTripsWithStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteTrip(tripId: Int, tagId: Int) {
        viewModelScope.launch {
            // First, remove all associations from the cross-reference table for that tag
            transactionRepository.removeAllTransactionsForTag(tagId)
            // Then, delete the trip record itself
            tripRepository.deleteTripById(tripId)
        }
    }
}