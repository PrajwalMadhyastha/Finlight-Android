package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.CategoryRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DefaultDispatcherProvider

class BudgetViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val budgetRepository = BudgetRepository(db.budgetDao())
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val categoryRepository = CategoryRepository(db.categoryDao())
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                    dispatcherProvider = DefaultDispatcherProvider(),
                )

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
