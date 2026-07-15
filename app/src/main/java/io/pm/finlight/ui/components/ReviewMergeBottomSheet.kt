package io.pm.finlight.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.TransactionDetails
import io.pm.finlight.ui.theme.ExpenseRedDark
import io.pm.finlight.ui.theme.ExpenseRedLight
import io.pm.finlight.ui.theme.IncomeGreenDark
import io.pm.finlight.ui.theme.IncomeGreenLight
import io.pm.finlight.utils.FormatUtils

@Composable
fun ReviewMergeBottomSheet(
    selectedTransactions: List<TransactionDetails>,
    anchorTransactionId: Int?,
    onAnchorSelected: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    // Calculate net amount using signed arithmetic
    fun signedAmount(txn: TransactionDetails): Double =
        if (txn.transaction.transactionType == "income") txn.transaction.amount else -txn.transaction.amount

    val netSigned = selectedTransactions.sumOf { signedAmount(it) }
    val finalAmount = kotlin.math.abs(netSigned)
    val finalType = if (netSigned >= 0.0) "Income" else "Expense"

    val isDark = isSystemInDarkTheme()
    val incomeGreen = if (isDark) IncomeGreenDark else IncomeGreenLight
    val expenseRed = if (isDark) ExpenseRedDark else ExpenseRedLight

    val pillColor = if (netSigned >= 0.0) incomeGreen else expenseRed
    val formattedAmount = FormatUtils.currencyFormatter.format(finalAmount)

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Review Merge",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            "Select the main (anchor) transaction. All other transactions will be merged into it, keeping its category and date. Notes and tags will be combined.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Net Result Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillColor.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Net Result: $formattedAmount ($finalType)",
                    color = pillColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Divider()

        Text(
            "Selected Transactions (${selectedTransactions.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedTransactions, key = { it.transaction.id }) { item ->
                val isSelected = item.transaction.id == anchorTransactionId
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAnchorSelected(item.transaction.id) }
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onAnchorSelected(item.transaction.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.transaction.description,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = FormatUtils.displayDateFormatter.format(item.transaction.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = FormatUtils.currencyFormatter.format(item.transaction.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (item.transaction.transactionType == "income") incomeGreen else expenseRed
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = anchorTransactionId != null
            ) {
                Text("Confirm Merge")
            }
        }
    }
}
