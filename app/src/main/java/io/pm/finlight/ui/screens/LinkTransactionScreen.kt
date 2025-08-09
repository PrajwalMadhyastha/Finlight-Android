// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/LinkTransactionScreen.kt
// REASON: FEATURE - The screen now includes an AlertDialog to confirm the user's
// choice before linking. The onClick handler has been updated to show this
// dialog. Upon confirmation, it calls the ViewModel to perform the link, passes
// a signal back to the previous screen to remove the item from the review list,
// and then navigates away, completing the feature's workflow.
// BUG FIX - The AlertDialog now correctly derives its background color from
// the app's MaterialTheme, ensuring it matches the selected theme (e.g.,
// Aurora) instead of defaulting to the system's light/dark mode.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
fun LinkTransactionScreen(
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
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var transactionToLink by remember { mutableStateOf<Transaction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SmsDetailsCard(potentialTxn)

        Text("Select a transaction to link:", style = MaterialTheme.typography.titleMedium)

        if (candidates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No potential matches found.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.id }) { transaction ->
                    LinkCandidateItem(
                        transaction = transaction,
                        onClick = {
                            transactionToLink = transaction
                            showConfirmationDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showConfirmationDialog && transactionToLink != null) {
        val isThemeDark = MaterialTheme.colorScheme.surface.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Confirm Link") },
            text = { Text("Link this SMS to the transaction for '${transactionToLink!!.description}'?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.linkTransaction(transactionToLink!!.id) {
                        // Pass the ID of the linked SMS back to the review screen
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("linked_sms_id", potentialTxn.sourceSmsId)
                        navController.popBackStack()
                    }
                    showConfirmationDialog = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = popupContainerColor
        )
    }
}

@Composable
private fun SmsDetailsCard(pt: PotentialTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SMS Details", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            Text("Amount: ₹${"%,.2f".format(pt.amount)}", fontWeight = FontWeight.Bold)
            Text("Type: ${pt.transactionType.replaceFirstChar { it.uppercase() }}")
            Text("Original Message: ${pt.originalMessage}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LinkCandidateItem(transaction: Transaction, onClick: () -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(dateFormatter.format(Date(transaction.date)), style = MaterialTheme.typography.bodySmall)
            }
            Text("₹${"%,.2f".format(transaction.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}
