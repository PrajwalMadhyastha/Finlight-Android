package io.pm.finlight.ui.screens

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import io.pm.finlight.GoalViewModel
import io.pm.finlight.Transaction
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.TransactionPickerSheet
import io.pm.finlight.utils.FormatUtils
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: Int,
    navController: NavController,
    goalViewModel: GoalViewModel = viewModel()
) {
    val goal by goalViewModel.getGoalById(goalId).collectAsState(initial = null)
    val linkedTotal by goalViewModel.getLinkedTotal(goalId).collectAsState(initial = 0.0)
    val linkedTransactions by goalViewModel.getLinkedTransactions(goalId).collectAsState(initial = emptyList<Transaction>())

    // We'll just fetch all recent transactions for the picker and filter out already linked ones.
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, -3) // Last 3 months
    val recentTransactions by goalViewModel.getRecentTransactions(cal.timeInMillis, System.currentTimeMillis()).collectAsState(initial = emptyList<Transaction>())

    var showTransactionPicker by remember { mutableStateOf(false) }

    if (goal == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentGoal = goal!!
    val progress = if (currentGoal.targetAmount > 0) (linkedTotal / currentGoal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500, easing = EaseOutCubic),
        label = "DetailGoalProgress"
    )

    val currencyFormat = remember { FormatUtils.currencyFormatter }
    val dateFormat = remember { FormatUtils.displayDateFormatter }

    val daysRemaining =
        currentGoal.targetDate?.let {
            val diff = it - System.currentTimeMillis()
            TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentGoal.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showTransactionPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Link")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                GlassPanel {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 12.dp,
                                trackColor = Color.Transparent
                            )
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = if (progress >= 1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round,
                                trackColor = Color.Transparent
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (!currentGoal.iconEmoji.isNullOrBlank()) {
                                    Text(text = currentGoal.iconEmoji!!, style = MaterialTheme.typography.displayMedium)
                                }
                                Text(
                                    text = "${(progress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "${currencyFormat.format(linkedTotal)} saved of ${currencyFormat.format(currentGoal.targetAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (daysRemaining != null) {
                            Text(
                                text = "$daysRemaining days remaining",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            if (!currentGoal.notes.isNullOrBlank()) {
                item {
                    Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    GlassPanel {
                        Text(
                            text = currentGoal.notes!!,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Linked Transactions (${linkedTransactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (linkedTransactions.isEmpty()) {
                item {
                    Text(
                        text = "No transactions linked yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(linkedTransactions, key = { "linked_${it.id}" }) { txn ->
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = txn.description, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = dateFormat.format(Date(txn.date)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = currencyFormat.format(txn.amount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            IconButton(onClick = { goalViewModel.unlinkTransactionFromGoal(goalId, txn.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Unlink", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTransactionPicker) {
        val linkedIds = linkedTransactions.map { it.id }.toSet()
        val availableTransactions = recentTransactions.filter { it.id !in linkedIds }
        TransactionPickerSheet(
            transactions = availableTransactions,
            onTransactionSelected = { txn ->
                goalViewModel.linkTransactionToGoal(goalId, txn.id)
            },
            onDismiss = { showTransactionPicker = false }
        )
    }
}
