// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AnalysisViewModel.kt
// REASON: FEATURE - The ViewModel has been significantly enhanced to support
// advanced filtering. It now fetches all categories, tags, and merchants to
// populate filter dropdowns. The UI state and reactive flows have been updated
// to manage the state of these new filters, calling the updated DAO queries
// with the selected filter parameters to produce a fully interactive analysis view.
// FIX - The reactive stream logic has been rewritten to correctly chain multiple
// `combine` operators. This resolves all build errors related to argument type
// mismatches and failed type inference by breaking down the complex combination
// of 11 flows into manageable, type-safe steps.
// FEATURE (Search) - The ViewModel now manages the state for the new search bar.
// It includes a StateFlow for the search query and a function to update it. The
// main reactive flow has been updated to pass this query to the DAO, and the logic
// now correctly resets the search query when the user switches between analysis
// dimensions (e.g., from Category to Tag).
//
// REASON: FEATURE (Analysis Filters) - Added `_includeExcluded` state flow
// to power the new toggle. This boolean is now included in `AnalysisUiState`
// and `AnalysisInputs`, passed through the reactive flows, and sent to the
// updated DAO methods. Added `onIncludeExcludedChanged` to update the state.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.*
import io.pm.finlight.data.model.SpendingAnalysisItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.util.*

enum class AnalysisDimension {
    CATEGORY, TAG, MERCHANT
}

enum class AnalysisTimePeriod {
    WEEK, MONTH, YEAR, ALL_TIME, CUSTOM
}

data class AnalysisUiState(
    val selectedDimension: AnalysisDimension = AnalysisDimension.CATEGORY,
    val selectedTimePeriod: AnalysisTimePeriod = AnalysisTimePeriod.MONTH,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val analysisItems: List<SpendingAnalysisItem> = emptyList(),
    val totalSpending: Double = 0.0,
    val isLoading: Boolean = true,
    // --- NEW: State for filters ---
    val showFilterSheet: Boolean = false,
    val selectedFilterCategory: Category? = null,
    val selectedFilterTag: Tag? = null,
    val selectedFilterMerchant: String? = null,
    val allCategories: List<Category> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val allMerchants: List<String> = emptyList(),
    // --- NEW: State for search ---
    val searchQuery: String = "",
    // --- NEW: State for "Include Excluded" toggle ---
    val includeExcluded: Boolean = false
)

// --- NEW: Helper data class for combining flows ---
private data class AnalysisInputs(
    val dimension: AnalysisDimension,
    val period: AnalysisTimePeriod,
    val dateRange: Pair<Long?, Long?>,
    val filterCat: Category?,
    val filterTag: Tag?,
    val filterMerchant: String?,
    val searchQuery: String?,
    // --- NEW: Add includeExcluded field ---
    val includeExcluded: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModel(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao
) : ViewModel() {

    // --- INPUTS: StateFlows to hold user selections ---
    private val _selectedDimension = MutableStateFlow(AnalysisDimension.CATEGORY)
    private val _selectedTimePeriod = MutableStateFlow(AnalysisTimePeriod.MONTH)
    private val _customDateRange = MutableStateFlow<Pair<Long?, Long?>>(Pair(null, null))
    private val _showFilterSheet = MutableStateFlow(false)

    // --- NEW: Input flows for advanced filters ---
    private val _selectedFilterCategory = MutableStateFlow<Category?>(null)
    private val _selectedFilterTag = MutableStateFlow<Tag?>(null)
    private val _selectedFilterMerchant = MutableStateFlow<String?>(null)

    // --- NEW: Input flow for search query ---
    private val _searchQuery = MutableStateFlow("")

    // --- NEW: Input flow for "Include Excluded" toggle ---
    private val _includeExcluded = MutableStateFlow(false)


    // --- DATA: Flows for filter dropdowns ---
    private val allCategories = categoryDao.getAllCategories()
    private val allTags = tagDao.getAllTags()
    private val allMerchants = transactionDao.getAllExpenseMerchants()

    // --- REFACTORED LOGIC ---

    // 1. Combine all user inputs into a single flow of a data class.
    private val analysisInputsFlow = combine(
        _selectedDimension, _selectedTimePeriod, _customDateRange,
        _selectedFilterCategory, _selectedFilterTag, _selectedFilterMerchant,
        _searchQuery,
        _includeExcluded // --- ADDED ---
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        AnalysisInputs(
            dimension = args[0] as AnalysisDimension,
            period = args[1] as AnalysisTimePeriod,
            dateRange = args[2] as Pair<Long?, Long?>,
            filterCat = args[3] as? Category,
            filterTag = args[4] as? Tag,
            filterMerchant = args[5] as? String,
            searchQuery = (args[6] as? String)?.takeIf { it.isNotBlank() },
            includeExcluded = args[7] as Boolean // --- ADDED ---
        )
    }

    // 2. Use that single input flow to trigger the database query.
    @OptIn(FlowPreview::class)
    private val analysisResultFlow = analysisInputsFlow
        .debounce(300) // Debounce search input
        .flatMapLatest { inputs ->
            val (start, end) = calculateDateRange(inputs.period, inputs.dateRange.first, inputs.dateRange.second)
            when (inputs.dimension) {
                // --- UPDATED: Pass includeExcluded to DAO methods ---
                AnalysisDimension.CATEGORY -> transactionDao.getSpendingAnalysisByCategory(start, end, inputs.filterTag?.id, inputs.filterMerchant, inputs.filterCat?.id, inputs.searchQuery, inputs.includeExcluded)
                AnalysisDimension.TAG -> transactionDao.getSpendingAnalysisByTag(start, end, inputs.filterCat?.id, inputs.filterMerchant, inputs.filterTag?.id, inputs.searchQuery, inputs.includeExcluded)
                AnalysisDimension.MERCHANT -> transactionDao.getSpendingAnalysisByMerchant(start, end, inputs.filterCat?.id, inputs.filterTag?.id, inputs.filterMerchant, inputs.searchQuery, inputs.includeExcluded)
            }
        }

    // 3. Combine the input flow, data flows, and result flow into the final UI state.
    val uiState: StateFlow<AnalysisUiState> = combine(
        analysisInputsFlow,
        _showFilterSheet,
        allCategories,
        allTags,
        allMerchants,
        analysisResultFlow
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val inputs = args[0] as AnalysisInputs
        @Suppress("UNCHECKED_CAST")
        val showSheet = args[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val cats = args[2] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val tags = args[3] as List<Tag>
        @Suppress("UNCHECKED_CAST")
        val merchants = args[4] as List<String>
        @Suppress("UNCHECKED_CAST")
        val items = args[5] as List<SpendingAnalysisItem>

        val total = items.sumOf { it.totalAmount }
        AnalysisUiState(
            selectedDimension = inputs.dimension,
            selectedTimePeriod = inputs.period,
            customStartDate = inputs.dateRange.first,
            customEndDate = inputs.dateRange.second,
            analysisItems = items,
            totalSpending = total,
            isLoading = false,
            showFilterSheet = showSheet,
            selectedFilterCategory = inputs.filterCat,
            selectedFilterTag = inputs.filterTag,
            selectedFilterMerchant = inputs.filterMerchant,
            allCategories = cats,
            allTags = tags,
            allMerchants = merchants,
            searchQuery = inputs.searchQuery ?: "",
            includeExcluded = inputs.includeExcluded // --- ADDED ---
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalysisUiState(isLoading = true)
    )

    fun selectDimension(dimension: AnalysisDimension) {
        _selectedDimension.value = dimension
        _searchQuery.value = "" // Clear search on dimension change
    }

    fun selectTimePeriod(period: AnalysisTimePeriod) {
        _selectedTimePeriod.value = period
        if (period != AnalysisTimePeriod.CUSTOM) {
            _customDateRange.value = Pair(null, null)
        }
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _selectedTimePeriod.value = AnalysisTimePeriod.CUSTOM
        _customDateRange.value = Pair(start, end)
    }

    // --- NEW: Functions to manage filter state ---
    fun onFilterSheetToggled(show: Boolean) {
        _showFilterSheet.value = show
    }

    fun selectFilterCategory(category: Category?) {
        _selectedFilterCategory.value = category
    }

    fun selectFilterTag(tag: Tag?) {
        _selectedFilterTag.value = tag
    }

    fun selectFilterMerchant(merchant: String?) {
        _selectedFilterMerchant.value = merchant
    }

    // --- NEW: Function to handle "Include Excluded" toggle ---
    fun onIncludeExcludedChanged(include: Boolean) {
        _includeExcluded.value = include
    }

    fun clearFilters() {
        _selectedFilterCategory.value = null
        _selectedFilterTag.value = null
        _selectedFilterMerchant.value = null
        _includeExcluded.value = false // --- ADDED ---
    }

    // --- NEW: Function to handle search query changes ---
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun calculateDateRange(period: AnalysisTimePeriod, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endDate = calendar.timeInMillis

        val startDate = when (period) {
            AnalysisTimePeriod.WEEK -> (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            AnalysisTimePeriod.MONTH -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis
            AnalysisTimePeriod.YEAR -> (calendar.clone() as Calendar).apply { add(Calendar.YEAR, -1) }.timeInMillis
            AnalysisTimePeriod.ALL_TIME -> 0L
            AnalysisTimePeriod.CUSTOM -> customStart ?: 0L
        }
        val finalEndDate = if (period == AnalysisTimePeriod.CUSTOM) customEnd ?: endDate else endDate
        return Pair(startDate, finalEndDate)
    }
}