package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator

class IncomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IncomeViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)

            @Suppress("UNCHECKED_CAST")
            return IncomeViewModel(
                transactionRepository,
                accountRepository,
                categoryRepository,
                settingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
