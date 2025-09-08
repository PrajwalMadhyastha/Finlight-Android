// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/TravelHistoryScreen.kt
// REASON: FEATURE - Added "Edit" and "Delete" IconButtons to each trip item.
// The "Edit" button navigates to the CurrencyTravelScreen with the tripId, and
// the "Delete" button shows a confirmation dialog before removing the trip.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.viewmodel.HistoricTripsViewModel
import io.pm.finlight.ui.viewmodel.HistoricTripsViewModelFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TravelHistoryScreen(
    navController: NavController
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = HistoricTripsViewModelFactory(application)
    val viewModel: HistoricTripsViewModel = viewModel(factory = factory)

    val trips by viewModel.historicTrips.collectAsState()
    var tripToDelete by remember { mutableStateOf<TripWithStats?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Travel History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (trips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No past trips recorded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(trips, key = { it.tripId }) { trip ->
                    HistoricTripItem(
                        modifier = Modifier.animateItemPlacement(animationSpec = spring()),
                        trip = trip,
                        onClick = { navController.navigate("trip_detail/${trip.tripId}/${trip.tagId}") },
                        onEditClick = { navController.navigate("currency_travel_settings?tripId=${trip.tripId}") },
                        onDeleteClick = { tripToDelete = trip }
                    )
                }
            }
        }
    }

    if (tripToDelete != null) {
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Delete Trip?") },
            text = { Text("Are you sure you want to delete '${tripToDelete?.tripName}'? This will untag ${tripToDelete?.transactionCount} transaction(s). This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrip(tripToDelete!!.tripId, tripToDelete!!.tagId)
                        tripToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistoricTripItem(
    modifier: Modifier = Modifier,
    trip: TripWithStats,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    GlassPanel(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.tripName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${dateFormat.format(Date(trip.startDate))} - ${dateFormat.format(Date(trip.endDate))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, "Edit Trip", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, "Delete Trip", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Spend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        currencyFormat.format(trip.totalSpend),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Transactions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${trip.transactionCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}