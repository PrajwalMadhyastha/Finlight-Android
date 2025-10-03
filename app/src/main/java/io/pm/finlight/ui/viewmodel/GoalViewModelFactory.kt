// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/GoalViewModelFactory.kt
// REASON: NEW FILE - This factory provides the GoalRepository dependency to the
// GoalViewModel, enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.GoalRepository
import io.pm.finlight.GoalViewModel
import io.pm.finlight.data.db.AppDatabase

class GoalViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val repository = GoalRepository(db.goalDao())
            @Suppress("UNCHECKED_CAST")
            return GoalViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
