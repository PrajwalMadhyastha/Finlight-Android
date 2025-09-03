// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AccountListScreen.kt
// REASON: FEATURE - Implemented the full UI for the Account Merging feature.
// This includes a new contextual top app bar for selection mode, updated list
// items that support long-press-to-select and display a selected state, and a
// comprehensive confirmation dialog that requires the user to pick a master account
// before proceeding with the destructive merge operation.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.AccountViewModel
import io.pm.finlight.AccountWithBalance
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.BankLogoHelper
import java.text.NumberFormat
import java.util.*

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    navController: NavController,
    viewModel: AccountViewModel,
) {
    val accounts by viewModel.accountsWithBalance.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    val isSelectionMode by viewModel.isSelectionModeActive.collectAsState()
    val selectedIds by viewModel.selectedAccountIds.collectAsState()
    var showMergeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = viewModel.uiEvent) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showMergeDialog = true },
                            enabled = selectedIds.size >= 2
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Merge Accounts")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(accounts, key = { it.account.id }) { accountWithBalance ->
                AccountListItem(
                    modifier = Modifier.animateItemPlacement(),
                    accountWithBalance = accountWithBalance,
                    isSelectionMode = isSelectionMode,
                    isSelected = accountWithBalance.account.id in selectedIds,
                    onToggleSelection = { viewModel.toggleAccountSelection(accountWithBalance.account.id) },
                    onEnterSelectionMode = { viewModel.enterSelectionMode(accountWithBalance.account.id) },
                    onNavigateToDetail = { navController.navigate("account_detail/${accountWithBalance.account.id}") },
                    onEditClick = { navController.navigate("edit_account/${accountWithBalance.account.id}") }
                )
            }
        }
    }

    if (showMergeDialog) {
        val accountsToMerge = accounts.filter { it.account.id in selectedIds }
        MergeConfirmationDialog(
            accountsToMerge = accountsToMerge,
            onDismiss = { showMergeDialog = false },
            onConfirm = { destinationAccountId ->
                viewModel.mergeSelectedAccounts(destinationAccountId)
                showMergeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountListItem(
    modifier: Modifier = Modifier,
    accountWithBalance: AccountWithBalance,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onEditClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent

    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onNavigateToDetail() },
                onLongClick = { if (!isSelectionMode) onEnterSelectionMode() }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = BankLogoHelper.getLogoForAccount(accountWithBalance.account.name)),
                contentDescription = "${accountWithBalance.account.name} Logo",
                modifier = Modifier.size(40.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = accountWithBalance.account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Balance: ${currencyFormat.format(accountWithBalance.balance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
            } else {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeConfirmationDialog(
    accountsToMerge: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    onConfirm: (destinationAccountId: Int) -> Unit
) {
    var selectedDestinationId by remember { mutableStateOf<Int?>(null) }
    val isThemeDark = MaterialTheme.colorScheme.surface.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = popupContainerColor,
        icon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Merge") },
        title = { Text("Merge Accounts") },
        text = {
            Column {
                Text(
                    "Select one account to keep. All transactions from the other accounts will be moved to this one, and the other accounts will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                accountsToMerge.forEach { accountWithBalance ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (accountWithBalance.account.id == selectedDestinationId),
                                onClick = { selectedDestinationId = accountWithBalance.account.id }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (accountWithBalance.account.id == selectedDestinationId),
                            onClick = { selectedDestinationId = accountWithBalance.account.id }
                        )
                        Text(
                            text = accountWithBalance.account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedDestinationId?.let { onConfirm(it) } },
                enabled = selectedDestinationId != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirm Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
