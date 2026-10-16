// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SearchViewModelFactory.kt
// REASON: REFACTOR (Domain 5 / Issue #339) - Decouple SearchViewModelFactory from AppDatabase:
// Standardized package to io.pm.finlight.ui.viewmodel and inject repositories
// through ServiceLocator with optional test overrides.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

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
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val resolvedTransactionRepo =
                transactionRepository ?: ServiceLocator.provideTransactionRepository(application)
            val resolvedAccountRepo =
                accountRepository ?: ServiceLocator.provideAccountRepository(application)
            val resolvedCategoryRepo =
                categoryRepository ?: ServiceLocator.provideCategoryRepository(application)
            val resolvedTagRepo =
                tagRepository ?: ServiceLocator.provideTagRepository(application)
            val resolvedDispatcher =
                dispatcherProvider ?: ServiceLocator.provideDispatcherProvider(application)

            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(
                transactionRepository = resolvedTransactionRepo,
                accountRepository = resolvedAccountRepo,
                categoryRepository = resolvedCategoryRepo,
                tagRepository = resolvedTagRepo,
                dispatcherProvider = resolvedDispatcher,
                initialCategoryId = initialCategoryId,
                initialDateMillis = initialDateMillis,
                initialQuery = initialQuery,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
