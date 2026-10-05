package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.utils.SystemTimeProvider

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
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
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val mergeTransactionsUseCase = ServiceLocator.provideMergeTransactionsUseCase(application, db)

            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                budgetDao = db.budgetDao(),
                settingsRepository = settingsRepository,
                timeProvider = SystemTimeProvider(),
                recurringTransactionDao = db.recurringTransactionDao(),
                recurringPatternDao = db.recurringPatternDao(),
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
