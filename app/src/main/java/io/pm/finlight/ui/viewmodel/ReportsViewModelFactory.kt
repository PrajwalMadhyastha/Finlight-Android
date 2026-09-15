package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ReportsViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase

class ReportsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportsViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val getMonthlyConsistencyDataUseCase =
                GetMonthlyConsistencyDataUseCase(
                    settingsRepository = settingsRepository,
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    dispatcherProvider = dispatcherProvider,
                )
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)

            @Suppress("UNCHECKED_CAST")
            return ReportsViewModel(
                transactionRepository = transactionRepository,
                categoryDao = db.categoryDao(),
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
