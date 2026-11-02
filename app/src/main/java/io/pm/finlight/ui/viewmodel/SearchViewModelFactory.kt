package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createSearchFactory(application, initialCategoryId, initialDateMillis, initialQuery)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class SearchViewModelFactory(
    private val application: Application,
    private val initialCategoryId: Int? = null,
    private val initialDateMillis: Long? = null,
    private val initialQuery: String? = null,
    private val transactionRepository: ITransactionRepository? = null,
    private val accountRepository: IAccountRepository? = null,
    private val categoryRepository: ICategoryRepository? = null,
    private val tagRepository: ITagRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideSearchViewModel(
                context = application,
                initialCategoryId = initialCategoryId,
                initialDateMillis = initialDateMillis,
                initialQuery = initialQuery,
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                tagRepository = tagRepository,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
