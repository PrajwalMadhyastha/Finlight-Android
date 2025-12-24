// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AddTransactionScreen.kt
// REASON: FEATURE (Quick Fill) - Integrated "Quick Fill" functionality.
// 1. Added `QuickFillCarousel` to display recent manual transactions when description is empty.
// 2. Added `TransactionHistorySheet` to allow searching and selecting older manual entries.
// 3. Observed `recentManualTransactions` and `historyManualTransactions` from ViewModel.
// 4. Hooked up selection logic to populate the form.
// 5. FIXED: Paste functionality by replacing description `Text` with `BasicTextField`.
// =================================================================================
package io.pm.finlight.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.*
import io.pm.finlight.ui.components.*
import io.pm.finlight.ui.theme.AuroraNumpadHighlight
import io.pm.finlight.ui.theme.GlassPanelBorder
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.BankLogoHelper
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private sealed class ComposerSheet {
    object Category : ComposerSheet()
    object Account : ComposerSheet()
    object Tags : ComposerSheet()
    object Notes : ComposerSheet()
    object Merchant : ComposerSheet()
    object History : ComposerSheet() // --- NEW: History Sheet
}

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: TransactionViewModel,
    isCsvEdit: Boolean = false,
    initialDataJson: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State observed from ViewModel ---
    val description by viewModel.addTransactionDescription.collectAsState()
    val amount by viewModel.addTransactionAmount.collectAsState()
    val selectedCategory by viewModel.addTransactionCategory.collectAsState()
    val selectedAccount by viewModel.addTransactionAccount.collectAsState()

    // --- Local State for UI Logic ---
    var transactionType by remember { mutableStateOf("expense") }
    var notes by remember { mutableStateOf("") }
    var attachedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var hasInteracted by remember { mutableStateOf(false) }
    val isAmountEntered by remember(amount) { derivedStateOf { (amount.toDoubleOrNull() ?: 0.0) > 0.0 } }
    val isDescriptionEntered by remember(description) { derivedStateOf { description.isNotBlank() } }


    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        attachedImageUris = attachedImageUris + uris
    }

    val accounts by viewModel.allAccounts.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val travelModeSettings by viewModel.travelModeSettings.collectAsState()
    val categoryNudgeData by viewModel.showCategoryNudge.collectAsState()
    val suggestedCategory by viewModel.suggestedCategory.collectAsState()

    // --- NEW: Observe Quick Fill data ---
    val recentManualTransactions by viewModel.recentManualTransactions.collectAsState()
    val historyManualTransactions by viewModel.historyManualTransactions.collectAsState()

    val selectedDateTime by remember { mutableStateOf(Calendar.getInstance()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var activeSheet by remember { mutableStateOf<ComposerSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    val isSaveEnabled = isAmountEntered

    var isDefaultAccountApplied by remember { mutableStateOf(false) }

    // This effect handles ALL initial data loading
    LaunchedEffect(initialDataJson, accounts, categories, defaultAccount, isCsvEdit) {
        if (isDefaultAccountApplied) return@LaunchedEffect // Only run once

        if (isCsvEdit && initialDataJson != null) {
            // --- CSV EDITING LOGIC ---
            try {
                val gson = Gson()
                val typeToken = object : TypeToken<Map<String, String>>() {}.type
                val initialDataMap: Map<String, String> = gson.fromJson(URLDecoder.decode(initialDataJson, "UTF-8"), typeToken)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                initialDataMap["Date"]?.let {
                    try {
                        selectedDateTime.time = dateFormat.parse(it) ?: Date()
                    } catch (e: Exception) { /* Keep default date on parse error */ }
                }
                viewModel.onAddTransactionDescriptionChanged(initialDataMap["Description"] ?: "")
                viewModel.onAddTransactionAmountChanged((initialDataMap["Amount"] ?: "").replace(".0", ""))
                transactionType = initialDataMap["Type"]?.lowercase() ?: "expense"
                val categoryName = initialDataMap["Category"] ?: ""
                val accountName = initialDataMap["Account"] ?: ""
                notes = initialDataMap["Notes"] ?: ""

                viewModel.onAddTransactionCategoryChanged(categories.find { it.name.equals(categoryName, ignoreCase = true) })

                // Try to find the account from the CSV
                val csvAccount = accounts.find { it.name.equals(accountName, ignoreCase = true) }
                if (csvAccount != null) {
                    viewModel.onAddTransactionAccountChanged(csvAccount)
                } else {
                    // Fallback to default "Cash Spends"
                    viewModel.onAddTransactionAccountChanged(defaultAccount ?: accounts.find { it.name.equals("Cash Spends", ignoreCase = true) })
                }

                isDefaultAccountApplied = true // Mark as applied

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading row data", Toast.LENGTH_SHORT).show()
            }
        } else if (!isCsvEdit) {
            // --- NEW TRANSACTION LOGIC ---
            if (defaultAccount != null) {
                viewModel.onAddTransactionAccountChanged(defaultAccount)
                isDefaultAccountApplied = true
            } else if (accounts.isNotEmpty()) {
                // Fallback in case the VM's flow is slow
                val cashAccount = accounts.find { it.name.equals("Cash Spends", ignoreCase = true) }
                if (cashAccount != null) {
                    viewModel.onAddTransactionAccountChanged(cashAccount)
                    isDefaultAccountApplied = true
                }
            }
        }
    }

    LaunchedEffect(suggestedCategory) {
        suggestedCategory?.let {
            viewModel.onAddTransactionCategoryChanged(it)
        }
    }


    LaunchedEffect(Unit) {
        viewModel.clearAddTransactionState()
    }

    LaunchedEffect(categoryNudgeData) {
        if (categoryNudgeData != null) {
            activeSheet = ComposerSheet.Category
        }
    }

    LaunchedEffect(validationError) {
        validationError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val categoryColor by remember(selectedCategory) {
        derivedStateOf {
            selectedCategory?.let { CategoryIconHelper.getIconBackgroundColor(it.colorKey) }
        }
    }

    val animatedCategoryColor by animateColorAsState(
        targetValue = categoryColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 500),
        label = "CategoryColorAnimation"
    )

    val isTravelModeActive = remember(travelModeSettings, selectedDateTime) {
        travelModeSettings?.let {
            it.isEnabled &&
                    selectedDateTime.timeInMillis >= it.startDate &&
                    selectedDateTime.timeInMillis <= it.endDate
        } ?: false
    }

    // --- Focus Requester for Amount Field ---
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(300) // Give UI time to draw
        focusRequester.requestFocus()
    }


    Box(modifier = Modifier.fillMaxSize()) {
        SpotlightBackground(color = animatedCategoryColor)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Compose Transaction") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        HelpActionIcon(helpKey = "add_transaction")
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()), // Screen is scrollable
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                AmountComposer(
                    amount = amount,
                    onAmountChange = { newValue ->
                        if (!hasInteracted) hasInteracted = true
                        if (newValue.length > 9) return@AmountComposer

                        // Regex to match a valid decimal number (up to 2 decimal places)
                        val regex = Regex("^\\d*\\.?\\d{0,2}\$")
                        if (newValue.isEmpty() || regex.matches(newValue)) {
                            viewModel.onAddTransactionAmountChanged(newValue)
                        }
                    },
                    focusRequester = focusRequester,
                    description = description,
                    onDescriptionChange = { viewModel.onAddTransactionDescriptionChanged(it) },
                    onDescriptionClick = { activeSheet = ComposerSheet.Merchant },
                    isTravelMode = isTravelModeActive,
                    travelModeSettings = travelModeSettings,
                    highlightDescription = hasInteracted && !isDescriptionEntered,
                    isDescriptionEntered = isDescriptionEntered,
                    hasInteractedWithNumpad = hasInteracted
                )

                // --- NEW: Quick Fill Carousel ---
                // Only show if description is empty and we have suggestions
                if (!isDescriptionEntered && recentManualTransactions.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    QuickFillCarousel(
                        recentTransactions = recentManualTransactions,
                        onQuickFillSelected = { txn ->
                            viewModel.onQuickFillSelected(txn)
                            // We might also want to set transaction type if available
                            transactionType = txn.transaction.transactionType
                        },
                        onViewAllClick = { activeSheet = ComposerSheet.History }
                    )
                }

                Spacer(Modifier.height(24.dp))

                TransactionTypeToggle(
                    selectedType = transactionType,
                    onTypeSelected = { transactionType = it }
                )

                Spacer(Modifier.height(24.dp))
                OrbitalChips(
                    selectedCategory = selectedCategory,
                    selectedAccount = selectedAccount,
                    selectedDateTime = selectedDateTime.time,
                    onCategoryClick = {
                        viewModel.onUserManuallySelectedCategory() // User is taking control
                        activeSheet = ComposerSheet.Category
                    },
                    onAccountClick = { activeSheet = ComposerSheet.Account },
                    onDateClick = { showDatePicker = true }
                )
                Spacer(Modifier.height(24.dp))
                ActionRow(
                    notes = notes,
                    tags = selectedTags,
                    imageCount = attachedImageUris.size,
                    onNotesClick = { activeSheet = ComposerSheet.Notes },
                    onTagsClick = { activeSheet = ComposerSheet.Tags },
                    onAttachmentClick = { imagePickerLauncher.launch("image/*") }
                )
                Spacer(Modifier.height(16.dp))


                // --- Save Button ---
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        viewModel.onSaveTapped(
                            description = description,
                            amountStr = amount,
                            accountId = selectedAccount?.id,
                            categoryId = selectedCategory?.id,
                            notes = notes,
                            date = selectedDateTime.timeInMillis,
                            transactionType = transactionType,
                            imageUris = attachedImageUris,
                            onSaveComplete = { navController.popBackStack() }
                        )
                    },
                    enabled = isSaveEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Save Transaction", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp)) // Padding at the bottom
            }
        }
    }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = {
                if (categoryNudgeData != null) {
                    viewModel.saveWithSelectedCategory(null) {
                        navController.popBackStack()
                    }
                }
                activeSheet = null
            },
            sheetState = sheetState,
            windowInsets = WindowInsets(0),
            containerColor = popupContainerColor
        ) {
            when (activeSheet) {
                is ComposerSheet.Account -> AccountPickerSheet(
                    accounts = accounts,
                    onAccountSelected = {
                        viewModel.onAddTransactionAccountChanged(it)
                        activeSheet = null
                    },
                    onAddNew = {
                        showCreateAccountDialog = true
                        activeSheet = null
                    }
                )
                is ComposerSheet.Category -> {
                    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight()) {
                        Text(
                            "Select Category",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        CategorySelectionGrid(
                            categories = categories,
                            onCategorySelected = {
                                viewModel.onAddTransactionCategoryChanged(it)
                                if (categoryNudgeData != null) {
                                    viewModel.saveWithSelectedCategory(it.id) {
                                        navController.popBackStack()
                                    }
                                }
                                activeSheet = null
                            },
                            onAddNew = {
                                showCreateCategoryDialog = true
                                activeSheet = null
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
                is ComposerSheet.Tags -> TagPickerSheet(
                    allTags = allTags,
                    selectedTags = selectedTags,
                    onTagSelected = viewModel::onTagSelected,
                    onAddNewTag = viewModel::addTagOnTheGo,
                    onConfirm = { activeSheet = null },
                    onDismiss = { activeSheet = null }
                )
                is ComposerSheet.Notes -> TextInputSheet(
                    title = "Add Notes",
                    initialValue = notes,
                    onConfirm = {
                        notes = it
                        activeSheet = null
                    }
                )
                is ComposerSheet.Merchant -> MerchantPredictionSheet(
                    viewModel = viewModel,
                    initialDescription = description,
                    onQueryChanged = {
                        viewModel.onAddTransactionDescriptionChanged(it)
                    },
                    onPredictionSelected = { prediction ->
                        viewModel.onAddTransactionDescriptionChanged(prediction.description)
                        if (!hasInteracted) hasInteracted = true
                        prediction.categoryId?.let { catId ->
                            val cat = categories.find { it.id == catId }
                            viewModel.onAddTransactionCategoryChanged(cat)
                        }
                        activeSheet = null
                    },
                    onManualSave = { newDescription ->
                        viewModel.onAddTransactionDescriptionChanged(newDescription)
                        if (!hasInteracted) hasInteracted = true
                        activeSheet = null
                    },
                    onDismiss = { activeSheet = null }
                )
                // --- NEW: History Sheet Content ---
                is ComposerSheet.History -> {
                    TransactionHistorySheet(
                        transactions = historyManualTransactions,
                        onTransactionSelected = { txn ->
                            viewModel.onQuickFillSelected(txn)
                            transactionType = txn.transaction.transactionType
                            activeSheet = null
                        }
                    )
                }
                null -> {}
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTime.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance().apply { timeInMillis = it }
                        selectedDateTime.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                        selectedDateTime.set(Calendar.MONTH, cal.get(Calendar.MONTH))
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor)
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.get(Calendar.HOUR_OF_DAY),
            initialMinute = selectedDateTime.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = popupContainerColor,
            title = { Text("Select Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    selectedDateTime.set(Calendar.MINUTE, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }

    if (showCreateAccountDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateAccountDialog = false },
            onConfirm = { name, type ->
                viewModel.createAccount(name, type) { newAccount ->
                    viewModel.onAddTransactionAccountChanged(newAccount)
                }
                showCreateAccountDialog = false
            }
        )
    }

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialog = false },
            onConfirm = { name, iconKey, colorKey ->
                viewModel.createCategory(name, iconKey, colorKey) { newCategory ->
                    viewModel.onAddTransactionCategoryChanged(newCategory)
                    if (categoryNudgeData != null) {
                        viewModel.saveWithSelectedCategory(newCategory.id) {
                            navController.popBackStack()
                        }
                    }
                }
                showCreateCategoryDialog = false
            }
        )
    }
}

// --- NEW: Quick Fill Carousel Composable ---
@Composable
fun QuickFillCarousel(
    recentTransactions: List<TransactionDetails>,
    onQuickFillSelected: (TransactionDetails) -> Unit,
    onViewAllClick: () -> Unit
) {
    Column {
        Text(
            text = "Quick Fill from Recent",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(recentTransactions) { details ->
                QuickFillChip(
                    details = details,
                    onClick = { onQuickFillSelected(details) }
                )
            }
            item {
                GlassPanel(
                    modifier = Modifier
                        .height(48.dp) // Match height of chips
                        .clickable(onClick = onViewAllClick),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "View All",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "View All",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickFillChip(
    details: TransactionDetails,
    onClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    GlassPanel(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = CategoryIconHelper.getIcon(details.categoryIconKey ?: "category")
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = details.transaction.description,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currencyFormat.format(details.transaction.amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- NEW: History Sheet Composable ---
@Composable
fun TransactionHistorySheet(
    transactions: List<TransactionDetails>,
    onTransactionSelected: (TransactionDetails) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredTransactions = remember(searchQuery, transactions) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.transaction.description.contains(searchQuery, ignoreCase = true) ||
                    (it.categoryName?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(0.9f)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            "Transaction History",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search History") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredTransactions) { details ->
                TransactionItem(
                    transactionDetails = details,
                    onClick = { onTransactionSelected(details) },
                    onCategoryClick = { /* No-op in this context */ }
                )
            }
            if (filteredTransactions.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    Text(
                        "No matches found.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightBackground(color: Color) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (color == Color.Transparent) 0f else 0.3f,
        animationSpec = tween(500),
        label = "SpotlightAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas {
            val paint = Paint().asFrameworkPaint()
            val radius = size.width * 1.2f
            paint.color = android.graphics.Color.TRANSPARENT
            paint.setShadowLayer(
                radius,
                0f,
                0f,
                color
                    .copy(alpha = animatedAlpha)
                    .toArgb()
            )
            it.nativeCanvas.drawCircle(center.x, center.y, radius / 2, paint)
        }
    }
}

@Composable
private fun AmountComposer(
    amount: String,
    onAmountChange: (String) -> Unit,
    focusRequester: FocusRequester,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onDescriptionClick: () -> Unit,
    isTravelMode: Boolean,
    travelModeSettings: TravelModeSettings?,
    highlightDescription: Boolean,
    isDescriptionEntered: Boolean,
    hasInteractedWithNumpad: Boolean
) {
    val currentTravelSettings = travelModeSettings
    val currencySymbol = if (isTravelMode && currentTravelSettings?.tripType == TripType.INTERNATIONAL) {
        CurrencyHelper.getCurrencySymbol(currentTravelSettings.currencyCode)
    } else {
        "₹"
    }
    val highlightColor = MaterialTheme.colorScheme.primary

    val animatedBorderColor by animateColorAsState(
        targetValue = if (highlightDescription) highlightColor else Color.Transparent,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "HighlightBorderAnimation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth() // This is OK
    ) {
        BasicTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, animatedBorderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (description.isEmpty()) {
                            Text(
                                text = if (hasInteractedWithNumpad) "What did you spend on?" else "Paid to...",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDescriptionClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Predictions",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // --- REFACTORED SECTION ---
        val textStyle = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center // <-- FIX 1: Set to Center
        )

        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier
                .fillMaxWidth() // <-- FIX 2: Add fillMaxWidth
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = textStyle, // <-- Pass Center-aligned style
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(), // <-- FIX 3: Add fillMaxWidth
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center // <-- FIX 4: Add Arrangement.Center
                ) {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Box(contentAlignment = Alignment.Center) { // <-- FIX 5: Change to Alignment.Center
                        if (amount.isEmpty()) {
                            Text(
                                "0",
                                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
        // --- END REFACTORED SECTION ---

        if (isTravelMode && currentTravelSettings?.tripType == TripType.INTERNATIONAL) {
            val enteredAmount = amount.toDoubleOrNull() ?: 0.0
            val convertedAmount = enteredAmount * (currentTravelSettings.conversionRate?.toDouble() ?: 1.0)
            val homeSymbol = "₹"
            Text(
                text = "≈ $homeSymbol${NumberFormat.getInstance().format(convertedAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun OrbitalChips(
    selectedCategory: Category?,
    selectedAccount: Account?,
    selectedDateTime: Date,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onDateClick: () -> Unit
) {
    // --- REFACTORED: Changed from Row to Column ---
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailChip(
            icon = selectedCategory?.let { CategoryIconHelper.getIcon(it.iconKey) } ?: Icons.Default.Category,
            text = selectedCategory?.name ?: "Category",
            onClick = onCategoryClick
        )
        DetailChip(
            icon = Icons.Default.AccountBalanceWallet,
            text = selectedAccount?.name ?: "Account",
            onClick = onAccountClick
        )
        DetailChip(
            icon = Icons.Default.CalendarToday,
            text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(selectedDateTime),
            onClick = onDateClick
        )
    }
}

@Composable
private fun DetailChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth(0.8f) // Take up 80% of width to look substantial
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionRow(
    notes: String,
    tags: Set<Tag>,
    imageCount: Int,
    onNotesClick: () -> Unit,
    onTagsClick: () -> Unit,
    onAttachmentClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
    ) {
        ActionIcon(
            icon = Icons.AutoMirrored.Filled.Notes,
            text = "Notes",
            isHighlighted = notes.isNotBlank(),
            onClick = onNotesClick
        )
        ActionIcon(
            icon = Icons.Default.NewLabel,
            text = "Tags",
            isHighlighted = tags.isNotEmpty(),
            onClick = onTagsClick
        )
        ActionIcon(
            icon = Icons.Default.Attachment,
            text = "Attach",
            isHighlighted = imageCount > 0,
            badgeCount = imageCount,
            onClick = onAttachmentClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIcon(
    icon: ImageVector,
    text: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        BadgedBox(badge = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Text("$badgeCount") }
            }
        }) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

// --- REFACTORED: `AccountPickerSheet` now uses a LazyColumn and ListItems ---
@Composable
private fun AccountPickerSheet(
    accounts: List<Account>,
    onAccountSelected: (Account) -> Unit,
    onAddNew: () -> Unit
) {
    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight()) {
        Text(
            "Select Account",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts) { account ->
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                account.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Image(
                                painter = painterResource(id = BankLogoHelper.getLogoForAccount(account.name)),
                                contentDescription = "${account.name} Logo",
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        modifier = Modifier.clickable { onAccountSelected(account) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Create New Account",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = "Create New Account",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { onAddNew() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(
    allTags: List<Tag>,
    selectedTags: Set<Tag>,
    onTagSelected: (Tag) -> Unit,
    onAddNewTag: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
        Text("Manage Tags", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
            IconButton(
                onClick = {
                    onAddNewTag(newTagName)
                    newTagName = ""
                },
                enabled = newTagName.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Tag", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newTagName.isNotBlank()) {
                    onAddNewTag(newTagName)
                }
                onConfirm()
            }) { Text("Save") }
        }
    }
}

@Composable
fun TextInputSheet(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Value") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onConfirm(initialValue) }) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onConfirm(text) }) { Text("Done") }
        }
    }
}

@Composable
fun TransactionTypeToggle(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val glassFillColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.04f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(glassFillColor)
            .border(1.dp, GlassPanelBorder, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val expenseSelected = selectedType == "expense"
        val incomeSelected = selectedType == "income"

        Button(
            onClick = { onTypeSelected("expense") },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (expenseSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (expenseSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = if (expenseSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent,
                disabledContentColor = if (expenseSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            elevation = null
        ) {
            Text("Expense", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onTypeSelected("income") },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (incomeSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (incomeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = if (incomeSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent,
                disabledContentColor = if (incomeSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            elevation = null
        ) {
            Text("Income", fontWeight = FontWeight.Bold)
        }
    }
}
