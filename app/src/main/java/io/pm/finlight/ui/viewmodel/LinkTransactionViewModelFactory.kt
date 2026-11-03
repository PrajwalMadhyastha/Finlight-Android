package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.di.FinlightViewModelFactory
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
    application: Application,
    potentialTransaction: PotentialTransaction,
    transactionRepository: ITransactionRepository? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createLinkTransactionFactory(
        context = application,
        potentialTransaction = potentialTransaction,
        transactionRepository = transactionRepository,
        dispatcherProvider = dispatcherProvider,
    )
