package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator

class BudgetViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val budgetRepository = BudgetRepository(db.budgetDao())
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)

            @Suppress("UNCHECKED_CAST")
            return BudgetViewModel(
                budgetRepository,
                settingsRepository,
                categoryRepository,
                transactionRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
