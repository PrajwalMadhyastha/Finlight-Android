// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/HistoricTripsViewModel.kt
// REASON: NEW FILE - This ViewModel provides the data for the new Travel History
// screen. It fetches a list of all historical trips along with their calculated
// spending stats from the TripRepository.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoricTripsViewModel(application: Application) : AndroidViewModel(application) {

    private val tripRepository: TripRepository

    val historicTrips: StateFlow<List<TripWithStats>>

    init {
        val db = AppDatabase.getInstance(application)
        tripRepository = TripRepository(db.tripDao())

        historicTrips = tripRepository.getAllTripsWithStats()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }
}
