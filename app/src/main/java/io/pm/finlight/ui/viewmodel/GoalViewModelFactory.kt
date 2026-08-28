// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/GoalViewModelFactory.kt
// REASON: FEATURE (Issue #104) - Updated to pass GoalTransactionLinkDao to the
// GoalRepository constructor, enabling dynamic transaction linking support.
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
            val repository = GoalRepository(db.goalDao(), db.goalTransactionLinkDao(), db.transactionQueryDao(), db.goalContributionDao())
            @Suppress("UNCHECKED_CAST")
            return GoalViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
