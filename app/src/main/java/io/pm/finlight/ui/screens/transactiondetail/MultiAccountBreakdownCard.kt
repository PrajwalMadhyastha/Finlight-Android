package io.pm.finlight.ui.screens.transactiondetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.SplitTransactionDetails
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.data.model.MergedTransactionItem
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.rememberSafeBankLogoPainter
import io.pm.finlight.ui.theme.ExpenseRedDark
import io.pm.finlight.ui.theme.ExpenseRedLight
import io.pm.finlight.ui.theme.IncomeGreenDark
import io.pm.finlight.ui.theme.IncomeGreenLight
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun MultiAccountBreakdownCard(
    entries: List<MergedTransactionItem>,
    reimbursements: List<TransactionDetails>,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val isDark = isSystemInDarkTheme()
    val incomeGreen = if (isDark) IncomeGreenDark else IncomeGreenLight
    val expenseRed = if (isDark) ExpenseRedDark else ExpenseRedLight

    GlassPanel(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCardClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            val totalItems = entries.size + reimbursements.size
            var currentIndex = 0

            entries.forEach { entry ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = rememberSafeBankLogoPainter(entry.accountName),
                        contentDescription = "${entry.accountName} logo",
                        modifier = Modifier.size(36.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.accountName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.isAnchor) {
                            Text(
                                text = "anchor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (entries.size > 1) {
                            Text(
                                text = "merged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    val sign = if (entry.transactionType == TransactionType.INCOME) "+" else "−"
                    val absAmount = abs(entry.amount)
                    Text(
                        text = "$sign${currencyFormat.format(absAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.transactionType == TransactionType.INCOME) incomeGreen else expenseRed,
                    )
                }
                currentIndex++
                if (currentIndex < totalItems) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                }
            }

            reimbursements.forEach { detail ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = rememberSafeBankLogoPainter(detail.accountName ?: ""),
                        contentDescription = "${detail.accountName} logo",
                        modifier = Modifier.size(36.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.accountName ?: "Unknown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "repayment",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val absAmount = abs(detail.transaction.amount)
                    Text(
                        text = "+${currencyFormat.format(absAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = incomeGreen,
                    )
                }
                currentIndex++
                if (currentIndex < totalItems) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Unmerge or unlink to reassign accounts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
fun AccountCard(
    details: TransactionDetails,
    readOnly: Boolean = false,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAccountClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberSafeBankLogoPainter(details.accountName ?: ""),
                contentDescription = "${details.accountName} Logo",
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = details.accountName ?: "N/A",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (readOnly) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Unmerge to reassign",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
            if (!readOnly) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SplitSummaryCard(
    splits: List<SplitTransactionDetails>,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Split Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                splits.forEach { splitDetail ->
                    SplitSummaryItem(splitDetail)
                }
            }
        }
    }
}

@Composable
fun SplitSummaryItem(
    details: SplitTransactionDetails,
    modifier: Modifier = Modifier,
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    Row(
        modifier = modifier.fillMaxWidth(),
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
                            details.categoryColorKey ?: "gray_light",
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIconHelper.getIcon(details.categoryIconKey ?: "category"),
                contentDescription = details.categoryName,
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = details.categoryName ?: "Uncategorized",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = currencyFormat.format(details.splitTransaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun CurrencyConversionInfoCard(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    val homeCurrencySymbol = "₹"
    val foreignCurrencySymbol = CurrencyHelper.getCurrencySymbol(transaction.currencyCode)
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 2 } }

    GlassPanel(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Currency Conversion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Original Amount:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$foreignCurrencySymbol${numberFormat.format(transaction.originalAmount ?: 0.0)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Exchange Rate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "1 ${transaction.currencyCode} = $homeCurrencySymbol${numberFormat.format(transaction.conversionRate ?: 0.0)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Converted Amount:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$homeCurrencySymbol${numberFormat.format(transaction.amount)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
