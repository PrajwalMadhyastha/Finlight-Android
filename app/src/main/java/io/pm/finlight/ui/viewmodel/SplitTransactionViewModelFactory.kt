package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createSplitTransactionFactory(application, transactionId)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class SplitTransactionViewModelFactory(
    application: Application,
    transactionId: Int,
    resolvedDb: AppDatabase? = null,
    transactionRepository: ITransactionRepository? = null,
    categoryRepository: ICategoryRepository? = null,
    splitTransactionRepository: ISplitTransactionRepository? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createSplitTransactionFactory(
        context = application,
        transactionId = transactionId,
        resolvedDb = resolvedDb,
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        splitTransactionRepository = splitTransactionRepository,
        dispatcherProvider = dispatcherProvider,
    )
