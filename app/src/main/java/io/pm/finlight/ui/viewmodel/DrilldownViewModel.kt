// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DrilldownViewModel.kt
// REASON: NEW FILE - This ViewModel and its associated factory power the new
// Category and Merchant detail screens. It provides a monthly spending trend
// chart and a list of transactions for a specific entity (category or merchant)
// within a given month.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

enum class DrilldownType {
    CATEGORY,
    MERCHANT
}

class DrilldownViewModelFactory(
    private val application: Application,
    private val drilldownType: DrilldownType,
    private val entityName: String,
    private val month: Int,
    private val year: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrilldownViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            @Suppress("UNCHECKED_CAST")
            return DrilldownViewModel(
                transactionDao = db.transactionDao(),
                drilldownType = drilldownType,
                entityName = entityName,
                month = month,
                year = year
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DrilldownViewModel(
    private val transactionDao: TransactionDao,
    private val drilldownType: DrilldownType,
    val entityName: String,
    private val month: Int,
    private val year: Int
) : ViewModel() {

    val transactionsForMonth: StateFlow<List<TransactionDetails>>
    val monthlyTrendChartData: StateFlow<Pair<BarData, List<String>>?>

    init {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
        }
        val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
        val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23) }.timeInMillis

        transactionsForMonth = when (drilldownType) {
            DrilldownType.CATEGORY -> transactionDao.getTransactionsForCategoryName(entityName, monthStart, monthEnd)
            DrilldownType.MERCHANT -> transactionDao.getTransactionsForMerchantName(entityName, monthStart, monthEnd)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        monthlyTrendChartData = flow {
            val endCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            val startCal = (endCal.clone() as Calendar).apply {
                add(Calendar.MONTH, -5)
                set(Calendar.DAY_OF_MONTH, 1)
            }

            val monthlyTotals = when (drilldownType) {
                DrilldownType.CATEGORY -> transactionDao.getMonthlySpendingForCategory(entityName, startCal.timeInMillis, endCal.timeInMillis)
                DrilldownType.MERCHANT -> transactionDao.getMonthlySpendingForMerchant(entityName, startCal.timeInMillis, endCal.timeInMillis)
            }.first()

            if (monthlyTotals.isEmpty()) {
                emit(null)
                return@flow
            }

            val entries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
            val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val totalsMap = monthlyTotals.associateBy { it.period }

            for (i in 0..5) {
                val monthCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, i) }
                val yearMonth = yearMonthFormat.format(monthCal.time)

                val total = totalsMap[yearMonth]?.totalAmount?.toFloat() ?: 0f
                entries.add(BarEntry(i.toFloat(), total))
                labels.add(monthFormat.format(monthCal.time))
            }

            val dataSet = BarDataSet(entries, "Monthly Spending").apply {
                color = 0xFF4DB6AC.toInt() // Teal
                setDrawValues(true)
                valueTextColor = 0xFFFFFFFF.toInt()
            }
            emit(Pair(BarData(dataSet), labels))

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }
}