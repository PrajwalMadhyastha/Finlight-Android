package io.pm.finlight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageParseRulesViewModel(
    private val customSmsRuleRepository: ICustomSmsRuleRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * A flow of all custom SMS parsing rules, collected as StateFlow for the UI.
     */
    val allRules: StateFlow<List<CustomSmsRule>> =
        customSmsRuleRepository.getAllRules()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /**
     * Deletes a given custom rule from the database.
     *
     * @param rule The rule to be deleted.
     */
    fun deleteRule(rule: CustomSmsRule) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                customSmsRuleRepository.delete(rule)
            } catch (e: Exception) {
                Log.e("ManageParseRulesVM", "Failed to delete custom SMS rule ${rule.id}", e)
                _errorMessage.value = e.message ?: "Failed to delete custom SMS rule"
            }
        }
    }
}
