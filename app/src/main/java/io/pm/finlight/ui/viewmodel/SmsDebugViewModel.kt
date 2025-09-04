// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModel.kt
// REASON: REFACTOR - The ViewModel's calls to the SmsParser have been updated to
// provide an implementation for the new SmsParseTemplateProvider interface. This
// allows the debugger to correctly use the full, unified parsing pipeline,
// including the heuristic learning engine.
// FEATURE - The ViewModel now fully replicates the real-time parsing pipeline
// by first running each SMS through an SmsClassifier. If the model is not
// confident the message is a transaction, it's marked as "Ignored by ML model";
// otherwise, it's passed to the main parser. This gives the user a transparent
// view of the entire decision-making process.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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


    private val smsRepository = SmsRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val smsClassifier = SmsClassifier(application)

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
    }


    init {
        refreshScan()
    }

    fun refreshScan() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val currentLimit = _uiState.value.loadCount
            val recentSms = smsRepository.fetchAllSms(null).take(currentLimit)
            val results = mutableListOf<SmsDebugResult>()

            for (sms in recentSms) {
                // --- UPDATED: Replicate the full pipeline from SmsReceiver ---
                val transactionConfidence = smsClassifier.classify(sms.body)
                val parseResult = if (transactionConfidence < 0.1) {
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
            val recentSms = withContext(Dispatchers.IO) { smsRepository.fetchAllSms(null).take(currentLimit) }
            val newResults = mutableListOf<SmsDebugResult>()
            val transactionsToImport = mutableListOf<PotentialTransaction>()

            val existingSmsHashes = withContext(Dispatchers.IO) {
                db.transactionDao().getAllSmsHashes().first().toSet()
            }

            for (sms in recentSms) {
                val newParseResult = withContext(Dispatchers.IO) {
                    // Re-run the full pipeline to see if the new rule makes a difference
                    val transactionConfidence = smsClassifier.classify(sms.body)
                    if (transactionConfidence < 0.1) {
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
                    val success = transactionViewModel.autoSaveSmsTransaction(txn)
                    if (success) {
                        importedCount++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (importedCount > 0) {
                    Toast.makeText(application, "Auto-imported $importedCount new transaction(s)!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(application, "Rule created! No new transactions to auto-import.", Toast.LENGTH_SHORT).show()
                }
            }

            _uiState.update { it.copy(isLoading = false, debugResults = newResults) }
        }
    }
}
