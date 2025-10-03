// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/ManageParseRulesViewModelFactory.kt
// REASON: NEW FILE - This factory provides the CustomSmsRuleDao dependency to the
// ManageParseRulesViewModel, enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.ManageParseRulesViewModel
import io.pm.finlight.data.db.AppDatabase

class ManageParseRulesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageParseRulesViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return ManageParseRulesViewModel(db.customSmsRuleDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}