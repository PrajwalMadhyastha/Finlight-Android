// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AccountViewModel.kt
// REASON: FIX (Testing) - The UI event channel (_uiEvent) has been changed
// from a default (Rendezvous) channel to an UNLIMITED buffered channel. This
// prevents the viewModelScope coroutine from suspending indefinitely on a `send`
// call when there is no collector, which was causing the `mergeSelectedAccounts`
// unit test to fail.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.Account
import io.pm.finlight.AccountRepository
import io.pm.finlight.AccountWithBalance
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class AccountViewModel(
    application: Application,
    private val repository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val accountsWithBalance: Flow<List<AccountWithBalance>>

    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive = _isSelectionModeActive.asStateFlow()

    private val _selectedAccountIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedAccountIds = _selectedAccountIds.asStateFlow()

    private val _suggestedMerges = MutableStateFlow<List<Pair<Account, Account>>>(emptyList())
    val suggestedMerges = _suggestedMerges.asStateFlow()

    init {
        accountsWithBalance = repository.accountsWithBalance

        viewModelScope.launch(Dispatchers.Default) {
            combine(
                repository.accountsWithBalance,
                settingsRepository.getDismissedMergeSuggestions()
            ) { accountsWithBalance, dismissedKeys ->
                checkForPotentialMerges(accountsWithBalance.map { it.account }, dismissedKeys)
            }.collect()
        }
    }

    private fun normalizeAccountName(name: String): String {
        return name.lowercase(Locale.getDefault())
            // Remove common keywords that don't add to uniqueness
            .replace(Regex("\\b(bank|card|credit|debit|account|acct|a/c|prepaid|wallet)\\b"), "")
            // Remove masked numbers and identifiers (e.g., xx1234, *1234, ...1234)
            .replace(Regex("\\s+ending with\\s+\\d+"), "")
            .replace(Regex("[x*]+\\d+|\\.{3,}\\d+"), "")
            // Remove special characters and collapse extra spaces
            .replace(Regex("[-_.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun checkForPotentialMerges(accounts: List<Account>, dismissedKeys: Set<String>) {
        if (accounts.size < 2) {
            _suggestedMerges.value = emptyList()
            return
        }

        val suggestions = mutableListOf<Pair<Account, Account>>()
        for (i in accounts.indices) {
            for (j in i + 1 until accounts.size) {
                val account1 = accounts[i]
                val account2 = accounts[j]

                val suggestionKey = "${min(account1.id, account2.id)}|${max(account1.id, account2.id)}"
                if (suggestionKey in dismissedKeys) continue

                val normalizedName1 = normalizeAccountName(account1.name)
                val normalizedName2 = normalizeAccountName(account2.name)

                // Skip comparison if normalization results in an empty string
                if (normalizedName1.isBlank() || normalizedName2.isBlank()) continue

                val similarity = calculateSimilarity(normalizedName1, normalizedName2)
                if (similarity > 0.85) { // 85% similarity threshold
                    suggestions.add(Pair(account1, account2))
                }
            }
        }
        _suggestedMerges.value = suggestions
    }

    fun dismissMergeSuggestion(suggestion: Pair<Account, Account>) {
        val key = "${min(suggestion.first.id, suggestion.second.id)}|${max(suggestion.first.id, suggestion.second.id)}"
        settingsRepository.addDismissedMergeSuggestion(key)
    }

    fun enterSelectionModeWithSuggestions(accounts: List<Account>) {
        _isSelectionModeActive.value = true
        _selectedAccountIds.value = accounts.map { it.id }.toSet()
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.isEmpty()) return 1.0
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance) / longer.length.toDouble()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1[i - 1] != s2[j - 1]) {
                            newValue = min(min(newValue, lastValue), costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) {
                costs[s2.length] = lastValue
            }
        }
        return costs[s2.length]
    }

    fun enterSelectionMode(accountId: Int?) {
        _isSelectionModeActive.value = true
        _selectedAccountIds.value = accountId?.let { setOf(it) } ?: emptySet()
    }

    fun toggleAccountSelection(accountId: Int) {
        _selectedAccountIds.update { currentSelection ->
            val newSelection = if (accountId in currentSelection) {
                currentSelection - accountId
            } else {
                currentSelection + accountId
            }

            if (newSelection.isEmpty() && isSelectionModeActive.value) {
                // Keep selection mode active even if count is 0,
                // allows deselecting all then selecting new ones.
                // It will be cleared explicitly via Cancel or Merge.
            }
            newSelection
        }
    }

    fun clearSelectionMode() {
        _isSelectionModeActive.value = false
        _selectedAccountIds.value = emptySet()
    }

    fun mergeSelectedAccounts(destinationAccountId: Int) {
        viewModelScope.launch {
            val sourceAccountIds = _selectedAccountIds.value.filter { it != destinationAccountId }

            // 1. Perform validation first.
            if (sourceAccountIds.isEmpty() || sourceAccountIds.size + 1 != _selectedAccountIds.value.size) {
                _uiEvent.send("Error: Invalid selection for merge.")
                return@launch // Exit without clearing selection so the user can fix it.
            }

            // 2. If validation passes, proceed with the operation inside a try-finally.
            try {
                repository.mergeAccounts(destinationAccountId, sourceAccountIds)
                _uiEvent.send("Accounts merged successfully.")
            } catch (e: Exception) {
                _uiEvent.send("Error merging accounts: ${e.message}")
            } finally {
                // This is now guaranteed to run after a valid merge attempt.
                clearSelectionMode()
            }
        }
    }

    fun getAccountById(accountId: Int): Flow<Account?> = repository.getAccountById(accountId)

    fun getAccountBalance(accountId: Int): Flow<Double> {
        return transactionRepository.getTransactionsForAccount(accountId).map { transactions ->
            transactions.sumOf { if (it.transactionType == "income") it.amount else -it.amount }
        }
    }

    fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionDetails>> {
        return transactionRepository.getTransactionsForAccountDetails(accountId)
    }

    fun addAccount(
        name: String,
        type: String,
    ) = viewModelScope.launch {
        if (name.isNotBlank() && type.isNotBlank()) {
            // This check should ideally be in the repository or a UseCase,
            // but keeping it here to match existing pattern.
            val existingAccount = repository.allAccounts.first().find { it.name.equals(name, ignoreCase = true) }
            if (existingAccount != null) {
                _uiEvent.send("An account named '$name' already exists.")
            } else {
                repository.insert(Account(name = name, type = type))
                _uiEvent.send("Account '$name' created.")
            }
        }
    }

    fun updateAccount(account: Account) =
        viewModelScope.launch {
            repository.update(account)
        }

    fun renameAccount(accountId: Int, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val accountToUpdate = repository.getAccountById(accountId).firstOrNull()
            accountToUpdate?.let {
                updateAccount(it.copy(name = newName))
            }
        }
    }

    fun deleteAccount(account: Account) =
        viewModelScope.launch {
            repository.delete(account)
        }
}