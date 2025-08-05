// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/AddTransactionScreen.kt
// REASON: UX REFINEMENT - The description/merchant field is now editable in-place.
// Tapping the description text now transforms it into a TextField directly on
// the screen, instead of opening a separate bottom sheet, creating a more fluid
// and intuitive editing experience.
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.*
import io.pm.finlight.ui.components.*
import io.pm.finlight.ui.theme.AuroraNumpadHighlight
import io.pm.finlight.ui.theme.GlassPanelBorder
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: TransactionViewModel,
    isCsvEdit: Boolean = false,
    initialDataJson: String? = null
) {
    // region State Variables
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("expense") } // Default to expense
    var notes by remember { mutableStateOf("") }
    var attachedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

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

    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    val selectedDateTime by remember { mutableStateOf(Calendar.getInstance()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var activeSheet by remember { mutableStateOf<ComposerSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    // --- NEW: State to manage in-place editing of the description ---
    var isEditingDescription by remember { mutableStateOf(false) }

    val isSaveEnabled = amount.isNotBlank() && description.isNotBlank() && selectedAccount != null && selectedCategory != null
    // endregion


    LaunchedEffect(Unit) {
        viewModel.clearAddTransactionState()
    }

    LaunchedEffect(defaultAccount) {
        if (!isCsvEdit && selectedAccount == null) {
            selectedAccount = defaultAccount
        }
    }

    LaunchedEffect(initialDataJson, accounts, categories) {
        if (isCsvEdit && initialDataJson != null) {
            try {
                val gson = Gson()
                val initialData: List<String> = gson.fromJson(URLDecoder.decode(initialDataJson, "UTF-8"), object : TypeToken<List<String>>() {}.type)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                initialData.getOrNull(0)?.let {
                    try {
                        selectedDateTime.time = dateFormat.parse(it) ?: Date()
                    } catch (e: Exception) { /* Keep default date on parse error */ }
                }
                description = initialData.getOrElse(1) { "" }
                amount = initialData.getOrElse(2) { "" }.replace(".0", "")
                transactionType = initialData.getOrElse(3) { "expense" }
                val categoryName = initialData.getOrElse(4) { "" }
                val accountName = initialData.getOrElse(5) { "" }
                notes = initialData.getOrElse(6) { "" }

                selectedCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                selectedAccount = accounts.find { it.name.equals(accountName, ignoreCase = true) }

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading row data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(validationError) {
        validationError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    // endregion

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
                    onDescriptionChange = { description = it },
                    isEditingDescription = isEditingDescription,
                    onEditModeToggle = { isEditingDescription = it },
                    isTravelMode = isTravelModeActive,
                    travelModeSettings = travelModeSettings
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

                GlassmorphicNumpad(
                    onDigitClick = { digit -> if (amount.length < 9) amount += digit },
                    onBackspaceClick = { if (amount.isNotEmpty()) amount = amount.dropLast(1) },
                    onConfirm = {
                        scope.launch {
                            val success = viewModel.addTransaction(
                                description = description,
                                categoryId = selectedCategory?.id,
                                amountStr = amount,
                                accountId = selectedAccount!!.id,
                                notes = notes.takeIf { it.isNotBlank() },
                                date = selectedDateTime.timeInMillis,
                                transactionType = transactionType,
                                imageUris = attachedImageUris
                            )
                            if (success) {
                                navController.popBackStack()
                            }
                        }
                    },
                    isConfirmEnabled = isSaveEnabled
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // region Modals and Dialogs
    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            windowInsets = WindowInsets(0),
            containerColor = if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight
        ) {
            when (val sheet = activeSheet) {
                is ComposerSheet.Account -> PickerSheet(
                    title = "Select Account",
                    items = accounts,
                    onItemSelected = { selectedAccount = it; activeSheet = null },
                    onAddNew = { showCreateAccountDialog = true; activeSheet = null },
                    itemContent = { account ->
                        Text(account.name, color = MaterialTheme.colorScheme.onSurface)
                    }
                )
                is ComposerSheet.Category -> PickerSheet(
                    title = "Select Category",
                    items = categories,
                    onItemSelected = { selectedCategory = it; activeSheet = null },
                    onAddNew = { showCreateCategoryDialog = true; activeSheet = null },
                    itemContent = { category ->
                        CategoryPickerItem(category)
                    }
                )
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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.get(Calendar.HOUR_OF_DAY),
            initialMinute = selectedDateTime.get(Calendar.MINUTE)
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            onConfirm = {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                selectedDateTime.set(Calendar.MINUTE, timePickerState.minute)
                showTimePicker = false
            }
        ) { TimePicker(state = timePickerState) }
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
                }
                showCreateCategoryDialog = false
            }
        )
    }
    // endregion
}

// region New UI Components for Composer
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
    onDescriptionChange: (String) -> Unit,
    isEditingDescription: Boolean,
    onEditModeToggle: (Boolean) -> Unit,
    isTravelMode: Boolean,
    travelModeSettings: TravelModeSettings?
) {
    val currencySymbol = if (isTravelMode) {
        CurrencyHelper.getCurrencySymbol(travelModeSettings?.currencyCode)
    } else {
        "₹" // Default to home currency symbol
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingDescription) {
        if (isEditingDescription) {
            focusRequester.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = isEditingDescription,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) with
                        fadeOut(animationSpec = tween(200))).using(
                    SizeTransform(clip = true)
                )
            }, label = "DescriptionEditAnimation"
        ) { isEditing ->
            if (isEditing) {
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Paid to...") },
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onEditModeToggle(false) }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    )
                )
            } else {
                Text(
                    text = description.ifBlank { "Paid to..." },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onEditModeToggle(true) }
                )
            }
        }

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
        if (isTravelMode && travelModeSettings != null) {
            val enteredAmount = amount.toDoubleOrNull() ?: 0.0
            val convertedAmount = enteredAmount * travelModeSettings.conversionRate
            val homeSymbol = "₹" // Assuming home is INR for now
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
private fun <T> PickerSheet(
    title: String,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    onAddNew: (() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight()) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onItemSelected(item) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemContent(item)
                }
            }
            if (onAddNew != null) {
                item {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAddNew)
                            .padding(vertical = 12.dp)
                            .height(76.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = "Create New",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "New",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryPickerItem(category: Category) {
    Box(
        modifier = Modifier
            .size(48.dp)
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
    Text(
        category.name,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )
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
            TextButton(onClick = { onConfirm(initialValue) }) { Text("Cancel") } // Revert on cancel
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
