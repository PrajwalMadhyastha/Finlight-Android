package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createLinkRecurringTransactionFactory(application, potentialTransaction)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class LinkRecurringTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction,
    private val resolvedDb: AppDatabase? = null,
    private val transactionRepository: ITransactionRepository? = null,
    private val recurringTransactionRepository: IRecurringTransactionRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(LinkRecurringTransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideLinkRecurringTransactionViewModel(
                context = application,
                potentialTransaction = potentialTransaction,
                resolvedDb = resolvedDb,
                transactionRepository = transactionRepository,
                recurringTransactionRepository = recurringTransactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
