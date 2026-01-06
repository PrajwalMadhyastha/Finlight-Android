// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/ReportsScreen.kt
// REASON: FIX - Passed the actual year from the `yearlyCalendarData` to the
// `ConsistencyCalendar`. This ensures that when the user navigates to a previous
// year, the calendar component renders the grid for that specific year instead of
// defaulting to the current one.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.ui.components.ConsistencyCalendar
import io.pm.finlight.ui.components.DetailedMonthlyCalendar
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.GroupedBarChart
import io.pm.finlight.utils.CategoryIconHelper
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = viewModel(),
) {
    val reportData by viewModel.reportData.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()

    val reportViewType by viewModel.reportViewType.collectAsState()
    val yearlyCalendarData by viewModel.consistencyCalendarData.collectAsState()
    val detailedMonthData by viewModel.detailedMonthData.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val consistencyStats by viewModel.displayedConsistencyStats.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periods = ReportPeriod.entries
                periods.chunked(2).forEach { rowPeriods ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPeriods.forEach { period ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = period == selectedPeriod,
                                onClick = { viewModel.selectPeriod(period) },
                                label = { Text(period.displayName) },
                                leadingIcon = if (period == selectedPeriod) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            reportData.insights?.let {
                ReportInsightsCard(insights = it)
            }
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Spending Consistency",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = reportViewType == ReportViewType.MONTHLY,
                            onClick = { viewModel.setReportView(ReportViewType.MONTHLY) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Monthly")
                        }
                        SegmentedButton(
                            selected = reportViewType == ReportViewType.YEARLY,
                            onClick = { viewModel.setReportView(ReportViewType.YEARLY) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Yearly")
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (reportViewType) {
                            ReportViewType.YEARLY -> {
                                if (yearlyCalendarData.isEmpty()) {
                                    CircularProgressIndicator()
                                } else {
                                    // Extract year from the first data item
                                    val year = remember(yearlyCalendarData) {
                                        val cal = Calendar.getInstance()
                                        cal.time = yearlyCalendarData.first().date
                                        cal.get(Calendar.YEAR)
                                    }

                                    ConsistencyCalendar(
                                        data = yearlyCalendarData,
                                        year = year,
                                        onDayClick = { date ->
                                            val dayData = yearlyCalendarData.find {
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
                            ReportViewType.MONTHLY -> {
                                if (detailedMonthData.isEmpty()) {
                                    CircularProgressIndicator()
                                } else {
                                    DetailedMonthlyCalendar(
                                        data = detailedMonthData,
                                        selectedMonth = selectedMonth,
                                        onPreviousMonth = viewModel::selectPreviousMonth,
                                        onNextMonth = viewModel::selectNextMonth,
                                        onDayClick = { date ->
                                            val dayData = detailedMonthData.find {
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

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(consistencyStats.noSpendDays, "No Spend")
                        StatItem(consistencyStats.goodDays, "Good Days")
                        StatItem(consistencyStats.badDays, "Over Budget")
                        StatItem(consistencyStats.noDataDays, "No Data")
                    }
                }
            }
        }


        item {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Spending by Category for ${reportData.periodTitle}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    val pieData = reportData.pieData
                    if (pieData == null || pieData.entryCount == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No expense data for this period.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DonutChart(
                                modifier = Modifier.weight(1.6f),
                                pieData = pieData,
                                onSliceClick = { entry ->
                                    val categoryName = entry.label
                                    val category = allCategories.find { it.name.equals(categoryName, ignoreCase = true) }
                                    category?.let {
                                        navController.navigate("search_screen?categoryId=${it.id}&expandFilters=false&focusSearch=false")
                                    }
                                }
                            )
                            ChartLegend(
                                modifier = Modifier.weight(1.6f),
                                pieData = pieData
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Income vs. Expense Trend",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    val trendDataPair = reportData.trendData
                    if (trendDataPair != null && trendDataPair.first.entryCount > 0) {
                        GroupedBarChart(
                            chartData = trendDataPair,
                            onBarClick = { entry ->
                                val monthIndex = entry.x.toInt()
                                val trends = viewModel.reportData.value.trendData?.first?.dataSets?.firstOrNull()?.getEntryForIndex(monthIndex)
                                if (trends != null) {
                                    val calendar = Calendar.getInstance()
                                    calendar.add(Calendar.MONTH, monthIndex - (reportData.trendData?.second?.size?.minus(1) ?: 0))
                                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                                    val dateMillis = calendar.timeInMillis
                                    navController.navigate("time_period_report_screen/${TimePeriod.MONTHLY}?date=$dateMillis")
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Not enough data for trend analysis.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Detailed Reports",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            GlassReportNavigationCard(
                title = "Spending Analysis",
                subtitle = "Break down spending by category, tag, or merchant.",
                icon = Icons.Default.Analytics,
                onClick = { navController.navigate("analysis_screen") }
            )
        }
        item {
            GlassReportNavigationCard(
                title = "Daily Report",
                subtitle = "See a summary of yesterday's spending.",
                icon = Icons.Default.CalendarViewDay,
                onClick = { navController.navigate("time_period_report_screen/${TimePeriod.DAILY}") }
            )
        }
        item {
            GlassReportNavigationCard(
                title = "Weekly Report",
                subtitle = "Analyze your spending week by week.",
                icon = Icons.Default.CalendarViewWeek,
                onClick = { navController.navigate("time_period_report_screen/${TimePeriod.WEEKLY}") }
            )
        }
        item {
            GlassReportNavigationCard(
                title = "Monthly Report",
                subtitle = "Get a high-level overview of your monthly habits.",
                icon = Icons.Default.CalendarViewMonth,
                onClick = { navController.navigate("time_period_report_screen/${TimePeriod.MONTHLY}") }
            )
        }
        item {
            GlassReportNavigationCard(
                title = "Yearly Report",
                subtitle = "Review your financial performance for the year.",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                onClick = { navController.navigate("time_period_report_screen/${TimePeriod.YEARLY}") }
            )
        }
    }
}

@Composable
private fun DonutChart(
    modifier: Modifier = Modifier,
    pieData: PieData,
    onSliceClick: (PieEntry) -> Unit
) {
    val dataSet = pieData.dataSet as? PieDataSet ?: return
    val totalAmount = remember(dataSet) {
        (0 until dataSet.entryCount).sumOf { dataSet.getEntryForIndex(it).y.toDouble() }.toFloat()
    }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(pieData) {
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pieData) {
                detectTapGestures { tapOffset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val diameter = min(size.width, size.height) * 0.8f
                    val radius = diameter / 2f
                    val strokeWidth = 32.dp.toPx()

                    val distance = sqrt((tapOffset.x - centerX).pow(2) + (tapOffset.y - centerY).pow(2))
                    if (distance < radius - strokeWidth / 2 || distance > radius + strokeWidth / 2) {
                        return@detectTapGestures
                    }

                    val dx = tapOffset.x - centerX
                    val dy = tapOffset.y - centerY
                    val angleRad = atan2(dy.toDouble(), dx.toDouble())
                    var angleDeg = Math.toDegrees(angleRad).toFloat()
                    if (angleDeg < 0) angleDeg += 360

                    val tapAngle = (angleDeg + 90) % 360

                    var currentAngle = 0f
                    for (i in 0 until dataSet.entryCount) {
                        val entry = dataSet.getEntryForIndex(i) as PieEntry
                        val sweepAngle = (entry.y / totalAmount) * 360f
                        if (tapAngle in currentAngle..(currentAngle + sweepAngle)) {
                            onSliceClick(entry)
                            return@detectTapGestures
                        }
                        currentAngle += sweepAngle
                    }
                }
            }
    ) {
        val strokeWidth = 32.dp.toPx()
        val diameter = min(size.width, size.height) * 0.8f
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val size = Size(diameter, diameter)
        var startAngle = -90f

        for (i in 0 until dataSet.entryCount) {
            val entry = dataSet.getEntryForIndex(i)
            val sweepAngle = (entry.y / totalAmount) * 360f
            val color = Color(dataSet.getColor(i))

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animationProgress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                topLeft = topLeft,
                size = size
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun ReportInsightsCard(insights: ReportInsights) {
    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val (text, color) = when {
                    insights.percentageChange == null -> "--" to MaterialTheme.colorScheme.onSurface
                    insights.percentageChange > 0 -> "↑ ${insights.percentageChange}%" to MaterialTheme.colorScheme.error
                    insights.percentageChange < 0 -> "↓ ${abs(insights.percentageChange)}%" to MaterialTheme.colorScheme.primary
                    else -> "No Change" to MaterialTheme.colorScheme.onSurface
                }
                Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
                Text("vs. previous period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Top Spend", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (insights.topCategory != null) {
                    Icon(
                        imageVector = CategoryIconHelper.getIcon(insights.topCategory.iconKey ?: "category"),
                        contentDescription = insights.topCategory.categoryName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CategoryIconHelper.getIconBackgroundColor(insights.topCategory.colorKey ?: "gray_light"))
                            .padding(8.dp),
                        tint = Color.Black
                    )
                    Text(insights.topCategory.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("--", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun GlassReportNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartLegend(modifier: Modifier = Modifier, pieData: PieData?) {
    val dataSet = pieData?.dataSet as? PieDataSet ?: return
    val totalValue = remember(dataSet) {
        (0 until dataSet.entryCount).sumOf { dataSet.getEntryForIndex(it).y.toDouble() }.toFloat()
    }

    LazyColumn(
        modifier = modifier.padding(start = 16.dp),
    ) {
        items(dataSet.entryCount) { i ->
            val entry = dataSet.getEntryForIndex(i)
            val color = dataSet.getColor(i)
            val percentage = if (totalValue > 0) (entry.y / totalValue * 100f) else 0f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(color)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${"%.1f".format(percentage)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}