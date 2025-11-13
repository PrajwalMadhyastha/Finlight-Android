// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RuleCreationViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel has been refactored to use
// constructor dependency injection for the CustomSmsRuleDao. It now extends
// ViewModel instead of AndroidViewModel, decoupling it from the Android framework
// and making it fully unit-testable.
//
// REASON: MODIFIED - Added `onTransactionTypeChanged` function to handle
// UI state updates from the new 3-state toggle.
// - `loadRuleForEditing` now populates the `transactionType` in the state.
// - `saveRule` now saves the `transactionType` from the state into the
//   `CustomSmsRule` entity.
//
// REASON: FIX (Bug) - `RuleCreationUiState` now stores the existing `merchantRegex`,
// `amountRegex`, and `accountRegex` strings.
// - `loadRuleForEditing` now populates these new fields from the loaded rule.
// - `saveRule` is updated with new logic: it only generates a new regex if a
//   new selection has been made (startIndex != -1). Otherwise, it preserves
//   the existing regex string from the state. This fixes a bug where
//   editing an old rule would wipe out its regex fields.
// =================================================================================
package io.pm.finlight

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Data class to hold the state of a user's selection for a custom rule.
 */
data class RuleSelection(
    val selectedText: String = "",
    val startIndex: Int = -1,
    val endIndex: Int = -1
)

/**
 * UI state for the RuleCreationScreen.
 */
data class RuleCreationUiState(
    val triggerSelection: RuleSelection = RuleSelection(),
    val merchantSelection: RuleSelection = RuleSelection(),
    val amountSelection: RuleSelection = RuleSelection(),
    val accountSelection: RuleSelection = RuleSelection(),
    val transactionType: String? = null,
    val ruleIdToEdit: Int? = null,
    // --- FIX: Add fields to hold existing regex strings during an edit ---
    val merchantRegex: String? = null,
    val amountRegex: String? = null,
    val accountRegex: String? = null
)

/**
 * ViewModel for the RuleCreationScreen.
 */
class RuleCreationViewModel(private val customSmsRuleDao: CustomSmsRuleDao) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleCreationUiState())
    val uiState = _uiState.asStateFlow()

    fun initializeStateForCreation(potentialTxn: PotentialTransaction) {
        val amountStr = String.format("%.2f", potentialTxn.amount)
        val amountIndex = potentialTxn.originalMessage.indexOf(amountStr)

        val amountSelection = if (amountIndex != -1) {
            RuleSelection(
                selectedText = amountStr,
                startIndex = amountIndex,
                endIndex = amountIndex + amountStr.length
            )
        } else {
            RuleSelection()
        }

        val merchantSelection = potentialTxn.merchantName?.let {
            val index = potentialTxn.originalMessage.indexOf(it)
            if (index != -1) RuleSelection(it, index, index + it.length) else RuleSelection()
        } ?: RuleSelection()

        val accountSelection = potentialTxn.potentialAccount?.let {
            val accountPart = it.formattedName.split(" ").last { part -> part.any { char -> char.isDigit() } }
            val index = potentialTxn.originalMessage.indexOf(accountPart)
            if (index != -1) RuleSelection(accountPart, index, index + accountPart.length) else RuleSelection()
        } ?: RuleSelection()

        _uiState.value = RuleCreationUiState(
            amountSelection = amountSelection,
            merchantSelection = merchantSelection,
            accountSelection = accountSelection,
            transactionType = potentialTxn.transactionType
        )
    }

    fun loadRuleForEditing(ruleId: Int) {
        viewModelScope.launch {
            val rule = customSmsRuleDao.getRuleById(ruleId).firstOrNull() ?: return@launch
            // --- FIX: Populate the state with all data from the loaded rule ---
            _uiState.value = RuleCreationUiState(
                triggerSelection = RuleSelection(selectedText = rule.triggerPhrase),
                merchantSelection = RuleSelection(selectedText = rule.merchantNameExample ?: ""),
                amountSelection = RuleSelection(selectedText = rule.amountExample ?: ""),
                accountSelection = RuleSelection(selectedText = rule.accountNameExample ?: ""),
                transactionType = rule.transactionType,
                ruleIdToEdit = rule.id,
                // --- FIX: Store the existing regex strings in the state ---
                merchantRegex = rule.merchantRegex,
                amountRegex = rule.amountRegex,
                accountRegex = rule.accountRegex
            )
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

    // --- NEW: Function to handle UI state change from the toggle ---
    fun onTransactionTypeChanged(newType: String?) {
        _uiState.update { it.copy(transactionType = newType) }
    }

    fun saveRule(fullSmsText: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.triggerSelection.selectedText.isBlank()) {
                Log.e("RuleCreation", "Cannot save rule without a trigger phrase.")
                onComplete()
                return@launch
            }

            // --- FIX: Conditional Regex Generation ---
            // Only generate a new regex if the user has made a new selection (startIndex != -1).
            // Otherwise, preserve the existing regex string from the loaded rule.

            val merchantRegex = if (currentState.merchantSelection.startIndex != -1) {
                generateRegex(fullSmsText, currentState.merchantSelection)
            } else {
                currentState.merchantRegex // Preserve old regex
            }

            val amountRegex = if (currentState.amountSelection.startIndex != -1) {
                generateRegex(fullSmsText, currentState.amountSelection)
            } else {
                currentState.amountRegex // Preserve old regex
            }

            val accountRegex = if (currentState.accountSelection.startIndex != -1) {
                generateRegex(fullSmsText, currentState.accountSelection)
            } else {
                currentState.accountRegex // Preserve old regex
            }
            // --- END OF FIX ---

            val rule = CustomSmsRule(
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
                transactionType = currentState.transactionType
            )

            if (currentState.ruleIdToEdit != null) {
                customSmsRuleDao.update(rule)
            } else {
                customSmsRuleDao.insert(rule)
            }
            onComplete()
        }
    }

    private fun generateRegex(fullText: String, selection: RuleSelection): String? {
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