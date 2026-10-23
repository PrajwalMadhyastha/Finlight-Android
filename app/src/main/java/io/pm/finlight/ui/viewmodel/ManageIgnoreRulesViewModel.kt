package io.pm.finlight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.IgnoreRule
import io.pm.finlight.RuleType
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageIgnoreRulesViewModel(
    private val ignoreRuleRepository: IIgnoreRuleRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * A flow of all ignore rules, collected as a StateFlow for the UI.
     */
    val allRules: StateFlow<List<IgnoreRule>> =
        ignoreRuleRepository.getAllRules()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /**
     * Adds a new ignore rule to the database.
     * @param pattern The pattern to be added (e.g., a sender name or a body phrase).
     * @param type The type of the rule (SENDER or BODY_PHRASE).
     */
    fun addIgnoreRule(
        pattern: String,
        type: RuleType,
    ) {
        if (pattern.isNotBlank()) {
            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    // User-added rules are not default rules
                    ignoreRuleRepository.insert(IgnoreRule(pattern = pattern.trim(), type = type, isDefault = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ManageIgnoreRulesVM", "Failed to insert ignore rule for pattern: $pattern", e)
                    _errorMessage.value = e.message ?: "Failed to add ignore rule"
                }
            }
        }
    }

    /**
     * Updates an existing ignore rule, typically to toggle its enabled status.
     * @param rule The rule to be updated.
     */
    fun updateIgnoreRule(rule: IgnoreRule) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                ignoreRuleRepository.update(rule)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ManageIgnoreRulesVM", "Failed to update ignore rule ${rule.id}", e)
                _errorMessage.value = e.message ?: "Failed to update ignore rule"
            }
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
            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    ignoreRuleRepository.delete(rule)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ManageIgnoreRulesVM", "Failed to delete ignore rule ${rule.id}", e)
                    _errorMessage.value = e.message ?: "Failed to delete ignore rule"
                }
            }
        }
    }
}
