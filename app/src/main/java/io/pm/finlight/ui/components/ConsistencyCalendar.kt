// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/ConsistencyCalendar.kt
// REASON: FIX - Refactored ConsistencyCalendar to accept a `year` parameter.
// Previously, it hardcoded `today.get(Calendar.YEAR)`, causing the grid to
// always render the current year even when viewing past data.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.pm.finlight.CalendarDayStatus
import io.pm.finlight.ConsistencyStats
import io.pm.finlight.SpendingStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.min

private val DAY_SIZE = 16.dp
private val DAY_SPACING = 4.dp

@Composable
fun MonthlyConsistencyCalendarCard(
    data: List<CalendarDayStatus>,
    stats: ConsistencyStats,
    selectedMonth: Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (Date) -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Spending Consistency",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                DetailedMonthlyCalendar(
                    data = data,
                    selectedMonth = selectedMonth,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onDayClick = onDayClick
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))

                // Stats displayed horizontally below calendar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(stats.noSpendDays, "No Spend")
                    StatItem(stats.goodDays, "Good Days")
                    StatItem(stats.badDays, "Over Budget")
                    StatItem(stats.noDataDays, "No Data")
                }
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
fun ConsistencyCalendar(
    data: List<CalendarDayStatus>,
    year: Int, // Added year parameter to support navigation
    modifier: Modifier = Modifier,
    onDayClick: (Date) -> Unit
) {
    if (data.isEmpty()) return

    val dataMap = remember(data) { data.associateByDate() }

    val today = remember { Calendar.getInstance() }

    // We now use the passed 'year' instead of hardcoding today's year.
    // This allows the calendar structure to reflect the year of the data being viewed.

    val months = (0..11).map { monthIndex ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        MonthData.fromCalendar(cal)
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = data) {
        if (data.isNotEmpty()) {
            val currentMonthIndex = today.get(Calendar.MONTH)
            // If viewing current year, scroll to current month.
            // If viewing past year, maybe scroll to end? For now, stick to current logic if years match.
            if (year == today.get(Calendar.YEAR)) {
                val scrollIndex = (currentMonthIndex - 2).coerceAtLeast(0)
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(scrollIndex)
                }
            } else {
                // If viewing a different year, start at beginning
                coroutineScope.launch {
                    lazyListState.scrollToItem(0)
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(
            state = lazyListState,
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DAY_SPACING * 2)
        ) {
            items(months) { monthData ->
                MonthColumn(
                    monthData = monthData,
                    year = year,
                    today = today,
                    dataMap = dataMap,
                    onDayClick = onDayClick
                )
            }
        }
        ConsistencyCalendarLegend()
    }
}

@Composable
fun DetailedMonthlyCalendar(
    modifier: Modifier = Modifier,
    data: List<CalendarDayStatus>,
    selectedMonth: Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (Date) -> Unit
) {
    val monthData = MonthData.fromCalendar(selectedMonth)
    val dataMap = data.associateByDate()
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayOfWeekFormat = remember { SimpleDateFormat("EE", Locale.getDefault()) }
    val weekDays = (Calendar.SUNDAY..Calendar.SATURDAY).map {
        dayOfWeekFormat.format(Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, it) }.time)
    }
    val today = remember { Calendar.getInstance() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header with month name and navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Month")
            }
            Text(
                text = monthYearFormat.format(selectedMonth.time),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Month")
            }
        }

        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDays.forEach { day ->
                Text(
                    text = day.take(1),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar grid
        val totalCells = monthData.startOffset + monthData.dayCount
        val rowCount = (totalCells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for (week in 0 until rowCount) {
                Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                    for (dayOfWeek in 0..6) {
                        val cellIndex = week * 7 + dayOfWeek
                        if (cellIndex >= monthData.startOffset && cellIndex < totalCells) {
                            val dayOfMonth = cellIndex - monthData.startOffset + 1
                            val currentDayCal = (selectedMonth.clone() as Calendar).apply {
                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }

                            if (currentDayCal.after(today)) {
                                Spacer(Modifier.size(28.dp))
                            } else {
                                val dayData = dataMap[currentDayCal.get(Calendar.DAY_OF_YEAR) to currentDayCal.get(Calendar.YEAR)]
                                DetailedDayCell(
                                    day = dayOfMonth,
                                    data = dayData,
                                    isToday = isSameDay(currentDayCal, today),
                                    onClick = { onDayClick(currentDayCal.time) }
                                )
                            }
                        } else {
                            Spacer(Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
        ConsistencyCalendarLegend()
    }
}

@Composable
private fun DetailedDayCell(
    day: Int,
    data: CalendarDayStatus?,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val color = when (data?.status) {
        SpendingStatus.NO_SPEND -> Color(0xFF39D353)
        SpendingStatus.WITHIN_LIMIT -> {
            val fraction = if (data.safeToSpend > 0) {
                data.amountSpent.toFloat() / data.safeToSpend
            } else {
                0f
            }
            lerp(Color(0xFFACD5F2), Color(0xFF006DAB), fraction.coerceIn(0f, 1f))
        }
        SpendingStatus.OVER_LIMIT -> {
            val fraction = if (data.safeToSpend > 0) {
                min((data.amountSpent.toFloat() / data.safeToSpend), 2f) - 1f
            } else {
                1f
            }
            lerp(Color(0xFFF87171), Color(0xFFB91C1C), fraction.coerceIn(0f, 1f))
        }
        else -> Color.Transparent
    }

    val textColor = if (data?.status == SpendingStatus.NO_DATA) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Theme-aware border color for current day
    val borderColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (isToday) Modifier.border(1.5.dp, borderColor, CircleShape) else Modifier)
            .clickable(enabled = data?.status != SpendingStatus.NO_DATA, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}


private data class MonthData(
    val name: String,
    val dayCount: Int,
    val startOffset: Int,
    val monthIndex: Int
) {
    companion object {
        fun fromCalendar(cal: Calendar): MonthData {
            val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK)
            val startOffset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
            return MonthData(monthName, daysInMonth, startOffset, cal.get(Calendar.MONTH))
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun MonthColumn(
    monthData: MonthData,
    year: Int,
    today: Calendar,
    dataMap: Map<Pair<Int, Int>, CalendarDayStatus>,
    onDayClick: (Date) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val monthNameStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textLayoutResult = remember(monthData.name) {
        textMeasurer.measure(monthData.name, monthNameStyle)
    }

    val daySizePx = with(LocalDensity.current) { DAY_SIZE.toPx() }
    val daySpacingPx = with(LocalDensity.current) { DAY_SPACING.toPx() }
    val additionalSpacingPx = with(LocalDensity.current) { 4.dp.toPx() }

    val totalCellSize = daySizePx + daySpacingPx

    val totalCells = monthData.startOffset + monthData.dayCount
    val weekCount = (totalCells + 6) / 7

    val canvasWidth = weekCount * totalCellSize - daySpacingPx
    val canvasHeight = 7 * totalCellSize - daySpacingPx + textLayoutResult.size.height + additionalSpacingPx

    val canvasWidthDp = with(LocalDensity.current) { canvasWidth.toDp() }
    val canvasHeightDp = with(LocalDensity.current) { canvasHeight.toDp() }

    val noDataColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    // Theme-aware border color for current day
    val isThemeDark = androidx.compose.foundation.isSystemInDarkTheme()
    val todayBorderColor = if (isThemeDark) {
        Color.White.copy(alpha = 0.8f) // White for dark mode
    } else {
        Color(0xFF1976D2) // Dark blue for light mode
    }

    Canvas(
        modifier = Modifier
            .width(canvasWidthDp)
            .height(canvasHeightDp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val week = floor(offset.x / totalCellSize).toInt()
                    val dayOfWeek = floor((offset.y - (textLayoutResult.size.height + additionalSpacingPx)) / totalCellSize).toInt()

                    val cellIndex = week * 7 + dayOfWeek
                    if (cellIndex >= monthData.startOffset && cellIndex < totalCells) {
                        val dayOfMonth = cellIndex - monthData.startOffset + 1
                        val currentDayCal = Calendar
                            .getInstance()
                            .apply {
                                set(year, monthData.monthIndex, dayOfMonth)
                            }
                        if (!currentDayCal.after(today)) {
                            onDayClick(currentDayCal.time)
                        }
                    }
                }
            }
    ) {
        // Draw month name
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(x = (size.width - textLayoutResult.size.width) / 2, y = 0f)
        )

        val yOffset = textLayoutResult.size.height + additionalSpacingPx

        for (week in 0 until weekCount) {
            for (day in 0..6) {
                val cellIndex = week * 7 + day
                if (cellIndex >= monthData.startOffset && cellIndex < totalCells) {
                    val dayOfMonth = cellIndex - monthData.startOffset + 1
                    val currentDayCal = Calendar.getInstance().apply {
                        set(year, monthData.monthIndex, dayOfMonth)
                    }

                    // Check if date is in the future (day-level comparison)
                    val isFuture = currentDayCal.get(Calendar.YEAR) > today.get(Calendar.YEAR) ||
                            (currentDayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                    currentDayCal.get(Calendar.DAY_OF_YEAR) > today.get(Calendar.DAY_OF_YEAR))

                    val dayData = if (!isFuture) {
                        dataMap[currentDayCal.get(Calendar.DAY_OF_YEAR) to year]
                    } else {
                        null
                    }

                    val color = when (dayData?.status) {
                        SpendingStatus.NO_SPEND -> Color(0xFF39D353)
                        SpendingStatus.WITHIN_LIMIT -> {
                            val fraction = if (dayData.safeToSpend > 0) {
                                (dayData.amountSpent / dayData.safeToSpend).toFloat()
                            } else {
                                0f
                            }
                            lerp(Color(0xFFACD5F2), Color(0xFF006DAB), fraction.coerceIn(0f, 1f))
                        }
                        SpendingStatus.OVER_LIMIT -> {
                            val fraction = if (dayData.safeToSpend > 0) {
                                min((dayData.amountSpent / dayData.safeToSpend).toFloat(), 2f) - 1f
                            } else {
                                1f
                            }
                            lerp(Color(0xFFF87171), Color(0xFFB91C1C), fraction.coerceIn(0f, 1f))
                        }
                        else -> noDataColor
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x = week * totalCellSize, y = day * totalCellSize + yOffset),
                        size = Size(daySizePx, daySizePx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )

                    // Draw border around today's cell
                    val isToday = !isFuture && isSameDay(currentDayCal, today)
                    if (isToday) {
                        drawRoundRect(
                            color = todayBorderColor,
                            topLeft = Offset(x = week * totalCellSize, y = day * totalCellSize + yOffset),
                            size = Size(daySizePx, daySizePx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

private fun List<CalendarDayStatus>.associateByDate(): Map<Pair<Int, Int>, CalendarDayStatus> {
    return this.associateBy {
        val cal = Calendar.getInstance()
        cal.time = it.date
        cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}