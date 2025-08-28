// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModel.kt
// REASON: FEATURE - The ViewModel now accepts a TransactionViewModel dependency
// to save transactions. A new `runAutoImportAndRefresh` function has been added.
// This function re-scans messages, identifies any that are newly parsable,
// auto-imports them, and then refreshes the UI state.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class to hold the result for each SMS
data class SmsDebugResult(
    val smsMessage: SmsMessage,
    val parseResult: ParseResult
)

// UI state for the screen
data class SmsDebugUiState(
    val isLoading: Boolean = true,
    val debugResults: List<SmsDebugResult> = emptyList()
)

class SmsDebugViewModel(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SmsDebugUiState())
    val uiState = _uiState.asStateFlow()

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
                    // Also check if it's already been imported by another means
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
