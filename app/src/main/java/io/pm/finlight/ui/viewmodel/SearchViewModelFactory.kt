// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SearchViewModelFactory.kt
// REASON: FEATURE - The factory now accepts an optional `initialDateMillis`
// parameter. This allows it to create a SearchViewModel that is pre-configured
// to filter for a specific date, which is essential for the new clickable
// calendar feature.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

class SearchViewModelFactory(
    private val application: Application,
    private val initialCategoryId: Int?,
    private val initialDateMillis: Long? // --- NEW: Add initial date
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(
                transactionDao = database.transactionDao(),
                accountDao = database.accountDao(),
                categoryDao = database.categoryDao(),
                initialCategoryId = initialCategoryId,
                initialDateMillis = initialDateMillis // --- NEW: Pass date to ViewModel
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}