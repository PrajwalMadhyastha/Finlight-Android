// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SearchViewModel.kt
// REASON: FIX - The ViewModel has been refactored to be fully reactive. It now
// uses a `flatMapLatest` operator on the UI state flow. This directly calls the
// new Flow-based DAO method, ensuring that `searchResults` is a "live" stream
// that automatically updates whenever the search criteria or the underlying
// transaction data changes, thus fixing the stale data bug.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class SearchUiState(
    val keyword: String = "",
    val selectedAccount: Account? = null,
    val selectedCategory: Category? = null,
    val transactionType: String = "All", // "All", "Income", "Expense"
    val startDate: Long? = null,
    val endDate: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val hasSearched: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val initialCategoryId: Int?,
    private val initialDateMillis: Long?
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val searchResults: StateFlow<List<TransactionDetails>> = uiState
        .debounce(300L) // Debounce user input for performance
        .flatMapLatest { state ->
            val filtersAreActive = state.selectedAccount != null ||
                    state.selectedCategory != null ||
                    state.transactionType != "All" ||
                    state.startDate != null ||
                    state.endDate != null

            val shouldSearch = state.keyword.isNotBlank() || filtersAreActive
            // We can update the `hasSearched` flag directly within the flow
            _uiState.update { it.copy(hasSearched = shouldSearch) }

            if (shouldSearch) {
                // Call the new reactive DAO function
                transactionDao.searchTransactions(
                    keyword = state.keyword,
                    accountId = state.selectedAccount?.id,
                    categoryId = state.selectedCategory?.id,
                    transactionType = if (state.transactionType.equals("All", ignoreCase = true)) null else state.transactionType.lowercase(),
                    startDate = state.startDate,
                    endDate = state.endDate
                )
            } else {
                // If there are no search criteria, return a Flow with an empty list
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Load initial filter options (accounts and categories)
        viewModelScope.launch {
            accountDao.getAllAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
        viewModelScope.launch {
            categoryDao.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
                if (initialCategoryId != null) {
                    val initialCategory = categories.find { it.id == initialCategoryId }
                    if (initialCategory != null) {
                        _uiState.update { it.copy(selectedCategory = initialCategory) }
                    }
                }
            }
        }

        // Pre-select date range if an initial date was passed from another screen
        if (initialDateMillis != null && initialDateMillis != -1L) {
            val cal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            val start = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            val end = cal.timeInMillis

            onDateChange(start, end)
        }
    }

    fun onKeywordChange(newKeyword: String) {
        _uiState.update { it.copy(keyword = newKeyword) }
    }

    fun onAccountChange(account: Account?) {
        _uiState.update { it.copy(selectedAccount = account) }
    }

    fun onCategoryChange(category: Category?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onTypeChange(type: String?) {
        _uiState.update { it.copy(transactionType = type ?: "All") }
    }

    fun onDateChange(
        start: Long? = _uiState.value.startDate,
        end: Long? = _uiState.value.endDate,
    ) {
        _uiState.update { it.copy(startDate = start, endDate = end) }
    }

    fun clearFilters() {
        _uiState.value =
            SearchUiState(
                accounts = _uiState.value.accounts,
                categories = _uiState.value.categories,
            )
    }
}
