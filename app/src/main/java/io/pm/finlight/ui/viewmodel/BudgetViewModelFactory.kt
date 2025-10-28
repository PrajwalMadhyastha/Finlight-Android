// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/BudgetViewModelFactory.kt
// REASON: NEW FILE - This factory handles the creation of BudgetViewModel for
// the main application. It instantiates the necessary repositories and injects
// them into the ViewModel's constructor, decoupling the ViewModel from direct
// database initialization and enabling easier testing.
//
// REASON: REFACTOR (Dynamic Budget) - The factory is updated to inject the
// `TransactionRepository`. This is now required by the `BudgetViewModel` to
// fetch the `monthlySummaries` needed for the new month navigation header.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.CategoryRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase

class BudgetViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val budgetRepository = BudgetRepository(db.budgetDao())
            val settingsRepository = SettingsRepository(application)
            val categoryRepository = CategoryRepository(db.categoryDao())
            // --- NEW: Add TransactionRepository dependency ---
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)


            @Suppress("UNCHECKED_CAST")
            return BudgetViewModel(
                budgetRepository,
                settingsRepository,
                categoryRepository,
                transactionRepository // --- NEW: Pass repository to ViewModel ---
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
