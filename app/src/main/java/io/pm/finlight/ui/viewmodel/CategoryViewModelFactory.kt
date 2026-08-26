// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CategoryViewModelFactory.kt
// REASON: NEW FILE - This factory handles the creation of CategoryViewModel
// for the main application. It instantiates all necessary repository dependencies
// and injects them into the ViewModel's constructor. This supports the
// dependency injection pattern required for unit testing.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.CategoryRepository
import io.pm.finlight.CategoryViewModel
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase

class CategoryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionQueryDao())
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    settingsRepository = settingsRepository,
                    tagRepository = tagRepository,
                    db = db,
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
