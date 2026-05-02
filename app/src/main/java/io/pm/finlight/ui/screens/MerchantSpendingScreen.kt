// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/MerchantSpendingScreen.kt
// REASON: REVERT - Reverted changes to make the items clickable. Navigation is
// now handled by the parent screen.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.MerchantSpendingSummary
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.ExpenseRedDark

@Composable
fun MerchantSpendingScreen(
    merchantList: List<MerchantSpendingSummary>,
    onMerchantClick: (MerchantSpendingSummary) -> Unit,
) {
    if (merchantList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No merchant data for this month.")
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(merchantList) { merchant ->
            MerchantSpendingCard(
                merchant = merchant,
                onClick = { onMerchantClick(merchant) },
            )
        }
    }
}

@Composable
fun MerchantSpendingCard(
    merchant: MerchantSpendingSummary,
    onClick: () -> Unit,
) {
    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    merchant.merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val visitText = if (merchant.transactionCount == 1) "1 visit" else "${merchant.transactionCount} visits"
                Text(
                    visitText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "₹${"%,.2f".format(merchant.totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseRedDark,
            )
        }
    }
}
