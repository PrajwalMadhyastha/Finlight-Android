// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ManageParseRulesViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel now uses constructor dependency
// injection for CustomSmsRuleDao and extends ViewModel instead of AndroidViewModel.
// This decouples it from the Application context, making it unit-testable.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageParseRulesViewModel(private val customSmsRuleDao: CustomSmsRuleDao) : ViewModel() {

    /**
     * A flow of all custom SMS parsing rules, collected as StateFlow for the UI.
     */
    val allRules: StateFlow<List<CustomSmsRule>> = customSmsRuleDao.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Deletes a given custom rule from the database.
     *
     * @param rule The rule to be deleted.
     */
    fun deleteRule(rule: CustomSmsRule) {
        viewModelScope.launch {
            customSmsRuleDao.delete(rule)
        }
    }
}