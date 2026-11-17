package io.pm.finlight.ui.screens.recurring

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.Transaction
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.viewmodel.PendingTransactionsViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun UpcomingPaymentsCard(
    navController: NavController,
    pendingViewModel: PendingTransactionsViewModel =
        viewModel(
            factory = FinlightViewModelFactory.create(LocalContext.current.applicationContext as Application),
        ),
    recurringViewModel: RecurringTransactionViewModel =
        viewModel(
            factory = FinlightViewModelFactory.create(LocalContext.current.applicationContext as Application),
        ),
) {
    val pending by pendingViewModel.pendingTransactions.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }

    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    val context = LocalContext.current
    LaunchedEffect(pendingViewModel) {
        pendingViewModel.uiEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val allRules by recurringViewModel.allRecurringTransactions.collectAsState(initial = emptyList())

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Upcoming Payments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (pending.isEmpty()) {
                Text(
                    text = "No pending payments. All caught up! \u2705",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                pending.take(3).forEach { txn ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = txn.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { selectedTransaction = txn },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(currencyFormat.format(txn.amount), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (pending.size > 3) {
                    TextButton(
                        onClick = { navController.navigate("recurring_transactions") },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("+${pending.size - 3} more", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    selectedTransaction?.let { txn ->
        val rule = allRules.find { it.id == txn.recurringRuleId }
        ConfirmPendingBottomSheet(
            transaction = txn,
            rule = rule,
            onDismiss = { selectedTransaction = null },
            pendingViewModel = pendingViewModel,
        )
    }
}
