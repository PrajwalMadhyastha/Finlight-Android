package io.pm.finlight.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.pm.finlight.data.DataExportService
import io.pm.finlight.ui.components.ConfirmationDialog
import io.pm.finlight.ui.components.DiagnosticReportPreviewSheet
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.SettingsActionItem
import io.pm.finlight.ui.components.SettingsToggleItem
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
private fun formatBackupTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    return FormatUtils.dateTimeFormatter.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAppLockEnabled by settingsViewModel.appLockEnabled.collectAsState()
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showRestoreSnapshotDialog by remember { mutableStateOf(false) }
    var showCsvInfoDialog by remember { mutableStateOf(false) }
    var showDiagnosticPreviewSheet by remember { mutableStateOf(false) }
    val healthCheckResult by settingsViewModel.healthCheckResult.collectAsState()
    val isCheckingHealth by settingsViewModel.isCheckingHealth.collectAsState()
    val isRepairingDb by settingsViewModel.isRepairingDb.collectAsState()
    val repairResultMessage by settingsViewModel.repairResultMessage.collectAsState()
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    val isAutoBackupEnabled by settingsViewModel.autoBackupEnabled.collectAsState()
    val isPrivacyModeEnabled by settingsViewModel.privacyModeEnabled.collectAsState()

    val lastBackupTimestamp by settingsViewModel.lastBackupTimestamp.collectAsState()
    val lastBackupFormatted = formatBackupTimestamp(lastBackupTimestamp)

    val showBackupSuccessDialog by settingsViewModel.showBackupSuccessDialog.collectAsState()

    LaunchedEffect(Unit) {
        settingsViewModel.uiEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(repairResultMessage) {
        repairResultMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            settingsViewModel.clearRepairResultMessage()
        }
    }

    val jsonFileSaverLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
            onResult = { uri ->
                uri?.let { targetUri ->
                    scope.launch {
                        try {
                            val outputStream = context.contentResolver.openOutputStream(targetUri)
                            if (outputStream != null) {
                                val success =
                                    outputStream.use { stream ->
                                        DataExportService.exportToJson(context, stream)
                                    }
                                if (success) {
                                    Toast.makeText(context, "Data exported successfully!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Error exporting data.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Error saving file.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving file.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )

    val csvFileSaverLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
            onResult = { uri ->
                uri?.let {
                    scope.launch {
                        val csvString = DataExportService.exportToCsvString(context)
                        if (csvString != null) {
                            try {
                                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                    outputStream.write(csvString.toByteArray())
                                }
                                Toast.makeText(context, "CSV exported successfully!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error saving CSV file.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Error exporting CSV data.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )

    val csvTemplateSaverLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
            onResult = { uri ->
                uri?.let {
                    scope.launch {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                outputStream.write(DataExportService.getCsvTemplateString().toByteArray())
                            }
                            Toast.makeText(context, "Template saved!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving template.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )

    val csvImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let {
                    settingsViewModel.validateCsvFile(it)
                    navController.navigate("csv_validation_screen")
                }
            },
        )

    val jsonImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let {
                    scope.launch {
                        if (DataExportService.importDataFromJson(context, it)) {
                            Toast.makeText(context, "Data imported successfully! Please restart the app.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to import data.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("data_settings_lazy_column"),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            GlassPanel {
                Column {
                    SettingsToggleItem(
                        title = "Enable App Lock",
                        subtitle = "Use biometrics or screen lock (PIN, pattern, password) to secure the app",
                        icon = Icons.Default.Fingerprint,
                        checked = isAppLockEnabled == true,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val authenticators =
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                val canAuthenticate = BiometricManager.from(context).canAuthenticate(authenticators)
                                if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                                    canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
                                ) {
                                    Toast.makeText(
                                        context,
                                        "Please set up a screen lock (PIN, pattern, password) or biometrics in device settings first.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    settingsViewModel.setAppLockEnabled(true)
                                }
                            } else {
                                settingsViewModel.setAppLockEnabled(false)
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsToggleItem(
                        title = "Privacy Mode",
                        subtitle = "Hide all amounts and balances",
                        icon = Icons.Default.VisibilityOff,
                        checked = isPrivacyModeEnabled,
                        onCheckedChange = { settingsViewModel.setPrivacyModeEnabled(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsToggleItem(
                        title = "Automatic Daily Backup",
                        subtitle = "Backup your data to Google Drive daily",
                        icon = Icons.Default.CloudUpload,
                        checked = isAutoBackupEnabled,
                        onCheckedChange = { settingsViewModel.setAutoBackupEnabled(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Export Data (JSON)",
                        subtitle = "Create a full backup of all your data",
                        icon = Icons.Default.DataObject,
                        onClick = {
                            val fileName = "Finlight_Backup_${FormatUtils.isoDateFormatter.format(Date())}.json"
                            jsonFileSaverLauncher.launch(fileName)
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Export Transactions (CSV)",
                        subtitle = "Save transactions in a spreadsheet format",
                        icon = Icons.Default.GridOn,
                        onClick = {
                            val fileName = "Finlight_Transactions_${FormatUtils.isoDateFormatter.format(Date())}.csv"
                            csvFileSaverLauncher.launch(fileName)
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Import from JSON",
                        subtitle = "Restore data from a backup file",
                        icon = Icons.Default.Download,
                        onClick = { showImportJsonDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Import from CSV",
                        subtitle = "Add new transactions from a CSV file",
                        icon = Icons.Default.PostAdd,
                        onClick = { showCsvInfoDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Create Backup Snapshot",
                        subtitle = "Last cloud backup: $lastBackupFormatted",
                        icon = Icons.Default.Save,
                        onClick = { settingsViewModel.createBackupSnapshot() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Restore from Snapshot",
                        subtitle = "Restore data from local backup snapshot",
                        icon = Icons.Default.SettingsBackupRestore,
                        onClick = { showRestoreSnapshotDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Diagnostic Report & Logs",
                        subtitle = "Inspect and export privacy-redacted diagnostics",
                        icon = Icons.Default.BugReport,
                        onClick = { showDiagnosticPreviewSheet = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsActionItem(
                        text = "Run Database Health Check",
                        subtitle = if (isCheckingHealth) "Analyzing database integrity..." else "Verify integrity and repair corrupted records",
                        icon = Icons.Default.HealthAndSafety,
                        onClick = {
                            settingsViewModel.runDatabaseHealthCheck()
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Photo receipts attached to transactions are not included in backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showBackupSuccessDialog) {
        BackupSnapshotSuccessDialog(
            onDismiss = { settingsViewModel.dismissBackupSuccessDialog() },
        )
    }

    if (showCsvInfoDialog) {
        CsvInfoDialog(
            onDismiss = { showCsvInfoDialog = false },
            onExportTemplate = {
                val fileName = "Finlight_Import_Template_${FormatUtils.isoDateFormatter.format(Date())}.csv"
                csvTemplateSaverLauncher.launch(fileName)
            },
            onProceed = {
                showCsvInfoDialog = false
                csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values"))
            },
        )
    }

    if (showImportJsonDialog) {
        ConfirmationDialog(
            title = "Import from JSON?",
            text = "WARNING: This will DELETE all current data and replace it. This cannot be undone.",
            confirmButtonText = "Wipe and Import",
            isDestructive = true,
            onDismiss = { showImportJsonDialog = false },
            onConfirm = {
                showImportJsonDialog = false
                jsonImportLauncher.launch(arrayOf("application/json"))
            },
        )
    }

    if (showRestoreSnapshotDialog) {
        ConfirmationDialog(
            title = "Restore from Snapshot?",
            text = "WARNING: This will replace current data with the latest local backup snapshot. This cannot be undone.",
            confirmButtonText = "Restore",
            isDestructive = true,
            onDismiss = { showRestoreSnapshotDialog = false },
            onConfirm = {
                showRestoreSnapshotDialog = false
                settingsViewModel.restoreFromBackupSnapshot()
            },
        )
    }

    healthCheckResult?.let { result ->
        AlertDialog(
            onDismissRequest = {
                if (!isRepairingDb) settingsViewModel.dismissHealthCheckResult()
            },
            containerColor = popupContainerColor,
            icon = {
                Icon(
                    imageVector = if (result.isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
            },
            title = {
                Text(if (result.isHealthy) "Database Healthy" else "Issues Detected")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (result.isHealthy) {
                        Text("SQLite integrity check passed with 0 errors. All account, transaction, and preference relations are valid.")
                    } else {
                        Text("The database self-health check detected the following issues:")
                        for (issue in result.issuesDescription) {
                            Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (!result.isHealthy) {
                    Button(
                        enabled = !isRepairingDb,
                        onClick = {
                            settingsViewModel.repairDatabase()
                        },
                    ) {
                        if (isRepairingDb) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Repairing...")
                        } else {
                            Text("Repair Database")
                        }
                    }
                } else {
                    Button(onClick = { settingsViewModel.dismissHealthCheckResult() }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (!result.isHealthy) {
                    TextButton(
                        enabled = !isRepairingDb,
                        onClick = { settingsViewModel.dismissHealthCheckResult() },
                    ) {
                        Text("Close")
                    }
                }
            },
        )
    }

    if (showDiagnosticPreviewSheet) {
        DiagnosticReportPreviewSheet(
            onDismissRequest = { showDiagnosticPreviewSheet = false },
        )
    }
}

@Composable
private fun BackupSnapshotSuccessDialog(onDismiss: () -> Unit) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = popupContainerColor,
        icon = {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = "Success",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Local Snapshot Created!", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Your secure local snapshot is ready. We've notified the Android Backup Manager to back this file up to your Google Drive.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append("Please Note: We don't control *when* this cloud backup happens. To force it immediately:\n")
                        append("1. Go to your phone's Settings.\n")
                        append("2. Search for 'Backup'.\n")
                        append("3. Find and tap the 'Back up now' button.")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        },
    )
}

@Composable
private fun CsvInfoDialog(
    onDismiss: () -> Unit,
    onExportTemplate: () -> Unit,
    onProceed: () -> Unit,
) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = popupContainerColor,
        title = { Text("CSV Import Format") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Please ensure your CSV file has the following columns in this exact order:")
                Text(
                    text = "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("• Date format must be: yyyy-MM-dd HH:mm:ss")
                Text("• Type must be 'income' or 'expense'.")
                Text("• isExcluded must be 'true' or 'false'.")
                Text("• Multiple tags should be separated by a pipe character (e.g., \"Work|Travel\").")
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onExportTemplate, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Template File")
                }
            }
        },
        confirmButton = {
            Button(onClick = onProceed) {
                Text("Proceed to Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
