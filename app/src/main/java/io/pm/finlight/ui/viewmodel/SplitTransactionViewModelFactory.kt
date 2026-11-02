package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
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
    private val application: Application,
    private val transactionId: Int,
    private val resolvedDb: AppDatabase? = null,
    private val transactionRepository: ITransactionRepository? = null,
    private val categoryRepository: ICategoryRepository? = null,
    private val splitTransactionRepository: ISplitTransactionRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(SplitTransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideSplitTransactionViewModel(
                context = application,
                transactionId = transactionId,
                resolvedDb = resolvedDb,
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                splitTransactionRepository = splitTransactionRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
