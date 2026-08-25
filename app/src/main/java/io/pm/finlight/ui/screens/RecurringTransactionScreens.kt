// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/RecurringTransactionScreens.kt
// REASON: FEATURE - The UI has been updated to support rule management. Each
// item now has "Edit" and "Delete" icon buttons. Tapping "Edit" navigates to
// the Add/Edit screen with the rule's ID, and tapping "Delete" shows a
// confirmation dialog before removing the rule.
// BUG FIX - Added the missing isDark() helper function to resolve compilation errors.
// ANIMATION - Added `animateItemPlacement()` to the RecurringTransactionItem
// in the LazyColumn. This makes the list fluidly animate changes when rules
// are added or removed.
//
// FEATURE (Issue #105): Added ConfirmPendingBottomSheet, UpcomingPaymentsCard,
// and RecurringSuggestionsCard for the new recurring transaction epic.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import io.pm.finlight.ui.viewmodel.RecurringTransactionViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.*
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.text.NumberFormat
import java.util.*

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecurringTransactionScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: RecurringTransactionViewModel =
        viewModel(
            factory = RecurringTransactionViewModelFactory(context.applicationContext as Application)
        )
    val recurringTransactions by viewModel.allRecurringTransactions.collectAsState(initial = emptyList())
    var ruleToDelete by remember { mutableStateOf<RecurringTransaction?>(null) }

    if (recurringTransactions.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No recurring transactions set up. Tap the '+' to add one.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(recurringTransactions, key = { it.id }) { rule ->
                RecurringTransactionItem(
                    modifier = Modifier.animateItemPlacement(),
                    rule = rule,
                    onEditClick = {
                        navController.navigate("add_recurring_transaction?ruleId=${rule.id}")
                    },
                    onDeleteClick = {
                        ruleToDelete = rule
                    },
                )
            }
        }
    }

    ruleToDelete?.let { rule ->
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Rule?") },
            text = { Text("Are you sure you want to delete the rule for '${rule.description}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRule(rule)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancel") }
            },
            containerColor = popupContainerColor,
        )
    }
}

@Composable
private fun RecurringTransactionItem(
    modifier: Modifier = Modifier,
    rule: RecurringTransaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val amountColor =
        if (rule.transactionType == TransactionType.EXPENSE) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Repeats ${rule.recurrenceInterval}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = currencyFormat.format(rule.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPendingBottomSheet(
    transaction: Transaction,
    rule: RecurringTransaction,
    onDismiss: () -> Unit,
    pendingViewModel: PendingTransactionsViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    var editedAmount by remember { mutableStateOf(transaction.amount.toString()) }
    val showAmountField = rule.isVariableBill

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        containerColor = popupContainerColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Confirm Payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = rule.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Expected: ${currencyFormat.format(rule.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Repeats ${rule.recurrenceInterval}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showAmountField) {
                OutlinedTextField(
                    value = editedAmount,
                    onValueChange = { editedAmount = it },
                    label = { Text("Actual Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            }

            Button(
                onClick = {
                    val confirmedAmount = if (showAmountField) editedAmount.toDoubleOrNull() else null
                    pendingViewModel.confirmPending(transaction.id, rule.id, confirmedAmount)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Confirm Payment", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    pendingViewModel.skipPending(transaction.id, rule.id)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text("Skip This Cycle")
            }
        }
    }
}

@Composable
fun UpcomingPaymentsCard(navController: NavController) {
    val pendingViewModel: PendingTransactionsViewModel = viewModel()
    val pending by pendingViewModel.pendingTransactions.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    val context = LocalContext.current
    val recurringViewModel: RecurringTransactionViewModel =
        viewModel(
            factory = RecurringTransactionViewModelFactory(context.applicationContext as Application)
        )
    val allRules by recurringViewModel.allRecurringTransactions.collectAsState(initial = emptyList())

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Upcoming Payments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (pending.isEmpty()) {
                Text(
                    text = "No pending payments. All caught up! \u2705",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                pending.take(3).forEach { txn ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = txn.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { selectedTransaction = txn },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(currencyFormat.format(txn.amount), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (pending.size > 3) {
                    TextButton(
                        onClick = { navController.navigate("recurring_transactions") },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("+${pending.size - 3} more", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    selectedTransaction?.let { txn ->
        val rule = allRules.find { it.id == txn.recurringRuleId }
        if (rule != null) {
            ConfirmPendingBottomSheet(
                transaction = txn,
                rule = rule,
                onDismiss = { selectedTransaction = null },
                pendingViewModel = pendingViewModel,
            )
        }
    }
}

@Composable
fun RecurringSuggestionsCard(navController: NavController) {
    val context = LocalContext.current
    val recurringViewModel: RecurringTransactionViewModel =
        viewModel(
            factory = RecurringTransactionViewModelFactory(context.applicationContext as Application)
        )
    val patterns by recurringViewModel.patternSuggestions.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    if (patterns.isEmpty()) return

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recurring Suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "We noticed these patterns in your transactions:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            patterns.take(3).forEach { pattern ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pattern.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${pattern.occurrences} occurrences \u00b7 ${currencyFormat.format(pattern.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            navController.navigate("add_recurring_transaction?patternSignature=${pattern.smsSignature}")
                        },
                    ) {
                        Text("Set Up Rule", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            TextButton(
                onClick = { navController.navigate("recurring_transactions") },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("View All Rules \u2192", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
