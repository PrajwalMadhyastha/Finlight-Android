package io.pm.finlight.ui.screens.sms

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NewLabel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import io.pm.finlight.Category
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.CategoryIconDisplay
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.InlineTextToolbarActionBar
import io.pm.finlight.ui.components.TransactionTypeToggle
import io.pm.finlight.ui.components.pickers.CategoryPickerBottomSheet
import io.pm.finlight.ui.components.pickers.TagPickerBottomSheet
import io.pm.finlight.ui.components.rememberInlineTextToolbar
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.utils.CurrencyHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat

private sealed class ApproveSheetContent {
    object Category : ApproveSheetContent()

    object Tags : ApproveSheetContent()

    object Description : ApproveSheetContent()
}

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApproveTransactionScreen(
    navController: NavController,
    transactionViewModel: TransactionViewModel,
    settingsViewModel: SettingsViewModel,
    potentialTxn: PotentialTransaction,
) {
    var description by remember {
        mutableStateOf(TextFieldValue(potentialTxn.merchantName ?: "", TextRange((potentialTxn.merchantName ?: "").length)))
    }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var selectedTransactionType by remember(potentialTxn.transactionType) {
        mutableStateOf(TransactionType.fromStringOrNull(potentialTxn.transactionType) ?: TransactionType.EXPENSE)
    }
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

    val isSaveEnabled = description.text.isNotBlank() && selectedCategory != null

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GlassPanel {
                    Column(
                        Modifier
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = description.text.ifBlank { "Description" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { activeSheetContent = ApproveSheetContent.Description },
                        )
                        Text(
                            "$currencySymbol${"%,.2f".format(potentialTxn.amount)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val currentTravelSettings = travelModeSettings
                        if (isForeign && currentTravelSettings != null) {
                            val convertedAmount = potentialTxn.amount * (currentTravelSettings.conversionRate?.toDouble() ?: 1.0)
                            Text(
                                "≈ $homeCurrencySymbol${NumberFormat.getInstance().format(convertedAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                TransactionTypeToggle(
                    selectedType = selectedTransactionType,
                    onTypeSelected = { selectedTransactionType = it },
                )
            }

            item {
                GlassPanel {
                    Column {
                        DetailRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Account",
                            value = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account",
                            onClick = null,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        DetailRow(
                            icon = Icons.Default.Category,
                            label = "Category",
                            value = selectedCategory?.name ?: "Select category",
                            onClick = { activeSheetContent = ApproveSheetContent.Category },
                            valueColor = if (selectedCategory == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            leadingIcon = { selectedCategory?.let { CategoryIconDisplay(it, Modifier.size(24.dp)) } },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        DetailRow(
                            icon = Icons.Default.NewLabel,
                            label = "Tags",
                            value = if (selectedTags.isEmpty()) "Add tags" else selectedTags.joinToString { it.name },
                            onClick = { activeSheetContent = ApproveSheetContent.Tags },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Add notes...") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = "Notes",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                    }
                }
            }
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val success =
                                    transactionViewModel.approveSmsTransaction(
                                        potentialTxn = potentialTxn.copy(transactionType = selectedTransactionType.dbValue),
                                        description = description.text,
                                        categoryId = selectedCategory?.id,
                                        notes = notes.text.takeIf { it.isNotBlank() },
                                        tags = selectedTags,
                                        isForeign = isForeign,
                                    )
                                if (success) {
                                    settingsViewModel.onTransactionApproved(potentialTxn.sourceSmsId)
                                    val merchantName = potentialTxn.merchantName
                                    if (merchantName != null) {
                                        settingsViewModel.saveMerchantRenameRule(merchantName, description.text)
                                    }

                                    if (merchantName != null && selectedCategory != null) {
                                        val mapping =
                                            MerchantCategoryMapping(
                                                parsedName = merchantName,
                                                categoryId = selectedCategory!!.id,
                                            )
                                        val autoImportedCount = settingsViewModel.applyLearningAndAutoImport(mapping)
                                        if (autoImportedCount > 0) {
                                            Toast.makeText(
                                                context,
                                                "Auto-saved $autoImportedCount similar transaction(s)!",
                                                Toast.LENGTH_LONG,
                                            ).show()
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
            containerColor = popupContainerColor,
        ) {
            when (activeSheetContent) {
                is ApproveSheetContent.Category ->
                    CategoryPickerBottomSheet(
                        categories = categories,
                        selectedCategoryId = selectedCategory?.id,
                        onCategorySelected = {
                            selectedCategory = it
                            activeSheetContent = null
                        },
                        onDismiss = { activeSheetContent = null },
                    )
                is ApproveSheetContent.Tags ->
                    TagPickerBottomSheet(
                        allTags = allTags,
                        selectedTags = selectedTags,
                        onTagSelected = transactionViewModel::onTagSelected,
                        onAddNewTag = transactionViewModel::addTagOnTheGo,
                        onConfirm = { activeSheetContent = null },
                        onDismiss = { activeSheetContent = null },
                    )
                is ApproveSheetContent.Description -> {
                    var tempDescription by remember {
                        mutableStateOf(TextFieldValue(description.text, TextRange(description.text.length)))
                    }
                    val inlineToolbar = rememberInlineTextToolbar()
                    CompositionLocalProvider(LocalTextToolbar provides inlineToolbar) {
                        Column(
                            modifier =
                                Modifier
                                    .padding(16.dp)
                                    .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Edit Description", style = MaterialTheme.typography.titleLarge)
                            InlineTextToolbarActionBar(inlineToolbar)
                            OutlinedTextField(
                                value = tempDescription,
                                onValueChange = { tempDescription = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
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
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
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
