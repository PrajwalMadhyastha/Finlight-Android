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
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
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
    val viewModel: RecurringTransactionViewModel = viewModel()
    val recurringTransactions by viewModel.allRecurringTransactions.collectAsState(initial = emptyList())
    var ruleToDelete by remember { mutableStateOf<RecurringTransaction?>(null) }

    if (recurringTransactions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No recurring transactions set up. Tap the '+' to add one.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancel") }
            },
            containerColor = popupContainerColor
        )
    }
}

@Composable
private fun RecurringTransactionItem(
    modifier: Modifier = Modifier,
    rule: RecurringTransaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val amountColor = if (rule.transactionType == "expense") {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Repeats ${rule.recurrenceInterval}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = currencyFormat.format(rule.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
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
