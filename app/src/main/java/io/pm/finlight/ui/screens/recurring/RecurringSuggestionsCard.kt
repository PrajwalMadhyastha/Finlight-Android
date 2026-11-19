package io.pm.finlight.ui.screens.recurring

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.ui.components.GlassPanel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RecurringSuggestionsCard(
    navController: NavController,
    recurringViewModel: RecurringTransactionViewModel =
        viewModel(
            factory = FinlightViewModelFactory.create(LocalContext.current.applicationContext as Application),
        ),
) {
    val patterns by recurringViewModel.patternSuggestions.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }

    if (patterns.isEmpty()) return

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recurring Suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "We noticed these patterns in your transactions:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            patterns.take(3).forEach { pattern ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pattern.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${pattern.occurrences} occurrences \u00b7 ${currencyFormat.format(pattern.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            navController.navigate("add_recurring_transaction?patternSignature=${pattern.smsSignature}")
                        },
                    ) {
                        Text("Set Up Rule", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            TextButton(
                onClick = { navController.navigate("recurring_transactions") },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("View All Rules \u2192", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
