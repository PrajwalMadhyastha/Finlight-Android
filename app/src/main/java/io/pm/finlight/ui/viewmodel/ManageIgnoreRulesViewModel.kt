// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ManageIgnoreRulesViewModel.kt
// REASON: FEATURE - The ViewModel has been updated to support creating
// different types of ignore rules. The `addIgnoreRule` function now accepts a
// `RuleType`, allowing the UI to specify whether a new rule should apply to
// the sender or the message body.
// REFACTOR (Testing) - The ViewModel now uses constructor dependency injection
// and extends ViewModel instead of AndroidViewModel, making it easier to unit test.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageIgnoreRulesViewModel(private val ignoreRuleDao: IgnoreRuleDao) : ViewModel() {

    /**
     * A flow of all ignore rules, collected as a StateFlow for the UI.
     */
    val allRules: StateFlow<List<IgnoreRule>> = ignoreRuleDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Adds a new ignore rule to the database.
     * @param pattern The pattern to be added (e.g., a sender name or a body phrase).
     * @param type The type of the rule (SENDER or BODY_PHRASE).
     */
    fun addIgnoreRule(pattern: String, type: RuleType) {
        if (pattern.isNotBlank()) {
            viewModelScope.launch {
                // User-added rules are not default rules
                ignoreRuleDao.insert(IgnoreRule(pattern = pattern.trim(), type = type, isDefault = false))
            }
        }
    }

    /**
     * Updates an existing ignore rule, typically to toggle its enabled status.
     * @param rule The rule to be updated.
     */
    fun updateIgnoreRule(rule: IgnoreRule) {
        viewModelScope.launch {
            ignoreRuleDao.update(rule)
        }
    }

    /**
     * Deletes a given ignore rule from the database. This should only be called
     * for non-default rules.
     * @param rule The rule to be deleted.
     */
    fun deleteIgnoreRule(rule: IgnoreRule) {
        // Safety check to prevent deleting default rules
        if (!rule.isDefault) {
            viewModelScope.launch {
                ignoreRuleDao.delete(rule)
            }
        }
    }
}