// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AccountMappingScreen.kt
// REASON: FEATURE (Help System - Phase 1) - Integrated the HelpActionIcon into
// the TopAppBar to provide users with contextual guidance on why and how to
// map their accounts.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.Account
import io.pm.finlight.AccountViewModel
import io.pm.finlight.SenderToMap
import io.pm.finlight.SettingsViewModel
import io.pm.finlight.ui.components.CreateAccountDialog
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMappingScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    accountViewModel: AccountViewModel = viewModel()
) {
    val sendersToMap by settingsViewModel.sendersToMap.collectAsState()
    val allAccounts by accountViewModel.accountsWithBalance.collectAsState(initial = emptyList())
    val accountsList = allAccounts.map { it.account }

    var userMappings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var senderForNewAccount by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map New Accounts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                // --- NEW: Add HelpActionIcon ---
                actions = {
                    HelpActionIcon(helpKey = "account_mapping_screen")
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            )
        },
        bottomBar = {
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "We found transactions from some new senders.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Map them to your accounts to import them correctly. You only have to do this once per sender.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(sendersToMap, key = { it.sender }) { senderInfo ->
                MappingItem(
                    senderInfo = senderInfo,
                    accounts = accountsList,
                    onAccountSelected = { sender, accountId ->
                        userMappings = userMappings + (sender to accountId)
                    },
                    onCreateNew = { sender ->
                        senderForNewAccount = sender
                        showCreateAccountDialog = true
                    }
                )
            }
        }
    }

    if (showCreateAccountDialog && senderForNewAccount != null) {
        CreateAccountDialog(
            onDismiss = { showCreateAccountDialog = false },
            onConfirm = { name, type ->
                accountViewModel.addAccount(name, type)
                // We don't map it here directly, the user can select it from the dropdown after creation
                showCreateAccountDialog = false
                senderForNewAccount = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingItem(
    senderInfo: SenderToMap,
    accounts: List<Account>,
    onAccountSelected: (sender: String, accountId: Int) -> Unit,
    onCreateNew: (sender: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedAccountName by remember { mutableStateOf("Select Account") }

    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Sender: ${senderInfo.sender}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Found ${senderInfo.transactionCount} transactions (e.g., for '${senderInfo.sampleMerchant}')",
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
                    label = { Text("Map to Account") },
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
                                onAccountSelected(senderInfo.sender, account.id)
                                expanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Create New Account...") },
                        onClick = {
                            onCreateNew(senderInfo.sender)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}