package io.pm.finlight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Data class to hold the state of a user's selection for a custom rule.
 */
data class RuleSelection(
    val selectedText: String = "",
    val startIndex: Int = -1,
    val endIndex: Int = -1,
)

/**
 * UI state for the RuleCreationScreen.
 */
data class RuleCreationUiState(
    val triggerSelection: RuleSelection = RuleSelection(),
    val merchantSelection: RuleSelection = RuleSelection(),
    val amountSelection: RuleSelection = RuleSelection(),
    val accountSelection: RuleSelection = RuleSelection(),
    val transactionType: TransactionType? = null,
    val ruleIdToEdit: Int? = null,
    val merchantRegex: String? = null,
    val amountRegex: String? = null,
    val accountRegex: String? = null,
    val errorMessage: String? = null,
    val sourceSmsBody: String? = null,
)

/**
 * ViewModel for the RuleCreationScreen.
 */
class RuleCreationViewModel(
    private val customSmsRuleRepository: ICustomSmsRuleRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(RuleCreationUiState())
    val uiState = _uiState.asStateFlow()

    fun initializeStateForCreation(potentialTxn: PotentialTransaction) {
        val amountStr = String.format("%.2f", potentialTxn.amount)
        val amountIndex = potentialTxn.originalMessage.indexOf(amountStr)

        val amountSelection =
            if (amountIndex != -1) {
                RuleSelection(
                    selectedText = amountStr,
                    startIndex = amountIndex,
                    endIndex = amountIndex + amountStr.length,
                )
            } else {
                RuleSelection()
            }

        val merchantSelection =
            potentialTxn.merchantName?.let {
                val index = potentialTxn.originalMessage.indexOf(it)
                if (index != -1) RuleSelection(it, index, index + it.length) else RuleSelection()
            } ?: RuleSelection()

        val accountSelection =
            potentialTxn.potentialAccount?.let {
                val accountPart = it.formattedName.split(" ").last { part -> part.any { char -> char.isDigit() } }
                val index = potentialTxn.originalMessage.indexOf(accountPart)
                if (index != -1) RuleSelection(accountPart, index, index + accountPart.length) else RuleSelection()
            } ?: RuleSelection()

        _uiState.value =
            RuleCreationUiState(
                amountSelection = amountSelection,
                merchantSelection = merchantSelection,
                accountSelection = accountSelection,
                transactionType = TransactionType.fromStringOrNull(potentialTxn.transactionType),
                sourceSmsBody = potentialTxn.originalMessage,
            )
    }

    fun loadRuleForEditing(ruleId: Int) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val rule = customSmsRuleRepository.getRuleById(ruleId).firstOrNull() ?: return@launch
                _uiState.update {
                    it.copy(
                        triggerSelection = RuleSelection(selectedText = rule.triggerPhrase),
                        merchantSelection = RuleSelection(selectedText = rule.merchantNameExample ?: ""),
                        amountSelection = RuleSelection(selectedText = rule.amountExample ?: ""),
                        accountSelection = RuleSelection(selectedText = rule.accountNameExample ?: ""),
                        transactionType = TransactionType.fromStringOrNull(rule.transactionType),
                        ruleIdToEdit = rule.id,
                        merchantRegex = rule.merchantRegex,
                        amountRegex = rule.amountRegex,
                        accountRegex = rule.accountRegex,
                        errorMessage = null,
                        sourceSmsBody = rule.sourceSmsBody,
                    )
                }
            } catch (e: Exception) {
                Log.e("RuleCreation", "Failed to load rule $ruleId for editing", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to load rule") }
            }
        }
    }

    fun onMarkAsTrigger(selection: RuleSelection) {
        _uiState.update { it.copy(triggerSelection = selection) }
    }

    fun onMarkAsMerchant(selection: RuleSelection) {
        _uiState.update { it.copy(merchantSelection = selection) }
    }

    fun onMarkAsAmount(selection: RuleSelection) {
        _uiState.update { it.copy(amountSelection = selection) }
    }

    fun onMarkAsAccount(selection: RuleSelection) {
        _uiState.update { it.copy(accountSelection = selection) }
    }

    fun onTransactionTypeChanged(newType: TransactionType?) {
        _uiState.update { it.copy(transactionType = newType) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveRule(
        fullSmsText: String,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val currentState = _uiState.value
                if (currentState.triggerSelection.selectedText.isBlank()) {
                    Log.e("RuleCreation", "Cannot save rule without a trigger phrase.")
                    withContext(dispatcherProvider.main) {
                        onComplete()
                    }
                    return@launch
                }

                val merchantRegex =
                    if (currentState.merchantSelection.startIndex != -1) {
                        generateRegex(fullSmsText, currentState.merchantSelection)
                    } else {
                        currentState.merchantRegex
                    }

                val amountRegex =
                    if (currentState.amountSelection.startIndex != -1) {
                        generateRegex(fullSmsText, currentState.amountSelection)
                    } else {
                        currentState.amountRegex
                    }

                val accountRegex =
                    if (currentState.accountSelection.startIndex != -1) {
                        generateRegex(fullSmsText, currentState.accountSelection)
                    } else {
                        currentState.accountRegex
                    }

                val rule =
                    CustomSmsRule(
                        id = currentState.ruleIdToEdit ?: 0,
                        triggerPhrase = currentState.triggerSelection.selectedText,
                        merchantRegex = merchantRegex,
                        amountRegex = amountRegex,
                        accountRegex = accountRegex,
                        merchantNameExample = currentState.merchantSelection.selectedText.takeIf { it.isNotBlank() },
                        amountExample = currentState.amountSelection.selectedText.takeIf { it.isNotBlank() },
                        accountNameExample = currentState.accountSelection.selectedText.takeIf { it.isNotBlank() },
                        priority = 10,
                        sourceSmsBody = fullSmsText,
                        transactionType = currentState.transactionType?.dbValue,
                    )

                if (currentState.ruleIdToEdit != null) {
                    customSmsRuleRepository.update(rule)
                } else {
                    customSmsRuleRepository.insert(rule)
                }
                withContext(dispatcherProvider.main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("RuleCreation", "Failed to save rule", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save rule") }
            }
        }
    }

    private fun generateRegex(
        fullText: String,
        selection: RuleSelection,
    ): String? {
        if (selection.startIndex == -1 || selection.selectedText.isBlank()) return null

        val textBefore = fullText.substring(0, selection.startIndex)
        val textAfter = fullText.substring(selection.endIndex)

        val prefixWords = textBefore.trim().split(Regex("\\s+")).takeLast(2)
        val prefix = prefixWords.joinToString(separator = " ")

        val suffixWords = textAfter.trim().split(Regex("\\s+")).take(2)
        val suffix = suffixWords.joinToString(separator = " ")

        val escapedPrefix = if (prefix.isNotBlank()) Pattern.quote(prefix) else ""
        val escapedSuffix = if (suffix.isNotBlank()) Pattern.quote(suffix) else ""

        return when {
            escapedPrefix.isNotBlank() && escapedSuffix.isNotBlank() -> "$escapedPrefix\\s*(.*?)\\s*$escapedSuffix"
            escapedPrefix.isNotBlank() -> "$escapedPrefix\\s*(.*)"
            escapedSuffix.isNotBlank() -> "(.*?)\\s*$escapedSuffix"
            else -> Pattern.quote(selection.selectedText)
        }
    }
}
