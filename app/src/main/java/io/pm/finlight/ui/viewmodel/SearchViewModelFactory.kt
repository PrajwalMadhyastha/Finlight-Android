// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SearchViewModelFactory.kt
// REASON: FEATURE - Updated the factory to accept the new `initialQuery`
// parameter and pass it to the SearchViewModel constructor.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

class SearchViewModelFactory(
    private val application: Application,
    private val initialCategoryId: Int?,
    private val initialDateMillis: Long?,
    private val initialQuery: String? // --- NEW: Add parameter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(
                transactionDao = database.transactionDao(),
                accountDao = database.accountDao(),
                categoryDao = database.categoryDao(),
                tagDao = database.tagDao(),
                initialCategoryId = initialCategoryId,
                initialDateMillis = initialDateMillis,
                initialQuery = initialQuery // --- NEW: Pass to VM
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}