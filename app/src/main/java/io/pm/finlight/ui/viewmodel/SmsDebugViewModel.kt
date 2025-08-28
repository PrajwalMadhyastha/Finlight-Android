// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModel.kt
// REASON: FEATURE - Added a filter to the SMS Debugger. The ViewModel now
// manages a filter state (All or Problematic) and exposes a derived StateFlow
// of filtered results, defaulting to show only ignored or unparsed messages.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class to hold the result for each SMS
data class SmsDebugResult(
    val smsMessage: SmsMessage,
    val parseResult: ParseResult
)

// --- NEW: Enum to represent the filter state ---
enum class SmsDebugFilter {
    ALL, PROBLEMATIC
}

// UI state for the screen
data class SmsDebugUiState(
    val isLoading: Boolean = true,
    val debugResults: List<SmsDebugResult> = emptyList(),
    val selectedFilter: SmsDebugFilter = SmsDebugFilter.PROBLEMATIC
)

class SmsDebugViewModel(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SmsDebugUiState())
    val uiState = _uiState.asStateFlow()

    // --- NEW: A derived flow that filters the results based on the selected filter ---
    val filteredDebugResults: StateFlow<List<SmsDebugResult>> = _uiState
        .map { state ->
            if (state.selectedFilter == SmsDebugFilter.ALL) {
                state.debugResults
            } else {
                state.debugResults.filter {
                    it.parseResult is ParseResult.Ignored || it.parseResult is ParseResult.NotParsed
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val smsRepository = SmsRepository(application)
    private val db = AppDatabase.getInstance(application)

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

    init {
        refreshScan()
    }

    fun refreshScan() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val recentSms = smsRepository.fetchAllSms(null).take(100)
            val results = mutableListOf<SmsDebugResult>()

            for (sms in recentSms) {
                val parseResult = SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMap(),
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider
                )
                results.add(SmsDebugResult(sms, parseResult))
            }

            _uiState.update { it.copy(isLoading = false, debugResults = results) }
        }
    }

    // --- NEW: Function to update the filter state ---
    fun setFilter(filter: SmsDebugFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun runAutoImportAndRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val originalResults = _uiState.value.debugResults

            // Re-scan to get the new state
            val recentSms = withContext(Dispatchers.IO) { smsRepository.fetchAllSms(null).take(100) }
            val newResults = mutableListOf<SmsDebugResult>()
            val transactionsToImport = mutableListOf<PotentialTransaction>()

            val existingSmsHashes = withContext(Dispatchers.IO) {
                db.transactionDao().getAllSmsHashes().first().toSet()
            }

            for (sms in recentSms) {
                val newParseResult = withContext(Dispatchers.IO) {
                    SmsParser.parseWithReason(
                        sms = sms,
                        mappings = emptyMap(),
                        customSmsRuleProvider = customSmsRuleProvider,
                        merchantRenameRuleProvider = merchantRenameRuleProvider,
                        ignoreRuleProvider = ignoreRuleProvider,
                        merchantCategoryMappingProvider = merchantCategoryMappingProvider
                    )
                }
                newResults.add(SmsDebugResult(sms, newParseResult))

                val originalResult = originalResults.find { it.smsMessage.id == sms.id }?.parseResult
                if ((originalResult is ParseResult.Ignored || originalResult is ParseResult.NotParsed) && newParseResult is ParseResult.Success) {
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
