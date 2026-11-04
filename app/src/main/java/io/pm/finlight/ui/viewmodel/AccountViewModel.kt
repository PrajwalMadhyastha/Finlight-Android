// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AccountViewModel.kt
// REASON: FIX (Flaky Test) - The UI event channel (_uiEvent) has been changed
// from a default (Rendezvous) channel to an UNLIMITED buffered channel. This
// prevents the viewModelScope coroutine from suspending indefinitely on a `send`
// call when there is no collector, which was causing the `mergeSelectedAccounts`
// unit test to fail.
// REASON: FEATURE (Error Handling) - Added try-catch blocks to `addAccount` and
// `deleteAccount` to gracefully handle potential repository exceptions and
// provide user feedback via the UI event channel.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.Account
import io.pm.finlight.AccountWithBalance
import io.pm.finlight.CreditCardMetrics
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import io.pm.finlight.isCreditCard
import io.pm.finlight.utils.DateUtils
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModel(
    private val repository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val settingsRepository: ISettingsRepository,
    private val mergeAccountsUseCase: MergeAccountsUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    constructor(
        repository: IAccountRepository,
        transactionRepository: ITransactionRepository,
        settingsRepository: ISettingsRepository,
        mergeAccountsUseCase: MergeAccountsUseCase,
        computationDispatcher: CoroutineDispatcher,
    ) : this(
        repository = repository,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
        mergeAccountsUseCase = mergeAccountsUseCase,
        dispatcherProvider =
            object : DispatcherProvider {
                override val main: CoroutineDispatcher get() = computationDispatcher
                override val io: CoroutineDispatcher get() = computationDispatcher
                override val default: CoroutineDispatcher get() = computationDispatcher
                override val unconfined: CoroutineDispatcher get() = computationDispatcher
            },
    )

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val accountsWithBalance: Flow<List<AccountWithBalance>> = repository.accountsWithBalance

    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive = _isSelectionModeActive.asStateFlow()

    private val _selectedAccountIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedAccountIds = _selectedAccountIds.asStateFlow()

    private val _suggestedMerges = MutableStateFlow<List<Pair<Account, Account>>>(emptyList())
    val suggestedMerges = _suggestedMerges.asStateFlow()

    val creditCardMetrics: StateFlow<Map<Int, CreditCardMetrics>> =
        repository.accountsWithBalance.flatMapLatest { accounts ->
            val creditCardAccounts =
                accounts
                    .map { it.account }
                    .filter { it.isCreditCard && (it.creditLimit ?: 0.0) > 0.0 }

            if (creditCardAccounts.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val accountMetricFlows =
                    creditCardAccounts.map { acc ->
                        transactionRepository.getTransactionsForAccount(acc.id).map { txns ->
                            acc.id to calculateCreditCardMetrics(acc, txns)
                        }
                    }
                combine(accountMetricFlows) { pairs ->
                    pairs.filter { it.second != null }.associate { it.first to it.second!! }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch(dispatcherProvider.default) {
            combine(
                repository.accountsWithBalance,
                settingsRepository.getDismissedMergeSuggestions(),
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

    private fun checkForPotentialMerges(
        accounts: List<Account>,
        dismissedKeys: Set<String>,
    ) {
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

                runCatching {
                    val safeName1 = account1.name.take(64)
                    val safeName2 = account2.name.take(64)
                    val normalizedName1 = normalizeAccountName(safeName1)
                    val normalizedName2 = normalizeAccountName(safeName2)

                    // Skip comparison if normalization results in an empty string
                    if (normalizedName1.isNotBlank() && normalizedName2.isNotBlank()) {
                        val similarity = calculateSimilarity(normalizedName1, normalizedName2)
                        if (similarity > 0.85) { // 85% similarity threshold
                            suggestions.add(Pair(account1, account2))
                        }
                    }
                }
            }
        }
        _suggestedMerges.value = suggestions
    }

    fun dismissMergeSuggestion(suggestion: Pair<Account, Account>) {
        val key = "${min(suggestion.first.id, suggestion.second.id)}|${max(suggestion.first.id, suggestion.second.id)}"
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                settingsRepository.addDismissedMergeSuggestion(key)
            } catch (e: Exception) {
                Log.e("AccountViewModel", "Error dismissing merge suggestion", e)
                _uiEvent.send("Error dismissing merge suggestion: ${e.message}")
            }
        }
    }

    fun enterSelectionModeWithSuggestions(accounts: List<Account>) {
        _isSelectionModeActive.value = true
        _selectedAccountIds.value = accounts.map { it.id }.toSet()
    }

    private fun calculateSimilarity(
        s1: String,
        s2: String,
    ): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.isEmpty()) return 1.0
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance) / longer.length.toDouble()
    }

    private fun levenshteinDistance(
        s1: String,
        s2: String,
    ): Int {
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
            val newSelection =
                if (accountId in currentSelection) {
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
        viewModelScope.launch(dispatcherProvider.io) {
            val sourceAccountIds = _selectedAccountIds.value.filter { it != destinationAccountId }

            // 1. Perform validation first.
            if (sourceAccountIds.isEmpty() || sourceAccountIds.size + 1 != _selectedAccountIds.value.size) {
                _uiEvent.send("Error: Invalid selection for merge.")
                return@launch // Exit without clearing selection so the user can fix it.
            }

            // 2. If validation passes, proceed with the operation inside a try-finally.
            try {
                mergeAccountsUseCase(destinationAccountId, sourceAccountIds)
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

    fun getCreditCardMetrics(accountId: Int): Flow<CreditCardMetrics?> {
        return repository.getAccountById(accountId).flatMapLatest { account ->
            if (account == null || !account.isCreditCard || account.creditLimit == null || account.creditLimit <= 0.0) {
                flowOf(null)
            } else {
                transactionRepository.getTransactionsForAccount(accountId).map { txns ->
                    calculateCreditCardMetrics(account, txns)
                }
            }
        }
    }

    fun calculateCreditCardMetrics(
        account: Account,
        transactions: List<Transaction>,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): CreditCardMetrics? {
        val limit = account.creditLimit ?: return null
        if (limit <= 0.0 || !account.isCreditCard) return null

        val billingDay = account.billingCycleDay ?: 1
        val (cycleStart, cycleEnd) = DateUtils.getCreditCardBillingCycleRange(billingDay, currentTimeMillis)

        val cycleTransactions =
            transactions.filter {
                it.date in cycleStart..cycleEnd && !it.isExcluded
            }

        val expenseSum =
            cycleTransactions
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sumOf { it.amount }
        val incomeSum =
            cycleTransactions
                .filter { it.transactionType == TransactionType.INCOME }
                .sumOf { it.amount }

        val currentCycleSpend = (expenseSum - incomeSum).coerceAtLeast(0.0)
        val availableLimit = (limit - currentCycleSpend).coerceAtLeast(0.0)
        val utilization = ((currentCycleSpend / limit) * 100.0).toFloat().coerceIn(0f, 1000f)

        val daysRemaining =
            max(
                0,
                ceil((cycleEnd - currentTimeMillis).toDouble() / (1000 * 60 * 60 * 24)).toInt(),
            )

        return CreditCardMetrics(
            creditLimit = limit,
            billingCycleDay = billingDay,
            currentCycleSpend = currentCycleSpend,
            availableLimit = availableLimit,
            utilizationPercentage = utilization,
            cycleStartDate = cycleStart,
            cycleEndDate = cycleEnd,
            daysRemainingInCycle = daysRemaining,
        )
    }

    fun getAccountBalance(accountId: Int): Flow<Long> {
        return transactionRepository.getTransactionsForAccount(accountId).map { transactions ->
            transactions.sumOf { if (it.transactionType == TransactionType.INCOME) it.amount else -it.amount }.roundToLong()
        }
    }

    fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionDetails>> {
        return transactionRepository.getTransactionsForAccountDetails(accountId)
    }

    suspend fun checkAccountName(
        name: String,
        excludeAccountId: Int? = null,
    ): AccountMatch {
        val accounts = repository.getAllAccountsSnapshot()
        val normalizedInput = normalizeAccountName(name.take(64))

        val exactMatch = accounts.find { it.name.equals(name, ignoreCase = true) && it.id != excludeAccountId }
        if (exactMatch != null) return AccountMatch(exactMatch, MatchType.EXACT)

        if (normalizedInput.isNotBlank()) {
            for (account in accounts) {
                if (account.id == excludeAccountId) continue
                val normalizedExisting = normalizeAccountName(account.name.take(64))
                if (normalizedExisting.isBlank()) continue
                if (calculateSimilarity(normalizedInput, normalizedExisting) > 0.85) {
                    return AccountMatch(account, MatchType.SIMILAR)
                }
            }
        }
        return AccountMatch(null, MatchType.NONE)
    }

    fun mergeAccounts(
        destinationAccountId: Int,
        sourceAccountIds: List<Int>,
        onComplete: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                mergeAccountsUseCase(destinationAccountId, sourceAccountIds)
                _uiEvent.send("Accounts merged successfully.")
                withContext(dispatcherProvider.main) { onComplete(true) }
            } catch (e: Exception) {
                _uiEvent.send("Error merging accounts: ${e.message}")
                withContext(dispatcherProvider.main) { onComplete(false) }
            }
        }
    }

    fun addAccount(
        name: String,
        type: String,
        onComplete: (Boolean) -> Unit = {},
    ) = addAccount(name, type, null, null, onComplete)

    fun addAccount(
        name: String,
        type: String,
        creditLimit: Double?,
        billingCycleDay: Int?,
        onComplete: (Boolean) -> Unit = {},
    ) = viewModelScope.launch(dispatcherProvider.io) {
        try {
            if (name.isNotBlank() && type.isNotBlank()) {
                val existingAccount = repository.getAllAccountsSnapshot().find { it.name.equals(name, ignoreCase = true) }
                if (existingAccount != null) {
                    _uiEvent.send("An account named '$name' already exists.")
                    withContext(dispatcherProvider.main) { onComplete(false) }
                } else {
                    repository.insert(
                        Account(
                            name = name,
                            type = type,
                            creditLimit = creditLimit,
                            billingCycleDay = billingCycleDay,
                        ),
                    )
                    _uiEvent.send("Account '$name' created.")
                    withContext(dispatcherProvider.main) { onComplete(true) }
                }
            } else {
                withContext(dispatcherProvider.main) { onComplete(false) }
            }
        } catch (e: Exception) {
            _uiEvent.send("Error creating account: ${e.message}")
            withContext(dispatcherProvider.main) { onComplete(false) }
        }
    }

    fun updateAccount(
        account: Account,
        onComplete: (Boolean) -> Unit = {},
    ) = viewModelScope.launch(dispatcherProvider.io) {
        try {
            val existingAccount = repository.getAllAccountsSnapshot().find { it.name.equals(account.name, ignoreCase = true) && it.id != account.id }
            if (existingAccount != null) {
                _uiEvent.send("An account named '${account.name}' already exists.")
                withContext(dispatcherProvider.main) { onComplete(false) }
            } else {
                repository.update(account)
                withContext(dispatcherProvider.main) { onComplete(true) }
            }
        } catch (e: Exception) {
            _uiEvent.send("Error updating account: ${e.message}")
            withContext(dispatcherProvider.main) { onComplete(false) }
        }
    }

    fun renameAccount(
        accountId: Int,
        newName: String,
    ) {
        if (newName.isBlank()) return
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val accountToUpdate = repository.getAccountByIdSync(accountId)
                accountToUpdate?.let {
                    updateAccount(it.copy(name = newName))
                }
            } catch (e: Exception) {
                _uiEvent.send("Error updating account: ${e.message}")
            }
        }
    }

    fun deleteAccount(account: Account) =
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                repository.delete(account)
                _uiEvent.send("Account '${account.name}' deleted.")
            } catch (e: Exception) {
                _uiEvent.send("Error deleting account: ${e.message}")
            }
        }
}

enum class MatchType { EXACT, SIMILAR, NONE }

data class AccountMatch(val account: Account?, val matchType: MatchType)
