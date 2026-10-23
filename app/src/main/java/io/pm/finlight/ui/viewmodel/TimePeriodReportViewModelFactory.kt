package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.di.ServiceLocator

class TimePeriodReportViewModelFactory(
    private val application: Application,
    private val timePeriod: TimePeriod,
    private val initialDateMillis: Long?,
    private val showPreviousMonth: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimePeriodReportViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val getMonthlyConsistencyDataUseCase = ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)

            @Suppress("UNCHECKED_CAST")
            return TimePeriodReportViewModel(
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timePeriod = timePeriod,
                initialDateMillis = initialDateMillis,
                showPreviousMonth = showPreviousMonth,
                dispatcherProvider = dispatcherProvider,
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
