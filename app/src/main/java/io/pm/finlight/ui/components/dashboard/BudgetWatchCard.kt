package io.pm.finlight.ui.components.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.BudgetWithSpending
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.utils.CategoryIconHelper
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

@Composable
fun BudgetWatchCard(
    budgetStatus: List<BudgetWithSpending>,
    navController: NavController,
) {
    GlassPanel(
        modifier = Modifier.clickable { navController.navigate("budget_screen") },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Budget Watch",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (budgetStatus.isEmpty()) {
                Text(
                    "No category-specific budgets set for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(budgetStatus, key = { it.budget.id }) { budget ->
                        CategoryBudgetGauge(budget = budget, navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetGauge(
    budget: BudgetWithSpending,
    navController: NavController,
) {
    val progress = if (budget.budget.amount > 0) (budget.spent / budget.budget.amount).toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "CategoryBudgetGaugeAnimation",
    )
    val remaining = budget.budget.amount - budget.spent

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .clickable { navController.navigate("budget_screen") }
                .width(90.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8.dp.toPx()
                val diameter = min(size.width, size.height) - strokeWidth
                drawArc(
                    color = Color.White.copy(alpha = 0.1f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                )
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360 * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Icon(
                imageVector = CategoryIconHelper.getIcon(budget.iconKey ?: "category"),
                contentDescription = budget.budget.categoryName,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = budget.budget.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "₹${NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).format(remaining.toInt())} left",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
