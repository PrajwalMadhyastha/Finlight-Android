// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/LinkRecurringTransactionScreen.kt
// REASON: BUG FIX - The navigation logic has been corrected to definitively fix
// the back stack issue. Instead of calling popBackStack() separately, the code
// now uses the popUpTo builder within the navigate call. This atomically
// navigates to the detail screen while simultaneously removing the linking
// screen from the back stack, ensuring a correct and intuitive back navigation
// experience for the user.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import io.pm.finlight.LinkTransactionViewModel
import io.pm.finlight.LinkTransactionViewModelFactory
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.Transaction
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.net.URLDecoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkRecurringTransactionScreen(
    navController: NavController,
    potentialTransactionJson: String
) {
    val potentialTxn = remember(potentialTransactionJson) {
        Gson().fromJson(URLDecoder.decode(potentialTransactionJson, "UTF-8"), PotentialTransaction::class.java)
    }

    val application = LocalContext.current.applicationContext as Application
    val factory = LinkTransactionViewModelFactory(application, potentialTxn)
    val viewModel: LinkTransactionViewModel = viewModel(factory = factory)

    val candidates by viewModel.linkableTransactions.collectAsState()
    var showConfirmationDialog by remember { mutableStateOf<Transaction?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Recurring Payment") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DuePaymentDetailsCard(viewModel.potentialTransaction)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.remindTomorrow {
                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notificationManager.cancel(potentialTxn.sourceSmsId.toInt())
                            }
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Remind Tomorrow")
                    }
                    Button(
                        onClick = { navController.navigate("recurring_transactions") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Manage Rules")
                    }
                }
            }

            item {
                Text(
                    "Or, link to a recent transaction:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (candidates.isEmpty()) {
                item {
                    GlassPanel {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No recent matching transactions found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(candidates, key = { it.id }) { transaction ->
                    LinkCandidateItem(
                        transaction = transaction,
                        onClick = { showConfirmationDialog = transaction }
                    )
                }
            }
        }
    }

    showConfirmationDialog?.let { transactionToLink ->
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        AlertDialog(
            onDismissRequest = { showConfirmationDialog = null },
            title = { Text("Confirm Link") },
            text = { Text("Link this payment to the transaction for '${transactionToLink.description}'?") },
            confirmButton = {
                Button(onClick = {
                    showConfirmationDialog = null
                    viewModel.linkTransaction(transactionToLink.id) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(potentialTxn.sourceSmsId.toInt())
                        // --- FIX: Use popUpTo for a more robust navigation ---
                        navController.navigate("transaction_detail/${transactionToLink.id}") {
                            // Pop the linking screen off the back stack
                            popUpTo("link_recurring_transaction/{potentialTransactionJson}") {
                                inclusive = true
                            }
                        }
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = null }) {
                    Text("Cancel")
                }
            },
            containerColor = popupContainerColor
        )
    }
}

@Composable
private fun DuePaymentDetailsCard(pt: PotentialTransaction) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val amountColor = if (pt.transactionType == "expense") {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Payment Due Today",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pt.merchantName ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    currencyFormat.format(pt.amount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }
        }
    }
}

@Composable
private fun LinkCandidateItem(transaction: Transaction, onClick: () -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    dateFormatter.format(Date(transaction.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                currencyFormat.format(transaction.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
