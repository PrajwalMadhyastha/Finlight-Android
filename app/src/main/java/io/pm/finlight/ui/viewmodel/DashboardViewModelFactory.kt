package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.SystemTimeProvider

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val getMonthlyConsistencyDataUseCase =
                ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val mergeTransactionsUseCase = ServiceLocator.provideMergeTransactionsUseCase(application)
            val budgetRepository = ServiceLocator.provideBudgetRepository(application)
            val recurringTransactionRepository = ServiceLocator.provideRecurringTransactionRepository(application)

            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                budgetRepository = budgetRepository,
                settingsRepository = settingsRepository,
                timeProvider = SystemTimeProvider(),
                recurringTransactionRepository = recurringTransactionRepository,
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
