package io.pm.finlight.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.SettingsActionItem
import io.pm.finlight.ui.components.SettingsToggleItem
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.flow.collectLatest
import java.util.Date

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val isScanning by settingsViewModel.isScanning.collectAsState()
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val smsScanStartDate by settingsViewModel.smsScanStartDate.collectAsState()
    val dateFormatter = remember { FormatUtils.longDateFormatter }
    val isUnknownTransactionPopupEnabled by settingsViewModel.unknownTransactionPopupEnabled.collectAsState()

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    val totalSms by settingsViewModel.totalSmsToScan.collectAsState()
    val processedSms by settingsViewModel.processedSmsCount.collectAsState()

    LaunchedEffect(Unit) {
        settingsViewModel.uiEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GlassPanel {
                Column {
                    SettingsActionItem(
                        text = "Scan Full Inbox",
                        subtitle = "Scan all messages to find transactions",
                        icon = Icons.AutoMirrored.Filled.ManageSearch,
                        onClick = {
                            if (!isScanning) {
                                settingsViewModel.startSmsScanAndIdentifyMappings(null) { _ -> }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Scan from specific date", color = MaterialTheme.colorScheme.onSurface) },
                        supportingContent = {
                            Text(
                                text = "Start date: ${dateFormatter.format(Date(smsScanStartDate))}",
                                modifier = Modifier.clickable { showDatePickerDialog = true },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Event, "Scan from date", tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Button(
                                onClick = {
                                    if (!isScanning) {
                                        settingsViewModel.startSmsScanAndIdentifyMappings(smsScanStartDate) { _ -> }
                                    }
                                },
                                enabled = !isScanning,
                            ) { Text("Scan") }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Manage Custom Parse Rules",
                        subtitle = "View or delete your SMS parsing rules",
                        icon = Icons.AutoMirrored.Filled.Rule,
                        onClick = { navController.navigate("manage_parse_rules") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Manage Parser Ignore List",
                        subtitle = "Add or remove phrases to ignore",
                        icon = Icons.Default.Block,
                        onClick = { navController.navigate("manage_ignore_rules") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Merchant Rename Rules",
                        subtitle = "View rules for auto-renaming merchants",
                        icon = Icons.Default.Storefront,
                        onClick = { navController.navigate("manage_merchant_rules") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Debug SMS Parsing",
                        subtitle = "See why recent messages were parsed or ignored",
                        icon = Icons.Default.BugReport,
                        onClick = { navController.navigate("sms_debug_screen") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsToggleItem(
                        title = "Popup for Unknown Transactions",
                        subtitle = "Show notification for new merchants",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        checked = isUnknownTransactionPopupEnabled,
                        onCheckedChange = { settingsViewModel.setUnknownTransactionPopupEnabled(it) },
                    )
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = smsScanStartDate)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            settingsViewModel.saveSmsScanStartDate(it)
                        }
                        showDatePickerDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
            },
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
            )
        }
    }

    if (isScanning && totalSms > 0) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissible */ },
            containerColor = popupContainerColor,
            title = { Text("Scanning Your Messages") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (processedSms.toFloat() / totalSms.toFloat()).coerceIn(0f, 1f) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Scanning: $processedSms / $totalSms messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { /* No button, non-dismissible */ },
        )
    } else if (isScanning) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Starting scan...", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
}
