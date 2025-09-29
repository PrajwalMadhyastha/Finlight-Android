// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/CsvValidationScreen.kt
// REASON: FEATURE (Help System - Phase 1) - Integrated the HelpActionIcon into
// the TopAppBar to explain the different row statuses and actions to the user.
// =================================================================================
package io.pm.finlight.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.*
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvValidationScreen(
    navController: NavController,
    viewModel: SettingsViewModel
) {
    val report by viewModel.csvValidationReport.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backStackEntry = navController.currentBackStackEntry
    val updatedRowJsonState = backStackEntry?.savedStateHandle?.getLiveData<String>("corrected_row")?.observeAsState()

    LaunchedEffect(updatedRowJsonState?.value) {
        val json = updatedRowJsonState?.value
        val line = backStackEntry?.savedStateHandle?.get<Int>("corrected_row_line")
        if (json != null && line != null) {
            val gson = Gson()
            val correctedData: List<String> = gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
            viewModel.updateAndRevalidateRow(line, correctedData)
            backStackEntry.savedStateHandle.remove<String>("corrected_row")
            backStackEntry.savedStateHandle.remove<Int>("corrected_row_line")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review CSV Import") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearCsvValidationReport()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                // --- NEW: Add HelpActionIcon ---
                actions = {
                    HelpActionIcon(helpKey = "csv_validation_screen")
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            val importableRowCount = report?.reviewableRows?.count {
                it.status == CsvRowStatus.VALID ||
                        it.status == CsvRowStatus.NEEDS_ACCOUNT_CREATION ||
                        it.status == CsvRowStatus.NEEDS_CATEGORY_CREATION ||
                        it.status == CsvRowStatus.NEEDS_BOTH_CREATION
            } ?: 0

            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = {
                        viewModel.clearCsvValidationReport()
                        navController.popBackStack()
                    }, modifier = Modifier.weight(1f)) { Text("Cancel") }

                    Button(
                        onClick = {
                            scope.launch {
                                val rowsToImport = report?.reviewableRows?.filter {
                                    it.status != CsvRowStatus.INVALID_AMOUNT &&
                                            it.status != CsvRowStatus.INVALID_DATE &&
                                            it.status != CsvRowStatus.INVALID_COLUMN_COUNT
                                }
                                if (!rowsToImport.isNullOrEmpty()) {
                                    viewModel.commitCsvImport(rowsToImport)
                                    Toast.makeText(context, "$importableRowCount transactions imported!", Toast.LENGTH_LONG).show()
                                    navController.navigate("dashboard") { popUpTo(0) }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = importableRowCount > 0
                    ) { Text("Import ($importableRowCount)") }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        val currentReport = report
        if (currentReport == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Validation Complete",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tap a row to edit it, or use the trash icon to ignore it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(currentReport.reviewableRows, key = { it.lineNumber }) { row ->
                    EditableRowItem(
                        row = row,
                        onEditClick = {
                            // --- UPDATED: Create a Map from headers and row data ---
                            val rowDataMap = currentReport.header.zip(row.rowData).toMap()
                            val gson = Gson()
                            // Serialize the Map, not the List.
                            val rowDataJson = gson.toJson(rowDataMap)
                            val encodedJson = URLEncoder.encode(rowDataJson, "UTF-8")
                            navController.navigate("add_transaction?isCsvEdit=true&csvLineNumber=${row.lineNumber}&initialDataJson=$encodedJson")
                        },
                        onDeleteClick = {
                            viewModel.removeRowFromReport(row)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditableRowItem(row: ReviewableRow, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    val statusColor = when (row.status) {
        CsvRowStatus.VALID -> MaterialTheme.colorScheme.primary
        CsvRowStatus.NEEDS_ACCOUNT_CREATION, CsvRowStatus.NEEDS_CATEGORY_CREATION, CsvRowStatus.NEEDS_BOTH_CREATION -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val icon = when (row.status) {
        CsvRowStatus.VALID -> Icons.Default.CheckCircle
        CsvRowStatus.NEEDS_ACCOUNT_CREATION, CsvRowStatus.NEEDS_CATEGORY_CREATION, CsvRowStatus.NEEDS_BOTH_CREATION -> Icons.Default.AddCircle
        else -> Icons.Default.Warning
    }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp)
                .clickable(onClick = onEditClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Status",
                tint = statusColor,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    "Line ${row.lineNumber}: ${row.rowData.getOrNull(1) ?: "N/A"}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    row.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Ignore this row", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}