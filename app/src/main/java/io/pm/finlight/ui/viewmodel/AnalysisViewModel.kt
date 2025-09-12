// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AnalysisViewModel.kt
// REASON: FIX - Refactored the ViewModel to use a proper unidirectional data flow.
// The previous implementation had a feedback loop where updating the UI state
// would immediately re-trigger the database query, causing infinite loading and
// UI flashing. This new structure separates user inputs from the final result,
// combining them reactively to produce a stable UI state and eliminating the loop.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionDao
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.SpendingAnalysisItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val isLoading: Boolean = true
)

class AnalysisViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return AnalysisViewModel(db.transactionDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModel(private val transactionDao: TransactionDao) : ViewModel() {

    // --- INPUTS: StateFlows to hold user selections ---
    private val _selectedDimension = MutableStateFlow(AnalysisDimension.CATEGORY)
    private val _selectedTimePeriod = MutableStateFlow(AnalysisTimePeriod.MONTH)
    private val _customDateRange = MutableStateFlow<Pair<Long?, Long?>>(Pair(null, null))

    // --- LOGIC: A flow that reacts to input changes and queries the DB ---
    private val analysisResultFlow = combine(
        _selectedDimension, _selectedTimePeriod, _customDateRange
    ) { dimension, period, dateRange ->
        // This Triple acts as the trigger for our database query
        Triple(dimension, period, dateRange)
    }.flatMapLatest { (dimension, period, dateRange) ->
        // This flow will cancel and restart if the inputs change
        val (start, end) = calculateDateRange(period, dateRange.first, dateRange.second)
        when (dimension) {
            AnalysisDimension.CATEGORY -> transactionDao.getSpendingAnalysisByCategory(start, end)
            AnalysisDimension.TAG -> transactionDao.getSpendingAnalysisByTag(start, end)
            AnalysisDimension.MERCHANT -> transactionDao.getSpendingAnalysisByMerchant(start, end)
        }
    }

    // --- OUTPUT: A single StateFlow for the UI, combining inputs and results ---
    val uiState: StateFlow<AnalysisUiState> = combine(
        _selectedDimension,
        _selectedTimePeriod,
        _customDateRange,
        analysisResultFlow
    ) { dimension, period, dateRange, items ->
        // This combines all our inputs and the final result into one state object for the UI
        val total = items.sumOf { it.totalAmount }
        AnalysisUiState(
            selectedDimension = dimension,
            selectedTimePeriod = period,
            customStartDate = dateRange.first,
            customEndDate = dateRange.second,
            analysisItems = items,
            totalSpending = total,
            isLoading = false // Loading is handled implicitly by stateIn's initial value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalysisUiState(isLoading = true) // Start in loading state, will update when the flow emits
    )

    fun selectDimension(dimension: AnalysisDimension) {
        _selectedDimension.value = dimension
    }

    fun selectTimePeriod(period: AnalysisTimePeriod) {
        _selectedTimePeriod.value = period
        // Clear custom dates if a predefined period is selected
        if (period != AnalysisTimePeriod.CUSTOM) {
            _customDateRange.value = Pair(null, null)
        }
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        // Setting a custom range automatically selects the CUSTOM period
        _selectedTimePeriod.value = AnalysisTimePeriod.CUSTOM
        _customDateRange.value = Pair(start, end)
    }

    private fun calculateDateRange(period: AnalysisTimePeriod, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        // Set to end of today to include all of today's transactions
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