package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator

class AccountViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val mergeAccountsUseCase = ServiceLocator.provideMergeAccountsUseCase(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)

            @Suppress("UNCHECKED_CAST")
            return AccountViewModel(
                repository = accountRepository,
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                mergeAccountsUseCase = mergeAccountsUseCase,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
