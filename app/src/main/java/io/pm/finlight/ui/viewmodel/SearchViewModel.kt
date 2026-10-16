// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SearchViewModel.kt
// REASON: REFACTOR (Domain 5 / Issue #339) - Decouple SearchViewModel from Raw DAOs
// & Expose searchTransactions:
// 1. Injected ITransactionRepository, IAccountRepository, ICategoryRepository,
//    ITagRepository, and DispatcherProvider.
// 2. Reactively combined metadata flows (accounts, categories, tags) into SearchUiState.
// 3. Decoupled hasSearched and removed re-entrant _uiState.update mutation from flatMapLatest.
// 4. Standardized package to io.pm.finlight.ui.viewmodel.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.FinancialSummary
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.SearchFilter
import io.pm.finlight.Tag
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class SearchQueryParameters(
    val keyword: String = "",
    val selectedAccount: Account? = null,
    val selectedCategory: Category? = null,
    val selectedTag: Tag? = null,
    val transactionType: String = "All",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val isDrilldown: Boolean = false,
) {
    val filtersAreActive: Boolean
        get() =
            selectedAccount != null ||
                selectedCategory != null ||
                selectedTag != null ||
                (!transactionType.equals("All", ignoreCase = true) && transactionType.isNotBlank()) ||
                startDate != null ||
                endDate != null

    val shouldSearch: Boolean
        get() = keyword.isNotBlank() || filtersAreActive || isDrilldown
}

data class SearchUiState(
    val keyword: String = "",
    val selectedAccount: Account? = null,
    val selectedCategory: Category? = null,
    val selectedTag: Tag? = null,
    // "All", "Income", "Expense"
    val transactionType: String = "All",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val hasSearched: Boolean = false,
    val showStartDatePicker: Boolean = false,
    val showEndDatePicker: Boolean = false,
    val displayDate: String? = null,
    // Flag for drilldown mode
    val isDrilldown: Boolean = false,
) {
    fun toQueryParameters(): SearchQueryParameters =
        SearchQueryParameters(
            keyword = keyword,
            selectedAccount = selectedAccount,
            selectedCategory = selectedCategory,
            selectedTag = selectedTag,
            transactionType = transactionType,
            startDate = startDate,
            endDate = endDate,
            isDrilldown = isDrilldown,
        )

    val filtersAreActive: Boolean
        get() = toQueryParameters().filtersAreActive

    val shouldSearch: Boolean
        get() = toQueryParameters().shouldSearch
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val tagRepository: ITagRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val initialCategoryId: Int? = null,
    private val initialDateMillis: Long? = null,
    private val initialQuery: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _daySummary = MutableStateFlow<FinancialSummary?>(null)
    val daySummary: StateFlow<FinancialSummary?> = _daySummary.asStateFlow()

    private var initialCategoryResolved = initialCategoryId == null
    private var daySummaryJob: Job? = null

    val searchResults: StateFlow<List<TransactionDetails>> =
        uiState
            .map { it.toQueryParameters() }
            .distinctUntilChanged()
            .debounce(300L)
            .flatMapLatest { params ->
                if (params.shouldSearch) {
                    val resolvedType =
                        if (params.transactionType.equals("All", ignoreCase = true)) {
                            null
                        } else {
                            TransactionType.fromStringOrNull(params.transactionType)
                        }
                    transactionRepository.searchTransactions(
                        query = params.keyword.trim(),
                        filter =
                            SearchFilter(
                                accountId = params.selectedAccount?.id,
                                categoryId = params.selectedCategory?.id,
                                tagId = params.selectedTag?.id,
                                transactionType = resolvedType,
                                startDate = params.startDate,
                                endDate = params.endDate,
                            ),
                    )
                        .catch { emit(emptyList()) }
                        .flowOn(dispatcherProvider.io)
                } else {
                    flowOf(emptyList())
                }
            }
            .flowOn(dispatcherProvider.default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch(dispatcherProvider.main) {
            uiState
                .map { it.toQueryParameters() }
                .distinctUntilChanged()
                .debounce(300L)
                .collect { params ->
                    _uiState.update { current ->
                        current.copy(hasSearched = params.shouldSearch)
                    }
                }
        }

        viewModelScope.launch(dispatcherProvider.main) {
            combine(
                accountRepository.allAccounts,
                categoryRepository.allCategories,
                tagRepository.allTags,
            ) { accounts, categories, tags ->
                Triple(accounts, categories, tags)
            }.flowOn(dispatcherProvider.io)
                .collect { (accounts, categories, tags) ->
                    _uiState.update { current ->
                        val resolvedCategory =
                            if (!initialCategoryResolved && initialCategoryId != null && current.selectedCategory == null) {
                                val found = categories.find { it.id == initialCategoryId }
                                if (found != null || categories.isNotEmpty()) {
                                    initialCategoryResolved = true
                                }
                                found
                            } else if (current.selectedCategory != null) {
                                categories.find { it.id == current.selectedCategory.id }
                            } else {
                                null
                            }
                        current.copy(
                            accounts = accounts,
                            categories = categories,
                            tags = tags,
                            selectedCategory = resolvedCategory,
                        )
                    }
                }
        }

        if (!initialQuery.isNullOrBlank()) {
            _uiState.update { current ->
                current.copy(
                    keyword = initialQuery,
                    isDrilldown = true,
                )
            }
        }

        if (initialDateMillis != null && initialDateMillis != -1L) {
            val cal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            val dateFormatter = FormatUtils.getFormatter("MMMM d, yyyy", Locale.getDefault())
            val formattedDate = dateFormatter.format(cal.time)

            _uiState.update { current ->
                current.copy(
                    startDate = start,
                    endDate = end,
                    displayDate = formattedDate,
                )
            }

            daySummaryJob =
                viewModelScope.launch(dispatcherProvider.main) {
                    transactionRepository.getFinancialSummaryForRangeFlow(start, end)
                        .flowOn(dispatcherProvider.io)
                        .collect { summary ->
                            _daySummary.value = summary
                        }
                }
        }
    }

    fun onKeywordChange(newKeyword: String) {
        _uiState.update { current ->
            current.copy(keyword = newKeyword, hasSearched = false)
        }
    }

    fun onAccountChange(account: Account?) {
        _uiState.update { current ->
            current.copy(selectedAccount = account, hasSearched = false)
        }
    }

    fun onCategoryChange(category: Category?) {
        initialCategoryResolved = true
        _uiState.update { current ->
            current.copy(selectedCategory = category, hasSearched = false)
        }
    }

    fun onTagChange(tag: Tag?) {
        _uiState.update { current ->
            current.copy(selectedTag = tag, hasSearched = false)
        }
    }

    fun onTypeChange(type: String?) {
        _uiState.update { current ->
            current.copy(transactionType = type ?: "All", hasSearched = false)
        }
    }

    fun onStartDateSelected(startDateMillis: Long?) {
        val adjustedStart =
            startDateMillis?.let {
                Calendar.getInstance().apply {
                    timeInMillis = it
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        daySummaryJob?.cancel()
        daySummaryJob = null
        _daySummary.value = null
        _uiState.update { current ->
            current.copy(startDate = adjustedStart, displayDate = null, hasSearched = false)
        }
    }

    fun onEndDateSelected(endDateMillis: Long?) {
        val adjustedEnd =
            endDateMillis?.let {
                Calendar.getInstance().apply {
                    timeInMillis = it
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }
        daySummaryJob?.cancel()
        daySummaryJob = null
        _daySummary.value = null
        _uiState.update { current ->
            current.copy(endDate = adjustedEnd, displayDate = null, hasSearched = false)
        }
    }

    fun onClearStartDate() {
        daySummaryJob?.cancel()
        daySummaryJob = null
        _daySummary.value = null
        _uiState.update { current ->
            current.copy(startDate = null, displayDate = null, hasSearched = false)
        }
    }

    fun onClearEndDate() {
        daySummaryJob?.cancel()
        daySummaryJob = null
        _daySummary.value = null
        _uiState.update { current ->
            current.copy(endDate = null, displayDate = null, hasSearched = false)
        }
    }

    fun onShowStartDatePicker(show: Boolean) {
        _uiState.update { it.copy(showStartDatePicker = show) }
    }

    fun onShowEndDatePicker(show: Boolean) {
        _uiState.update { it.copy(showEndDatePicker = show) }
    }

    fun clearFilters() {
        initialCategoryResolved = true
        daySummaryJob?.cancel()
        daySummaryJob = null
        _uiState.update { current ->
            SearchUiState(
                accounts = current.accounts,
                categories = current.categories,
                tags = current.tags,
                hasSearched = false,
            )
        }
        _daySummary.value = null
    }
}
