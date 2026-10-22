package io.pm.finlight.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.diagnostics.CrashDiagnosticsManager
import io.pm.finlight.diagnostics.DiagnosticsExportService
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticReportPreviewSheet(
    onDismissRequest: () -> Unit,
    database: AppDatabase? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val sheetContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    var includeDevice by remember { mutableStateOf(true) }
    var includeHealth by remember { mutableStateOf(true) }
    var includeCrash by remember { mutableStateOf(true) }
    val hasCrash = remember { CrashDiagnosticsManager.hasRecordedCrash(context) }

    var reportText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val resolvedDb = remember { database ?: AppDatabase.getInstance(context) }

    LaunchedEffect(includeDevice, includeHealth, includeCrash) {
        isLoading = true
        reportText =
            withContext(Dispatchers.IO) {
                DiagnosticsExportService.generateReport(
                    context = context,
                    database = resolvedDb,
                    includeDevice = includeDevice,
                    includeHealth = includeHealth,
                    includeCrash = includeCrash,
                )
            }
        isLoading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = sheetContainerColor,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val sheetScrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(sheetScrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Diagnostic Report & Logs",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Trust callout notice
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy Shield",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Zero-Cloud & Redacted: No account numbers, balances, or SMS texts are exposed. All data stays local until shared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Section Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = includeDevice,
                    onClick = { includeDevice = !includeDevice },
                    label = { Text("Device OS") },
                )
                FilterChip(
                    selected = includeHealth,
                    onClick = { includeHealth = !includeHealth },
                    label = { Text("Health Metrics") },
                )
                FilterChip(
                    selected = includeCrash,
                    onClick = { includeCrash = !includeCrash },
                    label = { Text(if (hasCrash) "Crash Log (!)" else "Crash Log") },
                )
            }

            // Monospace Log Viewer
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
                } else {
                    val verticalScroll = rememberScrollState()
                    val horizontalScroll = rememberScrollState()
                    Text(
                        text = reportText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll),
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        DiagnosticsExportService.copyToClipboard(context, reportText)
                        Toast.makeText(context, "Diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }

                Button(
                    onClick = {
                        scope.launch {
                            val file =
                                withContext(Dispatchers.IO) {
                                    DiagnosticsExportService.exportReportToFile(context, reportText)
                                }
                            val intent = DiagnosticsExportService.createShareIntent(context, file)
                            context.startActivity(Intent.createChooser(intent, "Share Diagnostic Report"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
