// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/SmsWorkflowScreens.kt
// REASON: FEATURE (Help System - Phase 3) - Wrapped the ReviewSmsScreen in a
// Scaffold and added a TopAppBar with a HelpActionIcon to provide users with
// contextual guidance on the SMS review process.
// FIX (UI) - Removed the local Scaffold and TopAppBar from ReviewSmsScreen.
// The main NavHost now provides a centralized TopAppBar, and this change
// removes the duplicate, resolving a UI bug.
// =================================================================================
package io.pm.finlight.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import com.google.gson.Gson
import io.pm.finlight.*
import io.pm.finlight.ui.components.CategorySelectionGrid
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.GlassPanelBorder
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.NumberFormat

private sealed class ApproveSheetContent {
    object Category : ApproveSheetContent()
    object Tags : ApproveSheetContent()
    object Description : ApproveSheetContent()
}

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSmsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val potentialTransactions by viewModel.potentialTransactions.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var hasLoadedOnce by remember { mutableStateOf(false) }

    val linkedSmsIdState = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<Long>("linked_sms_id")
        ?.observeAsState()
    val linkedSmsId = linkedSmsIdState?.value

    LaunchedEffect(linkedSmsId) {
        linkedSmsId?.let {
            viewModel.onTransactionLinked(it)
            navController.currentBackStackEntry?.savedStateHandle?.set("linked_sms_id", null)
        }
    }

    LaunchedEffect(isScanning, potentialTransactions) {
        if (!isScanning) {
            hasLoadedOnce = true
        }
        if (hasLoadedOnce && potentialTransactions.isEmpty()) {
            navController.popBackStack()
        }
    }

    if (isScanning && !hasLoadedOnce) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scanning for transactions...", style = MaterialTheme.typography.titleMedium)
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${potentialTransactions.size} potential transactions found.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(potentialTransactions, key = { it.sourceSmsId }) { pt ->
                PotentialTransactionItem(
                    transaction = pt,
                    onDismiss = { viewModel.dismissPotentialTransaction(it) },
                    onApprove = { transaction ->
                        val encodedPotentialTxn = URLEncoder.encode(Gson().toJson(transaction), "UTF-8")
                        val route = "approve_transaction_screen?potentialTxnJson=$encodedPotentialTxn"
                        navController.navigate(route)
                    },
                    onCreateRule = { transaction ->
                        val json = Gson().toJson(transaction)
                        val encodedJson = URLEncoder.encode(json, "UTF-8")
                        navController.navigate("rule_creation_screen?potentialTransactionJson=$encodedJson")
                    },
                    onLink = { transaction ->
                        val json = Gson().toJson(transaction)
                        val encodedJson = URLEncoder.encode(json, "UTF-8")
                        navController.navigate("link_transaction_screen/$encodedJson")
                    }
                )
            }
        }
    }
}

@Composable
fun PotentialTransactionItem(
    transaction: PotentialTransaction,
    onDismiss: (PotentialTransaction) -> Unit,
    onApprove: (PotentialTransaction) -> Unit,
    onCreateRule: (PotentialTransaction) -> Unit,
    onLink: (PotentialTransaction) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val amountColor = if (transaction.transactionType == "expense") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.merchantName ?: "Unknown Merchant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "₹${"%.2f".format(transaction.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            transaction.potentialAccount?.let {
                Text(
                    text = "Account: ${it.formattedName} (${it.accountType})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Type: ${transaction.transactionType.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Original Message: ${transaction.originalMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onLink(transaction) }) {
                    Text("Link to Existing")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onDismiss(transaction) }) { Text("Dismiss") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApprove(transaction) }) { Text("Approve") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApproveTransactionScreen(
    navController: NavController,
    transactionViewModel: TransactionViewModel,
    settingsViewModel: SettingsViewModel,
    potentialTxn: PotentialTransaction,
) {
    var description by remember { mutableStateOf(potentialTxn.merchantName ?: "") }
    var notes by remember { mutableStateOf("") }
    var selectedTransactionType by remember(potentialTxn.transactionType) { mutableStateOf(potentialTxn.transactionType) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val categories by transactionViewModel.allCategories.collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    val allTags by transactionViewModel.allTags.collectAsState()
    val selectedTags by transactionViewModel.selectedTags.collectAsState()

    var activeSheetContent by remember { mutableStateOf<ApproveSheetContent?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val travelModeSettings by transactionViewModel.travelModeSettings.collectAsState()
    val isForeign = potentialTxn.isForeignCurrency == true
    val currencySymbol = if (isForeign) CurrencyHelper.getCurrencySymbol(travelModeSettings?.currencyCode) else "₹"
    val homeCurrencySymbol = "₹"

    val isSaveEnabled = description.isNotBlank() && selectedCategory != null

    LaunchedEffect(key1 = potentialTxn.sourceSmsId) {
        NotificationManagerCompat.from(context).cancel(potentialTxn.sourceSmsId.toInt())
    }

    DisposableEffect(Unit) {
        onDispose {
            transactionViewModel.clearSelectedTags()
        }
    }

    LaunchedEffect(potentialTxn.categoryId, categories) {
        if (categories.isNotEmpty()) {
            potentialTxn.categoryId?.let { learnedCategoryId ->
                selectedCategory = categories.find { it.id == learnedCategoryId }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Approve Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GlassPanel {
                    Column(
                        Modifier
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = description.ifBlank { "Description" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { activeSheetContent = ApproveSheetContent.Description }
                        )
                        Text(
                            "$currencySymbol${"%,.2f".format(potentialTxn.amount)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val currentTravelSettings = travelModeSettings
                        if (isForeign && currentTravelSettings != null) {
                            val convertedAmount = potentialTxn.amount * (currentTravelSettings.conversionRate?.toDouble() ?: 1.0)
                            Text(
                                "≈ $homeCurrencySymbol${NumberFormat.getInstance().format(convertedAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                val glassFillColor = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(glassFillColor)
                        .border(1.dp, GlassPanelBorder, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { selectedTransactionType = "expense" },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTransactionType == "expense") MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selectedTransactionType == "expense") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = null
                    ) { Text("Expense", fontWeight = FontWeight.Bold) }

                    Button(
                        onClick = { selectedTransactionType = "income" },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTransactionType == "income") MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selectedTransactionType == "income") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = null
                    ) { Text("Income", fontWeight = FontWeight.Bold) }
                }
            }

            item {
                GlassPanel {
                    Column {
                        DetailRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Account",
                            value = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account",
                            onClick = null
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        DetailRow(
                            icon = Icons.Default.Category,
                            label = "Category",
                            value = selectedCategory?.name ?: "Select category",
                            onClick = { activeSheetContent = ApproveSheetContent.Category },
                            valueColor = if (selectedCategory == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            leadingIcon = { selectedCategory?.let { CategoryIcon(it, Modifier.size(24.dp)) } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        DetailRow(
                            icon = Icons.Default.NewLabel,
                            label = "Tags",
                            value = if (selectedTags.isEmpty()) "Add tags" else selectedTags.joinToString { it.name },
                            onClick = { activeSheetContent = ApproveSheetContent.Tags }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Add notes...") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Notes", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val success = transactionViewModel.approveSmsTransaction(
                                    potentialTxn = potentialTxn,
                                    description = description,
                                    categoryId = selectedCategory?.id,
                                    notes = notes.takeIf { it.isNotBlank() },
                                    tags = selectedTags,
                                    isForeign = isForeign
                                )
                                if (success) {
                                    settingsViewModel.onTransactionApproved(potentialTxn.sourceSmsId)
                                    val merchantName = potentialTxn.merchantName
                                    if (merchantName != null) {
                                        settingsViewModel.saveMerchantRenameRule(merchantName, description)
                                    }

                                    if (merchantName != null && selectedCategory != null) {
                                        val mapping = MerchantCategoryMapping(
                                            parsedName = merchantName,
                                            categoryId = selectedCategory!!.id
                                        )
                                        val autoImportedCount = settingsViewModel.applyLearningAndAutoImport(mapping)
                                        if (autoImportedCount > 0) {
                                            Toast.makeText(context, "Auto-saved $autoImportedCount similar transaction(s)!", Toast.LENGTH_LONG).show()
                                        }
                                    }

                                    navController.popBackStack()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isSaveEnabled,
                    ) { Text("Save Transaction") }
                }
            }
        }
    }

    if (activeSheetContent != null) {
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
        ModalBottomSheet(
            onDismissRequest = { activeSheetContent = null },
            sheetState = sheetState,
            windowInsets = WindowInsets(0),
            containerColor = popupContainerColor
        ) {
            when (activeSheetContent) {
                is ApproveSheetContent.Category -> ApproveCategoryPickerSheet(
                    items = categories,
                    onItemSelected = { selectedCategory = it; activeSheetContent = null }
                )
                is ApproveSheetContent.Tags -> ApproveTagPickerSheet(
                    allTags = allTags,
                    selectedTags = selectedTags,
                    onTagSelected = transactionViewModel::onTagSelected,
                    onAddNewTag = transactionViewModel::addTagOnTheGo,
                    onConfirm = { activeSheetContent = null }
                )
                is ApproveSheetContent.Description -> {
                    var tempDescription by remember { mutableStateOf(description) }
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Edit Description", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = tempDescription,
                            onValueChange = { tempDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { activeSheetContent = null }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                description = tempDescription
                                activeSheetContent = null
                            }) { Text("Done") }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        } else {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ApproveCategoryPickerSheet(
    items: List<Category>,
    onItemSelected: (Category) -> Unit
) {
    Column(modifier = Modifier
        .navigationBarsPadding()
        .fillMaxHeight()) {
        Text(
            "Select Category",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        CategorySelectionGrid(
            categories = items,
            onCategorySelected = onItemSelected
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApproveTagPickerSheet(
    allTags: List<Tag>,
    selectedTags: Set<Tag>,
    onTagSelected: (Tag) -> Unit,
    onAddNewTag: (String) -> Unit,
    onConfirm: () -> Unit
) {
    var newTagName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Manage Tags", style = MaterialTheme.typography.titleLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            allTags.forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = { onTagSelected(tag) },
                    label = { Text(tag.name) }
                )
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                label = { Text("New Tag Name") },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    onAddNewTag(newTagName)
                    newTagName = ""
                },
                enabled = newTagName.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Tag")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onConfirm) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newTagName.isNotBlank()) {
                    onAddNewTag(newTagName)
                }
                onConfirm()
            }) { Text("Done") }
        }
    }
}

@Composable
private fun CategoryIcon(category: Category, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(CategoryIconHelper.getIconBackgroundColor(category.colorKey)),
        contentAlignment = Alignment.Center
    ) {
        if (category.iconKey == "letter_default") {
            Text(
                text = category.name.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                fontSize = 22.sp
            )
        } else {
            Icon(
                imageVector = CategoryIconHelper.getIcon(category.iconKey),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}