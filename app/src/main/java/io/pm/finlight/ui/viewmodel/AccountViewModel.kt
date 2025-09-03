// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/AccountViewModel.kt
// REASON: FEATURE - The ViewModel has been completely updated to manage the new
// account merging feature. It now includes StateFlows for selection mode status
// and the set of selected account IDs. New functions have been added to handle
// entering/exiting selection mode, toggling selections, and calling the
// repository's atomic merge function.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AccountRepository
    private val transactionRepository: TransactionRepository
    private val db = AppDatabase.getInstance(application)

    private val _uiEvent = Channel<String>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val accountsWithBalance: Flow<List<AccountWithBalance>>

    // --- NEW: State for selection mode ---
    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive = _isSelectionModeActive.asStateFlow()

    private val _selectedAccountIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedAccountIds = _selectedAccountIds.asStateFlow()


    init {
        repository = AccountRepository(db)
        transactionRepository = TransactionRepository(db.transactionDao())
        accountsWithBalance = repository.accountsWithBalance
    }

    // --- NEW: Functions to manage selection ---
    fun enterSelectionMode(accountId: Int) {
        _isSelectionModeActive.value = true
        _selectedAccountIds.value = setOf(accountId)
    }

    fun toggleAccountSelection(accountId: Int) {
        _selectedAccountIds.update { currentSelection ->
            val newSelection = if (accountId in currentSelection) {
                currentSelection - accountId
            } else {
                currentSelection + accountId
            }

            if (newSelection.isEmpty()) {
                _isSelectionModeActive.value = false
            }
            newSelection
        }
    }

    fun clearSelectionMode() {
        _isSelectionModeActive.value = false
        _selectedAccountIds.value = emptySet()
    }

    // --- NEW: Function to execute the merge ---
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