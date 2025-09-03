// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DashboardViewModelFactory.kt
// REASON: REFACTOR - The factory now passes the full AppDatabase instance to the
// AccountRepository. This is required to support the new transactional account
// merging logic.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val transactionRepository = TransactionRepository(db.transactionDao())
            val accountRepository = AccountRepository(db) // --- UPDATED ---
            val settingsRepository = SettingsRepository(application)

            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                budgetDao = db.budgetDao(),
                settingsRepository = settingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
