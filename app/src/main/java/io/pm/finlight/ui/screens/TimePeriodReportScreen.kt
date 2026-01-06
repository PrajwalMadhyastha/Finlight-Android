// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/TimePeriodReportScreen.kt
// REASON: FIX - Passed the required `year` parameter to `ConsistencyCalendar` inside
// `TimePeriodReportScreen`. This resolves the build error caused by the recent
// API change in `ConsistencyCalendar` (which now requires an explicit year).
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.ui.components.ConsistencyCalendar
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.components.MonthlyConsistencyCalendarCard
import io.pm.finlight.ui.components.ReportPeriodSelector
import io.pm.finlight.ui.components.SpendingSummaryCard
import io.pm.finlight.ui.components.TransactionItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePeriodReportScreen(
    navController: NavController,
    timePeriod: TimePeriod,
    transactionViewModel: TransactionViewModel,
    initialDateMillis: Long? = null,
    showPreviousMonth: Boolean = false
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = TimePeriodReportViewModelFactory(application, timePeriod, initialDateMillis, showPreviousMonth)
    val viewModel: TimePeriodReportViewModel = viewModel(factory = factory)

    val selectedDate by viewModel.selectedDate.collectAsState()
    val transactions by viewModel.transactionsForPeriod.collectAsState()
    val chartDataPair by viewModel.chartData.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val monthlyConsistencyData by viewModel.monthlyConsistencyData.collectAsState()
    val yearlyConsistencyData by viewModel.yearlyConsistencyData.collectAsState()
    val consistencyStats by viewModel.consistencyStats.collectAsState()

    // --- FIX: Use ViewModel-derived totals which account for outlier exclusions ---
    val totalSpent by viewModel.totalExpenses.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val averageSpent by viewModel.monthlyAverageExpenses.collectAsState()
    val averageIncome by viewModel.monthlyAverageIncome.collectAsState()

    val yearlyMonthlyBreakdown by viewModel.yearlyMonthlyBreakdown.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (timePeriod == TimePeriod.DAILY) {
            NotificationManagerCompat.from(context).cancel(2) // Daily Report Notification ID
        }
    }

    var dragAmount by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        if (dragAmount > 150) {
                            viewModel.selectPreviousPeriod()
                        } else if (dragAmount < -150) {
                            viewModel.selectNextPeriod()
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                ) { change, horizontalDragAmount ->
                    dragAmount += horizontalDragAmount
                    change.consume()
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ReportPeriodSelector(
                    timePeriod = timePeriod,
                    selectedDate = selectedDate.time,
                    onPrevious = viewModel::selectPreviousPeriod,
                    onNext = viewModel::selectNextPeriod
                )
            }

            item {
                SpendingSummaryCard(
                    totalSpent = totalSpent,
                    totalIncome = totalIncome,
                    averageSpent = averageSpent,
                    averageIncome = averageIncome
                )
            }

            if (timePeriod == TimePeriod.YEARLY && yearlyMonthlyBreakdown.isNotEmpty()) {
                item {
                    YearlyOutlierManagementCard(
                        breakdowns = yearlyMonthlyBreakdown,
                        onToggleIncome = viewModel::toggleIncomeExclusion,
                        onToggleExpense = viewModel::toggleExpenseExclusion
                    )
                }
            }

            item {
                insights?.let {
                    ReportInsightsCard(insights = it)
                }
            }

            if (timePeriod == TimePeriod.MONTHLY) {
                item {
                    MonthlyConsistencyCalendarCard(
                        data = monthlyConsistencyData,
                        stats = consistencyStats,
                        selectedMonth = selectedDate,
                        onPreviousMonth = viewModel::selectPreviousPeriod,
                        onNextMonth = viewModel::selectNextPeriod,
                        onDayClick = { date ->
                            val dayData = monthlyConsistencyData.find {
                                val cal1 = Calendar.getInstance().apply { time = it.date }
                                val cal2 = Calendar.getInstance().apply { time = date }
                                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                            }
                            val safeToSpend = dayData?.safeToSpend ?: 0L
                            navController.navigate("search_screen?date=${date.time}&safeToSpend=$safeToSpend&focusSearch=false")
                        }
                    )
                }
            }

            if (timePeriod == TimePeriod.YEARLY) {
                item {
                    GlassPanel {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Yearly Spending Consistency",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (yearlyConsistencyData.isEmpty()) {
                                CircularProgressIndicator()
                            } else {
                                ConsistencyCalendar(
                                    data = yearlyConsistencyData,
                                    year = selectedDate.get(Calendar.YEAR), // FIX: Pass the year here
                                    onDayClick = { date ->
                                        val dayData = yearlyConsistencyData.find {
                                            val cal1 = Calendar.getInstance().apply { time = it.date }
                                            val cal2 = Calendar.getInstance().apply { time = date }
                                            cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                                        }
                                        val safeToSpend = dayData?.safeToSpend ?: 0L
                                        navController.navigate("search_screen?date=${date.time}&safeToSpend=$safeToSpend&focusSearch=false")
                                    }
                                )
                            }
                        }
                    }
                }
            }


            item {
                GlassPanel {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Spending Chart",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        if (chartDataPair != null) {
                            SpendingBarChart(
                                chartData = chartDataPair!!
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No chart data for this period.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (transactions.isNotEmpty() && timePeriod != TimePeriod.YEARLY) {
                item {
                    Text(
                        "Transactions in this Period",
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
            } else if (timePeriod != TimePeriod.YEARLY) {
                item {
                    GlassPanel {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No transactions recorded for this period.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlyOutlierManagementCard(
    breakdowns: List<MonthlyBreakdown>,
    onToggleIncome: (String) -> Unit,
    onToggleExpense: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            .apply { maximumFractionDigits = 0 }
    }

    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Manage Outlier Months",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        "Exclude months with irregular high spend/income to see a more accurate monthly average.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("Month", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                        Text("Income", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        Text("Spend", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    breakdowns.forEach { breakdown ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                breakdown.monthName,
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Income Toggle + Amount
                            Column(
                                modifier = Modifier.weight(1f).clickable { onToggleIncome(breakdown.monthKey) },
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    currencyFormat.format(breakdown.income),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (breakdown.isIncomeExcluded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                    fontWeight = if (breakdown.isIncomeExcluded) FontWeight.Normal else FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (breakdown.isIncomeExcluded) Icons.Default.CheckBoxOutlineBlank else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (breakdown.isIncomeExcluded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Expense Toggle + Amount
                            Column(
                                modifier = Modifier.weight(1f).clickable { onToggleExpense(breakdown.monthKey) },
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    currencyFormat.format(breakdown.expenses),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (breakdown.isExpenseExcluded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                                    fontWeight = if (breakdown.isExpenseExcluded) FontWeight.Normal else FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (breakdown.isExpenseExcluded) Icons.Default.CheckBoxOutlineBlank else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (breakdown.isExpenseExcluded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingBarChart(chartData: Pair<BarData, List<String>>) {
    val (barData, labels) = chartData
    val selectedIndex = labels.size - 1

    val highlightColor = MaterialTheme.colorScheme.primary.toArgb()
    val defaultColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val axisTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val valueTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val colors = labels.indices.map { if (it == selectedIndex) highlightColor else defaultColor }
    (barData.dataSets.first() as BarDataSet).colors = colors

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
            (chart.data.dataSets.first() as BarDataSet).valueTextColor = valueTextColor
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}