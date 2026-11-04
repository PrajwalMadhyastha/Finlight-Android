package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.FinlightViewModelFactory
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
    application: Application,
    potentialTransaction: PotentialTransaction,
    resolvedDb: AppDatabase? = null,
    transactionRepository: ITransactionRepository? = null,
    recurringTransactionRepository: IRecurringTransactionRepository? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createLinkRecurringTransactionFactory(
        context = application,
        potentialTransaction = potentialTransaction,
        resolvedDb = resolvedDb,
        transactionRepository = transactionRepository,
        recurringTransactionRepository = recurringTransactionRepository,
        dispatcherProvider = dispatcherProvider,
    )
