package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoricTripsViewModel(
    private val tripRepository: ITripRepository,
    private val transactionRepository: ITransactionRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val historicTrips: StateFlow<List<TripWithStats>> =
        tripRepository.getAllTripsWithStats()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    fun deleteTrip(
        tripId: Int,
        tagId: Int,
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                // First, remove all associations from the cross-reference table for that tag
                transactionRepository.removeAllTransactionsForTag(tagId)
                // Then, delete the trip record itself
                tripRepository.deleteTripById(tripId)
            } catch (e: Exception) {
                _uiEvent.send("Error deleting trip: ${e.message}")
            }
        }
    }
}
