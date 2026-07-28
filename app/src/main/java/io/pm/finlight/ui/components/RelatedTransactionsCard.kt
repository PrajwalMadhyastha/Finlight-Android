package io.pm.finlight.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Payments

data class RelatedTransactionItem(
    val id: Any,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val amountPrefix: String = "",
    val amountColor: Color,
    val actionIcon: ImageVector? = null,
    val actionContentDescription: String? = null,
    val onActionClick: (() -> Unit)? = null
)

data class SummaryModifier(
    val label: String,
    val amount: Double,
    val prefix: String,
    val color: Color
)

data class RelatedTransactionSummary(
    val topRowLabel: String,
    val topRowAmount: Double,
    val modifiers: List<SummaryModifier>,
    val bottomRowLabel: String,
    val bottomRowAmount: Double
)

@Composable
fun RelatedTransactionsCard(
    headerIcon: ImageVector,
    headerIconTint: Color,
    title: String,
    subtitle: String,
    headerAction: @Composable (() -> Unit)? = null,
    emptyStateText: String,
    items: List<RelatedTransactionItem>,
    summary: RelatedTransactionSummary
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // --- Header ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = headerIconTint,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (headerAction != null) {
                    headerAction()
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            if (items.isEmpty()) {
                Text(
                    text = emptyStateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // --- Item rows ---
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${item.amountPrefix}${currencyFormat.format(item.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = item.amountColor,
                        )
                        if (item.actionIcon != null && item.onActionClick != null) {
                            IconButton(
                                onClick = item.onActionClick,
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                            ) {
                                Icon(
                                    imageVector = item.actionIcon,
                                    contentDescription = item.actionContentDescription,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                // --- Summary rows ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = summary.topRowLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = currencyFormat.format(summary.topRowAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                summary.modifiers.forEach { modifier ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = modifier.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = modifier.color,
                        )
                        Text(
                            text = "${modifier.prefix}${currencyFormat.format(modifier.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = modifier.color,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = summary.bottomRowLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currencyFormat.format(summary.bottomRowAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedRelatedActivityCard(
    currentAmount: Double,
    isExpense: Boolean,
    reimbursements: List<io.pm.finlight.TransactionDetails>,
    mergedEntries: List<io.pm.finlight.data.model.MergedTransactionItem>,
    onLinkClick: () -> Unit,
    onUnlinkClick: (Int) -> Unit,
    onUnmergeClick: () -> Unit,
) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val hasReimbursements = isExpense
    val hasMerged = mergedEntries.size > 1

    if (!hasReimbursements && !hasMerged) return

    val totalRepaid = reimbursements.sumOf { it.transaction.amount }
    val mergedChildren = mergedEntries.filter { !it.isAnchor }
    val totalMerged =
        mergedChildren.sumOf { child ->
            val isSameType = (isExpense && child.transactionType == "expense") || (!isExpense && child.transactionType == "income")
            if (isSameType) child.amount else -child.amount
        }

    val anchor = mergedEntries.firstOrNull { it.isAnchor }
    val originalAmount =
        if (hasMerged) {
            anchor?.amount ?: (currentAmount + totalRepaid - totalMerged)
        } else {
            currentAmount + totalRepaid
        }

    val items = mutableListOf<RelatedTransactionItem>()

    // Add merged items
    if (hasMerged) {
        items.addAll(
            mergedChildren.map { child ->
                val isSameType = (isExpense && child.transactionType == "expense") || (!isExpense && child.transactionType == "income")
                val prefix = if (isSameType) "+ " else "- "
                RelatedTransactionItem(
                    id = "merge_${child.hashCode()}",
                    title = "(Merged) ${child.description.ifBlank { "Unknown" }}",
                    subtitle = "${dateFormat.format(java.util.Date(child.date))} • ${child.accountName}",
                    amount = child.amount,
                    amountPrefix = prefix,
                    amountColor = MaterialTheme.colorScheme.primary,
                    actionIcon = null,
                    actionContentDescription = null,
                    onActionClick = null
                )
            }
        )
    }

    // Add reimbursements
    if (hasReimbursements) {
        items.addAll(
            reimbursements.map { detail ->
                RelatedTransactionItem(
                    id = "repay_${detail.transaction.id}",
                    title = "(Repayment) ${detail.transaction.description}",
                    subtitle = detail.accountName ?: "Unknown account",
                    amount = detail.transaction.amount,
                    amountPrefix = "- ",
                    amountColor = MaterialTheme.colorScheme.primary,
                    actionIcon = Icons.Default.Close,
                    actionContentDescription = "Unlink repayment",
                    onActionClick = { onUnlinkClick(detail.transaction.id) }
                )
            }
        )
    }

    val modifiers = mutableListOf<SummaryModifier>()
    if (hasMerged) {
        modifiers.add(
            SummaryModifier(
                label = "Total merged in",
                amount = kotlin.math.abs(totalMerged),
                prefix = if (totalMerged >= 0) "+ " else "- ",
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
    if (hasReimbursements && (totalRepaid > 0 || !hasMerged)) {
        modifiers.add(
            SummaryModifier(
                label = "Total repaid",
                amount = totalRepaid,
                prefix = "- ",
                color = MaterialTheme.colorScheme.primary
            )
        )
    }

    val summary =
        RelatedTransactionSummary(
            topRowLabel = if (hasMerged) "Anchor original amount" else "Original amount",
            topRowAmount = originalAmount,
            modifiers = modifiers,
            bottomRowLabel = if (hasMerged && !hasReimbursements) "Current amount" else "Net cost",
            bottomRowAmount = currentAmount
        )

    val title =
        if (hasMerged && hasReimbursements) {
            "Related Activity"
        } else if (hasMerged) {
            "Merged Transactions"
        } else {
            "Repayments"
        }
    val subtitle =
        if (hasMerged && hasReimbursements) {
            "Merged items and linked repayments."
        } else if (hasMerged) {
            "These were combined into one."
        } else {
            "Got paid back for this? Link it here."
        }
    val icon =
        if (hasMerged && hasReimbursements) {
            Icons.Default.Link
        } else if (hasMerged) {
            Icons.AutoMirrored.Filled.MergeType
        } else {
            Icons.Default.Payments
        }

    RelatedTransactionsCard(
        headerIcon = icon,
        headerIconTint = MaterialTheme.colorScheme.primary,
        title = title,
        subtitle = subtitle,
        headerAction = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasMerged) {
                    Text(
                        text = "Unmerge",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier
                                .clickable { onUnmergeClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                if (hasMerged && hasReimbursements) {
                    VerticalDivider(
                        modifier = Modifier.height(14.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }

                if (hasReimbursements) {
                    Text(
                        text = "+ Link",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clickable { onLinkClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        },
        emptyStateText = if (hasMerged && !hasReimbursements) "No merged transactions." else "No repayments yet.",
        items = items,
        summary = summary
    )
}
