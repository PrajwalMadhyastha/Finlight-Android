// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AddEditAccountScreen.kt
// REASON: NEW FILE - This file combines the logic for both adding and editing
// an account into a single, unified screen. It has been completely redesigned
// to align with the "Project Aurora" vision, using GlassPanel components for
// all form elements and ensuring high-contrast, theme-aware text and colors.
// BUG FIX - The AlertDialog now correctly derives its background color from
// the app's MaterialTheme, ensuring it matches the selected theme (e.g.,
// Aurora) instead of defaulting to the system's light/dark mode.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.AccountType
import io.pm.finlight.ui.components.ConfirmationDialog
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.AccountMatch
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.MatchType
import kotlinx.coroutines.launch

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
fun AddEditAccountScreen(
    navController: NavController,
    viewModel: AccountViewModel,
    accountId: Int?,
) {
    val isEditMode = accountId != null
    val titleText = if (isEditMode) "Edit Account" else "Add New Account"

    var accountName by remember { mutableStateOf(TextFieldValue("")) }
    var accountType by remember { mutableStateOf(TextFieldValue("")) }
    var creditLimitText by remember { mutableStateOf(TextFieldValue("")) }
    var billingDayText by remember { mutableStateOf(TextFieldValue("")) }
    val isCreditCard = AccountType.fromString(accountType.text) == AccountType.CREDIT_CARD
    val isCreditLimitInvalid =
        isCreditCard && creditLimitText.text.isNotBlank() &&
            (creditLimitText.text.toDoubleOrNull() == null || (creditLimitText.text.toDoubleOrNull() ?: 0.0) < 0.0)
    val billingDayInt = billingDayText.text.toIntOrNull()
    val isBillingDayInvalid =
        isCreditCard && billingDayText.text.isNotBlank() &&
            (billingDayInt == null || billingDayInt !in 1..31)

    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var matchDialogState by remember { mutableStateOf<AccountMatch?>(null) }

    val accountToEdit by if (isEditMode) {
        viewModel.getAccountById(accountId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    fun saveAccount() {
        val onComplete = { success: Boolean ->
            if (success) navController.popBackStack()
        }
        val parsedLimit = if (isCreditCard) creditLimitText.text.toDoubleOrNull() else null
        val parsedBillingDay = if (isCreditCard) billingDayText.text.toIntOrNull()?.coerceIn(1, 31) else null
        if (isEditMode) {
            accountToEdit?.let {
                viewModel.updateAccount(
                    it.copy(
                        name = accountName.text,
                        type = accountType.text,
                        creditLimit = parsedLimit,
                        billingCycleDay = parsedBillingDay,
                    ),
                    onComplete,
                )
            }
        } else {
            viewModel.addAccount(
                name = accountName.text,
                type = accountType.text,
                creditLimit = parsedLimit,
                billingCycleDay = parsedBillingDay,
                onComplete = onComplete,
            )
        }
    }

    LaunchedEffect(accountToEdit) {
        accountToEdit?.let {
            accountName = TextFieldValue(it.name, TextRange(it.name.length))
            accountType = TextFieldValue(it.type, TextRange(it.type.length))
            val limitStr =
                it.creditLimit?.let { limit ->
                    if (limit % 1.0 == 0.0) limit.toLong().toString() else limit.toString()
                } ?: ""
            creditLimitText = TextFieldValue(limitStr, TextRange(limitStr.length))
            val dayStr = it.billingCycleDay?.toString() ?: ""
            billingDayText = TextFieldValue(dayStr, TextRange(dayStr.length))
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            GlassPanel {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Account Name (e.g., Savings, Credit Card)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
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
                            ),
                    )
                    OutlinedTextField(
                        value = accountType,
                        onValueChange = { accountType = it },
                        label = { Text("Account Type (e.g., Bank, Wallet)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
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
                            ),
                    )

                    AnimatedVisibility(visible = isCreditCard) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = creditLimitText,
                                onValueChange = { newValue ->
                                    if (newValue.text.all { it.isDigit() || it == '.' }) {
                                        creditLimitText = newValue
                                    }
                                },
                                label = { Text("Credit Limit (e.g., 100000)") },
                                isError = isCreditLimitInvalid,
                                supportingText =
                                    if (isCreditLimitInvalid) {
                                        { Text("Please enter a valid credit limit") }
                                    } else {
                                        null
                                    },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors =
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
                                    ),
                            )
                            OutlinedTextField(
                                value = billingDayText,
                                onValueChange = { newValue ->
                                    if (newValue.text.all { it.isDigit() } && newValue.text.length <= 2) {
                                        billingDayText = newValue
                                    }
                                },
                                label = { Text("Billing Day of Month (1 - 31)") },
                                isError = isBillingDayInvalid,
                                supportingText = {
                                    if (isBillingDayInvalid) {
                                        Text("Billing day must be between 1 and 31")
                                    } else {
                                        Text("Day each month when your statement is generated")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors =
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
                                    ),
                            )
                        }
                    }
                }
            }

            if (isEditMode) {
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete Account")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    scope.launch {
                        val match = viewModel.checkAccountName(accountName.text, accountId)
                        if (match.matchType != MatchType.NONE) {
                            matchDialogState = match
                        } else {
                            saveAccount()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = accountName.text.isNotBlank() && accountType.text.isNotBlank() && !isCreditLimitInvalid && !isBillingDayInvalid,
            ) {
                Text(if (isEditMode) "Update" else "Save")
            }
        }
    }

    if (matchDialogState != null) {
        val match = matchDialogState!!
        val isExact = match.matchType == MatchType.EXACT
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        AlertDialog(
            onDismissRequest = { matchDialogState = null },
            title = { Text(if (isExact) "Account Already Exists" else "Similar Account Found", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                if (isExact) {
                    Text(
                        "An exact match was found.\n\n" +
                            "Existing Account Details:\n" +
                            "• Name: ${match.account?.name}\n" +
                            "• Type: ${match.account?.type}\n\n" +
                            if (isEditMode) "You cannot create a duplicate. Would you like to merge your current account into this one?" else "You cannot create a duplicate account. Please choose another name.",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        "A similar account was found.\n\n" +
                            "Existing Account Details:\n" +
                            "• Name: ${match.account?.name}\n" +
                            "• Type: ${match.account?.type}\n\n" +
                            if (isEditMode) "Would you like to merge your current account into this one, or save anyway?" else "Would you like to save anyway?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            containerColor = popupContainerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isExact) {
                        TextButton(onClick = {
                            saveAccount()
                            matchDialogState = null
                        }) {
                            Text("Save Anyway")
                        }
                    }
                    if (isEditMode && match.account != null) {
                        Button(onClick = {
                            val onComplete = { success: Boolean ->
                                if (success) navController.popBackStack()
                            }
                            viewModel.mergeAccounts(match.account.id, listOf(accountId!!), onComplete)
                            matchDialogState = null
                        }) {
                            Text("Merge")
                        }
                    } else if (isExact) {
                        Button(onClick = { matchDialogState = null }) {
                            Text("Choose another name")
                        }
                    }
                }
            },
            dismissButton = {
                if (!isExact || isEditMode) {
                    OutlinedButton(onClick = { matchDialogState = null }) {
                        Text(if (isEditMode) "Rename differently" else "Choose another name")
                    }
                }
            }
        )
    }

    if (showDeleteDialog && accountToEdit != null) {
        ConfirmationDialog(
            title = "Confirm Deletion",
            text = "Are you sure you want to delete this account? This will also delete all associated transactions.",
            confirmButtonText = "Delete",
            isDestructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteAccount(accountToEdit!!)
                showDeleteDialog = false
                navController.popBackStack()
            },
        )
    }
}
