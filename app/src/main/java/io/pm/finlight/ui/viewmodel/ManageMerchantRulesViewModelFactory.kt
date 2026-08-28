package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ManageMerchantRulesViewModel
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.data.db.AppDatabase

class ManageMerchantRulesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageMerchantRulesViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val repository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
            @Suppress("UNCHECKED_CAST")
            return ManageMerchantRulesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
