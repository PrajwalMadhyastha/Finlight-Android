// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/ManageIgnoreRulesViewModelFactory.kt
// REASON: NEW FILE - This factory provides the IgnoreRuleDao dependency to the
// ManageIgnoreRulesViewModel, enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ManageIgnoreRulesViewModel
import io.pm.finlight.data.db.AppDatabase

class ManageIgnoreRulesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageIgnoreRulesViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return ManageIgnoreRulesViewModel(db.ignoreRuleDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}