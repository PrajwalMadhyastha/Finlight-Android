package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.create(application)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class PendingTransactionsViewModelFactory(
    private val application: Application,
    private val transactionRepository: ITransactionRepository? = null,
    private val recurringTransactionRepository: IRecurringTransactionRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PendingTransactionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.providePendingTransactionsViewModel(
                context = application,
                transactionRepository = transactionRepository,
                recurringTransactionRepository = recurringTransactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        return create(modelClass)
    }
}
