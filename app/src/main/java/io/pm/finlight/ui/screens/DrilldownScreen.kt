// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/DrilldownScreen.kt
// REASON: FIX (Crash) - Injected TransactionViewModel as a parameter instead of
// using the default viewModel() constructor. This resolves a crash caused by the
// ViewModel's complex dependencies not being provided by the default factory.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import io.pm.finlight.DrilldownType
import io.pm.finlight.DrilldownViewModel
import io.pm.finlight.DrilldownViewModelFactory
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.TransactionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrilldownScreen(
    navController: NavController,
    drilldownType: DrilldownType,
    entityName: String,
    month: Int,
    year: Int,
    transactionViewModel: TransactionViewModel // --- FIX: Inject ViewModel ---
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = DrilldownViewModelFactory(application, drilldownType, entityName, month, year)
    val viewModel: DrilldownViewModel = viewModel(factory = factory)
    // val transactionViewModel: TransactionViewModel = viewModel() // --- REMOVED: Buggy line ---

    val transactions by viewModel.transactionsForMonth.collectAsState()
    val chartData by viewModel.monthlyTrendChartData.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassPanel {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "6-Month Spending Trend",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    if (chartData != null) {
                        SpendingBarChart(chartData = chartData!!)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Not enough data for a trend chart.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (transactions.isNotEmpty()) {
            item {
                Text(
                    "Transactions this month",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(transactions, key = { it.transaction.id }) { transaction ->
                TransactionItem(
                    transactionDetails = transaction,
                    onClick = { navController.navigate("transaction_detail/${transaction.transaction.id}") },
                    onCategoryClick = { transactionViewModel.requestCategoryChange(it) }
                )
            }
        }
    }
}

@Composable
private fun SpendingBarChart(chartData: Pair<BarData, List<String>>) {
    val (barData, labels) = chartData
    val axisTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val valueTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)
                setDrawValueAboveBar(true)
                setTouchEnabled(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(false)
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(labels)
                    textColor = axisTextColor
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                }
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = axisTextColor and 0x22FFFFFF
                    setDrawLabels(false)
                    setDrawAxisLine(false)
                    axisMinimum = 0f
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            chart.data = barData
            (chart.data.dataSets.first()).valueTextColor = valueTextColor
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}