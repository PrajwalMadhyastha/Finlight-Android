package io.pm.finlight.ui.viewmodel

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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
    val priority: Int = 10,
)

/**
 * ViewModel for the RuleCreationScreen.
 */
class RuleCreationViewModel(
    private val customSmsRuleRepository: ICustomSmsRuleRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    companion object {
        const val KEY_RULE_PATTERN = "rulePattern"
        const val KEY_RULE_PATTERN_START = "rulePatternStart"
        const val KEY_RULE_PATTERN_END = "rulePatternEnd"

        const val KEY_RULE_TYPE = "ruleType"

        const val KEY_MERCHANT_TEXT = "ruleMerchantText"
        const val KEY_MERCHANT_START = "ruleMerchantStart"
        const val KEY_MERCHANT_END = "ruleMerchantEnd"

        const val KEY_AMOUNT_TEXT = "ruleAmountText"
        const val KEY_AMOUNT_START = "ruleAmountStart"
        const val KEY_AMOUNT_END = "ruleAmountEnd"

        const val KEY_ACCOUNT_TEXT = "ruleAccountText"
        const val KEY_ACCOUNT_START = "ruleAccountStart"
        const val KEY_ACCOUNT_END = "ruleAccountEnd"

        const val KEY_RULE_ID_TO_EDIT = "ruleIdToEdit"
        const val KEY_SOURCE_SMS_BODY = "ruleSourceSmsBody"
        const val KEY_MERCHANT_REGEX = "ruleMerchantRegex"
        const val KEY_AMOUNT_REGEX = "ruleAmountRegex"
        const val KEY_ACCOUNT_REGEX = "ruleAccountRegex"
        const val KEY_PRIORITY = "rulePriority"
        const val KEY_INITIALIZED = "ruleInitialized"
    }

    private fun restoreStateFromHandle(): RuleCreationUiState {
        val triggerText: String? = savedStateHandle[KEY_RULE_PATTERN]
        val triggerStart: Int? = savedStateHandle[KEY_RULE_PATTERN_START]
        val triggerEnd: Int? = savedStateHandle[KEY_RULE_PATTERN_END]
        val triggerSelection =
            if (triggerText != null) {
                RuleSelection(triggerText, triggerStart ?: -1, triggerEnd ?: -1)
            } else {
                RuleSelection()
            }

        val merchantText: String? = savedStateHandle[KEY_MERCHANT_TEXT]
        val merchantStart: Int? = savedStateHandle[KEY_MERCHANT_START]
        val merchantEnd: Int? = savedStateHandle[KEY_MERCHANT_END]
        val merchantSelection =
            if (merchantText != null) {
                RuleSelection(merchantText, merchantStart ?: -1, merchantEnd ?: -1)
            } else {
                RuleSelection()
            }

        val amountText: String? = savedStateHandle[KEY_AMOUNT_TEXT]
        val amountStart: Int? = savedStateHandle[KEY_AMOUNT_START]
        val amountEnd: Int? = savedStateHandle[KEY_AMOUNT_END]
        val amountSelection =
            if (amountText != null) {
                RuleSelection(amountText, amountStart ?: -1, amountEnd ?: -1)
            } else {
                RuleSelection()
            }

        val accountText: String? = savedStateHandle[KEY_ACCOUNT_TEXT]
        val accountStart: Int? = savedStateHandle[KEY_ACCOUNT_START]
        val accountEnd: Int? = savedStateHandle[KEY_ACCOUNT_END]
        val accountSelection =
            if (accountText != null) {
                RuleSelection(accountText, accountStart ?: -1, accountEnd ?: -1)
            } else {
                RuleSelection()
            }

        val ruleTypeStr: String? = savedStateHandle[KEY_RULE_TYPE]
        val transactionType = ruleTypeStr?.let { TransactionType.fromStringOrNull(it) }

        return RuleCreationUiState(
            triggerSelection = triggerSelection,
            merchantSelection = merchantSelection,
            amountSelection = amountSelection,
            accountSelection = accountSelection,
            transactionType = transactionType,
            ruleIdToEdit = savedStateHandle[KEY_RULE_ID_TO_EDIT],
            merchantRegex = savedStateHandle[KEY_MERCHANT_REGEX],
            amountRegex = savedStateHandle[KEY_AMOUNT_REGEX],
            accountRegex = savedStateHandle[KEY_ACCOUNT_REGEX],
            sourceSmsBody = savedStateHandle[KEY_SOURCE_SMS_BODY],
            priority = savedStateHandle[KEY_PRIORITY] ?: 10,
        )
    }

    private fun persistStateToHandle(state: RuleCreationUiState) {
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_RULE_PATTERN] = state.triggerSelection.selectedText
        savedStateHandle[KEY_RULE_PATTERN_START] = state.triggerSelection.startIndex
        savedStateHandle[KEY_RULE_PATTERN_END] = state.triggerSelection.endIndex

        savedStateHandle[KEY_MERCHANT_TEXT] = state.merchantSelection.selectedText
        savedStateHandle[KEY_MERCHANT_START] = state.merchantSelection.startIndex
        savedStateHandle[KEY_MERCHANT_END] = state.merchantSelection.endIndex

        savedStateHandle[KEY_AMOUNT_TEXT] = state.amountSelection.selectedText
        savedStateHandle[KEY_AMOUNT_START] = state.amountSelection.startIndex
        savedStateHandle[KEY_AMOUNT_END] = state.amountSelection.endIndex

        savedStateHandle[KEY_ACCOUNT_TEXT] = state.accountSelection.selectedText
        savedStateHandle[KEY_ACCOUNT_START] = state.accountSelection.startIndex
        savedStateHandle[KEY_ACCOUNT_END] = state.accountSelection.endIndex

        savedStateHandle[KEY_RULE_TYPE] = state.transactionType?.dbValue
        savedStateHandle[KEY_RULE_ID_TO_EDIT] = state.ruleIdToEdit
        savedStateHandle[KEY_MERCHANT_REGEX] = state.merchantRegex
        savedStateHandle[KEY_AMOUNT_REGEX] = state.amountRegex
        savedStateHandle[KEY_ACCOUNT_REGEX] = state.accountRegex
        savedStateHandle[KEY_SOURCE_SMS_BODY] = state.sourceSmsBody
        savedStateHandle[KEY_PRIORITY] = state.priority
    }

    private val _uiState =
        MutableStateFlow(
            if (savedStateHandle.contains(KEY_INITIALIZED) ||
                savedStateHandle.contains(KEY_RULE_PATTERN) ||
                savedStateHandle.contains(KEY_RULE_TYPE)
            ) {
                restoreStateFromHandle()
            } else {
                RuleCreationUiState()
            },
        )
    val uiState = _uiState.asStateFlow()

    fun initializeStateForCreation(potentialTxn: PotentialTransaction) {
        if (savedStateHandle.contains(KEY_INITIALIZED) ||
            savedStateHandle.contains(KEY_RULE_PATTERN) ||
            savedStateHandle.contains(KEY_RULE_TYPE)
        ) {
            return
        }
        val dotAmountStr = String.format(Locale.ROOT, "%.2f", potentialTxn.amount)
        val commaAmountStr = String.format(Locale.GERMANY, "%.2f", potentialTxn.amount)
        val dotIndex = potentialTxn.originalMessage.indexOf(dotAmountStr)
        val commaIndex = potentialTxn.originalMessage.indexOf(commaAmountStr)

        val (amountStr, amountIndex) =
            when {
                dotIndex != -1 -> dotAmountStr to dotIndex
                commaIndex != -1 -> commaAmountStr to commaIndex
                else -> dotAmountStr to -1
            }

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
                val accountPart = it.formattedName.split(" ").lastOrNull { part -> part.any { char -> char.isDigit() } }
                val index = accountPart?.let { part -> potentialTxn.originalMessage.indexOf(part) } ?: -1
                if (index != -1 && accountPart != null) {
                    RuleSelection(accountPart, index, index + accountPart.length)
                } else {
                    RuleSelection()
                }
            } ?: RuleSelection()

        val newState =
            RuleCreationUiState(
                amountSelection = amountSelection,
                merchantSelection = merchantSelection,
                accountSelection = accountSelection,
                transactionType = TransactionType.fromStringOrNull(potentialTxn.transactionType),
                sourceSmsBody = potentialTxn.originalMessage,
            )
        _uiState.value = newState
        persistStateToHandle(newState)
    }

    fun loadRuleForEditing(ruleId: Int) {
        if (savedStateHandle.get<Int>(KEY_RULE_ID_TO_EDIT) == ruleId &&
            savedStateHandle.contains(KEY_INITIALIZED)
        ) {
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val rule = customSmsRuleRepository.getRuleById(ruleId).firstOrNull()
                if (rule == null) {
                    _uiState.update { it.copy(errorMessage = "Rule not found") }
                    return@launch
                }
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
                        priority = rule.priority,
                    )
                }
                persistStateToHandle(_uiState.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RuleCreation", "Failed to load rule $ruleId for editing", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to load rule") }
            }
        }
    }

    fun onMarkAsTrigger(selection: RuleSelection) {
        _uiState.update { it.copy(triggerSelection = selection) }
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_RULE_PATTERN] = selection.selectedText
        savedStateHandle[KEY_RULE_PATTERN_START] = selection.startIndex
        savedStateHandle[KEY_RULE_PATTERN_END] = selection.endIndex
    }

    fun onMarkAsMerchant(selection: RuleSelection) {
        _uiState.update { it.copy(merchantSelection = selection) }
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_MERCHANT_TEXT] = selection.selectedText
        savedStateHandle[KEY_MERCHANT_START] = selection.startIndex
        savedStateHandle[KEY_MERCHANT_END] = selection.endIndex
    }

    fun onMarkAsAmount(selection: RuleSelection) {
        _uiState.update { it.copy(amountSelection = selection) }
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_AMOUNT_TEXT] = selection.selectedText
        savedStateHandle[KEY_AMOUNT_START] = selection.startIndex
        savedStateHandle[KEY_AMOUNT_END] = selection.endIndex
    }

    fun onMarkAsAccount(selection: RuleSelection) {
        _uiState.update { it.copy(accountSelection = selection) }
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_ACCOUNT_TEXT] = selection.selectedText
        savedStateHandle[KEY_ACCOUNT_START] = selection.startIndex
        savedStateHandle[KEY_ACCOUNT_END] = selection.endIndex
    }

    fun onTransactionTypeChanged(newType: TransactionType?) {
        _uiState.update { it.copy(transactionType = newType) }
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_RULE_TYPE] = newType?.dbValue
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
                    _uiState.update { it.copy(errorMessage = "Trigger phrase cannot be blank") }
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
                        priority = currentState.priority,
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RuleCreation", "Failed to save rule", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save rule") }
            }
        }
    }

    @VisibleForTesting
    internal fun generateRegex(
        fullText: String,
        selection: RuleSelection,
    ): String? {
        if (selection.startIndex !in 0..fullText.length ||
            selection.endIndex !in selection.startIndex..fullText.length ||
            selection.selectedText.isBlank()
        ) {
            return null
        }

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
