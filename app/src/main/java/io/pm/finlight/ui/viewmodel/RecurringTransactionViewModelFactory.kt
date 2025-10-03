// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/RecurringTransactionViewModelFactory.kt
// REASON: NEW FILE - This factory provides the Application context and
// RecurringTransactionRepository dependency to the RecurringTransactionViewModel,
// enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.data.db.AppDatabase

class RecurringTransactionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecurringTransactionViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val repository = RecurringTransactionRepository(db.recurringTransactionDao())
            @Suppress("UNCHECKED_CAST")
            return RecurringTransactionViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
