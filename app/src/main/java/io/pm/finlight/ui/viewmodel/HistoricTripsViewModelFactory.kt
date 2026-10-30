package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import io.pm.finlight.di.ServiceLocator

class HistoricTripsViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoricTripsViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val tripRepository = TripRepository(db.tripDao())
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)

            @Suppress("UNCHECKED_CAST")
            return HistoricTripsViewModel(
                tripRepository = tripRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
