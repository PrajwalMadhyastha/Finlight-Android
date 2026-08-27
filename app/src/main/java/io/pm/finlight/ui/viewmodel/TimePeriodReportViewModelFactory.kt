package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase

class TimePeriodReportViewModelFactory(
    private val application: Application,
    private val timePeriod: TimePeriod,
    private val initialDateMillis: Long?,
    private val showPreviousMonth: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimePeriodReportViewModel::class.java)) {
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
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                    dispatcherProvider = dispatcherProvider,
                )

            @Suppress("UNCHECKED_CAST")
            return TimePeriodReportViewModel(
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
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
