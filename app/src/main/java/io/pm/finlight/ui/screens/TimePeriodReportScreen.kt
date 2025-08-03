// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/TimePeriodReportScreen.kt
// REASON: FIX - The composable now accepts a `showPreviousMonth` boolean
// parameter. This parameter is passed directly to the ViewModelFactory, enabling
// it to create a ViewModel that is correctly initialized to the previous month's
// data when navigated to from a monthly summary notification.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import android.graphics.Typeface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.MonthlyConsistencyCalendarCard
import io.pm.finlight.ui.components.TransactionItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePeriodReportScreen(
    navController: NavController,
    timePeriod: TimePeriod,
    transactionViewModel: TransactionViewModel,
    initialDateMillis: Long? = null,
    showPreviousMonth: Boolean = false // --- NEW: Add parameter
) {
    val application = LocalContext.current.applicationContext as Application
    // --- UPDATED: Pass the new parameter to the factory ---
    val factory = TimePeriodReportViewModelFactory(application, timePeriod, initialDateMillis, showPreviousMonth)
    val viewModel: TimePeriodReportViewModel = viewModel(factory = factory)

    val selectedDate by viewModel.selectedDate.collectAsState()
    val transactions by viewModel.transactionsForPeriod.collectAsState()
    val chartDataPair by viewModel.chartData.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val monthlyConsistencyData by viewModel.monthlyConsistencyData.collectAsState()
    val consistencyStats by viewModel.consistencyStats.collectAsState()

    val totalSpent = transactions.filter { it.transaction.transactionType == "expense" && !it.transaction.isExcluded }.sumOf { it.transaction.amount }
    val totalIncome by viewModel.totalIncome.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (timePeriod == TimePeriod.DAILY) {
            NotificationManagerCompat.from(context).cancel(2) // Daily Report Notification ID
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (timePeriod) {
                            TimePeriod.DAILY -> "Daily Report"
                            TimePeriod.WEEKLY -> "Weekly Report"
                            TimePeriod.MONTHLY -> "Monthly Report"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        var dragAmount by remember { mutableStateOf(0f) }

        Column(
            modifier = Modifier
                .padding(innerPadding)
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
                    ReportHeader(
                        totalSpent = totalSpent,
                        totalIncome = totalIncome,
                        timePeriod = timePeriod,
                        selectedDate = selectedDate.time
                    )
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
                                navController.navigate("search_screen?date=${date.time}")
                            }
                        )
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

                if (transactions.isNotEmpty()) {
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
                } else {
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
}

@Composable
private fun ReportHeader(totalSpent: Double, totalIncome: Double, timePeriod: TimePeriod, selectedDate: Date) {
    val subtitle = when (timePeriod) {
        TimePeriod.DAILY -> {
            val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            val startCal = Calendar.getInstance().apply {
                time = selectedDate
                add(Calendar.HOUR_OF_DAY, -24)
            }
            "Since ${format.format(startCal.time)}"
        }
        TimePeriod.WEEKLY -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            val startCal = Calendar.getInstance().apply {
                time = selectedDate
                add(Calendar.DAY_OF_YEAR, -7)
            }
            "Since ${format.format(startCal.time)}"
        }
        TimePeriod.MONTHLY -> {
            val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val startCal = (selectedDate.clone() as Date).apply {
                val cal = Calendar.getInstance()
                cal.time = this
                cal.set(Calendar.DAY_OF_MONTH, 1)
                time = cal.timeInMillis
            }
            "For ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate)}"
        }
    }

    val backgroundIcon = when (timePeriod) {
        TimePeriod.DAILY -> Icons.Default.CalendarViewDay
        TimePeriod.WEEKLY -> Icons.Default.CalendarViewWeek
        TimePeriod.MONTHLY -> Icons.Default.CalendarViewMonth
    }

    GlassPanel {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = backgroundIcon,
                contentDescription = null,
                modifier = Modifier.size(180.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Income",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(totalIncome).drop(1)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Spent",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(totalSpent).drop(1)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
