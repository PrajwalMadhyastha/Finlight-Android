package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.MerchantRuleCollisionDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MerchantRuleUiItem(
    val rule: MerchantRenameRule,
    val transactionCount: Int = 0,
    val linkedCategory: Category? = null,
    val conflictingRules: List<MerchantRenameRule> = emptyList(),
)

class ManageMerchantRulesViewModel(
    private val merchantRenameRuleRepository: IMerchantRenameRuleRepository,
    private val transactionRepository: ITransactionRepository? = null,
    private val categoryMappingRepository: IMerchantCategoryMappingRepository? = null,
    private val categoryRepository: ICategoryRepository? = null,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedRuleForTransactions = MutableStateFlow<MerchantRenameRule?>(null)
    val selectedRuleForTransactions: StateFlow<MerchantRenameRule?> = _selectedRuleForTransactions.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedRuleTransactions: StateFlow<List<TransactionDetails>> =
        _selectedRuleForTransactions
            .flatMapLatest { rule ->
                if (rule == null || transactionRepository == null) {
                    flowOf(emptyList())
                } else {
                    transactionRepository.getTransactionsByOriginalDescription(rule.originalName)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    fun selectRuleForTransactions(rule: MerchantRenameRule?) {
        _selectedRuleForTransactions.value = rule
    }

    fun clearSelectedRuleForTransactions() {
        _selectedRuleForTransactions.value = null
    }

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

    private val transactionCountsFlow: Flow<Map<String, Int>> =
        transactionRepository?.getTransactionCountsByOriginalDescription() ?: flowOf(emptyMap())

    val transactionCounts: StateFlow<Map<String, Int>> =
        transactionCountsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap(),
            )

    private val categoryMappingsFlow: Flow<List<MerchantCategoryMapping>> =
        categoryMappingRepository?.getAllMappings() ?: flowOf(emptyList())

    private val categoriesFlow: Flow<List<Category>> =
        categoryRepository?.allCategories ?: flowOf(emptyList())

    /**
     * Complete UI state combining rename rules, transaction impact analytics,
     * linked category mappings, collision detection, and search filtering.
     */
    val uiRules: StateFlow<List<MerchantRuleUiItem>> =
        combine(
            allRules,
            transactionCountsFlow,
            categoryMappingsFlow,
            categoriesFlow,
            _searchQuery,
        ) { rules, txCounts, mappings, categories, query ->
            val collisions = MerchantRuleCollisionDetector.findCollisions(rules)
            val categoryMapByParsedName = mappings.associateBy { it.parsedName.lowercase() }
            val categoryById = categories.associateBy { it.id }

            val trimmed = query.trim()
            val filtered =
                if (trimmed.isEmpty()) {
                    rules
                } else {
                    rules.filter { rule ->
                        rule.originalName.contains(trimmed, ignoreCase = true) ||
                            rule.newName.contains(trimmed, ignoreCase = true)
                    }
                }

            val sorted =
                filtered.sortedWith(
                    compareBy<MerchantRenameRule, String>(String.CASE_INSENSITIVE_ORDER) { it.newName }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.originalName },
                )

            sorted.map { rule ->
                val count = txCounts[rule.originalName.lowercase()] ?: 0
                val mapping =
                    categoryMapByParsedName[rule.originalName.lowercase()]
                        ?: categoryMapByParsedName[rule.newName.lowercase()]
                val linkedCat = mapping?.let { categoryById[it.categoryId] }
                val ruleCollisions = collisions[rule.originalName.lowercase()] ?: emptyList()

                MerchantRuleUiItem(
                    rule = rule,
                    transactionCount = count,
                    linkedCategory = linkedCat,
                    conflictingRules = ruleCollisions,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Filtered list of rename rules based on the user's search query,
     * sorted alphabetically by new (renamed) merchant name.
     */
    val filteredRules: StateFlow<List<MerchantRenameRule>> =
        uiRules
            .map { list -> list.map { it.rule } }
            .stateIn(
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
     * Deletes a merchant rename rule and retroactively reverts matching
     * transactions' description back to the original SMS description.
     */
    fun deleteRuleAndSync(rule: MerchantRenameRule) {
        viewModelScope.launch {
            merchantRenameRuleRepository.deleteByOriginalName(rule.originalName)
            transactionRepository?.updateDescriptionByOriginalDescription(
                originalDesc = rule.originalName,
                newDescription = rule.originalName,
            )
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
     * Updates the target display name for an existing raw merchant name
     * and retroactively syncs existing transactions matching this rule to the new display name.
     */
    fun updateRuleAndSync(
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
            transactionRepository?.updateDescriptionByOriginalDescription(
                originalDesc = trimmedOriginal,
                newDescription = trimmedNew,
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
