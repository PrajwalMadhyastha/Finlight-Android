// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/DrilldownViewModel.kt
// REASON: NEW FILE - This ViewModel and its associated factory power the new
// Category and Merchant detail screens. It provides a monthly spending trend
// chart and a list of transactions for a specific entity (category or merchant)
// within a given month.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TransactionDetails
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.flow.*
import java.util.*

enum class DrilldownType {
    CATEGORY,
    MERCHANT,
}

class DrilldownViewModelFactory(
    private val application: Application,
    private val drilldownType: DrilldownType,
    private val entityName: String,
    private val month: Int,
    private val year: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrilldownViewModel::class.java)) {
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            @Suppress("UNCHECKED_CAST")
            return DrilldownViewModel(
                transactionRepository = transactionRepository,
                drilldownType = drilldownType,
                entityName = entityName,
                month = month,
                year = year,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DrilldownViewModel(
    private val transactionRepository: ITransactionRepository,
    private val drilldownType: DrilldownType,
    val entityName: String,
    private val month: Int,
    private val year: Int,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    val transactionsForMonth: StateFlow<List<TransactionDetails>>
    val monthlyTrendChartData: StateFlow<Pair<BarData, List<String>>?>

    init {
        val calendar =
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
            }
        val monthStart =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        val monthEnd =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

        transactionsForMonth =
            when (drilldownType) {
                DrilldownType.CATEGORY -> transactionRepository.getTransactionsForCategoryName(entityName, monthStart, monthEnd)
                DrilldownType.MERCHANT -> transactionRepository.getTransactionsForMerchantName(entityName, monthStart, monthEnd)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val endCal =
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
        val startCal =
            (endCal.clone() as Calendar).apply {
                add(Calendar.MONTH, -5)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        monthlyTrendChartData =
            transactionRepository.getMonthlyTrends(startCal.timeInMillis)
                .map { monthlyTrends ->
                    if (monthlyTrends.isEmpty()) {
                        null
                    } else {
                        val incomeEntries = mutableListOf<BarEntry>()
                        val expenseEntries = mutableListOf<BarEntry>()
                        val labels = mutableListOf<String>()
                        val monthFormat = FormatUtils.getFormatter("MMM", Locale.getDefault())
                        val yearMonthFormat = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault())
                        val trendsMap = monthlyTrends.associateBy { it.monthYear }

                        for (i in 0..5) {
                            val monthCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, i) }
                            val yearMonth = yearMonthFormat.format(monthCal.time)

                            val trend = trendsMap[yearMonth]
                            incomeEntries.add(BarEntry(i.toFloat(), trend?.totalIncome?.toFloat() ?: 0f))
                            expenseEntries.add(BarEntry(i.toFloat(), trend?.totalExpenses?.toFloat() ?: 0f))
                            labels.add(monthFormat.format(monthCal.time))
                        }

                        val incomeDataSet =
                            BarDataSet(incomeEntries, "Income").apply {
                                color = 0xFF66BB6A.toInt() // Green
                            }
                        val expenseDataSet =
                            BarDataSet(expenseEntries, "Expense").apply {
                                color = 0xFFEF5350.toInt() // Red
                            }
                        Pair(BarData(incomeDataSet, expenseDataSet), labels)
                    }
                }.flowOn(dispatcherProvider.default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }
}
