package io.pm.finlight.ui.screens.sms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSmsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val potentialTransactions by viewModel.potentialTransactions.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var hasLoadedOnce by remember { mutableStateOf(false) }

    val linkedSmsIdState =
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Long>("linked_sms_id")
            ?.observeAsState()
    val linkedSmsId = linkedSmsIdState?.value

    LaunchedEffect(linkedSmsId) {
        linkedSmsId?.let {
            viewModel.onTransactionLinked(it)
            navController.currentBackStackEntry?.savedStateHandle?.set("linked_sms_id", null)
        }
    }

    LaunchedEffect(isScanning, potentialTransactions) {
        if (!isScanning) {
            hasLoadedOnce = true
        }
        if (hasLoadedOnce && potentialTransactions.isEmpty()) {
            navController.popBackStack()
        }
    }

    if (isScanning && !hasLoadedOnce) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scanning for transactions...", style = MaterialTheme.typography.titleMedium)
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${potentialTransactions.size} potential transactions found.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(potentialTransactions, key = { it.sourceSmsId }) { pt ->
                PotentialTransactionItem(
                    transaction = pt,
                    onDismiss = { viewModel.dismissPotentialTransaction(it) },
                    onApprove = { transaction ->
                        val encodedPotentialTxn = URLEncoder.encode(Gson().toJson(transaction), "UTF-8")
                        val route = "approve_transaction_screen?potentialTxnJson=$encodedPotentialTxn"
                        navController.navigate(route)
                    },
                    onCreateRule = { transaction ->
                        val json = Gson().toJson(transaction)
                        val encodedJson = URLEncoder.encode(json, "UTF-8")
                        navController.navigate("rule_creation_screen?potentialTransactionJson=$encodedJson")
                    },
                    onLink = { transaction ->
                        val json = Gson().toJson(transaction)
                        val encodedJson = URLEncoder.encode(json, "UTF-8")
                        navController.navigate("link_transaction_screen/$encodedJson")
                    },
                )
            }
        }
    }
}

@Composable
fun PotentialTransactionItem(
    transaction: PotentialTransaction,
    onDismiss: (PotentialTransaction) -> Unit,
    onApprove: (PotentialTransaction) -> Unit,
    onCreateRule: (PotentialTransaction) -> Unit,
    onLink: (PotentialTransaction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val amountColor =
                if (TransactionType.fromStringOrNull(transaction.transactionType) == TransactionType.EXPENSE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.merchantName ?: "Unknown Merchant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "₹${"%.2f".format(transaction.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            transaction.potentialAccount?.let {
                Text(
                    text = "Account: ${it.formattedName} (${it.accountType})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Type: ${transaction.transactionType.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Original Message: ${transaction.originalMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onLink(transaction) }) {
                    Text("Link to Existing")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onDismiss(transaction) }) { Text("Dismiss") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApprove(transaction) }) { Text("Approve") }
            }
        }
    }
}
