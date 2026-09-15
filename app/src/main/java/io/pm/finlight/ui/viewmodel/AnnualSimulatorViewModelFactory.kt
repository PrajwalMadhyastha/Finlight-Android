package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator

class AnnualSimulatorViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnnualSimulatorViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            @Suppress("UNCHECKED_CAST")
            return AnnualSimulatorViewModel(transactionRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
