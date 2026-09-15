package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ManageMerchantRulesViewModel
import io.pm.finlight.MerchantCategoryMappingRepository
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator

class ManageMerchantRulesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageMerchantRulesViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val categoryMappingRepository = MerchantCategoryMappingRepository(db.merchantCategoryMappingDao())
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
