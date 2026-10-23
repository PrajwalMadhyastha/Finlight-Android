// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/PendingTransactionsViewModelFactory.kt
// REASON: NEW FILE (Issue #338) - Factory to instantiate PendingTransactionsViewModel
// with injected ITransactionRepository, IRecurringTransactionRepository, and DispatcherProvider.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

class PendingTransactionsViewModelFactory(
    private val application: Application,
    private val transactionRepository: ITransactionRepository? = null,
    private val recurringTransactionRepository: IRecurringTransactionRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PendingTransactionsViewModel::class.java)) {
            val resolvedTransactionRepo =
                transactionRepository ?: ServiceLocator.provideTransactionRepository(application)
            val resolvedRecurringRepo =
                recurringTransactionRepository ?: ServiceLocator.provideRecurringTransactionRepository(application)
            val resolvedDispatcher =
                dispatcherProvider ?: ServiceLocator.provideDispatcherProvider(application)

            @Suppress("UNCHECKED_CAST")
            return PendingTransactionsViewModel(
                transactionRepository = resolvedTransactionRepo,
                recurringTransactionRepository = resolvedRecurringRepo,
                dispatcherProvider = resolvedDispatcher,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
