// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AccountDetailScreen.kt
// REASON: FEATURE (Help System - Phase 3) - Wrapped the screen in a Scaffold
// and added a TopAppBar with a HelpActionIcon to provide users with contextual
// guidance.
// FIX (UI) - Removed the local Scaffold and TopAppBar. The main NavHost now
// provides a centralized TopAppBar, and this change removes the duplicate,
// resolving a UI bug.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.Account
import io.pm.finlight.TransactionDetails
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.ExpenseRedDark
import io.pm.finlight.ui.theme.ExpenseRedLight
import io.pm.finlight.ui.theme.IncomeGreenDark
import io.pm.finlight.ui.theme.IncomeGreenLight
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.utils.BankLogoHelper
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    navController: NavController,
    viewModel: AccountViewModel,
    accountId: Int,
) {
    val account by viewModel.getAccountById(accountId).collectAsState(initial = null)
    val balance by viewModel.getAccountBalance(accountId).collectAsState(initial = 0.0)
    val transactions by viewModel.getTransactionsForAccount(accountId).collectAsState(initial = emptyList())

    val currentAccount = account ?: return // Don't compose if account is not loaded yet

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AccountDetailHeader(
                account = currentAccount,
                balance = balance
            )
        }

        if (transactions.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            items(transactions, key = { it.transaction.id }) { details ->
                // --- UPDATED: Pass the navigation logic to the item ---
                AccountDetailTransactionItem(
                    transactionDetails = details,
                    onClick = {
                        navController.navigate("transaction_detail/${details.transaction.id}")
                    }
                )
            }
        } else {
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Text(
                            "No transactions for this account yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetailHeader(account: Account, balance: Double) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val balanceColor = when {
        balance > 0 -> MaterialTheme.colorScheme.primary
        balance < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = BankLogoHelper.getLogoForAccount(account.name)),
                contentDescription = "${account.name} Logo",
                modifier = Modifier.size(50.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Current Balance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormat.format(balance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
            }
        }
    }
}

@Composable
private fun AccountDetailTransactionItem(
    transactionDetails: TransactionDetails,
    onClick: () -> Unit // --- NEW: Accept an onClick lambda ---
) {
    val contentAlpha = if (transactionDetails.transaction.isExcluded) 0.5f else 1f
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    // --- UPDATED: Apply the clickable modifier to the GlassPanel ---
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transactionDetails.transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = dateFormatter.format(Date(transactionDetails.transaction.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Spacer(Modifier.width(16.dp))

            val isIncome = transactionDetails.transaction.transactionType == "income"
            val amountColor = if (isSystemInDarkTheme()) {
                if (isIncome) IncomeGreenDark else ExpenseRedDark
            } else {
                if (isIncome) IncomeGreenLight else ExpenseRedLight
            }.copy(alpha = contentAlpha)

            Text(
                text = currencyFormat.format(transactionDetails.transaction.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
        }
    }
}