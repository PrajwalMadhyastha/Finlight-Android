// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/BudgetScreen.kt
// REASON: FEATURE (Historical Budgets) - The `EditOverallBudgetDialog`'s
// `onConfirm` lambda is updated. It now passes the `viewModel.selectedMonth.value`
// to the refactored `saveOverallBudget` function, enabling the user to set
// a budget for the specific month they are currently viewing.
//
// REASON: REFACTOR (Dynamic Budget) - The BudgetScreen is now dynamic.
// - It adds the `MonthlySummaryHeader` (from TransactionListScreen) to allow
//   users to navigate between different months.
// - It collects the new dynamic state from `BudgetViewModel` (e.g.,
//   `overallBudgetForSelectedMonth`, `budgetsForSelectedMonth`).
// - `OverallBudgetHub` is updated to accept a nullable `Float?` and
//   correctly displays "Not Set" when the budget is `null`.
// - `EditOverallBudgetDialog` is also updated to handle the nullable budget.
//
// REASON: FIX (Bug) - The `MonthlySummaryHeader` has been refactored to consume
// the new `List<Pair<Calendar, Float?>>` from the ViewModel. It now correctly
// displays the *budget* for each month (or "Not Set") instead of the *spent*
// amount, fixing the bug reported by the user.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.Budget
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.BudgetWithSpending
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min
import kotlin.math.roundToLong

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

// --- NEW: Copied from TransactionListScreen for the header ---
private fun formatAmountInLakhs(amount: Long): String {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        .apply { maximumFractionDigits = 0 }
    if (amount < 1000) return currencyFormat.format(amount)
    if (amount < 100000) return "${currencyFormat.format(amount / 1000)}K"
    return "${NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }.format(amount / 100000.0)}L"
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    navController: NavController,
    viewModel: BudgetViewModel = viewModel(),
) {
    // --- REFACTORED: Collect dynamic state from ViewModel ---
    val budgetsForSelectedMonth by viewModel.budgetsForSelectedMonth.collectAsState()
    val overallBudgetForSelectedMonth by viewModel.overallBudgetForSelectedMonth.collectAsState()
    val totalSpendingForSelectedMonth by viewModel.totalSpendingForSelectedMonth.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val monthlySummaries by viewModel.monthlySummaries.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var budgetToDelete by remember { mutableStateOf<Budget?>(null) }
    var showOverallBudgetDialog by remember { mutableStateOf(false) }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- NEW: Add MonthlySummaryHeader for navigation ---
        item {
            MonthlySummaryHeader(
                selectedMonth = selectedMonth,
                monthlySummaries = monthlySummaries,
                onMonthSelected = { viewModel.setSelectedMonth(it) }
            )
        }

        item {
            OverallBudgetHub(
                // --- REFACTORED: Pass dynamic state ---
                totalBudget = overallBudgetForSelectedMonth,
                totalSpent = totalSpendingForSelectedMonth,
                onEditClick = { showOverallBudgetDialog = true }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Category Budgets",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { navController.navigate("add_budget") }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Category Budget",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- REFACTORED: Use dynamic state ---
        if (budgetsForSelectedMonth.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No category budgets set. Tap the '+' icon to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(budgetsForSelectedMonth, key = { it.budget.id }) { budgetWithSpending ->
                CategoryBudgetItem(
                    budgetWithSpending = budgetWithSpending,
                    onEdit = { navController.navigate("edit_budget/${budgetWithSpending.budget.id}") },
                    onDelete = {
                        budgetToDelete = budgetWithSpending.budget
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    if (showDeleteDialog && budgetToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Budget?") },
            text = { Text("Are you sure you want to delete the budget for '${budgetToDelete?.categoryName}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        budgetToDelete?.let { viewModel.deleteBudget(it) }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
            containerColor = popupContainerColor
        )
    }

    if (showOverallBudgetDialog) {
        EditOverallBudgetDialog(
            // --- REFACTORED: Pass nullable Float? ---
            currentBudget = overallBudgetForSelectedMonth,
            onDismiss = { showOverallBudgetDialog = false },
            onConfirm = { newAmount ->
                // --- UPDATED: Pass the selectedMonth to the save function ---
                viewModel.saveOverallBudget(newAmount, selectedMonth)
                showOverallBudgetDialog = false
            }
        )
    }
}

// --- REFACTORED: This composable is now updated to show budget, not spending ---
@Composable
private fun MonthlySummaryHeader(
    selectedMonth: Calendar,
    monthlySummaries: List<Pair<Calendar, Float?>>,
    onMonthSelected: (Calendar) -> Unit
) {
    val monthFormat = SimpleDateFormat("LLL", Locale.getDefault())
    val monthYearFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    var showMonthScroller by remember { mutableStateOf(false) }

    val selectedTabIndex = monthlySummaries.indexOfFirst { (calendar, _) ->
        calendar.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH) &&
                calendar.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR)
    }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMonthScroller = !showMonthScroller }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthYearFormat.format(selectedMonth.time),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (showMonthScroller) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (showMonthScroller) "Hide month selector" else "Show month selector",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        AnimatedVisibility(
            visible = showMonthScroller,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200))
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                indicator = {},
                divider = {}
            ) {
                monthlySummaries.forEach { (calendar, totalBudget) ->
                    val isSelected = calendar.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH) &&
                            calendar.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR)
                    Tab(
                        selected = isSelected,
                        onClick = {
                            onMonthSelected(calendar)
                            showMonthScroller = false
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = monthFormat.format(calendar.time),
                                    style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                // --- THIS IS THE FIX ---
                                val budgetText = totalBudget?.let { formatAmountInLakhs(it.roundToLong()) } ?: "Not Set"
                                val budgetColor = if (totalBudget != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                Text(
                                    text = budgetText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) budgetColor else budgetColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun OverallBudgetHub(
    // --- REFACTORED: Accept nullable Float? ---
    totalBudget: Float?,
    totalSpent: Long,
    onEditClick: () -> Unit
) {
    // --- REFACTORED: Handle null budget ---
    val budgetValue = totalBudget ?: 0f
    val progress = if (budgetValue > 0) (totalSpent.toFloat() / budgetValue) else 0f
    val remaining = budgetValue - totalSpent
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1500), label = "OverallBudgetProgress"
    )
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }


    GlassPanel(modifier = Modifier.clickable(onClick = onEditClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Overall Monthly Budget",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                OverallBudgetGauge(progress = animatedProgress)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Remaining",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // --- REFACTORED: Handle null budget display ---
                    if (totalBudget == null) {
                        Text(
                            "Not Set",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            currencyFormat.format(remaining),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Spent: ${currencyFormat.format(totalSpent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // --- REFACTORED: Handle null budget display ---
                val budgetText = if (totalBudget == null) "Not Set" else currencyFormat.format(budgetValue)
                Text(
                    "Budget: $budgetText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OverallBudgetGauge(progress: Float) {
    val progressBrush = Brush.sweepGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primary
        )
    )
    // --- FIX: Read color from theme outside the Canvas scope ---
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 12.dp.toPx()
        val diameter = min(size.width, size.height) - strokeWidth
        val radius = diameter / 2
        val center = Offset(size.width / 2, size.height / 2)

        drawCircle(
            color = trackColor, // Use the variable here
            style = Stroke(width = strokeWidth),
            radius = radius,
            center = center
        )

        drawArc(
            brush = progressBrush,
            startAngle = -90f,
            sweepAngle = 360 * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(diameter, diameter),
            topLeft = Offset(center.x - radius, center.y - radius)
        )
    }
}

@Composable
private fun CategoryBudgetItem(
    budgetWithSpending: BudgetWithSpending,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (budgetWithSpending.budget.amount > 0) (budgetWithSpending.spent / budgetWithSpending.budget.amount).toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "CategoryProgress"
    )
    val progressColor = when {
        progress > 1f -> MaterialTheme.colorScheme.error
        progress > 0.8f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }


    GlassPanel {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            CategoryIconHelper.getIconBackgroundColor(
                                budgetWithSpending.colorKey ?: "gray_light"
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIconHelper.getIcon(budgetWithSpending.iconKey ?: "category"),
                        contentDescription = budgetWithSpending.budget.categoryName,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        budgetWithSpending.budget.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${currencyFormat.format(budgetWithSpending.spent)} of ${currencyFormat.format(budgetWithSpending.budget.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Budget", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Budget", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun EditOverallBudgetDialog(
    // --- REFACTORED: Accept nullable Float? ---
    currentBudget: Float?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // --- REFACTORED: Handle null/0f for initial display ---
    var budgetInput by remember {
        mutableStateOf(
            if (currentBudget != null && currentBudget > 0f) {
                currentBudget.roundToLong().toString()
            } else {
                ""
            }
        )
    }
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Overall Budget") },
        text = {
            OutlinedTextField(
                value = budgetInput,
                onValueChange = { budgetInput = it.filter { char -> char.isDigit() } },
                label = { Text("Total Monthly Budget Amount") },
                leadingIcon = { Text("₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(budgetInput) },
                enabled = budgetInput.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = popupContainerColor
    )
}
