// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/IncomeViewModelFactory.kt
// REASON: NEW FILE - This factory provides all necessary repository dependencies
// to the IncomeViewModel, enabling constructor injection for better testability.
//
// REASON: MODIFIED - Injected SettingsRepository to allow the ViewModel to
// observe the Privacy Mode state.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase

class IncomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IncomeViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
            val accountRepository = AccountRepository(db)
            val categoryRepository = CategoryRepository(db.categoryDao())

            @Suppress("UNCHECKED_CAST")
            return IncomeViewModel(
                transactionRepository,
                accountRepository,
                categoryRepository,
                settingsRepository // --- NEW: Pass SettingsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}