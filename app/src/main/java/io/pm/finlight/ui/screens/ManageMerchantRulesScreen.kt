package io.pm.finlight.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
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
import io.pm.finlight.ui.components.ConfirmationDialog
import io.pm.finlight.ui.components.EmptyStateMessage
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageMerchantRulesScreen(
    navController: NavController,
    viewModel: ManageMerchantRulesViewModel,
) {
    val filteredRules by viewModel.filteredRules.collectAsState()
    val allRules by viewModel.allRules.collectAsState()
    val totalCount by viewModel.totalRulesCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var ruleToDelete by remember { mutableStateOf<MerchantRenameRule?>(null) }
    var ruleToEdit by remember { mutableStateOf<MerchantRenameRule?>(null) }
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

        // --- 3. Stats / Summary Header ---
        if (allRules.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (searchQuery.isBlank()) {
                            "$totalCount active rename rules"
                        } else {
                            "Showing ${filteredRules.size} of $totalCount rules"
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
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
        } else if (filteredRules.isEmpty()) {
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = filteredRules,
                    key = { it.originalName.lowercase() },
                ) { rule ->
                    MerchantRuleItemCard(
                        modifier = Modifier.animateItemPlacement(),
                        rule = rule,
                        onEditClick = { ruleToEdit = rule },
                        onDeleteClick = { ruleToDelete = rule },
                    )
                }
            }
        }
    }

    if (ruleToDelete != null) {
        ConfirmationDialog(
            title = "Delete Rename Rule?",
            text = "Are you sure you want to delete the rename rule for \"${ruleToDelete!!.originalName}\" → \"${ruleToDelete!!.newName}\"?",
            confirmButtonText = "Delete",
            isDestructive = true,
            onDismiss = { ruleToDelete = null },
            onConfirm = {
                viewModel.deleteRule(ruleToDelete!!)
                ruleToDelete = null
            },
        )
    }

    if (ruleToEdit != null) {
        EditMerchantRuleDialog(
            rule = ruleToEdit!!,
            onDismiss = { ruleToEdit = null },
            onConfirm = { newName ->
                viewModel.updateRule(ruleToEdit!!.originalName, newName)
                ruleToEdit = null
            },
        )
    }

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
    rule: MerchantRenameRule,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row with Raw SMS Name -> Arrow -> Canonical New Name
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            }

            // Actions: Edit and Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
    }
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
