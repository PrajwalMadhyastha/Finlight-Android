package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.pm.finlight.TransactionDetails
import io.pm.finlight.ui.components.TransactionItem

@Composable
fun TransactionHistorySheet(
    transactions: List<TransactionDetails>,
    onTransactionSelected: (TransactionDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTransactions =
        remember(searchQuery, transactions) {
            if (searchQuery.isBlank()) {
                transactions
            } else {
                transactions.filter {
                    it.transaction.description.contains(searchQuery, ignoreCase = true) ||
                        (it.categoryName?.contains(searchQuery, ignoreCase = true) == true)
                }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .padding(16.dp),
    ) {
        Text(
            "Transaction History",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search History") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filteredTransactions, key = { it.transaction.id }) { details ->
                TransactionItem(
                    transactionDetails = details,
                    onClick = { onTransactionSelected(details) },
                    onCategoryClick = { /* No-op in this context */ },
                )
            }
            if (filteredTransactions.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    Text(
                        "No matches found.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
