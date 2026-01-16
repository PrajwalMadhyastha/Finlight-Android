// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/GlassmorphismComponents.kt
// REASON: FIX (Navigation) - The "View All" button's onClick logic in
// `AuroraRecentTransactionsCard` has been updated. It now uses
// `popUpTo(BottomNavItem.Dashboard.route)` instead of
// `popUpTo(navController.graph.findStartDestination().id)`.
//
// This change aligns its behavior with the main Bottom Navigation Bar,
// ensuring that navigating from the dashboard to the transaction list correctly
// highlights the "Transactions" tab and maintains a clean, predictable back stack.
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    totalBudget: Long,
    amountSpent: Long,
    amountRemaining: Long,
    income: Long,
    safeToSpend: Long,
    navController: NavController,
    monthYear: String,
    budgetHealthSummary: String,
    isPrivacyModeEnabled: Boolean
) {
    val progress = if (totalBudget > 0) (amountSpent.toFloat() / totalBudget.toFloat()) else 0f
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Line 1: Income and Budget side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MinimalStatItem(
                    label = "Income",
                    amount = income,
                    onClick = { navController.navigate("income_screen") },
                    isPrivacyModeEnabled = isPrivacyModeEnabled
                )
                MinimalStatItem(
                    label = "Budget",
                    amount = totalBudget,
                    onClick = { navController.navigate("budget_screen") },
                    isPrivacyModeEnabled = isPrivacyModeEnabled
                )
            }

            // Line 2: Safe to Spend with subtle emphasis
            EmphasizedMinimalStatItem(
                label = "Safe to Spend",
                amount = safeToSpend,
                isPerDay = true,
                isPrivacyModeEnabled = isPrivacyModeEnabled
            )
        }
    }
}

@Composable
private fun MinimalStatItem(
    label: String,
    amount: Long,
    isPrivacyModeEnabled: Boolean,
    onClick: (() -> Unit)? = null
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "MinimalStatItemAnimation"
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = clickableModifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrivacyAwareText(
            amount = animatedAmount.roundToInt(),
            isPrivacyMode = isPrivacyModeEnabled,
            isCurrency = true,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = "tnum",
                letterSpacing = 0.3.sp
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmphasizedMinimalStatItem(
    label: String,
    amount: Long,
    isPerDay: Boolean,
    isPrivacyModeEnabled: Boolean
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "EmphasizedMinimalStatItemAnimation"
    )

    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                PrivacyAwareText(
                    amount = animatedAmount.roundToInt(),
                    isPrivacyMode = isPrivacyModeEnabled,
                    isCurrency = true,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFeatureSettings = "tnum",
                        letterSpacing = 0.5.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isPerDay) {
                    Text(
                        text = "/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun AuroraProgressBar(progress: Float) {
    val animatedPercentage = (progress * 100).roundToInt()
    val isDark = isSystemInDarkTheme()
    
    // Vibrant gradient colors - adjusted warning to be more orange
    val progressGradient = when {
        progress > 0.9 -> listOf(Color(0xFFFF3B30), Color(0xFFFF2D55))
        progress > 0.7 -> listOf(Color(0xFFFF8C00), Color(0xFFFF9500))
        else -> listOf(Color(0xFF34C759), Color(0xFF30D158))
    }
    
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.25f)
    } else {
        Color.Black.copy(alpha = 0.15f)
    }
    
    // Zone colors for background - subtle but visible
    val greenZone = if (isDark) {
        Color(0xFF34C759).copy(alpha = 0.20f)
    } else {
        Color(0xFF34C759).copy(alpha = 0.15f)
    }
    
    val orangeZone = if (isDark) {
        Color(0xFFFF8C00).copy(alpha = 0.20f)
    } else {
        Color(0xFFFF8C00).copy(alpha = 0.15f)
    }
    
    val redZone = if (isDark) {
        Color(0xFFFF3B30).copy(alpha = 0.20f)
    } else {
        Color(0xFFFF3B30).copy(alpha = 0.15f)
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
                // Border
                drawRoundRect(
                    color = borderColor,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                
                // Green zone (0-70%)
                drawRoundRect(
                    color = greenZone,
                    size = Size(width = size.width * 0.7f, height = size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
                
                // Orange zone (70-90%)
                drawRect(
                    color = orangeZone,
                    topLeft = Offset(size.width * 0.7f, 0f),
                    size = Size(width = size.width * 0.2f, height = size.height)
                )
                
                // Red zone (90-100%)
                drawRoundRect(
                    color = redZone,
                    topLeft = Offset(size.width * 0.9f, 0f),
                    size = Size(width = size.width * 0.1f, height = size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )

                if (progress > 0) {
                    // Vibrant progress fill
                    drawRoundRect(
                        brush = Brush.horizontalGradient(colors = progressGradient),
                        size = Size(width = size.width * progress, height = size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    )
                    
                    // Inner glow
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        size = Size(width = size.width * progress, height = size.height * 0.5f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(Constraints())
        val canvasPlaceable = measurables[1].measure(constraints)
        val progressWidth = (canvasPlaceable.width * progress).toInt()
        val textX = (progressWidth - textPlaceable.width / 2).coerceIn(0, canvasPlaceable.width - textPlaceable.width)
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
                            // --- FIX: This is the correct popUpTo logic ---
                            popUpTo(BottomNavItem.Dashboard.route) {
                                saveState = true
                            }
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
