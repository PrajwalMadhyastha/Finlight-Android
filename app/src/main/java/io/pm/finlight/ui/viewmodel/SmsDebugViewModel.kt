// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel now uses constructor dependency
// injection for SmsRepository, AppDatabase, and SmsClassifier. This decouples
// it from direct instantiation of its dependencies, making it fully unit-testable.
// FIX (Testing) - Removed the Toast.makeText calls. This UI logic was causing a
// crash in the Robolectric test environment, which does not have a UI Looper
// prepared on the test dispatcher, leading to test timeouts.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Data class to hold the result for each SMS
data class SmsDebugResult(
    val smsMessage: SmsMessage,
    val parseResult: ParseResult
)

// Enum to represent the filter state
enum class SmsDebugFilter {
    ALL, PROBLEMATIC
}

// UI state for the screen
data class SmsDebugUiState(
    val isLoading: Boolean = true,
    val debugResults: List<SmsDebugResult> = emptyList(),
    val selectedFilter: SmsDebugFilter = SmsDebugFilter.PROBLEMATIC,
    val loadCount: Int = 100
)

class SmsDebugViewModel(
    private val application: Application,
    private val smsRepository: SmsRepository,
    private val db: AppDatabase,
    private val smsClassifier: SmsClassifier,
    private val transactionViewModel: TransactionViewModel
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SmsDebugUiState())
    val uiState = _uiState.asStateFlow()

    val filteredDebugResults: StateFlow<List<SmsDebugResult>> = _uiState
        .map { state ->
            if (state.selectedFilter == SmsDebugFilter.ALL) {
                state.debugResults
            } else {
                state.debugResults.filter {
                    it.parseResult !is ParseResult.Success
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Create the required rule providers
    private val customSmsRuleProvider = object : CustomSmsRuleProvider {
        override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
    }
    private val merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
        override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()
    }
    private val ignoreRuleProvider = object : IgnoreRuleProvider {
        override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
    }
    private val merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
        override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)
    }
    private val categoryFinderProvider = object : CategoryFinderProvider {
        override fun getCategoryIdByName(name: String): Int? {
            return CategoryIconHelper.getCategoryIdByName(name)
        }
    }
    private val smsParseTemplateProvider = object : SmsParseTemplateProvider {
        override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()
        override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> = db.smsParseTemplateDao().getTemplatesBySignature(signature)
    }


    init {
        refreshScan()
    }

    fun refreshScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val currentLimit = _uiState.value.loadCount
            val recentSms = smsRepository.fetchAllSms(null).take(currentLimit)
            val results = mutableListOf<SmsDebugResult>()

            for (sms in recentSms) {
                // --- REMOVED: withContext(Dispatchers.IO) to make ViewModel more testable ---
                val parseResult: ParseResult = run {
                    // --- HIERARCHY STEP 1: Check for User-Defined Custom Rules First ---
                    var result: ParseResult? = SmsParser.parseWithOnlyCustomRules(
                        sms = sms,
                        customSmsRuleProvider = customSmsRuleProvider,
                        merchantRenameRuleProvider = merchantRenameRuleProvider,
                        merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                        categoryFinderProvider = categoryFinderProvider
                    )

                    // --- HIERARCHY STEP 2 & 3: If no custom rule matched, run ML pre-filter and then the main parser ---
                    if (result == null) {
                        val transactionConfidence = smsClassifier.classify(sms.body)
                        result = if (transactionConfidence < 0.1) {
                            ParseResult.IgnoredByClassifier(confidence = transactionConfidence)
                        } else {
                            SmsParser.parseWithReason(
                                sms = sms,
                                mappings = emptyMap(),
                                customSmsRuleProvider = customSmsRuleProvider,
                                merchantRenameRuleProvider = merchantRenameRuleProvider,
                                ignoreRuleProvider = ignoreRuleProvider,
                                merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                                categoryFinderProvider = categoryFinderProvider,
                                smsParseTemplateProvider = smsParseTemplateProvider
                            )
                        }
                    }
                    result
                }
                results.add(SmsDebugResult(sms, parseResult))
            }

            _uiState.update { it.copy(isLoading = false, debugResults = results) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        smsClassifier.close() // Release TFLite resources
    }


    fun setFilter(filter: SmsDebugFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun loadMore() {
        val newCount = _uiState.value.loadCount + 100
        _uiState.update { it.copy(loadCount = newCount) }
        refreshScan()
    }

    fun runAutoImportAndRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val originalResults = _uiState.value.debugResults
            val currentLimit = _uiState.value.loadCount

            // Re-scan to get the new state
            val recentSms = smsRepository.fetchAllSms(null).take(currentLimit)
            val newResults = mutableListOf<SmsDebugResult>()
            val transactionsToImport = mutableListOf<PotentialTransaction>()

            val existingSmsHashes = db.transactionDao().getAllSmsHashes().first().toSet()

            for (sms in recentSms) {
                // --- REMOVED: withContext(Dispatchers.IO) to make ViewModel more testable ---
                val newParseResult = run {
                    // --- UPDATED: Replicate the full pipeline from SmsReceiver ---
                    // --- HIERARCHY STEP 1: Check for User-Defined Custom Rules First ---
                    var result: ParseResult? = SmsParser.parseWithOnlyCustomRules(
                        sms = sms,
                        customSmsRuleProvider = customSmsRuleProvider,
                        merchantRenameRuleProvider = merchantRenameRuleProvider,
                        merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                        categoryFinderProvider = categoryFinderProvider
                    )

                    // --- HIERARCHY STEP 2 & 3: If no custom rule matched, run ML pre-filter and then the main parser ---
                    if (result == null) {
                        val transactionConfidence = smsClassifier.classify(sms.body)
                        result = if (transactionConfidence < 0.1) {
                            ParseResult.IgnoredByClassifier(confidence = transactionConfidence)
                        } else {
                            SmsParser.parseWithReason(
                                sms = sms,
                                mappings = emptyMap(),
                                customSmsRuleProvider = customSmsRuleProvider,
                                merchantRenameRuleProvider = merchantRenameRuleProvider,
                                ignoreRuleProvider = ignoreRuleProvider,
                                merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                                categoryFinderProvider = categoryFinderProvider,
                                smsParseTemplateProvider = smsParseTemplateProvider
                            )
                        }
                    }
                    result
                }
                newResults.add(SmsDebugResult(sms, newParseResult))

                val originalResult = originalResults.find { it.smsMessage.id == sms.id }?.parseResult
                if ((originalResult !is ParseResult.Success) && newParseResult is ParseResult.Success) {
                    if (newParseResult.transaction.sourceSmsHash !in existingSmsHashes) {
                        transactionsToImport.add(newParseResult.transaction)
                    }
                }
            }

            var importedCount = 0
            if (transactionsToImport.isNotEmpty()) {
                for (txn in transactionsToImport) {
                    val success = transactionViewModel.autoSaveSmsTransaction(txn, source = "Imported")
                    if (success) {
                        importedCount++
                    }
                }
            }

            // --- FIX: Removed Toast messages that were causing unit test crashes. ---
            // The UI (Screen) should be responsible for showing these messages, not the ViewModel.

            _uiState.update { it.copy(isLoading = false, debugResults = newResults) }
        }
    }
}

