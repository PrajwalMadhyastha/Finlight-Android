// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/SplitTransactionScreen.kt
// REASON: FEATURE (Help System - Phase 2) - Integrated the HelpActionIcon into
// the TopAppBar to provide users with contextual guidance on how to split a
// transaction.
// REASON: FIX (Crash) - The composable signature now accepts the
// TransactionViewModel as a parameter. The local `viewModel()` call has been
// removed, fixing the `NoSuchMethodException` crash.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.*
import io.pm.finlight.ui.components.CategorySelectionGrid
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import java.text.NumberFormat
import java.util.*

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTransactionScreen(
    navController: NavController,
    transactionId: Int,
    transactionViewModel: TransactionViewModel // --- FIX: Accept shared ViewModel
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = SplitTransactionViewModelFactory(application, transactionId)
    val viewModel: SplitTransactionViewModel = viewModel(factory = factory)
    // --- FIX: Remove the local viewModel() call that was crashing ---
    // val transactionViewModel: TransactionViewModel = viewModel()

    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categoryRepository.allCategories.collectAsState(initial = emptyList())
    val context = LocalContext.current

    var activeSheetTarget by remember { mutableStateOf<SplitItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isSaveEnabled = uiState.remainingAmount == 0.0 && uiState.splitItems.all { it.category != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    HelpActionIcon(helpKey = "split_transaction")
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SplitHeader(
                        parentTransaction = uiState.parentTransaction,
                        remainingAmount = uiState.remainingAmount
                    )
                }

                // --- NEW: Conditionally show conversion info card ---
                if (uiState.parentTransaction?.currencyCode != null) {
                    item {
                        ConversionInfoCard(transaction = uiState.parentTransaction!!)
                    }
                }


                items(uiState.splitItems, key = { it.id }) { item ->
                    SplitItemRow(
                        item = item,
                        currencyCode = uiState.parentTransaction?.currencyCode,
                        onAmountChange = { newAmount -> viewModel.updateSplitAmount(item, newAmount) },
                        onCategoryClick = { activeSheetTarget = item },
                        onDeleteClick = { viewModel.removeSplitItem(item) }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.addSplitItem() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Split")
                        Spacer(Modifier.width(8.dp))
                        Text("Add Item")
                    }
                }
            }

            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            transactionViewModel.saveTransactionSplits(transactionId, uiState.splitItems) {
                                Toast.makeText(context, "Transaction split saved!", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isSaveEnabled
                    ) { Text("Save Splits") }
                }
            }
        }
    }

    if (activeSheetTarget != null) {
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        ModalBottomSheet(
            onDismissRequest = { activeSheetTarget = null },
            sheetState = sheetState,
            containerColor = popupContainerColor
        ) {
            SplitCategoryPickerSheet(
                categories = categories,
                onCategorySelected = { category ->
                    activeSheetTarget?.let {
                        viewModel.updateSplitCategory(it, category)
                    }
                    activeSheetTarget = null
                }
            )
        }
    }
}

@Composable
private fun SplitHeader(parentTransaction: Transaction?, remainingAmount: Double) {
    // --- UPDATED: Use foreign currency info if available ---
    val isTravelMode = parentTransaction?.originalAmount != null
    val totalAmount = parentTransaction?.originalAmount ?: parentTransaction?.amount ?: 0.0
    val currencySymbol = if (isTravelMode) {
        CurrencyHelper.getCurrencySymbol(parentTransaction?.currencyCode)
    } else {
        "₹"
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val remainingColor = when {
        remainingAmount > 0.001 || remainingAmount < -0.001 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Total Amount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$currencySymbol${currencyFormat.format(totalAmount).drop(1)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Remaining: $currencySymbol${currencyFormat.format(remainingAmount).drop(1)}",
                style = MaterialTheme.typography.titleMedium,
                color = remainingColor
            )
        }
    }
}

@Composable
private fun SplitItemRow(
    item: SplitItem,
    currencyCode: String?,
    onAmountChange: (String) -> Unit,
    onCategoryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // --- UPDATED: Use foreign currency symbol if available ---
    val currencySymbol = if (currencyCode != null) {
        CurrencyHelper.getCurrencySymbol(currencyCode)
    } else {
        "₹"
    }

    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCategoryClick)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val icon = item.category?.let { CategoryIconHelper.getIcon(it.iconKey) } ?: Icons.Default.Add
                Icon(
                    imageVector = icon,
                    contentDescription = "Category",
                    tint = if (item.category != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.category?.name ?: "Set",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.category != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = item.amount,
                onValueChange = onAmountChange,
                modifier = Modifier.weight(1f),
                label = { Text("Amount") },
                leadingIcon = { Text(currencySymbol) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Split", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SplitCategoryPickerSheet(
    categories: List<Category>,
    onCategorySelected: (Category) -> Unit
) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Text(
            "Select Category for Split",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        // --- UPDATED: Replaced the LazyVerticalGrid with the new reusable component ---
        CategorySelectionGrid(
            categories = categories,
            onCategorySelected = onCategorySelected
        )
        Spacer(Modifier.height(16.dp))
    }
}

// --- NEW: A card to display conversion info ---
@Composable
private fun ConversionInfoCard(transaction: Transaction) {
    val homeCurrencySymbol = "₹"
    val foreignCurrencySymbol = CurrencyHelper.getCurrencySymbol(transaction.currencyCode)
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 } }

    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Conversion Info",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Rate: 1 ${transaction.currencyCode} = $homeCurrencySymbol${numberFormat.format(transaction.conversionRate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
