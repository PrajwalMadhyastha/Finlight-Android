package io.pm.finlight.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.TransactionDetails
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.platform.testTag

@Composable
fun MergeSuggestionCard(
    suggestion: Pair<TransactionDetails, TransactionDetails>,
    onMerge: () -> Unit,
    onDismiss: () -> Unit
) {
    val parent = suggestion.first.transaction
    val child = suggestion.second.transaction

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val parentAmount = currencyFormat.format(parent.amount)
    val childAmount = currencyFormat.format(child.amount)
    val totalAmount = currencyFormat.format(parent.amount + child.amount)
    val merchant = parent.description

    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallMerge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Merge Suggestion",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).testTag("dismiss_merge_button")) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "We noticed another charge at $merchant for $childAmount.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onMerge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("merge_button"),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
            ) {
                Text("Merge for a total of $totalAmount")
            }
        }
    }
}
