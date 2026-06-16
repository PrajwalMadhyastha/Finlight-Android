// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/GoalAllocationDialog.kt
// REASON: FEATURE (Issue #104) - New component for smart allocation prompts.
// This dialog appears when a large income is saved, prompting the user to allocate
// a portion of it to one of their active savings goals.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.Goal
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalAllocationDialog(
    transactionAmount: Double,
    activeGoals: List<Goal>,
    onDismiss: () -> Unit,
    onConfirm: (List<Goal>) -> Unit,
) {
    var selectedGoals by remember { mutableStateOf(setOf<Goal>()) }
    val isThemeDark = isSystemInDarkTheme()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
    val currencyFormat = remember { FormatUtils.currencyFormatter }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = popupContainerColor,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Smart Allocation",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "You just received ${currencyFormat.format(transactionAmount)}. Would you like to link this transaction to help reach your savings goals?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(activeGoals, key = { it.id }) { goal ->
                        val isSelected = selectedGoals.contains(goal)
                        GoalSelectionItem(
                            goal = goal,
                            isSelected = isSelected,
                            onClick = {
                                selectedGoals =
                                    if (isSelected) {
                                        selectedGoals - goal
                                    } else {
                                        selectedGoals + goal
                                    }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Not Now")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedGoals.toList()) },
                        enabled = selectedGoals.isNotEmpty(),
                    ) {
                        Text("Link to Selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSelectionItem(
    goal: Goal,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val currencyFormat = remember { FormatUtils.currencyFormatter }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!goal.iconEmoji.isNullOrBlank()) {
                    Text(
                        text = goal.iconEmoji,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Column {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Target: ${currencyFormat.format(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Checkbox(
                checked = isSelected,
                // Handled by surface click
                onCheckedChange = null,
            )
        }
    }
}
