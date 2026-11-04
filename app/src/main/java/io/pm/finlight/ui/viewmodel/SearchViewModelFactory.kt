package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.di.FinlightViewModelFactory
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
    application: Application,
    initialCategoryId: Int? = null,
    initialDateMillis: Long? = null,
    initialQuery: String? = null,
    transactionRepository: ITransactionRepository? = null,
    accountRepository: IAccountRepository? = null,
    categoryRepository: ICategoryRepository? = null,
    tagRepository: ITagRepository? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createSearchFactory(
        context = application,
        initialCategoryId = initialCategoryId,
        initialDateMillis = initialDateMillis,
        initialQuery = initialQuery,
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        tagRepository = tagRepository,
        dispatcherProvider = dispatcherProvider,
    )
