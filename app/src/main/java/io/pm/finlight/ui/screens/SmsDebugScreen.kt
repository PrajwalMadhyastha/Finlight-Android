// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/SmsDebugScreen.kt
// REASON: FEATURE (Help System - Phase 1) - Integrated the HelpActionIcon into
// the TopAppBar to explain the different parsing statuses to the user.
// FIX (UI) - Removed the local Scaffold and TopAppBar. The main NavHost now
// provides a centralized TopAppBar, and this change removes the duplicate,
// resolving a UI bug.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import io.pm.finlight.*
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.viewmodel.SmsDebugViewModelFactory
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsDebugScreen(
    navController: NavController,
    transactionViewModel: TransactionViewModel
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = SmsDebugViewModelFactory(application, transactionViewModel)
    val viewModel: SmsDebugViewModel = viewModel(factory = factory)

    val uiState by viewModel.uiState.collectAsState()
    val filteredResults by viewModel.filteredDebugResults.collectAsState()

    val autoImportResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<Boolean>("auto_import_after_rule_creation")
        ?.observeAsState()

    LaunchedEffect(autoImportResult?.value) {
        if (autoImportResult?.value == true) {
            viewModel.runAutoImportAndRefresh()
            navController.currentBackStackEntry?.savedStateHandle?.set("auto_import_after_rule_creation", false)
        }
    }

    if (uiState.isLoading && uiState.debugResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.selectedFilter == SmsDebugFilter.PROBLEMATIC,
                        onClick = { viewModel.setFilter(SmsDebugFilter.PROBLEMATIC) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Ignored")
                    }
                    SegmentedButton(
                        selected = uiState.selectedFilter == SmsDebugFilter.ALL,
                        onClick = { viewModel.setFilter(SmsDebugFilter.ALL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("All")
                    }
                }
            }

            item {
                Text(
                    "Showing the last ${uiState.loadCount} SMS messages received.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filteredResults.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No problematic messages found in the last ${uiState.loadCount} SMS.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredResults, key = { it.smsMessage.id }) { result ->
                    SmsDebugItem(result = result, navController = navController)
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.loadMore() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Load More")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsDebugItem(result: SmsDebugResult, navController: NavController) {
    // --- UPDATED: Handle the new IgnoredByClassifier state ---
    val statusColor = when (result.parseResult) {
        is ParseResult.Success -> MaterialTheme.colorScheme.primary
        is ParseResult.Ignored -> MaterialTheme.colorScheme.onSurfaceVariant
        is ParseResult.NotParsed -> MaterialTheme.colorScheme.error
        is ParseResult.IgnoredByClassifier -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (result.parseResult) {
        is ParseResult.Success -> Icons.Default.CheckCircle
        is ParseResult.Ignored -> Icons.Default.Block
        is ParseResult.NotParsed -> Icons.Default.Warning
        is ParseResult.IgnoredByClassifier -> Icons.Default.AutoAwesome
    }
    val title = when (result.parseResult) {
        is ParseResult.Success -> "Parsed Successfully"
        is ParseResult.Ignored -> "Ignored by Rule"
        is ParseResult.NotParsed -> "Not Parsed"
        is ParseResult.IgnoredByClassifier -> "Ignored by ML Model"
    }


    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = "Status", tint = statusColor)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "From: ${result.smsMessage.sender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text = result.smsMessage.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))

            when (val parseResult = result.parseResult) {
                is ParseResult.Success -> {
                    val txn = parseResult.transaction
                    Text("Result: ${txn.merchantName ?: "Unknown"} (${txn.amount})", color = statusColor)
                }
                is ParseResult.Ignored -> {
                    Text("Reason: ${parseResult.reason}", color = statusColor)
                    CreateRuleButton(result.smsMessage, navController)
                }
                is ParseResult.NotParsed -> {
                    Text("Reason: ${parseResult.reason}", color = statusColor)
                    CreateRuleButton(result.smsMessage, navController)
                }
                // --- UPDATED: Display logic for the ML model result ---
                is ParseResult.IgnoredByClassifier -> {
                    val confidencePercent = (1 - parseResult.confidence) * 100
                    Text(
                        "Reason: ${parseResult.reason} (${"%.1f".format(confidencePercent)}% confident it's not a transaction)",
                        color = statusColor
                    )
                    CreateRuleButton(result.smsMessage, navController)
                }
            }
        }
    }
}

@Composable
private fun CreateRuleButton(sms: SmsMessage, navController: NavController) {
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            // Create a temporary PotentialTransaction just to pass the message body
            val potentialTxn = PotentialTransaction(
                sourceSmsId = sms.id,
                smsSender = sms.sender,
                amount = 0.0, // Amount is not relevant here, rule screen will re-parse
                transactionType = "expense", // Default, can be changed in rule screen
                merchantName = null,
                originalMessage = sms.body,
                date = sms.date
            )
            val json = Gson().toJson(potentialTxn)
            val encodedJson = URLEncoder.encode(json, "UTF-8")
            navController.navigate("rule_creation_screen?potentialTransactionJson=$encodedJson")
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Create Rule For This Message")
    }
}