// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DashboardViewModelFactory.kt
// REASON: REFACTOR - The factory now passes the full AppDatabase instance to the
// AccountRepository. This is required to support the new transactional account
// merging logic.
// FIX (Race Condition) - The factory now also passes the SettingsRepository and
// TagRepository to the TransactionRepository. This is required for the new
// centralized, atomic travel mode tagging logic.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

import io.pm.finlight.utils.SystemTimeProvider

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
            val accountRepository = AccountRepository(db)
            val merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())

            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                budgetDao = db.budgetDao(),
                settingsRepository = settingsRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                timeProvider = SystemTimeProvider()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
