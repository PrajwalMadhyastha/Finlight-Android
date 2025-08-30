// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/RuleCreationScreen.kt
// REASON: FEATURE - The save logic now sets an `auto_import_after_rule_creation`
// flag on the previous back stack entry. This signals the SMS Debugger screen to
// automatically import any transactions that are now parsable with the new rule.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.components.GlassPanel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class RuleCreationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RuleCreationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RuleCreationViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCreationScreen(
    navController: NavController,
    potentialTransactionJson: String?,
    ruleId: Int?
) {
    val context = LocalContext.current.applicationContext as Application
    val viewModel: RuleCreationViewModel = viewModel(factory = RuleCreationViewModelFactory(context))

    val uiState by viewModel.uiState.collectAsState()
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    val isEditMode = ruleId != null

    LaunchedEffect(key1 = ruleId, key2 = potentialTransactionJson) {
        if (isEditMode) {
            viewModel.loadRuleForEditing(ruleId!!)
            val rule = AppDatabase.getInstance(context).customSmsRuleDao().getRuleById(ruleId).firstOrNull()
            if (rule != null) {
                textFieldValue = TextFieldValue(rule.sourceSmsBody)
            }
        } else if (potentialTransactionJson != null) {
            val potentialTxn = Gson().fromJson(potentialTransactionJson, PotentialTransaction::class.java)
            viewModel.initializeStateForCreation(potentialTxn)
            textFieldValue = TextFieldValue(potentialTxn.originalMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Parsing Rule" else "Create Parsing Rule") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassPanel {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Long-press text to select it, then tap a 'Mark as...' button below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Full SMS Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    val customTextSelectionColors = TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            readOnly = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            val selection = textFieldValue.selection
            val isSelectionActive = !selection.collapsed

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val start = min(selection.start, selection.end)
                        val end = max(selection.start, selection.end)
                        val selectedText = textFieldValue.text.substring(start, end)
                        viewModel.onMarkAsTrigger(RuleSelection(selectedText, start, end))
                    },
                    enabled = isSelectionActive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as Trigger Phrase")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val start = min(selection.start, selection.end)
                            val end = max(selection.start, selection.end)
                            val selectedText = textFieldValue.text.substring(start, end)
                            viewModel.onMarkAsMerchant(RuleSelection(selectedText, start, end))
                        },
                        enabled = isSelectionActive,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Mark as Merchant")
                    }
                    Button(
                        onClick = {
                            val start = min(selection.start, selection.end)
                            val end = max(selection.start, selection.end)
                            val selectedText = textFieldValue.text.substring(start, end)
                            viewModel.onMarkAsAmount(RuleSelection(selectedText, start, end))
                        },
                        enabled = isSelectionActive,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Mark as Amount")
                    }
                }
                Button(
                    onClick = {
                        val start = min(selection.start, selection.end)
                        val end = max(selection.start, selection.end)
                        val selectedText = textFieldValue.text.substring(start, end)
                        viewModel.onMarkAsAccount(RuleSelection(selectedText, start, end))
                    },
                    enabled = isSelectionActive,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Mark as Account Info")
                }
            }


            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Defined Rule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    RuleSummaryItem(
                        icon = Icons.Default.Flag,
                        label = "Trigger",
                        value = uiState.triggerSelection.selectedText.ifBlank { "Not set (Required)" },
                        isError = uiState.triggerSelection.selectedText.isBlank()
                    )
                    RuleSummaryItem(
                        icon = Icons.Default.Store,
                        label = "Merchant",
                        value = uiState.merchantSelection.selectedText.ifBlank { "Not set" }
                    )
                    RuleSummaryItem(
                        icon = Icons.Default.Paid,
                        label = "Amount",
                        value = uiState.amountSelection.selectedText.ifBlank { "Not set" }
                    )
                    RuleSummaryItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "Account",
                        value = uiState.accountSelection.selectedText.ifBlank { "Not set" }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                val isSaveEnabled = uiState.triggerSelection.selectedText.isNotBlank() &&
                        (uiState.merchantSelection.selectedText.isNotBlank() || uiState.amountSelection.selectedText.isNotBlank() || uiState.accountSelection.selectedText.isNotBlank())

                Button(
                    onClick = {
                        scope.launch {
                            viewModel.saveRule(textFieldValue.text) {
                                // This flag is for the TransactionDetailScreen
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("reparse_needed", true)
                                // This new flag is for the SmsDebugScreen
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("auto_import_after_rule_creation", true)
                                navController.popBackStack()
                            }
                        }
                    },
                    enabled = isSaveEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isEditMode) "Update Rule" else "Save Rule")
                }
            }
        }
    }
}

@Composable
private fun RuleSummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
