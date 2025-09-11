// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AnalysisDetailScreen.kt
// REASON: NEW FILE - This screen serves as the drill-down view for the Spending
// Analysis feature. It displays a simple, scrollable list of all transactions
// that match the selected dimension and time period from the previous screen.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.TransactionItem
import io.pm.finlight.ui.viewmodel.AnalysisDetailViewModel
import io.pm.finlight.ui.viewmodel.AnalysisDetailViewModelFactory
import io.pm.finlight.ui.viewmodel.AnalysisDimension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisDetailScreen(
    navController: NavController,
    dimension: AnalysisDimension,
    dimensionId: String,
    title: String,
    startDate: Long,
    endDate: Long
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = AnalysisDetailViewModelFactory(application, dimension, dimensionId, startDate, endDate)
    val viewModel: AnalysisDetailViewModel = viewModel(factory = factory)
    val transactionViewModel: TransactionViewModel = viewModel()

    val transactions by viewModel.transactions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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