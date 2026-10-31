package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TransactionDetails
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class TripDetailViewModel(
    tripRepository: ITripRepository,
    transactionRepository: ITransactionRepository,
    tripId: Int,
    tagId: Int,
    dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    val tripDetails: StateFlow<TripWithStats?>
    val transactions: StateFlow<List<TransactionDetails>>

    init {
        tripDetails =
            tripRepository.getTripWithStatsById(tripId)
                .flowOn(dispatcherProvider.io)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        transactions =
            transactionRepository.getTransactionsByTagId(tagId)
                .flowOn(dispatcherProvider.io)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )
    }
}
