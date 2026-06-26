// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AddEditGoalScreen.kt
// REASON: FEATURE (Issue #104) - Removed the manual "Already Saved" field.
// Goal progress is now computed dynamically from linked transactions.
// Added an optional notes field for goal personalization.
// Updated saveGoal() call to match the new ViewModel signature.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.*
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.FormatUtils
import java.util.*

// Helper to detect perceived luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGoalScreen(
    navController: NavController,
    goalId: Int? = null,
    goalViewModel: GoalViewModel,
    txnViewModel: TransactionViewModel,
) {
    // Screen mode
    val isEditMode = goalId != null
    val screenTitle = if (isEditMode) "Edit Savings Goal" else "New Savings Goal"

    // Live data
    val accounts by txnViewModel.allAccounts.collectAsState(initial = emptyList())

    // Local UI state
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var targetAmount by remember { mutableStateOf(TextFieldValue("")) }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var targetDateMillis by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }

    var showDatePicker by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    // Pre-populate when editing
    val goalToEdit by if (isEditMode) {
        goalViewModel.getGoalById(goalId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    LaunchedEffect(goalToEdit, accounts) {
        goalToEdit?.let { goal ->
            val nameStr = goal.name
            val targetStr =
                if (goal.targetAmount % 1.0 == 0.0) {
                    goal.targetAmount.toLong().toString()
                } else {
                    goal.targetAmount.toString()
                }
            val notesStr = goal.notes ?: ""
            name = TextFieldValue(nameStr, TextRange(nameStr.length))
            targetAmount = TextFieldValue(targetStr, TextRange(targetStr.length))
            notes = TextFieldValue(notesStr, TextRange(notesStr.length))
            targetDateMillis = goal.targetDate
            selectedAccount = accounts.find { it.id == goal.accountId }
        }
    }

    // Theme-aware popup background for dialogs (transparency fix)
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor =
        if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ------------ Goal Basics ------------
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Goal Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = auroraTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = targetAmount,
                        onValueChange = { tfv ->
                            targetAmount = tfv.copy(text = tfv.text.filter { ch -> ch.isDigit() || ch == '.' })
                        },
                        label = { Text("Target Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Text("₹") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = auroraTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = auroraTextFieldColors(),
                    )
                }
            }
        }

        // ------------ Account Picker ------------
        item {
            GlassPanel {
                Column(Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "Select Account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Allocate To Account") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = accountExpanded,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            colors = auroraTextFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false },
                            modifier =
                                Modifier.background(
                                    if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight,
                                ),
                        ) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        selectedAccount = account
                                        accountExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ------------ Target Date ------------
        item {
            GlassPanel {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Target Date",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val dateDisplay =
                        targetDateMillis?.let {
                            FormatUtils.displayDateFormatter.format(Date(it))
                        } ?: "Select"
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(dateDisplay)
                    }
                }
            }
        }

        // ------------ Save / Cancel Buttons ------------
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }

                val saveEnabled =
                    name.text.isNotBlank() &&
                        targetAmount.text.toDoubleOrNull() != null &&
                        selectedAccount != null

                Button(
                    onClick = {
                        val tgtAmt = targetAmount.text.toDouble()

                        goalViewModel.saveGoal(
                            id = goalId,
                            name = name.text.trim(),
                            targetAmount = tgtAmt,
                            targetDate = targetDateMillis,
                            accountId = selectedAccount!!.id,
                            offlineContribution = goalToEdit?.savedAmount ?: 0.0,
                            notes = notes.text.trim().ifBlank { null },
                        )
                        navController.popBackStack()
                    },
                    enabled = saveEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text(if (isEditMode) "Update" else "Save") }
            }
        }
    }

    // ---------- Date Picker Dialog (with transparency fix) ----------
    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = targetDateMillis ?: System.currentTimeMillis(),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
            // FIX: Explicit containerColor so the dialog is not transparent
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
            )
        }
    }
}

// ---------- Re-usable Aurora-style TextField colors ----------
@Composable
private fun auroraTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
