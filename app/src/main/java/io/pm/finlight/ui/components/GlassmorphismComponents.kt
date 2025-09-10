// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/GlassmorphismComponents.kt
// REASON: FEATURE - The header of the `AccountsCarouselCard` is now clickable.
// Tapping the "Accounts" title on the dashboard card will navigate the user
// directly to the main `account_list` screen, providing an intuitive shortcut.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import io.pm.finlight.*
import io.pm.finlight.ui.theme.GlassPanelBorder
import androidx.compose.ui.graphics.vector.ImageVector
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.utils.BankLogoHelper
import io.pm.finlight.utils.CategoryIconHelper
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    isCustomizationMode: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val borderModifier = if (isCustomizationMode) {
        Modifier.border(
            width = 1.dp,
            brush = Brush.horizontalGradient(listOf(GlassPanelBorder, GlassPanelBorder.copy(alpha = 0.5f))),
            shape = RoundedCornerShape(24.dp)
        )
    } else {
        Modifier.border(1.dp, GlassPanelBorder, RoundedCornerShape(24.dp))
    }

    val glassFillColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.04f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(glassFillColor)
            .then(borderModifier),
        content = content
    )
}

@Composable
fun DashboardHeroCard(
    totalBudget: Float,
    amountSpent: Float,
    amountRemaining: Float,
    income: Float,
    safeToSpend: Float,
    navController: NavController,
    monthYear: String,
    budgetHealthSummary: String,
    isPrivacyModeEnabled: Boolean
) {
    val progress = if (totalBudget > 0) (amountSpent / totalBudget) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "BudgetProgressAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = budgetHealthSummary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = buildAnnotatedString {
                    append("Spent in ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(monthYear)
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrivacyAwareText(
                amount = amountSpent,
                isPrivacyMode = isPrivacyModeEnabled,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuroraProgressBar(progress = animatedProgress)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PrivacyAwareText(
                    amount = amountRemaining,
                    isPrivacyMode = isPrivacyModeEnabled,
                    prefix = "Remaining: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrivacyAwareText(
                    amount = totalBudget,
                    isPrivacyMode = isPrivacyModeEnabled,
                    prefix = "Total: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(label = "Income", amount = income, onClick = { navController.navigate("income_screen") }, isPrivacyModeEnabled = isPrivacyModeEnabled)
            StatItem(label = "Budget", amount = totalBudget, isCurrency = true, onClick = { navController.navigate("budget_screen") }, isPrivacyModeEnabled = isPrivacyModeEnabled)
            StatItem(label = "Safe to Spend", amount = safeToSpend, isPerDay = true, isPrivacyModeEnabled = isPrivacyModeEnabled)
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    amount: Float,
    isPrivacyModeEnabled: Boolean,
    isCurrency: Boolean = true,
    isPerDay: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount,
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "StatItemAnimation"
    )
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = clickableModifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrivacyAwareText(
                amount = animatedAmount,
                isPrivacyMode = isPrivacyModeEnabled,
                isCurrency = isCurrency,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isPerDay) {
                Text(
                    text = "/day",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                )
            }
        }
    }
}


@Composable
private fun AuroraProgressBar(progress: Float) {
    val animatedPercentage = (progress * 100).roundToInt()
    val progressColor = when {
        progress > 0.9 -> MaterialTheme.colorScheme.error
        progress > 0.7 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Layout(
        content = {
            Text(
                text = "$animatedPercentage%",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    style = Stroke(width = 1.dp.toPx()),
                    topLeft = Offset(0f, 1.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.1f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )

                if (progress > 0) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(progressColor.copy(alpha = 0.6f), progressColor)
                        ),
                        size = Size(width = size.width * progress, height = size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(Constraints())
        val canvasPlaceable = measurables[1].measure(constraints)

        val progressWidth = (canvasPlaceable.width * progress).toInt()
        val textX = (progressWidth - textPlaceable.width / 2).coerceIn(
            0,
            canvasPlaceable.width - textPlaceable.width
        )
        val textY = (canvasPlaceable.height - textPlaceable.height) / 2

        layout(canvasPlaceable.width, canvasPlaceable.height + textPlaceable.height + 4.dp.roundToPx()) {
            canvasPlaceable.placeRelative(0, textPlaceable.height + 4.dp.roundToPx())
            textPlaceable.placeRelative(textX, 0)
        }
    }
}

@Composable
fun AccountsCarouselCard(
    accounts: List<AccountWithBalance>,
    navController: NavController
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- UPDATED: Make the header row clickable ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("account_list") }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(accounts) { account ->
                AccountItem(account = account, navController = navController)
            }
        }
    }
}

@Composable
private fun AccountItem(account: AccountWithBalance, navController: NavController) {
    GlassPanel(
        modifier = Modifier
            .width(180.dp)
            .height(110.dp)
            .clickable { navController.navigate("account_detail/${account.account.id}") }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(id = BankLogoHelper.getLogoForAccount(account.account.name)),
                contentDescription = "${account.account.name} Logo",
                modifier = Modifier.height(24.dp)
            )
            Column {
                Text(
                    text = account.account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(account.balance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BudgetWatchCard(
    budgetStatus: List<BudgetWithSpending>,
    navController: NavController
) {
    GlassPanel(
        modifier = Modifier.clickable { navController.navigate("budget_screen") }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Budget Watch",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (budgetStatus.isEmpty()) {
                Text(
                    "No category-specific budgets set for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(budgetStatus) { budget ->
                        CategoryBudgetGauge(budget = budget, navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetGauge(budget: BudgetWithSpending, navController: NavController) {
    val progress = if (budget.budget.amount > 0) (budget.spent / budget.budget.amount).toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "CategoryBudgetGaugeAnimation"
    )
    val remaining = budget.budget.amount - budget.spent

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable { navController.navigate("budget_screen") }
            .width(90.dp)
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
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360 * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = CategoryIconHelper.getIcon(budget.iconKey ?: "category"),
                contentDescription = budget.budget.categoryName,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = budget.budget.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(remaining.toInt())} left",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AuroraRecentTransactionsCard(
    transactions: List<TransactionDetails>,
    navController: NavController,
    onCategoryClick: (TransactionDetails) -> Unit
) {
    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { navController.navigate("add_transaction") },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        navController.navigate(BottomNavItem.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) { Text("View All") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                Text(
                    "No transactions yet.",
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transactions.forEach { details ->
                        TransactionItem(
                            transactionDetails = details,
                            onClick = {
                                navController.navigate("transaction_detail/${details.transaction.id}")
                            },
                            onCategoryClick = onCategoryClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuroraQuickActionsCard(navController: NavController) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Timeline,
                text = "View Trends",
                onClick = {
                    navController.navigate(BottomNavItem.Reports.route) {
                        popUpTo(BottomNavItem.Dashboard.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            QuickActionItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PieChart,
                text = "View Categories",
                onClick = {
                    navController.navigate("transaction_list?initialTab=1") {
                        popUpTo(BottomNavItem.Dashboard.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}