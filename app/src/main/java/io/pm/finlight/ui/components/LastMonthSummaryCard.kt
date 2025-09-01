// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/LastMonthSummaryCard.kt
// REASON: NEW FILE - This composable defines the UI for the new "Last Month
// Summary" card. It includes a title, dismiss button, infographic, formatted
// spending/earning text, and a button to navigate to the full monthly report.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.LastMonthSummary
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.ui.theme.ExpenseRedDark
import io.pm.finlight.ui.theme.IncomeGreenDark
import java.text.NumberFormat
import java.util.*

@Composable
fun LastMonthSummaryCard(
    summary: LastMonthSummary,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Last Month's Summary",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Summary Info",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = ExpenseRedDark)) {
                                append(currencyFormat.format(summary.totalExpenses))
                            }
                            append(" spent and ")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = IncomeGreenDark)) {
                                append(currencyFormat.format(summary.totalIncome))
                            }
                            append(" earned.")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tap below to see the full breakdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    navController.navigate("time_period_report_screen/${TimePeriod.MONTHLY}?showPreviousMonth=true")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Full Report")
            }
        }
    }
}
