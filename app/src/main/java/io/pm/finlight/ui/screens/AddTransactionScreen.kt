// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AddTransactionScreen.kt
// REASON: FIX - The `onConfirm` lambda in the `GlassmorphicNumpad` now correctly
// includes the `onSaveComplete` callback, passing the navigation action to the
// ViewModel. This resolves the bug where the UI would not navigate away after a
// successful auto-categorized save.
// =================================================================================
package io.pm.finlight.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
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

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("expense") }
    var notes by remember { mutableStateOf("") }
    var attachedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // --- NEW: State for guidance system ---
    var hasInteracted by remember { mutableStateOf(false) }
    val isAmountEntered = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
    val isDescriptionEntered = description.isNotBlank()


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


    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    val selectedDateTime by remember { mutableStateOf(Calendar.getInstance()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var activeSheet by remember { mutableStateOf<ComposerSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    // --- MODIFIED: Save is enabled with only Amount and Description ---
    val isSaveEnabled = isAmountEntered && isDescriptionEntered

    var isDefaultAccountApplied by remember { mutableStateOf(false) }
    LaunchedEffect(defaultAccount) {
        if (!isCsvEdit && !isDefaultAccountApplied && defaultAccount != null) {
            selectedAccount = defaultAccount
            isDefaultAccountApplied = true
        }
    }


    LaunchedEffect(Unit) {
        viewModel.clearAddTransactionState()
    }

    LaunchedEffect(initialDataJson, accounts, categories) {
        if (isCsvEdit && initialDataJson != null) {
            try {
                val gson = Gson()
                val typeToken = object : TypeToken<Map<String, String>>() {}.type
                val initialDataMap: Map<String, String> = gson.fromJson(URLDecoder.decode(initialDataJson, "UTF-8"), typeToken)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                initialDataMap["Date"]?.let {
                    try {
                        selectedDateTime.time = dateFormat.parse(it) ?: Date()
                    } catch (e: Exception) { /* Keep default date on parse error */
                    }
                }
                description = initialDataMap["Description"] ?: ""
                amount = (initialDataMap["Amount"] ?: "").replace(".0", "")
                transactionType = initialDataMap["Type"]?.lowercase() ?: "expense"
                val categoryName = initialDataMap["Category"] ?: ""
                val accountName = initialDataMap["Account"] ?: ""
                notes = initialDataMap["Notes"] ?: ""

                selectedCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                selectedAccount = accounts.find { it.name.equals(accountName, ignoreCase = true) }

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading row data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- NEW: Effect to trigger the Category Nudge bottom sheet ---
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.5f))
                AmountComposer(
                    amount = amount,
                    description = description,
                    onDescriptionClick = { activeSheet = ComposerSheet.Merchant },
                    isTravelMode = isTravelModeActive,
                    travelModeSettings = travelModeSettings,
                    // --- NEW: Pass guidance state ---
                    highlightDescription = hasInteracted && !isDescriptionEntered
                )
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
                    onCategoryClick = { activeSheet = ComposerSheet.Category },
                    onAccountClick = { activeSheet = ComposerSheet.Account },
                    onDateClick = { showDatePicker = true }
                )
                Spacer(Modifier.weight(1f))

                ActionRow(
                    notes = notes,
                    tags = selectedTags,
                    imageCount = attachedImageUris.size,
                    onNotesClick = { activeSheet = ComposerSheet.Notes },
                    onTagsClick = { activeSheet = ComposerSheet.Tags },
                    onAttachmentClick = { imagePickerLauncher.launch("image/*") }
                )

                // --- NEW: Guidance Checklist ---
                AnimatedVisibility(
                    visible = hasInteracted && !isSaveEnabled,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    ValidationChecklist(
                        isAmountEntered = isAmountEntered,
                        isDescriptionEntered = isDescriptionEntered
                    )
                }


                GlassmorphicNumpad(
                    onDigitClick = { digit ->
                        if (!hasInteracted) hasInteracted = true
                        if (amount.length < 9) amount += digit
                    },
                    onBackspaceClick = { if (amount.isNotEmpty()) amount = amount.dropLast(1) },
                    onConfirm = {
                        viewModel.onSaveTapped(
                            description = description,
                            amountStr = amount,
                            accountId = selectedAccount?.id,
                            notes = notes,
                            date = selectedDateTime.timeInMillis,
                            transactionType = transactionType,
                            imageUris = attachedImageUris,
                            onSaveComplete = { navController.popBackStack() }
                        )
                    },
                    isConfirmEnabled = isSaveEnabled
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = {
                // If this sheet was triggered by the nudge, complete the save flow on dismiss
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
                        selectedAccount = it
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
                                selectedCategory = it
                                // If this sheet was triggered by the nudge, complete the save flow
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
                    onConfirm = { activeSheet = null }
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
                    onPredictionSelected = { prediction ->
                        description = prediction.description
                        if (!hasInteracted) hasInteracted = true
                        prediction.categoryId?.let { catId ->
                            selectedCategory = categories.find { it.id == catId }
                        }
                        activeSheet = null
                    },
                    onManualSave = { newDescription ->
                        description = newDescription
                        if (!hasInteracted) hasInteracted = true
                        activeSheet = null
                    },
                    onDismiss = { activeSheet = null }
                )
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
                    selectedAccount = newAccount
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
                    selectedCategory = newCategory
                    // If this was triggered by the nudge, complete the save
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

// --- NEW: A modifier to draw a glowing highlight ---
fun Modifier.glow(color: Color, isEnabled: Boolean) = this.drawBehind {
    if (isEnabled) {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        val spread = 6.dp.toPx()
        frameworkPaint.color = color.toArgb()
        frameworkPaint.setShadowLayer(
            spread, 0f, 0f, color.copy(alpha = 0.5f).toArgb()
        )
        drawRoundRect(
            color = Color.Transparent, // The color is from the shadow layer
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        )
    }
}

// --- NEW: Checklist composable ---
@Composable
private fun ValidationChecklist(
    isAmountEntered: Boolean,
    isDescriptionEntered: Boolean
) {
    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChecklistItem(label = "Enter an amount", isChecked = isAmountEntered)
            ChecklistItem(label = "Add a description", isChecked = isDescriptionEntered)
        }
    }
}

@Composable
private fun ChecklistItem(label: String, isChecked: Boolean) {
    val color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = color)
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AmountComposer(
    amount: String,
    description: String,
    onDescriptionClick: () -> Unit,
    isTravelMode: Boolean,
    travelModeSettings: TravelModeSettings?,
    highlightDescription: Boolean
) {
    val currentTravelSettings = travelModeSettings
    val currencySymbol = if (isTravelMode && currentTravelSettings?.tripType == TripType.INTERNATIONAL) {
        CurrencyHelper.getCurrencySymbol(currentTravelSettings.currencyCode)
    } else {
        "₹"
    }
    val highlightColor = MaterialTheme.colorScheme.error

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = description.ifBlank { "Paid to..." },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDescriptionClick)
                .glow(highlightColor, highlightDescription)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currencySymbol,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Top)
            )
            Text(
                text = if (amount.isEmpty()) "0" else amount,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
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
        modifier = Modifier.clickable(onClick = onClick)
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

@Composable
private fun GlassmorphicNumpad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirm: () -> Unit,
    isConfirmEnabled: Boolean
) {
    val buttons = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        ".", "0"
    )

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            buttons.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { digit ->
                        NumpadButton(text = digit, modifier = Modifier.weight(1f)) {
                            onDigitClick(digit)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumpadButton(icon = Icons.AutoMirrored.Filled.Backspace, modifier = Modifier.weight(1f)) {
                    onBackspaceClick()
                }
                Button(
                    onClick = onConfirm,
                    enabled = isConfirmEnabled,
                    modifier = Modifier
                        .weight(2f)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Confirm", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun NumpadButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed) AuroraNumpadHighlight else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

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
            modifier = Modifier.padding(16.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 85.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts) { account ->
                AccountGridItem(
                    account = account,
                    onSelected = { onAccountSelected(account) }
                )
            }
            item {
                AddNewAccountGridItem(onAddNew = onAddNew)
            }
        }
    }
}

@Composable
private fun AccountGridItem(
    account: Account,
    onSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelected)
            .padding(vertical = 12.dp)
            .height(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = BankLogoHelper.getLogoForAccount(account.name)),
            contentDescription = "${account.name} Logo",
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            account.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AddNewAccountGridItem(onAddNew: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onAddNew)
            .padding(vertical = 12.dp)
            .height(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AddCircleOutline,
            contentDescription = "Create New Account",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "New",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(
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
            horizontalArrangement = Arrangement.End
        ) {
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
private fun TransactionTypeToggle(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    val glassFillColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.04f)
    }

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
        val expenseSelected = selectedType == "expense"
        val incomeSelected = selectedType == "income"

        Button(
            onClick = { onTypeSelected("expense") },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (expenseSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (expenseSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ),
            elevation = null
        ) {
            Text("Expense", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onTypeSelected("income") },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (incomeSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (incomeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ),
            elevation = null
        ) {
            Text("Income", fontWeight = FontWeight.Bold)
        }
    }
}