package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.di.ServiceLocator

class LinkRecurringTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkRecurringTransactionViewModel::class.java)) {
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val recurringTransactionRepository = ServiceLocator.provideRecurringTransactionRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            @Suppress("UNCHECKED_CAST")
            return LinkRecurringTransactionViewModel(
                transactionRepository = transactionRepository,
                recurringTransactionRepository = recurringTransactionRepository,
                potentialTransaction = potentialTransaction,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
