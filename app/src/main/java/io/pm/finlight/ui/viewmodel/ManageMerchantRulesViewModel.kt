package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageMerchantRulesViewModel(
    private val merchantRenameRuleRepository: IMerchantRenameRuleRepository,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Flow of all merchant rename rules in the database.
     */
    val allRules: StateFlow<List<MerchantRenameRule>> =
        merchantRenameRuleRepository
            .getAllRules()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /**
     * Filtered list of rename rules based on the user's search query,
     * sorted alphabetically by new (renamed) merchant name.
     */
    val filteredRules: StateFlow<List<MerchantRenameRule>> =
        combine(allRules, _searchQuery) { rules, query ->
            val trimmed = query.trim()
            val baseList =
                if (trimmed.isEmpty()) {
                    rules
                } else {
                    rules.filter { rule ->
                        rule.originalName.contains(trimmed, ignoreCase = true) ||
                            rule.newName.contains(trimmed, ignoreCase = true)
                    }
                }
            baseList.sortedWith(
                compareBy<MerchantRenameRule, String>(String.CASE_INSENSITIVE_ORDER) { it.newName }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.originalName },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Total count of active rename rules.
     */
    val totalRulesCount: StateFlow<Int> =
        allRules
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0,
            )

    /**
     * Updates the search filter query.
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clears the current search query.
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Deletes a merchant rename rule by its original merchant name.
     */
    fun deleteRule(rule: MerchantRenameRule) {
        viewModelScope.launch {
            merchantRenameRuleRepository.deleteByOriginalName(rule.originalName)
        }
    }

    /**
     * Updates the target display name for an existing raw merchant name.
     */
    fun updateRule(
        originalName: String,
        newName: String,
    ) {
        val trimmedOriginal = originalName.trim()
        val trimmedNew = newName.trim()
        if (trimmedOriginal.isBlank() || trimmedNew.isBlank()) {
            return
        }
        viewModelScope.launch {
            merchantRenameRuleRepository.insert(
                MerchantRenameRule(
                    originalName = trimmedOriginal,
                    newName = trimmedNew,
                ),
            )
        }
    }

    /**
     * Creates and saves a new merchant rename rule.
     * Validates that both names are non-blank and distinct.
     */
    fun addRule(
        originalName: String,
        newName: String,
    ) {
        val trimmedOriginal = originalName.trim()
        val trimmedNew = newName.trim()
        if (trimmedOriginal.isBlank() || trimmedNew.isBlank() || trimmedOriginal.equals(trimmedNew, ignoreCase = true)) {
            return
        }
        viewModelScope.launch {
            merchantRenameRuleRepository.insert(
                MerchantRenameRule(
                    originalName = trimmedOriginal,
                    newName = trimmedNew,
                ),
            )
        }
    }
}
