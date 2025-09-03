// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AccountViewModel.kt
// REASON: FEATURE - The ViewModel now implements the logic for smart merge
// suggestions. It uses a Levenshtein distance algorithm to find potential
// duplicates, manages a StateFlow for these suggestions, and handles dismissing
// them via the SettingsRepository. A new function allows the UI to enter
// selection mode with the suggested accounts pre-selected.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AccountRepository
    private val transactionRepository: TransactionRepository
    private val settingsRepository: SettingsRepository
    private val db = AppDatabase.getInstance(application)

    private val _uiEvent = Channel<String>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val accountsWithBalance: Flow<List<AccountWithBalance>>

    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive = _isSelectionModeActive.asStateFlow()

    private val _selectedAccountIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedAccountIds = _selectedAccountIds.asStateFlow()

    private val _suggestedMerges = MutableStateFlow<List<Pair<Account, Account>>>(emptyList())
    val suggestedMerges = _suggestedMerges.asStateFlow()

    init {
        repository = AccountRepository(db)
        transactionRepository = TransactionRepository(db.transactionDao())
        settingsRepository = SettingsRepository(application)
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

                val similarity = calculateSimilarity(account1.name, account2.name)
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
            if (sourceAccountIds.isEmpty() || sourceAccountIds.size + 1 != _selectedAccountIds.value.size) {
                _uiEvent.send("Error: Invalid selection for merge.")
                return@launch
            }
            try {
                repository.mergeAccounts(destinationAccountId, sourceAccountIds)
                _uiEvent.send("Accounts merged successfully.")
            } catch (e: Exception) {
                _uiEvent.send("Error merging accounts: ${e.message}")
            } finally {
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
            val existingAccount = db.accountDao().findByName(name)
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