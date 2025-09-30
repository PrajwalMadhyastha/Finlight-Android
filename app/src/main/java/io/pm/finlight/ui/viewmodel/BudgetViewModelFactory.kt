// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/BudgetViewModelFactory.kt
// REASON: NEW FILE - This factory handles the creation of BudgetViewModel for
// the main application. It instantiates the necessary repositories and injects
// them into the ViewModel's constructor, decoupling the ViewModel from direct
// database initialization and enabling easier testing.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.BudgetRepository
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.CategoryRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.data.db.AppDatabase

class BudgetViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val budgetRepository = BudgetRepository(db.budgetDao())
            val settingsRepository = SettingsRepository(application)
            val categoryRepository = CategoryRepository(db.categoryDao())

            @Suppress("UNCHECKED_CAST")
            return BudgetViewModel(
                budgetRepository,
                settingsRepository,
                categoryRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
