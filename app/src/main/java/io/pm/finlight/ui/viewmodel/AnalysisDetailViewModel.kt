// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/AnalysisDetailViewModel.kt
// REASON: NEW FILE - This ViewModel and factory power the drill-down screen for
// the new Spending Analysis feature. It takes the dimension, ID, and date range
// and fetches the specific list of transactions that match.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionDao
import io.pm.finlight.TransactionDetails
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AnalysisDetailViewModelFactory(
    private val application: Application,
    private val dimension: AnalysisDimension,
    private val dimensionId: String,
    private val startDate: Long,
    private val endDate: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalysisDetailViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return AnalysisDetailViewModel(db.transactionDao(), dimension, dimensionId, startDate, endDate) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AnalysisDetailViewModel(
    transactionDao: TransactionDao,
    dimension: AnalysisDimension,
    dimensionId: String,
    startDate: Long,
    endDate: Long
) : ViewModel() {

    val transactions: StateFlow<List<TransactionDetails>>

    init {
        val transactionFlow = when (dimension) {
            AnalysisDimension.CATEGORY -> transactionDao.getTransactionsForCategoryInRange(dimensionId.toInt(), startDate, endDate)
            AnalysisDimension.TAG -> transactionDao.getTransactionsForTagInRange(dimensionId.toInt(), startDate, endDate)
            AnalysisDimension.MERCHANT -> transactionDao.getTransactionsForMerchantInRange(dimensionId, startDate, endDate)
        }
        transactions = transactionFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
}