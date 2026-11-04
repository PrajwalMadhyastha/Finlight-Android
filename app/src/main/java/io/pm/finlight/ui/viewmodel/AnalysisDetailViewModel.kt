package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.TransactionDetails
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createAnalysisDetailFactory(application, dimension, dimensionId, startDate, endDate)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class AnalysisDetailViewModelFactory(
    application: Application,
    dimension: AnalysisDimension,
    dimensionId: String,
    startDate: Long,
    endDate: Long,
    resolvedDb: AppDatabase? = null,
    transactionAnalyticsDao: TransactionAnalyticsDao? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createAnalysisDetailFactory(
        context = application,
        dimension = dimension,
        dimensionId = dimensionId,
        startDate = startDate,
        endDate = endDate,
        resolvedDb = resolvedDb,
        transactionAnalyticsDao = transactionAnalyticsDao,
        dispatcherProvider = dispatcherProvider,
    )

class AnalysisDetailViewModel(
    private val transactionAnalyticsDao: TransactionAnalyticsDao,
    private val dimension: AnalysisDimension,
    private val dimensionId: String,
    private val startDate: Long,
    private val endDate: Long,
    dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    val transactions: StateFlow<List<TransactionDetails>>

    init {
        val transactionFlow =
            when (dimension) {
                AnalysisDimension.CATEGORY -> transactionAnalyticsDao.getTransactionsForCategoryInRange(dimensionId.toInt(), startDate, endDate)
                AnalysisDimension.TAG -> transactionAnalyticsDao.getTransactionsForTagInRange(dimensionId.toInt(), startDate, endDate)
                AnalysisDimension.MERCHANT -> transactionAnalyticsDao.getTransactionsForMerchantInRange(dimensionId, startDate, endDate)
            }
        transactions =
            transactionFlow
                .flowOn(dispatcherProvider.io)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
}
