package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator

class ReportsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportsViewModel::class.java)) {
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)
            val getMonthlyConsistencyDataUseCase = ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)

            @Suppress("UNCHECKED_CAST")
            return ReportsViewModel(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
