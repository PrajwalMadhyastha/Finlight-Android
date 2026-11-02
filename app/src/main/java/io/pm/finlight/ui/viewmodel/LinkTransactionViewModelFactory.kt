package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createLinkTransactionFactory(application, potentialTransaction)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class LinkTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction,
    private val transactionRepository: ITransactionRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(LinkTransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideLinkTransactionViewModel(
                context = application,
                potentialTransaction = potentialTransaction,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
