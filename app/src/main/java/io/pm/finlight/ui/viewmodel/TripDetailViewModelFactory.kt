// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TripDetailViewModelFactory.kt
// REASON: REFACTOR (Testing) - The factory has been updated to instantiate all
// necessary repository dependencies and inject them into the TripDetailViewModel's
// constructor, supporting the new dependency injection pattern.
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

class TripDetailViewModelFactory(
    private val application: Application,
    private val tripId: Int,
    private val tagId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripDetailViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val tripRepository = TripRepository(db.tripDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)

            @Suppress("UNCHECKED_CAST")
            return TripDetailViewModel(
                tripRepository,
                transactionRepository,
                tripId,
                tagId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
