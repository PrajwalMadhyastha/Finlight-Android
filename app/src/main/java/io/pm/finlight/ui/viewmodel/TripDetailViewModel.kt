// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TripDetailViewModel.kt
// REASON: NEW FILE - This ViewModel powers the new Trip Detail screen. It takes a
// tripId and tagId, and fetches the specific trip's stats and its associated
// list of transactions from their respective repositories.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TripDetailViewModelFactory(
    private val application: Application,
    private val tripId: Int,
    private val tagId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TripDetailViewModel(application, tripId, tagId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TripDetailViewModel(
    application: Application,
    tripId: Int,
    tagId: Int
) : AndroidViewModel(application) {

    private val tripRepository: TripRepository
    private val transactionRepository: TransactionRepository

    val tripDetails: StateFlow<TripWithStats?>
    val transactions: StateFlow<List<TransactionDetails>>

    init {
        val db = AppDatabase.getInstance(application)
        tripRepository = TripRepository(db.tripDao())
        transactionRepository = TransactionRepository(db.transactionDao())

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
