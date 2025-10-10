// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AccountMappingScreen.kt
// REASON: FEATURE (Account Learning) - The screen has been completely rewritten
// to support the new `AccountAlias` learning flow. It now receives a list of
// `AccountMappingRequest` objects and displays a clear, descriptive prompt for
// each item, indicating whether the user is mapping an unparsed account
// identifier (e.g., "A/c xx1234") or a generic SMS sender.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.Account
import io.pm.finlight.ui.components.CreateAccountDialog
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.AccountMappingRequest
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMappingScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    accountViewModel: AccountViewModel = viewModel()
) {
    val mappingsToReview by settingsViewModel.mappingsToReview.collectAsState()
    val allAccounts by accountViewModel.accountsWithBalance.collectAsState(initial = emptyList())
    val accountsList = allAccounts.map { it.account }

    var userMappings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Map New Accounts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "We found transactions from new accounts or senders. Map them once, and we'll remember your choice for future imports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(mappingsToReview, key = { it.identifier }) { request ->
                MappingItem(
                    request = request,
                    accounts = accountsList,
                    onAccountSelected = { identifier, accountId ->
                        userMappings = userMappings + (identifier to accountId)
                    },
                    onCreateNew = { showCreateAccountDialog = true }
                )
            }
        }

        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
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
                        settingsViewModel.finalizeImport(userMappings)
                        navController.popBackStack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Finish Import")
                }
            }
        }
    }


    if (showCreateAccountDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateAccountDialog = false },
            onConfirm = { name, type ->
                accountViewModel.addAccount(name, type)
                showCreateAccountDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingItem(
    request: AccountMappingRequest,
    accounts: List<Account>,
    onAccountSelected: (identifier: String, accountId: Int) -> Unit,
    onCreateNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedAccountName by remember { mutableStateOf("Select Account") }

    val title = if (request.isSenderMapping) "Unknown Sender" else "New Account Found"

    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                request.identifier,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Found ${request.transactionCount} transaction(s) (e.g., for '${request.sampleMerchant}')",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedAccountName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Map to Your Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(
                        if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight
                    )
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                selectedAccountName = account.name
                                onAccountSelected(request.identifier, account.id)
                                expanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Create New Account...") },
                        onClick = {
                            onCreateNew()
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}