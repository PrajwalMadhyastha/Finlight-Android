// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/BudgetScreen.kt
// REASON: MAJOR REFACTOR - The entire screen has been redesigned to align with
// the "Project Aurora" vision. Standard cards and progress bars have been
// replaced with custom GlassPanel components and more artistic, animated data
// visualizations. The layout is now more dynamic and visually engaging,
// transforming it into a "Budget Hub" while preserving all original
// functionality and ensuring high-contrast legibility.
// FIX: Corrected a @Composable invocation error by reading the theme color
// outside the Canvas draw scope.
// BUG FIX - The AlertDialogs now correctly derive their background color from
// the app's MaterialTheme, ensuring they match the selected theme (e.g.,
// Aurora) instead of defaulting to the system's light/dark mode.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import java.util.*
import kotlin.math.min

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    navController: NavController,
    viewModel: BudgetViewModel = viewModel(),
) {
    val categoryBudgets by viewModel.budgetsForCurrentMonth.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val totalSpending by viewModel.totalSpending.collectAsState()
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
        item {
            OverallBudgetHub(
                totalBudget = overallBudget,
                totalSpent = totalSpending,
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

        if (categoryBudgets.isEmpty()) {
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
            items(categoryBudgets, key = { it.budget.id }) { budgetWithSpending ->
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
            currentBudget = overallBudget,
            onDismiss = { showOverallBudgetDialog = false },
            onConfirm = { newAmount ->
                viewModel.saveOverallBudget(newAmount)
                showOverallBudgetDialog = false
            }
        )
    }
}

@Composable
private fun OverallBudgetHub(
    totalBudget: Float,
    totalSpent: Double,
    onEditClick: () -> Unit
) {
    val progress = if (totalBudget > 0) (totalSpent.toFloat() / totalBudget) else 0f
    val remaining = totalBudget - totalSpent
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1500), label = "OverallBudgetProgress"
    )

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
                    Text(
                        "₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(remaining).drop(1)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Spent: ₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(totalSpent).drop(1)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Budget: ₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(totalBudget).drop(1)}",
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
                        "₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(budgetWithSpending.spent).drop(1)} of ₹${NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(budgetWithSpending.budget.amount).drop(1)}",
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
    currentBudget: Float,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var budgetInput by remember { mutableStateOf(if (currentBudget > 0) "%.0f".format(currentBudget) else "") }
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
