// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/ReimbursementCard.kt
// REASON: NEW FILE - UI card for the "Link Repayment" feature. Shown on expense
// detail screens to display linked income transactions and net cost. Also provides
// a card for income screens to show which expense they offset, with options to
// navigate to that expense or unlink the repayment.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.TransactionDetails
import java.text.NumberFormat
import java.util.*

/**
 * Shown on an expense transaction's detail screen.
 * Lists all income transactions that have been linked as repayments, and shows
 * the original amount, total repaid, and net cost after deductions.
 *
 * @param currentExpenseAmount The CURRENT amount on the expense (already reduced by prior links).
 * @param reimbursements All linked income transactions.
 * @param onLinkClick Opens the picker sheet to link a new reimbursement.
 * @param onUnlinkClick Unlinks the given income transaction.
 */
@Composable
fun ReimbursementCard(
    currentExpenseAmount: Double,
    reimbursements: List<TransactionDetails>,
    onLinkClick: () -> Unit,
    onUnlinkClick: (incomeId: Int) -> Unit,
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val totalRepaid = reimbursements.sumOf { it.transaction.amount }
    val originalAmount = currentExpenseAmount + totalRepaid

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Header ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Repayments",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Got paid back for this? Link it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onLinkClick) {
                    Text("+ Link")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            if (reimbursements.isEmpty()) {
                Text(
                    text = "No repayments yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // --- Reimbursement rows ---
                reimbursements.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.transaction.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = detail.accountName ?: "Unknown account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "- ${currencyFormat.format(detail.transaction.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = { onUnlinkClick(detail.transaction.id) },
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unlink repayment",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
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
                        text = "Original amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = currencyFormat.format(originalAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Total repaid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "- ${currencyFormat.format(totalRepaid)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Net cost",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currencyFormat.format(currentExpenseAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Shown on an income transaction's detail screen when it is linked as a repayment for an expense.
 * Allows the user to navigate to the linked expense detail screen or unlink the repayment.
 *
 * @param linkedExpense The expense transaction this income is linked to.
 * @param onNavigateToExpense Called when the user taps the expense row to navigate to it.
 * @param onUnlinkClick Called when the user confirms unlinking the repayment.
 */
@Composable
fun LinkedAsReimbursementBadge(
    linkedExpense: TransactionDetails,
    onNavigateToExpense: () -> Unit,
    onUnlinkClick: () -> Unit,
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    var showUnlinkConfirmation by remember { mutableStateOf(false) }

    if (showUnlinkConfirmation) {
        ConfirmationDialog(
            title = "Unlink Repayment?",
            text = "This will remove the link to \"${linkedExpense.transaction.description}\" and restore the original expense amount.",
            confirmButtonText = "Unlink",
            isDestructive = true,
            onDismiss = { showUnlinkConfirmation = false },
            onConfirm = {
                showUnlinkConfirmation = false
                onUnlinkClick()
            },
        )
    }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            // --- Header row ---
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Linked as repayment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { showUnlinkConfirmation = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "Unlink",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            // --- Tappable expense row ---
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToExpense)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "For expense",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = linkedExpense.transaction.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    linkedExpense.accountName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = currencyFormat.format(linkedExpense.transaction.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "View expense",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
