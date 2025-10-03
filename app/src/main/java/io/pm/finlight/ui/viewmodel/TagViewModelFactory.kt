// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TagViewModelFactory.kt
// REASON: NEW FILE - This factory provides the necessary repository dependencies
// to the TagViewModel, enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.TagRepository
import io.pm.finlight.TagViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository

class TagViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TagViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val tripRepository = TripRepository(db.tripDao())
            @Suppress("UNCHECKED_CAST")
            return TagViewModel(application, tagRepository, tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
