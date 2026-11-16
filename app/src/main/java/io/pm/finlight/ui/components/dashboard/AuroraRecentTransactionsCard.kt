package io.pm.finlight.ui.components.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.TransactionDetails
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.TransactionItem

@Composable
fun AuroraRecentTransactionsCard(
    transactions: List<TransactionDetails>,
    navController: NavController,
    onCategoryClick: (TransactionDetails) -> Unit,
) {
    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = { navController.navigate("add_transaction") },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        navController.navigate(BottomNavItem.Transactions.route) {
                            popUpTo(BottomNavItem.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                ) { Text("View All") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                Text(
                    "No transactions yet.",
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transactions.forEach { details ->
                        key(details.transaction.id) {
                            TransactionItem(
                                transactionDetails = details,
                                onClick = {
                                    navController.navigate("transaction_detail/${details.transaction.id}")
                                },
                                onCategoryClick = onCategoryClick,
                            )
                        }
                    }
                }
            }
        }
    }
}
