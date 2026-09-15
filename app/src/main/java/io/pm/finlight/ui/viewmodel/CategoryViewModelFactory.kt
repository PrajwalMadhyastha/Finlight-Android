package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.CategoryViewModel
import io.pm.finlight.di.ServiceLocator

class CategoryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)

            @Suppress("UNCHECKED_CAST")
            return CategoryViewModel(
                categoryRepository = categoryRepository,
                transactionRepository = transactionRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
