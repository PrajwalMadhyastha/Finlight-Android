// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SearchViewModel.kt
// REASON: FIX - The ViewModel has been refactored to be fully reactive. It now
// uses a `flatMapLatest` operator on the UI state flow. This directly calls the
// new Flow-based DAO method, ensuring that `searchResults` is a "live" stream
// that automatically updates whenever the search criteria or the underlying
// transaction data changes, thus fixing the stale data bug.
// FEATURE - The ViewModel now supports filtering by tags. The UI state includes
// the list of all tags and the currently selected tag. The logic has been updated
// to fetch all tags and pass the selected tag's ID to the DAO's search query.
// FIX - The UI state now includes boolean flags (`showStartDatePicker`, `showEndDatePicker`)
// to control the visibility of the date pickers. The state management has been
// hoisted from the screen to the ViewModel to resolve a bug where the pickers
// would not appear on click. New functions have been added to manage this state.
// FIX - The logic for setting the end date now adjusts the timestamp to the end
// of the selected day (23:59:59). This ensures that the date range is inclusive
// and correctly includes transactions from the selected end date.
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
    val selectedTag: Tag? = null,
    val transactionType: String = "All", // "All", "Income", "Expense"
    val startDate: Long? = null,
    val endDate: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val hasSearched: Boolean = false,
    val showStartDatePicker: Boolean = false,
    val showEndDatePicker: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
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
                    state.selectedTag != null ||
                    state.transactionType != "All" ||
                    state.startDate != null ||
                    state.endDate != null

            val shouldSearch = state.keyword.isNotBlank() || filtersAreActive
            _uiState.update { it.copy(hasSearched = shouldSearch) }

            if (shouldSearch) {
                transactionDao.searchTransactions(
                    keyword = state.keyword,
                    accountId = state.selectedAccount?.id,
                    categoryId = state.selectedCategory?.id,
                    tagId = state.selectedTag?.id,
                    transactionType = if (state.transactionType.equals("All", ignoreCase = true)) null else state.transactionType.lowercase(),
                    startDate = state.startDate,
                    endDate = state.endDate
                )
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
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
        viewModelScope.launch {
            tagDao.getAllTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }

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

            _uiState.update { it.copy(startDate = start, endDate = end) }
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

    fun onTagChange(tag: Tag?) {
        _uiState.update { it.copy(selectedTag = tag) }
    }

    fun onTypeChange(type: String?) {
        _uiState.update { it.copy(transactionType = type ?: "All") }
    }

    fun onStartDateSelected(startDateMillis: Long?) {
        _uiState.update { it.copy(startDate = startDateMillis) }
    }

    fun onEndDateSelected(endDateMillis: Long?) {
        val adjustedEnd = endDateMillis?.let {
            Calendar.getInstance().apply {
                timeInMillis = it
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        }
        _uiState.update { it.copy(endDate = adjustedEnd) }
    }

    fun onClearStartDate() {
        _uiState.update { it.copy(startDate = null) }
    }

    fun onClearEndDate() {
        _uiState.update { it.copy(endDate = null) }
    }

    fun onShowStartDatePicker(show: Boolean) {
        _uiState.update { it.copy(showStartDatePicker = show) }
    }

    fun onShowEndDatePicker(show: Boolean) {
        _uiState.update { it.copy(showEndDatePicker = show) }
    }

    fun clearFilters() {
        _uiState.value =
            SearchUiState(
                accounts = _uiState.value.accounts,
                categories = _uiState.value.categories,
                tags = _uiState.value.tags,
            )
    }
}