// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AddRecurringTransactionScreen.kt
// REASON: FEATURE - The screen now supports both "add" and "edit" modes. It
// accepts an optional ruleId, loads the existing rule's data if provided,
// and calls the appropriate ViewModel function (insert or update) upon saving.
// FIX - The theme detection logic for the ExposedDropdownMenu has been corrected
// to check the background color instead of the transparent surface color. This
// ensures the dropdown's background has the correct contrast in all themes.
// FIX (UI) - Removed the local Scaffold and TopAppBar. The main NavHost now
// provides a centralized TopAppBar, and this change removes the duplicate,
// resolving a UI bug.
// FIX (Build) - Removed the local, private definition of TransactionTypeToggle
// to resolve an overload resolution ambiguity error. The screen now uses the
// public version from AddTransactionScreen.kt.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Date
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import io.pm.finlight.ui.viewmodel.RecurringTransactionViewModelFactory
import java.util.Locale
import androidx.compose.ui.Alignment
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringTransactionScreen(
    navController: NavController,
    ruleId: Int?,
) {
    val context = LocalContext.current
    val recurringViewModel: RecurringTransactionViewModel = viewModel(
        factory = RecurringTransactionViewModelFactory(context.applicationContext as Application)
    )
    val transactionViewModel: TransactionViewModel = viewModel()

    val isEditMode = ruleId != null
    val titleText = if (isEditMode) "Edit Recurring Rule" else "Add Recurring Rule"

    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("expense") }

    val recurrenceIntervals = listOf("Daily", "Weekly", "Monthly", "Yearly")
    var selectedInterval by remember { mutableStateOf(recurrenceIntervals[2]) }
    var intervalExpanded by remember { mutableStateOf(false) }

    // --- NEW (Issue #105) ---
    var isVariableBill by remember { mutableStateOf(false) }
    var smsSenderId by remember { mutableStateOf("") }
    var autoApprove by remember { mutableStateOf(false) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val accounts by transactionViewModel.allAccounts.collectAsState(initial = emptyList())
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var accountExpanded by remember { mutableStateOf(false) }

    val categories by transactionViewModel.allCategories.collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }

    // --- NEW: Correct theme detection ---
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    val ruleToEdit by if (isEditMode) {
        recurringViewModel.getRuleById(ruleId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    LaunchedEffect(ruleToEdit, accounts, categories) {
        if (isEditMode) {
            ruleToEdit?.let { rule ->
                description = rule.description
                amount = rule.amount.toString()
                transactionType = rule.transactionType
                selectedInterval = rule.recurrenceInterval
                selectedAccount = accounts.find { it.id == rule.accountId }
                selectedCategory = categories.find { it.id == rule.categoryId }
                // --- NEW (Issue #105) ---
                isVariableBill = rule.isVariableBill
                smsSenderId = rule.smsSenderId ?: ""
                autoApprove = rule.autoApprove
                endDate = rule.endDate
            }
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TransactionTypeToggle(
                selectedType = transactionType,
                onTypeSelected = { transactionType = it },
            )
        }

        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Variable Bill", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Switch(checked = isVariableBill, onCheckedChange = { isVariableBill = it })
                    }

                    if (!isVariableBill) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Approve Payments", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text("Automatically confirm without asking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoApprove, onCheckedChange = { autoApprove = it })
                        }
                    }
                }
            }
        }

        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = auroraTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(if (isVariableBill) "Expected Amount" else "Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Text("₹") },
                        colors = auroraTextFieldColors(),
                    )
                    if (isVariableBill) {
                        OutlinedTextField(
                            value = smsSenderId,
                            onValueChange = { smsSenderId = it },
                            label = { Text("Linked SMS Sender ID (e.g. AM-BESCOM)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = auroraTextFieldColors(),
                        )
                    }
                }
            }
        }

        item {
            GlassPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExposedDropdownMenuBox(expanded = intervalExpanded, onExpandedChange = { intervalExpanded = !intervalExpanded }) {
                        OutlinedTextField(
                            value = selectedInterval,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Repeats") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            colors = auroraTextFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = intervalExpanded,
                            onDismissRequest = { intervalExpanded = false },
                            // --- UPDATED ---
                            modifier = Modifier.background(popupContainerColor),
                        ) {
                            recurrenceIntervals.forEach { interval ->
                                DropdownMenuItem(text = { Text(interval) }, onClick = {
                                    selectedInterval = interval
                                    intervalExpanded = false
                                })
                            }
                        }
                    }

                    ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = !accountExpanded }) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "Select Account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            colors = auroraTextFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false },
                            // --- UPDATED ---
                            modifier = Modifier.background(popupContainerColor),
                        ) {
                            accounts.forEach { account ->
                                DropdownMenuItem(text = { Text(account.name) }, onClick = {
                                    selectedAccount = account
                                    accountExpanded = false
                                })
                            }
                        }
                    }

                    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            colors = auroraTextFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            // --- UPDATED ---
                            modifier = Modifier.background(popupContainerColor),
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                })
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                        OutlinedTextField(
                            value = endDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("End Date (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            // disabled so the Box handles clicks
                            enabled = false,
                            colors =
                                auroraTextFieldColors().copy(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                ),
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val amountDouble = amount.toDoubleOrNull()
                        if (amountDouble != null && selectedAccount != null) {
                            recurringViewModel.saveRule(
                                ruleId = ruleId,
                                description = description,
                                amount = amountDouble,
                                transactionType = transactionType,
                                recurrenceInterval = selectedInterval,
                                startDate = ruleToEdit?.startDate ?: System.currentTimeMillis(),
                                accountId = selectedAccount!!.id,
                                categoryId = selectedCategory?.id,
                                lastRunDate = ruleToEdit?.lastRunDate,
                                isVariableBill = isVariableBill,
                                autoApprove = if (isVariableBill) false else autoApprove,
                                endDate = endDate,
                                smsSenderId = smsSenderId.takeIf { isVariableBill && it.isNotBlank() },
                            )
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = description.isNotBlank() && amount.isNotBlank() && selectedAccount != null && selectedCategory != null,
                ) {
                    Text(if (isEditMode) "Update Rule" else "Save Rule")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
            )
        }
    }
}

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
