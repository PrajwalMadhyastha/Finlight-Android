// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/TripDetailScreen.kt
// REASON: NEW FILE - This is the main UI for the Trip Detail screen. It displays
// a header with the trip's stats and a list of all transactions tagged for
// that trip, allowing users to drill down into their travel spending.
// FIX (UI) - Removed the local Scaffold and TopAppBar. The main NavHost now
// provides a centralized TopAppBar, and this change removes the duplicate,
// resolving a UI bug.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material3.*
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
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.TransactionItem
import io.pm.finlight.ui.viewmodel.TripDetailViewModel
import io.pm.finlight.ui.viewmodel.TripDetailViewModelFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    tripId: Int,
    tagId: Int
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = TripDetailViewModelFactory(application, tripId, tagId)
    val viewModel: TripDetailViewModel = viewModel(factory = factory)
    val transactionViewModel: TransactionViewModel = viewModel() // For category change sheet

    val tripDetails by viewModel.tripDetails.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            tripDetails?.let {
                TripDetailHeader(trip = it)
            }
        }

        if (transactions.isNotEmpty()) {
            item {
                Text(
                    "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(transactions, key = { it.transaction.id }) { transaction ->
                TransactionItem(
                    transactionDetails = transaction,
                    onClick = { navController.navigate("transaction_detail/${transaction.transaction.id}") },
                    onCategoryClick = { transactionViewModel.requestCategoryChange(it) }
                )
            }
        }
    }
}

@Composable
private fun TripDetailHeader(trip: TripWithStats) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CardTravel,
                contentDescription = "Trip",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Spent on this Trip",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormat.format(trip.totalSpend),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${dateFormat.format(Date(trip.startDate))} - ${dateFormat.format(Date(trip.endDate))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}