// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/ReportsViewModelFactory.kt
// REASON: REFACTOR (Testing) - The factory has been updated to provide the
// ReportsViewModel with its required repository and DAO dependencies, enabling
// constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ReportsViewModel
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase

class ReportsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportsViewModel::class.java)) {
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
                    deletedSmsHashDao = db.deletedSmsHashDao(),
                    mergeRecordDao = db.mergeRecordDao(),
                    db = db,
                )

            @Suppress("UNCHECKED_CAST")
            return ReportsViewModel(
                transactionRepository = transactionRepository,
                categoryDao = db.categoryDao(),
                settingsRepository = settingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
