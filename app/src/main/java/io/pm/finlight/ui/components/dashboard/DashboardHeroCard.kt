package io.pm.finlight.ui.components.dashboard

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.pm.finlight.ui.components.AuroraProgressBar
import io.pm.finlight.ui.components.PrivacyAwareText
import kotlin.math.roundToInt

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
    isPrivacyModeEnabled: Boolean,
) {
    val progress = if (totalBudget > 0) (amountSpent.toFloat() / totalBudget.toFloat()) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "BudgetProgressAnimation",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = budgetHealthSummary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text =
                    buildAnnotatedString {
                        append("Spent in ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(monthYear)
                        }
                    },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrivacyAwareText(
                amount = amountSpent,
                isPrivacyMode = isPrivacyModeEnabled,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AuroraProgressBar(progress = animatedProgress)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PrivacyAwareText(
                    amount = amountRemaining,
                    isPrivacyMode = isPrivacyModeEnabled,
                    prefix = "Remaining: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrivacyAwareText(
                    amount = totalBudget,
                    isPrivacyMode = isPrivacyModeEnabled,
                    prefix = "Total: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Line 1: Income and Budget side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MinimalStatItem(
                    label = "Income",
                    amount = income,
                    onClick = { navController.navigate("income_screen") },
                    isPrivacyModeEnabled = isPrivacyModeEnabled,
                )
                MinimalStatItem(
                    label = "Budget",
                    amount = totalBudget,
                    onClick = { navController.navigate("budget_screen") },
                    isPrivacyModeEnabled = isPrivacyModeEnabled,
                )
            }

            // Line 2: Safe to Spend with subtle emphasis
            EmphasizedMinimalStatItem(
                label = "Safe to Spend",
                amount = safeToSpend,
                isPerDay = true,
                isPrivacyModeEnabled = isPrivacyModeEnabled,
            )
        }
    }
}

@Composable
private fun MinimalStatItem(
    label: String,
    amount: Long,
    isPrivacyModeEnabled: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "MinimalStatItemAnimation",
    )

    val clickableModifier =
        if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }

    Column(
        modifier = clickableModifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrivacyAwareText(
            amount = animatedAmount.roundToInt(),
            isPrivacyMode = isPrivacyModeEnabled,
            isCurrency = true,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontFeatureSettings = "tnum",
                    letterSpacing = 0.3.sp,
                ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmphasizedMinimalStatItem(
    label: String,
    amount: Long,
    isPerDay: Boolean,
    isPrivacyModeEnabled: Boolean,
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "EmphasizedMinimalStatItemAnimation",
    )

    val isDark = isSystemInDarkTheme()
    val backgroundColor =
        if (isDark) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                PrivacyAwareText(
                    amount = animatedAmount.roundToInt(),
                    isPrivacyMode = isPrivacyModeEnabled,
                    isCurrency = true,
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontFeatureSettings = "tnum",
                            letterSpacing = 0.5.sp,
                        ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isPerDay) {
                    Text(
                        text = "/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}
