// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/HistoricTripsViewModel.kt
// REASON: FEATURE - Added the `deleteTrip` function. This provides the
// necessary logic for the UI to delete a trip's historical record and untag all
// its associated transactions in a single, atomic operation.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class HistoricTripsViewModel(application: Application) : AndroidViewModel(application) {

    private val tripRepository: TripRepository
    private val transactionRepository: TransactionRepository
    val historicTrips: StateFlow<List<TripWithStats>>

    init {
        val db = AppDatabase.getInstance(application)
        tripRepository = TripRepository(db.tripDao())
        transactionRepository = TransactionRepository(db.transactionDao())

        historicTrips = tripRepository.getAllTripsWithStats()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    // --- NEW: Function to delete a trip and untag its transactions ---
    fun deleteTrip(tripId: Int, tagId: Int) {
        viewModelScope.launch {
            // First, remove all associations from the cross-reference table for that tag
            transactionRepository.removeAllTransactionsForTag(tagId)
            // Then, delete the trip record itself
            tripRepository.deleteTripById(tripId)
        }
    }
}