package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import io.pm.finlight.di.ServiceLocator

class CurrencyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CurrencyViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val tagRepository = ServiceLocator.provideTagRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val tripRepository = TripRepository(db.tripDao())
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)

            @Suppress("UNCHECKED_CAST")
            return CurrencyViewModel(
                settingsRepository = settingsRepository,
                tripRepository = tripRepository,
                transactionRepository = transactionRepository,
                tagRepository = tagRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
