package io.pm.finlight.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import kotlinx.coroutines.launch

data class TrendMonthData(
    val label: String,
    val income: Float,
    val expense: Float
)

@Composable
fun ModernTrendChart(
    chartData: Pair<BarData, List<String>>,
    onBarClick: ((Int) -> Unit)? = null,
    initialScrollIndex: Int? = null // Optional: specify which month to scroll to initially
) {
    val (barData, labels) = chartData
    
    // Extract data from BarData - handle both single and dual datasets
    val monthsData = remember(barData, labels) {
        val dataSetCount = barData.dataSetCount
        
        if (dataSetCount >= 2) {
            // Dual dataset: income + expense
            val incomeDataSet = barData.dataSets.getOrNull(0) as? BarDataSet
            val expenseDataSet = barData.dataSets.getOrNull(1) as? BarDataSet
            
            labels.mapIndexed { index, label ->
                TrendMonthData(
                    label = label,
                    income = incomeDataSet?.getEntryForIndex(index)?.y ?: 0f,
                    expense = expenseDataSet?.getEntryForIndex(index)?.y ?: 0f
                )
            }
        } else {
            // Single dataset: expense only (for Daily, Weekly, Drilldown)
            val expenseDataSet = barData.dataSets.getOrNull(0) as? BarDataSet
            
            labels.mapIndexed { index, label ->
                TrendMonthData(
                    label = label,
                    income = 0f,
                    expense = expenseDataSet?.getEntryForIndex(index)?.y ?: 0f
                )
            }
        }
    }
    
    val maxValue = remember(monthsData) {
        monthsData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1f
    }
    
    // Calculate Y-axis labels
    val yAxisSteps = remember(maxValue) {
        calculateYAxisSteps(maxValue)
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to specified index or end
    LaunchedEffect(monthsData) {
        if (monthsData.isNotEmpty()) {
            coroutineScope.launch {
                val targetIndex = initialScrollIndex ?: (monthsData.size - 1)
                listState.animateScrollToItem(maxOf(0, minOf(targetIndex, monthsData.size - 1)))
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = Color(0xFF66BB6A),
                label = "Income"
            )
            Spacer(Modifier.width(24.dp))
            LegendItem(
                color = Color(0xFFEF5350),
                label = "Expense"
            )
        }
        
        // Chart with Y-axis
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Y-axis
            YAxis(
                steps = yAxisSteps,
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight()
            )
            
            // Chart
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(monthsData) { index, monthData ->
                    MonthBarGroup(
                        monthData = monthData,
                        maxValue = maxValue,
                        yAxisSteps = yAxisSteps,
                        onClick = { onBarClick?.invoke(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawRoundRect(
                color = color,
                size = size,
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MonthBarGroup(
    monthData: TrendMonthData,
    maxValue: Float,
    yAxisSteps: List<Float>,
    onClick: () -> Unit
) {
    val incomeHeight by animateFloatAsState(
        targetValue = if (maxValue > 0) monthData.income / maxValue else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "incomeHeight"
    )
    
    val expenseHeight by animateFloatAsState(
        targetValue = if (maxValue > 0) monthData.expense / maxValue else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "expenseHeight"
    )
    
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Bars container with grid lines
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp)
        ) {
            // Grid lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color.Gray.copy(alpha = 0.2f)
                yAxisSteps.forEachIndexed { index, _ ->
                    if (index > 0) { // Skip the bottom line
                        val y = size.height * (1f - index.toFloat() / (yAxisSteps.size - 1))
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
            
            // Bars
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // Income bar
                Bar(
                    heightFraction = incomeHeight,
                    color = Color(0xFF66BB6A)
                )
                
                // Expense bar
                Bar(
                    heightFraction = expenseHeight,
                    color = Color(0xFFEF5350)
                )
            }
        }
        
        // Month label
        Text(
            text = monthData.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun YAxis(
    steps: List<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        steps.reversed().forEach { value ->
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Bar(
    heightFraction: Float,
    color: Color
) {
    Canvas(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
    ) {
        val barHeight = size.height * heightFraction
        val barWidth = size.width
        
        if (barHeight > 0) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

private fun calculateYAxisSteps(maxValue: Float): List<Float> {
    // Round up to a nice number
    val roundedMax = when {
        maxValue <= 1000 -> ((maxValue / 100).toInt() + 1) * 100f
        maxValue <= 10000 -> ((maxValue / 1000).toInt() + 1) * 1000f
        maxValue <= 100000 -> ((maxValue / 10000).toInt() + 1) * 10000f
        maxValue <= 1000000 -> ((maxValue / 100000).toInt() + 1) * 100000f
        else -> ((maxValue / 1000000).toInt() + 1) * 1000000f
    }
    
    // Create 5 steps (0, 25%, 50%, 75%, 100%)
    return listOf(
        0f,
        roundedMax * 0.25f,
        roundedMax * 0.5f,
        roundedMax * 0.75f,
        roundedMax
    )
}

private fun formatValue(value: Float): String {
    return when {
        value >= 10000000 -> String.format("%.1fCr", value / 10000000)
        value >= 100000 -> String.format("%.1fL", value / 100000)
        value >= 1000 -> String.format("%.1fK", value / 1000)
        value > 0 -> value.toInt().toString()
        else -> "0"
    }
}
