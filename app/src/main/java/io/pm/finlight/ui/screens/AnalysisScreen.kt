// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AnalysisScreen.kt
// REASON: UX REFINEMENT - Replaced the full-screen DatePickerDialog with a more
// intuitive ModalBottomSheet. The new sheet contains an explicit "Apply"
// button, making the custom date range selection process clearer and more
// user-friendly. The sheet state is also configured to skip the partially
// expanded state, ensuring it opens fully for a better user experience.
// FIX - Explicitly provided DatePickerDefaults.colors to the DateRangePicker
// to ensure text content has the correct contrast against the bottom sheet's
// background, resolving a legibility issue in certain themes.
// FIX - Replaced ModalBottomSheet with a standard Dialog wrapper for the
// DateRangePicker. This gives more control over the background surface color
// and ensures correct theme propagation, fixing text legibility issues.
// FIX - Corrected the DateRangePicker implementation within its Dialog to
// properly apply theme colors, ensuring text is always legible against the
// dialog's background, regardless of the app or system theme.
// REFACTOR - Replaced the single DateRangePicker with a two-step date selection
// dialog. Users now tap "Start Date" and "End Date" fields individually,
// each launching a separate DatePickerDialog. This provides a more controlled
// and familiar user experience for defining a custom date range.
// FIX - Resolved build error by implementing date validation correctly within
// rememberDatePickerState using a SelectableDates object, instead of passing
// a dateValidator parameter to the DatePicker composable.
// FIX - Lifted date picker visibility state to the parent AnalysisScreen to
// resolve an issue where the pickers would not open on click. Correctly applied
// theme colors to the DatePickerDialog to ensure text legibility.
// FIX - Reverted date picker to ModalBottomSheet implementation and corrected
// the theme detection logic to ensure proper text color contrast.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.*

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
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.customStartDate,
            initialSelectedEndDateMillis = uiState.customEndDate
        )
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        ModalBottomSheet(
            onDismissRequest = { showDateRangePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = popupContainerColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.9f) // Use most of the screen
                    .navigationBarsPadding()
            ) {
                Text(
                    "Select Date Range",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.weight(1f),
                    colors = DatePickerDefaults.colors(
                        containerColor = Color.Transparent, // Picker is transparent, sheet provides color
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        headlineContentColor = MaterialTheme.colorScheme.onSurface,
                        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dayContentColor = MaterialTheme.colorScheme.onSurface,
                        disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledSelectedDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                        todayContentColor = MaterialTheme.colorScheme.primary,
                        todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDateRangePicker = false }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.setCustomDateRange(
                                dateRangePickerState.selectedStartDateMillis,
                                dateRangePickerState.selectedEndDateMillis
                            )
                            showDateRangePicker = false
                        },
                        enabled = dateRangePickerState.selectedEndDateMillis != null
                    ) {
                        Text("Apply")
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