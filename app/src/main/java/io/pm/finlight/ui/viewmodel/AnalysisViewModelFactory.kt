// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AnalysisViewModelFactory.kt
// REASON: FEATURE - The factory has been updated to inject the CategoryDao and
// TagDao into the AnalysisViewModel. This is a necessary change to allow the
// ViewModel to fetch the data required to populate the new filter dropdowns.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

class AnalysisViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return AnalysisViewModel(
                transactionDao = db.transactionDao(),
                categoryDao = db.categoryDao(),
                tagDao = db.tagDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
