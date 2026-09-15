package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.SystemTimeProvider

class WhatIfViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WhatIfViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)

            @Suppress("UNCHECKED_CAST")
            return WhatIfViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timeProvider = SystemTimeProvider(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
