package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase

class TransactionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val tagRepository = ServiceLocator.provideTagRepository(application)
            val resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepository)
            val mergeTransactionsUseCase = ServiceLocator.provideMergeTransactionsUseCase(application, db)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val manageReimbursementUseCase = ServiceLocator.provideManageReimbursementUseCase(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)
            val smsRepository = ServiceLocator.provideSmsRepository(application)

            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(
                application = application,
                db = db,
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                tagRepository = tagRepository,
                settingsRepository = settingsRepository,
                smsRepository = smsRepository,
                merchantRenameRuleRepository = ServiceLocator.provideMerchantRenameRuleRepository(application, db),
                merchantCategoryMappingRepository = ServiceLocator.provideMerchantCategoryMappingRepository(application, db),
                merchantMappingRepository = ServiceLocator.provideMerchantMappingRepository(application, db),
                splitTransactionRepository = ServiceLocator.provideSplitTransactionRepository(application, db),
                smsParseTemplateDao = db.smsParseTemplateDao(),
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                dispatcherProvider = dispatcherProvider,
                manageReimbursementUseCase = manageReimbursementUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
