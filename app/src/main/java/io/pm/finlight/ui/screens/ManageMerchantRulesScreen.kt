package io.pm.finlight.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.pm.finlight.ManageMerchantRulesViewModel
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRuleUiItem
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.components.ConfirmationDialog
import io.pm.finlight.ui.components.EmptyStateMessage
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.FormatUtils
import java.util.Date

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageMerchantRulesScreen(
    navController: NavController,
    viewModel: ManageMerchantRulesViewModel,
) {
    val uiRules by viewModel.uiRules.collectAsState()
    val allRules by viewModel.allRules.collectAsState()
    val totalCount by viewModel.totalRulesCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedRuleForTransactions by viewModel.selectedRuleForTransactions.collectAsState()
    val selectedRuleTransactions by viewModel.selectedRuleTransactions.collectAsState()

    var ruleToDelete by remember { mutableStateOf<MerchantRuleUiItem?>(null) }
    var retroactiveDeletePrompt by remember { mutableStateOf<MerchantRuleUiItem?>(null) }
    var ruleToEdit by remember { mutableStateOf<MerchantRuleUiItem?>(null) }
    var retroactiveEditPrompt by remember { mutableStateOf<Pair<MerchantRuleUiItem, String>?>(null) }
    var ruleForCollision by remember { mutableStateOf<MerchantRuleUiItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- 1. Search Bar ---
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = {
                    Text(
                        "Search raw or renamed merchants…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }

        // --- 2. Add Rule Button ---
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rule")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add New Rule")
        }

        // --- 3. Rules List Header / Count ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rename Rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (searchQuery.isBlank()) "$totalCount active" else "${uiRules.size} of $totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- 4. Content Area ---
        if (allRules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage(
                    message = "No merchant rename rules have been created yet.\n\nFinlight learns rules automatically when you edit transactions or when recurring merchant patterns are detected.",
                    icon = Icons.Default.Storefront,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        } else if (uiRules.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage(
                    message = "No rename rules match \"$searchQuery\"",
                    icon = Icons.Default.Info,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("rules_lazy_column"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = uiRules,
                    key = { it.rule.originalName.lowercase() },
                ) { item ->
                    MerchantRuleItemCard(
                        modifier = Modifier.animateItemPlacement(),
                        item = item,
                        onEditClick = { ruleToEdit = item },
                        onDeleteClick = {
                            if (item.transactionCount > 0) {
                                retroactiveDeletePrompt = item
                            } else {
                                ruleToDelete = item
                            }
                        },
                        onCollisionClick = { ruleForCollision = item },
                        onTransactionCountClick = {
                            viewModel.selectRuleForTransactions(item.rule)
                        },
                    )
                }
            }
        }
    }

    // --- Standard Delete Dialog (0 Transactions) ---
    if (ruleToDelete != null) {
        ConfirmationDialog(
            title = "Delete Rename Rule?",
            text = "Are you sure you want to delete the rename rule for \"${ruleToDelete!!.rule.originalName}\" → \"${ruleToDelete!!.rule.newName}\"?",
            confirmButtonText = "Delete",
            isDestructive = true,
            onDismiss = { ruleToDelete = null },
            onConfirm = {
                viewModel.deleteRule(ruleToDelete!!.rule)
                ruleToDelete = null
            },
        )
    }

    // --- Retroactive Delete Dialog (>0 Transactions) ---
    if (retroactiveDeletePrompt != null) {
        val item = retroactiveDeletePrompt!!
        RetroactiveDeleteRuleDialog(
            item = item,
            onDismiss = { retroactiveDeletePrompt = null },
            onDeleteAndSync = {
                viewModel.deleteRuleAndSync(item.rule)
                retroactiveDeletePrompt = null
            },
            onDeleteOnly = {
                viewModel.deleteRule(item.rule)
                retroactiveDeletePrompt = null
            },
        )
    }

    // --- Edit Dialog ---
    if (ruleToEdit != null) {
        val item = ruleToEdit!!
        EditMerchantRuleDialog(
            rule = item.rule,
            onDismiss = { ruleToEdit = null },
            onConfirm = { newName ->
                ruleToEdit = null
                if (item.transactionCount > 0 && !newName.equals(item.rule.newName, ignoreCase = true)) {
                    retroactiveEditPrompt = Pair(item, newName)
                } else {
                    viewModel.updateRule(item.rule.originalName, newName)
                }
            },
        )
    }

    // --- Retroactive Edit Dialog (>0 Transactions) ---
    if (retroactiveEditPrompt != null) {
        val (item, newName) = retroactiveEditPrompt!!
        RetroactiveEditRuleDialog(
            item = item,
            newName = newName,
            onDismiss = { retroactiveEditPrompt = null },
            onUpdateAndSync = {
                viewModel.updateRuleAndSync(item.rule.originalName, newName)
                retroactiveEditPrompt = null
            },
            onUpdateOnly = {
                viewModel.updateRule(item.rule.originalName, newName)
                retroactiveEditPrompt = null
            },
        )
    }

    // --- Collision / Shadowing Warning Dialog ---
    if (ruleForCollision != null) {
        RuleCollisionDialog(
            item = ruleForCollision!!,
            onDismiss = { ruleForCollision = null },
        )
    }

    // --- Impacted Transactions Sheet ---
    if (selectedRuleForTransactions != null) {
        val rule = selectedRuleForTransactions!!
        ImpactedTransactionsSheet(
            rule = rule,
            transactions = selectedRuleTransactions,
            onDismiss = { viewModel.clearSelectedRuleForTransactions() },
            onTransactionClick = { transaction ->
                navController.navigate("transaction_detail/${transaction.transaction.id}")
            },
            onSyncAll = {
                viewModel.updateRuleAndSync(rule.originalName, rule.newName)
            },
        )
    }

    // --- Add Rule Dialog ---
    if (showAddDialog) {
        AddMerchantRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { originalName, newName ->
                viewModel.addRule(originalName, newName)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun MerchantRuleItemCard(
    modifier: Modifier = Modifier,
    item: MerchantRuleUiItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCollisionClick: () -> Unit,
    onTransactionCountClick: () -> Unit,
) {
    val rule = item.rule
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top Row: Raw Name -> Arrow -> Canonical Name + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Raw Name Column
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "RAW / SMS",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rule.originalName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Arrow Indicator
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Renamed to",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )

                // Renamed Display Name Column
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "RENAMED TO",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rule.newName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Actions: Collision Warning (if any), Edit, Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.conflictingRules.isNotEmpty()) {
                        IconButton(
                            onClick = onCollisionClick,
                            modifier = Modifier.testTag("warning_rule_${rule.originalName}"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Rule ambiguity warning",
                                tint = Color(0xFFFFB74D),
                            )
                        }
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.testTag("edit_rule_${rule.originalName}"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit rename rule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_rule_${rule.originalName}"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete rename rule",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Bottom Row: Badges / Chips (Transaction Impact & Linked Category)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1. Transaction Impact Chip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (item.transactionCount > 0) 0.8f else 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier =
                        Modifier
                            .testTag("tx_count_${rule.originalName}")
                            .then(
                                if (item.transactionCount > 0) {
                                    Modifier.clickable(onClick = onTransactionCountClick)
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = if (item.transactionCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "${item.transactionCount} ${if (item.transactionCount == 1) "transaction" else "transactions"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // 2. Linked Category Chip (if present)
                if (item.linkedCategory != null) {
                    val category = item.linkedCategory
                    val categoryColor = CategoryIconHelper.getIconBackgroundColor(category.colorKey)
                    Surface(
                        color = categoryColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("cat_chip_${rule.originalName}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = CategoryIconHelper.getIcon(category.iconKey),
                                contentDescription = "Category: ${category.name}",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RetroactiveDeleteRuleDialog(
    item: MerchantRuleUiItem,
    onDismiss: () -> Unit,
    onDeleteAndSync: () -> Unit,
    onDeleteOnly: () -> Unit,
) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
    val count = item.transactionCount
    val rule = item.rule

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Rename Rule?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Are you sure you want to delete the rename rule for \"${rule.originalName}\" → \"${rule.newName}\"?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "This rule currently applies to $count ${if (count == 1) "transaction" else "transactions"} in your ledger. Would you like to revert existing transactions back to \"${rule.originalName}\" or delete the rule only?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDeleteAndSync,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Revert $count Txns & Delete")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDeleteOnly) {
                    Text("Delete Rule Only")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        containerColor = popupContainerColor,
    )
}

@Composable
fun RetroactiveEditRuleDialog(
    item: MerchantRuleUiItem,
    newName: String,
    onDismiss: () -> Unit,
    onUpdateAndSync: () -> Unit,
    onUpdateOnly: () -> Unit,
) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
    val count = item.transactionCount
    val rule = item.rule

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Existing Transactions?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "You updated the display name for \"${rule.originalName}\" from \"${rule.newName}\" to \"$newName\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "This rule applies to $count ${if (count == 1) "transaction" else "transactions"}. Would you like to update existing transactions to match \"$newName\"?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateAndSync,
            ) {
                Text("Update Rule & $count Txns")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onUpdateOnly) {
                    Text("Update Rule Only")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        containerColor = popupContainerColor,
    )
}

@Composable
fun RuleCollisionDialog(
    item: MerchantRuleUiItem,
    onDismiss: () -> Unit,
) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                )
                Text("Rule Ambiguity Warning")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Rule \"${item.rule.originalName}\" → \"${item.rule.newName}\" shares merchant roots with:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.conflictingRules.forEach { conflict ->
                            Text(
                                text = "• ${conflict.originalName} → ${conflict.newName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Text(
                    text = "Rule Hierarchy in Finlight:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text =
                        "1. Exact Raw Match: Exact SMS merchant names take top priority.\n" +
                            "2. Token Overlap (≥85%): More specific token matches resolve next.\n" +
                            "3. Reverse Canonical: Matches canonical display names across variants.\n\n" +
                            "Different rename targets for overlapping names may lead to unexpected renaming on new transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        },
        containerColor = popupContainerColor,
    )
}

@Composable
fun EditMerchantRuleDialog(
    rule: MerchantRenameRule,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit,
) {
    var newName by remember(rule) { mutableStateOf(rule.newName) }
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Rename Rule") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text(
                        text = "Raw / SMS Merchant Name",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rule.originalName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("edit_rule_display_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName.trim())
                    }
                },
                enabled = newName.isNotBlank(),
                modifier = Modifier.testTag("edit_rule_save_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = popupContainerColor,
    )
}

@Composable
fun AddMerchantRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (originalName: String, newName: String) -> Unit,
) {
    var originalName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    val trimmedOriginal = originalName.trim()
    val trimmedNew = newName.trim()
    val isSameName =
        trimmedOriginal.isNotEmpty() &&
            trimmedNew.isNotEmpty() &&
            trimmedOriginal.equals(trimmedNew, ignoreCase = true)
    val isValid = trimmedOriginal.isNotBlank() && trimmedNew.isNotBlank() && !isSameName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Rename Rule") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = originalName,
                    onValueChange = { originalName = it },
                    label = { Text("Raw / SMS Merchant Name") },
                    placeholder = { Text("e.g. UBER *TRIP") },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("add_rule_raw_input"),
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Uber") },
                    singleLine = true,
                    isError = isSameName,
                    supportingText = {
                        if (isSameName) {
                            Text(
                                text = "Display name must be different from raw name",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("add_rule_display_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onConfirm(trimmedOriginal, trimmedNew)
                    }
                },
                enabled = isValid,
                modifier = Modifier.testTag("add_rule_confirm_button"),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = popupContainerColor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpactedTransactionsSheet(
    rule: MerchantRenameRule,
    transactions: List<TransactionDetails>,
    onDismiss: () -> Unit,
    onTransactionClick: (TransactionDetails) -> Unit,
    onSyncAll: () -> Unit,
) {
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { FormatUtils.displayDateFormatter }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        containerColor = popupContainerColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Impacted Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${rule.originalName} → ${rule.newName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "${transactions.size} txns",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (transactions.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No active transactions in the ledger match this rule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .testTag("impacted_transactions_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(transactions, key = { it.transaction.id }) { tx ->
                        val catColor = CategoryIconHelper.getIconBackgroundColor(tx.categoryColorKey ?: "gray")
                        val catIcon = CategoryIconHelper.getIcon(tx.categoryIconKey ?: "help_outline")
                        val isExpense = tx.transaction.transactionType == TransactionType.EXPENSE
                        val amountColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

                        GlassPanel(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onTransactionClick(tx)
                                        onDismiss()
                                    }
                                    .testTag("impacted_tx_${tx.transaction.id}"),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = catColor.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = tx.categoryName ?: "Category",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.transaction.description,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${dateFormat.format(Date(tx.transaction.date))} • ${tx.accountName ?: "Unknown"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${if (isExpense) "-" else "+"}${FormatUtils.currencyFormatter.format(tx.transaction.amount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = amountColor,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Edit transaction",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                val hasUnsynced = transactions.any { it.transaction.description != rule.newName }
                if (hasUnsynced) {
                    Button(
                        onClick = {
                            onSyncAll()
                            onDismiss()
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("sync_all_impacted_txs_button"),
                    ) {
                        Text("Sync All ${transactions.size} Txns to \"${rule.newName}\"")
                    }
                }
            }
        }
    }
}
