package io.pm.finlight.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.SettingsActionItem
import io.pm.finlight.ui.components.SettingsToggleItem
import io.pm.finlight.ui.components.WeeklyReportTimePicker
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.utils.FormatUtils
import java.util.Calendar

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        GlassPanel {
            Column {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
) {
    val isWeeklySummaryEnabled by settingsViewModel.weeklySummaryEnabled.collectAsState()
    val isDailyReportEnabled by settingsViewModel.dailyReportEnabled.collectAsState()
    val isMonthlySummaryEnabled by settingsViewModel.monthlySummaryEnabled.collectAsState()
    val dailyReportTime by settingsViewModel.dailyReportTime.collectAsState()
    var showDailyTimePicker by remember { mutableStateOf(false) }
    val weeklyReportTime by settingsViewModel.weeklyReportTime.collectAsState()
    var showWeeklyTimePicker by remember { mutableStateOf(false) }
    val isAutoCaptureNotificationEnabled by settingsViewModel.autoCaptureNotificationEnabled.collectAsState()
    val isAutoBackupEnabled by settingsViewModel.autoBackupEnabled.collectAsState()
    val isAutoBackupNotificationEnabled by settingsViewModel.autoBackupNotificationEnabled.collectAsState()

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SettingsSection(title = "Transaction Notifications") {
                SettingsToggleItem(
                    title = "Auto-Captured Transactions",
                    subtitle = "Notify when a transaction is saved from an SMS",
                    icon = Icons.Default.Sms,
                    checked = isAutoCaptureNotificationEnabled,
                    onCheckedChange = { settingsViewModel.setAutoCaptureNotificationEnabled(it) },
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = "Backup Notification") {
                SettingsToggleItem(
                    title = "Backup Notification",
                    subtitle = "Notify when a backup is complete",
                    icon = Icons.Default.CloudUpload,
                    checked = isAutoBackupNotificationEnabled,
                    onCheckedChange = { settingsViewModel.setAutoBackupNotificationEnabled(it) },
                    enabled = isAutoBackupEnabled,
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = "Summaries & Reports") {
                Column {
                    SettingsToggleItem(
                        title = "Daily Summary",
                        subtitle = "Report of yesterday's spending",
                        icon = Icons.Default.Notifications,
                        checked = isDailyReportEnabled,
                        onCheckedChange = { settingsViewModel.setDailyReportEnabled(it) },
                    )
                    SettingsActionItem(
                        text = "Daily Report Time",
                        subtitle = "Current: ${String.format("%02d:%02d", dailyReportTime.first, dailyReportTime.second)}",
                        icon = Icons.Default.Schedule,
                        onClick = { showDailyTimePicker = true },
                        enabled = isDailyReportEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsToggleItem(
                        title = "Weekly Summary",
                        subtitle = "Summary of your finances every week",
                        icon = Icons.Default.CalendarViewWeek,
                        checked = isWeeklySummaryEnabled,
                        onCheckedChange = { settingsViewModel.setWeeklySummaryEnabled(it) },
                    )
                    SettingsActionItem(
                        text = "Weekly Report Time",
                        subtitle = "Current: ${FormatUtils.dayOfWeekFormatter.format(
                            Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, weeklyReportTime.first) }.time,
                        )} at ${String.format("%02d:%02d", weeklyReportTime.second, weeklyReportTime.third)}",
                        icon = Icons.Default.Schedule,
                        onClick = { showWeeklyTimePicker = true },
                        enabled = isWeeklySummaryEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsToggleItem(
                        title = "Monthly Summary",
                        subtitle = "Delivered on the 1st of each month at 9 AM",
                        icon = Icons.Default.CalendarViewMonth,
                        checked = isMonthlySummaryEnabled,
                        onCheckedChange = { settingsViewModel.setMonthlySummaryEnabled(it) },
                    )
                }
            }
        }
    }

    if (showDailyTimePicker) {
        val timePickerState =
            rememberTimePickerState(
                initialHour = dailyReportTime.first,
                initialMinute = dailyReportTime.second,
                is24Hour = false,
            )
        AlertDialog(
            onDismissRequest = { showDailyTimePicker = false },
            title = { Text("Select Daily Report Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.saveDailyReportTime(timePickerState.hour, timePickerState.minute)
                        showDailyTimePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDailyTimePicker = false }) { Text("Cancel") }
            },
            containerColor = popupContainerColor,
        )
    }

    if (showWeeklyTimePicker) {
        WeeklyReportTimePicker(
            initialDay = weeklyReportTime.first,
            initialHour = weeklyReportTime.second,
            initialMinute = weeklyReportTime.third,
            onDismiss = { showWeeklyTimePicker = false },
            onConfirm = { day, hour, minute ->
                settingsViewModel.saveWeeklyReportTime(day, hour, minute)
                showWeeklyTimePicker = false
            },
        )
    }
}
