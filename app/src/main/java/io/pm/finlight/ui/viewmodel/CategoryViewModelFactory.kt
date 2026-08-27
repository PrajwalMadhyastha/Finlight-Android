package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.CategoryRepository
import io.pm.finlight.CategoryViewModel
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.DefaultDispatcherProvider

class CategoryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
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
            return CategoryViewModel(
                categoryRepository = CategoryRepository(db.categoryDao()),
                transactionRepository = transactionRepository,
                categoryDao = db.categoryDao(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
