package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator

class BudgetViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            val budgetRepository = ServiceLocator.provideBudgetRepository(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)

            @Suppress("UNCHECKED_CAST")
            return BudgetViewModel(
                budgetRepository = budgetRepository,
                settingsRepository = settingsRepository,
                categoryRepository = categoryRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
