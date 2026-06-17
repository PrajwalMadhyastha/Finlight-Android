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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.GoalViewModel
import io.pm.finlight.Transaction
import io.pm.finlight.data.db.entity.GoalContribution
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.components.TransactionPickerSheet
import io.pm.finlight.utils.FormatUtils
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
private fun getPopupContainerColor() = if (MaterialTheme.colorScheme.background.isDark()) PopupSurfaceDark else PopupSurfaceLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: Int,
    navController: NavController,
    goalViewModel: GoalViewModel = viewModel()
) {
    val goal by goalViewModel.getGoalById(goalId).collectAsState(initial = null)
    val linkedTotal by goalViewModel.getLinkedTotal(goalId).collectAsState(initial = 0.0)
    val offlineTotal by goalViewModel.getTotalContributionForGoal(goalId).collectAsState(initial = 0.0)
    val offlineContributions by goalViewModel.getContributionsForGoal(goalId).collectAsState(initial = emptyList())
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
    val totalSaved = linkedTotal + offlineTotal
    val progress = if (currentGoal.targetAmount > 0) (totalSaved / currentGoal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500, easing = EaseOutCubic),
        label = "DetailGoalProgress"
    )

    var showContributionDialog by remember { mutableStateOf(false) }
    var contributionToEdit by remember { mutableStateOf<GoalContribution?>(null) }

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
                        Text("Link a transaction")
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
                            text = "${currencyFormat.format(totalSaved)} saved of ${currencyFormat.format(currentGoal.targetAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (offlineTotal > 0.0) {
                            Text(
                                text = "(${currencyFormat.format(linkedTotal)} linked + ${currencyFormat.format(offlineTotal)} offline)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manual Contributions (${offlineContributions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { 
                        contributionToEdit = null
                        showContributionDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (offlineContributions.isEmpty()) {
                item {
                    Text("No manual contributions yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(offlineContributions, key = { "offline_${it.id}" }) { contrib ->
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = contrib.description.ifBlank { "Manual Addition" }, style = MaterialTheme.typography.titleMedium)
                                Text(text = dateFormat.format(Date(contrib.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(text = currencyFormat.format(contrib.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                            Row {
                                IconButton(onClick = {
                                    contributionToEdit = contrib
                                    showContributionDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { goalViewModel.deleteContribution(contrib) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
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

    if (showContributionDialog) {
        var amount by remember { mutableStateOf(TextFieldValue(contributionToEdit?.amount?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "")) }
        var description by remember { mutableStateOf(TextFieldValue(contributionToEdit?.description ?: "")) }

        AlertDialog(
            onDismissRequest = { showContributionDialog = false },
            containerColor = getPopupContainerColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(if (contributionToEdit == null) "Add Contribution" else "Edit Contribution") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { tfv -> amount = tfv.copy(text = tfv.text.filter { ch -> ch.isDigit() || ch == '.' }) },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Text("₹") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.text.toDoubleOrNull() ?: 0.0
                        if (contributionToEdit == null) {
                            goalViewModel.insertContribution(GoalContribution(goalId = goalId, amount = amt, date = System.currentTimeMillis(), description = description.text.trim()))
                        } else {
                            goalViewModel.updateContribution(contributionToEdit!!.copy(amount = amt, description = description.text.trim()))
                        }
                        showContributionDialog = false
                    },
                    enabled = amount.text.isNotBlank() && amount.text.toDoubleOrNull() != null
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContributionDialog = false }) { Text("Cancel") }
            }
        )
    }
}
