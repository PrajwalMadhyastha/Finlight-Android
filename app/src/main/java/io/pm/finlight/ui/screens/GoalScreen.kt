// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/GoalScreen.kt
// REASON: FEATURE (Issue #104) - Updated for dynamic progress tracking.
// Progress is now computed from linked transaction totals instead of the
// deprecated savedAmount field. Each goal card loads its linked total via
// the GoalViewModel for real-time accuracy.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.*
import io.pm.finlight.ui.components.ConfirmationDialog
import io.pm.finlight.ui.components.EmptyStateMessage
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.utils.FormatUtils
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GoalScreen(
    navController: NavController,
    goalViewModel: GoalViewModel = viewModel(),
) {
    val goals by goalViewModel.allGoals.collectAsState()
    var goalToDelete by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        if (goals.isEmpty()) {
            EmptyStateMessage(
                message = "No savings goals yet. Tap '+' to add one!",
                icon = Icons.Default.Info,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(goals, key = { it.id }) { goal ->
                    GoalItem(
                        modifier = Modifier.animateItemPlacement(),
                        goal = goal,
                        goalViewModel = goalViewModel,
                        onEdit = { navController.navigate("add_edit_goal/${goal.id}") },
                        onDelete = {
                            goalToDelete =
                                Goal(
                                    id = goal.id,
                                    name = goal.name,
                                    targetAmount = goal.targetAmount,
                                    targetDate = goal.targetDate,
                                    accountId = goal.accountId,
                                    notes = goal.notes,
                                    iconEmoji = goal.iconEmoji,
                                    priority = goal.priority,
                                )
                        },
                        onClick = { navController.navigate("goal_detail/${goal.id}") },
                    )
                }
            }
        }
    }

    goalToDelete?.let { goal ->
        ConfirmationDialog(
            title = "Delete Goal?",
            text = "Are you sure you want to delete the goal '${goal.name}'?",
            confirmButtonText = "Delete",
            isDestructive = true,
            onDismiss = { goalToDelete = null },
            onConfirm = {
                goalViewModel.deleteGoal(goal)
                goalToDelete = null
            },
        )
    }
}

@Composable
private fun GoalItem(
    modifier: Modifier = Modifier,
    goal: GoalWithAccountName,
    goalViewModel: GoalViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val linkedTotal by goalViewModel.getLinkedTotal(goal.id).collectAsState(initial = 0.0)
    val offlineTotal by goalViewModel.getTotalContributionForGoal(goal.id).collectAsState(initial = 0.0)
    val linkedCount by goalViewModel.getLinkedTransactionCount(goal.id).collectAsState(initial = 0)
    val linkedTransactions by goalViewModel.getLinkedTransactions(goal.id).collectAsState(initial = emptyList())

    val totalSaved = linkedTotal + offlineTotal
    val progress = if (goal.targetAmount > 0) (totalSaved / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic),
        label = "GoalProgress",
    )

    val currencyFormat = remember { FormatUtils.currencyFormatter }
    val dateFormat = remember { FormatUtils.displayDateFormatter }

    // Calculate days remaining
    val daysRemaining =
        goal.targetDate?.let {
            val diff = it - System.currentTimeMillis()
            TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
        }

    // Calculate projected completion date
    val projectedDate =
        remember(linkedTotal, linkedTransactions, goal.targetAmount, totalSaved) {
            if (linkedTransactions.isEmpty() || linkedTotal <= 0) return@remember null
            val firstDate = linkedTransactions.minByOrNull { it.date }?.date ?: return@remember null
            val daysSinceFirst = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstDate).coerceAtLeast(1)
            val avgPerDay = linkedTotal / daysSinceFirst
            if (avgPerDay <= 0) return@remember null
            val remainingAmount = goal.targetAmount - totalSaved
            if (remainingAmount <= 0) return@remember System.currentTimeMillis()
            val daysToComplete = remainingAmount / avgPerDay
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysToComplete.toLong())
        }

    GlassPanel(modifier = modifier.clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular Progress with Emoji
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        strokeWidth = 6.dp,
                        trackColor = Color.Transparent,
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = if (progress >= 1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = Color.Transparent,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!goal.iconEmoji.isNullOrBlank()) {
                            Text(
                                text = goal.iconEmoji,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            text = "${(progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Linked to: ${goal.accountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Milestone Badges
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MilestoneBadge(text = "25%", reached = progress >= 0.25f)
                        MilestoneBadge(text = "50%", reached = progress >= 0.5f)
                        MilestoneBadge(text = "75%", reached = progress >= 0.75f)
                        MilestoneBadge(text = "100%", reached = progress >= 1f)
                    }
                }

                Column {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Goal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Goal", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${(progress * 100).roundToInt()}% Complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$linkedCount txns linked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${currencyFormat.format(totalSaved)} / ${currencyFormat.format(goal.targetAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    
                    if (offlineTotal > 0.0) {
                        Text(
                            text = "(${currencyFormat.format(linkedTotal)} linked + ${currencyFormat.format(offlineTotal)} offline)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Countdown or Projection
                    if (daysRemaining != null) {
                        Text(
                            text = if (daysRemaining > 0) "$daysRemaining days left" else "Target date passed",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (daysRemaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    } else if (projectedDate != null) {
                        Text(
                            text = "Projected: ${dateFormat.format(Date(projectedDate))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View & Link Transactions")
            }
        }
    }
}

@Composable
fun MilestoneBadge(
    text: String,
    reached: Boolean
) {
    Surface(
        color = if (reached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = CircleShape,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (reached) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
