// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TripDetailViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel has been refactored to use
// constructor dependency injection for TripRepository and TransactionRepository.
// It now extends ViewModel instead of AndroidViewModel, decoupling it from the
// Android framework and making it fully unit-testable.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TripDetailViewModel(
    tripRepository: TripRepository,
    transactionRepository: TransactionRepository,
    tripId: Int,
    tagId: Int
) : ViewModel() {

    val tripDetails: StateFlow<TripWithStats?>
    val transactions: StateFlow<List<TransactionDetails>>

    init {
        tripDetails = tripRepository.getTripWithStatsById(tripId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

        transactions = transactionRepository.getTransactionsByTagId(tagId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }
}