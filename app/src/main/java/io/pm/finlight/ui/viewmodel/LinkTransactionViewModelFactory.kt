// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/LinkTransactionViewModelFactory.kt
// REASON: REFACTOR (Testing) - The factory has been updated to instantiate all
// necessary repository dependencies (TransactionRepository, RecurringTransactionDao)
// and inject them into the LinkTransactionViewModel's constructor, supporting the new
// dependency injection pattern required for unit testing.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.viewmodel.LinkTransactionViewModel

class LinkTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkTransactionViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
            val recurringTransactionDao = db.recurringTransactionDao()
            @Suppress("UNCHECKED_CAST")
            return LinkTransactionViewModel(
                transactionRepository,
                recurringTransactionDao,
                potentialTransaction
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
