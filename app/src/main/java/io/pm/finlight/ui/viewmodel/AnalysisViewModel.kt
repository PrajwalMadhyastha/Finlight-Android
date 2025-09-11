// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AnalysisViewModel.kt
// REASON: NEW FILE - This ViewModel and its factory power the new Spending
// Analysis screen. It manages the user's selected dimension and time period,
// fetches the aggregated spending data, and exposes it to the UI.
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

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        _uiState.onEach { state ->
            if (!state.isLoading) {
                _uiState.update { it.copy(isLoading = true) }
            }
        }.flatMapLatest { state ->
            val (start, end) = calculateDateRange(state)
            when (state.selectedDimension) {
                AnalysisDimension.CATEGORY -> transactionDao.getSpendingAnalysisByCategory(start, end)
                AnalysisDimension.TAG -> transactionDao.getSpendingAnalysisByTag(start, end)
                AnalysisDimension.MERCHANT -> transactionDao.getSpendingAnalysisByMerchant(start, end)
            }
        }.onEach { items ->
            val total = items.sumOf { it.totalAmount }
            _uiState.update {
                it.copy(
                    analysisItems = items,
                    totalSpending = total,
                    isLoading = false
                )
            }
        }.launchIn(viewModelScope)
    }

    fun selectDimension(dimension: AnalysisDimension) {
        _uiState.update { it.copy(selectedDimension = dimension) }
    }

    fun selectTimePeriod(period: AnalysisTimePeriod) {
        _uiState.update { it.copy(selectedTimePeriod = period) }
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _uiState.update { it.copy(customStartDate = start, customEndDate = end, selectedTimePeriod = AnalysisTimePeriod.CUSTOM) }
    }

    private fun calculateDateRange(state: AnalysisUiState): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        val startDate = when (state.selectedTimePeriod) {
            AnalysisTimePeriod.WEEK -> calendar.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            AnalysisTimePeriod.MONTH -> calendar.apply { add(Calendar.MONTH, -1) }.timeInMillis
            AnalysisTimePeriod.YEAR -> calendar.apply { add(Calendar.YEAR, -1) }.timeInMillis
            AnalysisTimePeriod.ALL_TIME -> 0L
            AnalysisTimePeriod.CUSTOM -> state.customStartDate ?: 0L
        }
        val finalEndDate = if (state.selectedTimePeriod == AnalysisTimePeriod.CUSTOM) state.customEndDate ?: endDate else endDate
        return Pair(startDate, finalEndDate)
    }
}