// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/CategorySpendingScreen.kt
// REASON: FEATURE - The DonutChart is now interactive. It accepts an
// onSliceClick lambda and uses a pointerInput modifier to detect taps. It
// calculates the angle of the tap to determine which slice was clicked and
// invokes the callback, allowing navigation to the category's drilldown screen.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.CategorySpending
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.utils.CategoryIconHelper
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun CategorySpendingScreen(
    spendingList: List<CategorySpending>,
    onCategoryClick: (CategorySpending) -> Unit,
) {
    if (spendingList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No spending data for this month.")
        }
        return
    }

    val totalSpending = spendingList.sumOf { it.totalAmount }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Category Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DonutChart(
                            modifier = Modifier.weight(1.6f),
                            data = spendingList,
                            onSliceClick = onCategoryClick, // Pass the click handler down
                        )
                        ChartLegend(
                            modifier = Modifier.weight(1.6f),
                            spendingList = spendingList,
                        )
                    }
                }
            }
        }

        items(spendingList) { categorySpending ->
            CategorySpendingCard(
                categorySpending = categorySpending,
                totalSpending = totalSpending,
                onClick = { onCategoryClick(categorySpending) },
            )
        }
    }
}

/**
 * A custom composable that draws an animated donut chart using the Canvas API.
 */
@Composable
private fun DonutChart(
    modifier: Modifier = Modifier,
    data: List<CategorySpending>,
    onSliceClick: (CategorySpending) -> Unit,
) {
    val totalAmount = remember(data) { data.sumOf { it.totalAmount }.toFloat() }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 1000))
    }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(data) { // Key to data to ensure the lambda has the latest list
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val diameter = min(size.width, size.height) * 0.8f
                        val radius = diameter / 2f
                        val strokeWidth = 32.dp.toPx()

                        // Check if the tap is within the donut's bounds (not in the center hole or outside)
                        val distance = sqrt((tapOffset.x - centerX).pow(2) + (tapOffset.y - centerY).pow(2))
                        if (distance < radius - strokeWidth / 2 || distance > radius + strokeWidth / 2) {
                            return@detectTapGestures
                        }

                        // Calculate the angle of the tap relative to the center
                        val dx = tapOffset.x - centerX
                        val dy = tapOffset.y - centerY
                        val angleRad = atan2(dy.toDouble(), dx.toDouble())
                        var angleDeg = Math.toDegrees(angleRad).toFloat()
                        if (angleDeg < 0) angleDeg += 360

                        // Convert the angle to the chart's coordinate system (starts at -90 degrees)
                        val tapAngle = (angleDeg + 90) % 360

                        // Find which slice corresponds to the tap angle
                        var currentAngle = 0f
                        for (item in data) {
                            val sweepAngle = (item.totalAmount.toFloat() / totalAmount) * 360f
                            if (tapAngle in currentAngle..(currentAngle + sweepAngle)) {
                                onSliceClick(item)
                                return@detectTapGestures
                            }
                            currentAngle += sweepAngle
                        }
                    }
                },
    ) {
        val strokeWidth = 32.dp.toPx()
        val diameter = min(size.width, size.height) * 0.8f
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val size = Size(diameter, diameter)
        var startAngle = -90f

        data.forEach { item ->
            val sweepAngle = (item.totalAmount.toFloat() / totalAmount) * 360f
            val color = CategoryIconHelper.getIconBackgroundColor(item.colorKey ?: "gray_light")

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animationProgress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                topLeft = topLeft,
                size = size,
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun CategorySpendingCard(
    categorySpending: CategorySpending,
    totalSpending: Double,
    onClick: () -> Unit,
) {
    val percentage = if (totalSpending > 0) (categorySpending.totalAmount / totalSpending * 100) else 0.0

    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            CategoryIconHelper.getIconBackgroundColor(
                                categorySpending.colorKey ?: "gray_light",
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategoryIconHelper.getIcon(categorySpending.iconKey ?: "category"),
                    contentDescription = categorySpending.categoryName,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    categorySpending.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${"%.1f".format(percentage)}% of total spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "₹${"%,.2f".format(categorySpending.totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChartLegend(
    modifier: Modifier = Modifier,
    spendingList: List<CategorySpending>,
) {
    val totalValue = remember(spendingList) { spendingList.sumOf { it.totalAmount } }

    LazyColumn(
        modifier = modifier.padding(start = 16.dp),
    ) {
        items(spendingList) { item ->
            val color = CategoryIconHelper.getIconBackgroundColor(item.colorKey ?: "gray_light")
            val percentage = if (totalValue > 0) (item.totalAmount / totalValue * 100) else 0.0

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.categoryName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${"%.1f".format(percentage)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
