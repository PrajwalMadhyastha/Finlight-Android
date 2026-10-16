package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ManageMerchantRulesViewModel
import io.pm.finlight.di.ServiceLocator

class ManageMerchantRulesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageMerchantRulesViewModel::class.java)) {
            val merchantRenameRuleRepository = ServiceLocator.provideMerchantRenameRuleRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val categoryMappingRepository = ServiceLocator.provideMerchantCategoryMappingRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)

            @Suppress("UNCHECKED_CAST")
            return ManageMerchantRulesViewModel(
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                transactionRepository = transactionRepository,
                categoryMappingRepository = categoryMappingRepository,
                categoryRepository = categoryRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
