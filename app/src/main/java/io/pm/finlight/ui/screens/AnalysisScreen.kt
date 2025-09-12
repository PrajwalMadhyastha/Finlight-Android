// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AnalysisScreen.kt
// REASON: FIX (UX) - The DatePickerDialog for custom date ranges now has an
// explicit, theme-aware background color. This prevents it from becoming
// transparent on the "Project Aurora" themed screens, ensuring the confirmation
// and cancel buttons are always visible and usable.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.data.model.SpendingAnalysisItem
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.AnalysisDimension
import io.pm.finlight.ui.viewmodel.AnalysisTimePeriod
import io.pm.finlight.ui.viewmodel.AnalysisViewModel
import io.pm.finlight.ui.viewmodel.AnalysisViewModelFactory
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.*

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    navController: NavController
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = AnalysisViewModelFactory(application)
    val viewModel: AnalysisViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var showDateRangePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = uiState.selectedDimension.ordinal,
        ) {
            AnalysisDimension.entries.forEach { dimension ->
                Tab(
                    selected = uiState.selectedDimension == dimension,
                    onClick = { viewModel.selectDimension(dimension) },
                    text = { Text(dimension.name.replaceFirstChar { it.titlecase() }) }
                )
            }
        }

        TimePeriodFilter(
            selectedPeriod = uiState.selectedTimePeriod,
            onPeriodSelected = { viewModel.selectTimePeriod(it) },
            onCustomRangeClick = { showDateRangePicker = true }
        )

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    TotalSpendingHero(
                        total = uiState.totalSpending,
                        dimension = uiState.selectedDimension,
                        timePeriod = uiState.selectedTimePeriod
                    )
                }

                if (uiState.analysisItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No spending data for this selection.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.analysisItems, key = { it.dimensionId }) { item ->
                        AnalysisItemRow(
                            item = item,
                            onClick = {
                                val encodedName = URLEncoder.encode(item.dimensionName, "UTF-8")
                                // Use the date range from the UI state for navigation
                                val (start, end) = viewModel.run {
                                    val (s, e) = calculateDateRange(uiState.selectedTimePeriod, uiState.customStartDate, uiState.customEndDate)
                                    s to e
                                }
                                navController.navigate("analysis_detail_screen/${uiState.selectedDimension.name}/${item.dimensionId}/${start}/${end}?title=$encodedName")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        val isThemeDark = MaterialTheme.colorScheme.surface.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        Dialog(onDismissRequest = { showDateRangePicker = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = popupContainerColor,
                modifier = Modifier.wrapContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DateRangePicker(state = dateRangePickerState)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDateRangePicker = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showDateRangePicker = false
                                viewModel.setCustomDateRange(
                                    dateRangePickerState.selectedStartDateMillis,
                                    dateRangePickerState.selectedEndDateMillis
                                )
                            },
                            enabled = dateRangePickerState.selectedEndDateMillis != null
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePeriodFilter(
    selectedPeriod: AnalysisTimePeriod,
    onPeriodSelected: (AnalysisTimePeriod) -> Unit,
    onCustomRangeClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AnalysisTimePeriod.entries.filter { it != AnalysisTimePeriod.CUSTOM }) { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.name.replaceFirstChar { it.titlecase() }.replace("_", " ")) },
                leadingIcon = if (selectedPeriod == period) {
                    { Icon(Icons.Default.Check, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
        item {
            FilterChip(
                selected = selectedPeriod == AnalysisTimePeriod.CUSTOM,
                onClick = onCustomRangeClick,
                label = { Text("Custom") },
                leadingIcon = { Icon(Icons.Default.EditCalendar, "Custom Range", Modifier.size(FilterChipDefaults.IconSize)) }
            )
        }
    }
}

@Composable
private fun TotalSpendingHero(
    total: Double,
    dimension: AnalysisDimension,
    timePeriod: AnalysisTimePeriod
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val timeText = when (timePeriod) {
        AnalysisTimePeriod.CUSTOM -> "in custom range"
        AnalysisTimePeriod.ALL_TIME -> "for all time"
        else -> "in the last ${timePeriod.name.lowercase()}"
    }


    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Total spent by ${dimension.name.lowercase()} $timeText",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                currencyFormat.format(total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AnalysisItemRow(
    item: SpendingAnalysisItem,
    onClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.dimensionName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${item.transactionCount} transaction(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                currencyFormat.format(item.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// Private function in AnalysisViewModel is not needed here
// but this is an extension function on the ViewModel
// so it must be defined at the top level in this file.
private fun AnalysisViewModel.calculateDateRange(
    period: AnalysisTimePeriod,
    customStart: Long?,
    customEnd: Long?
): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    // Set to end of today to include all of today's transactions
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    val endDate = calendar.timeInMillis

    val startDate = when (period) {
        AnalysisTimePeriod.WEEK -> (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        AnalysisTimePeriod.MONTH -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis
        AnalysisTimePeriod.YEAR -> (calendar.clone() as Calendar).apply { add(Calendar.YEAR, -1) }.timeInMillis
        AnalysisTimePeriod.ALL_TIME -> 0L
        AnalysisTimePeriod.CUSTOM -> customStart ?: 0L
    }
    val finalEndDate = if (period == AnalysisTimePeriod.CUSTOM) customEnd ?: endDate else endDate
    return Pair(startDate, finalEndDate)
}