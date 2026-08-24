// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/CurrencyViewModelFactory.kt
// REASON: NEW FILE - This factory provides all necessary repository dependencies
// to the CurrencyViewModel, enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository

class CurrencyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CurrencyViewModel::class.java)) {
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
            val tripRepository = TripRepository(db.tripDao())

            @Suppress("UNCHECKED_CAST")
            return CurrencyViewModel(
                application,
                settingsRepository,
                tripRepository,
                transactionRepository,
                tagRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
