// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SplitTransactionViewModelFactory.kt
// REASON: REFACTOR (Testing) - The factory now instantiates and provides all
// required repository dependencies to the SplitTransactionViewModel's constructor,
// supporting the new dependency injection pattern.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

class SplitTransactionViewModelFactory(
    private val application: Application,
    private val transactionId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SplitTransactionViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
            val categoryRepository = CategoryRepository(db.categoryDao())
            val splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao())

            @Suppress("UNCHECKED_CAST")
            return SplitTransactionViewModel(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                splitTransactionRepository = splitTransactionRepository,
                transactionId = transactionId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
