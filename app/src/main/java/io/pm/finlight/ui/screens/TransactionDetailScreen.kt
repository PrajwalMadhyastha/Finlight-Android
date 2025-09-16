// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/TransactionDetailScreen.kt
// REASON: REFACTOR - The category selection grid (CategoryPickerSheet) now uses the
// new, reusable CategorySelectionGrid composable. This ensures a consistent, compact
// layout and centralizes the grid's UI logic.
// FIX - The TagPickerSheet layout has been refactored. The FlowRow containing
// the tags is now wrapped in a scrollable Column with a weight, ensuring the
// list is scrollable if it overflows while keeping the header and footer fixed.
// =================================================================================
package io.pm.finlight.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import io.pm.finlight.*
import io.pm.finlight.ui.components.*
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import io.pm.finlight.R
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.ui.viewmodel.SettingsViewModelFactory
import io.pm.finlight.utils.BankLogoHelper

private const val TAG = "DetailScreenDebug"

private sealed class SheetContent {
    object Amount : SheetContent()
    object Notes : SheetContent()
    object Account : SheetContent()
    object Category : SheetContent()
    object Tags : SheetContent()
    object Merchant : SheetContent()
}

private sealed interface DetailScreenState {
    object Loading : DetailScreenState
    data class Success(val details: TransactionDetails) : DetailScreenState
    object Exit : DetailScreenState
}

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    navController: NavController,
    transactionId: Int,
    viewModel: TransactionViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(),
    onSaveRenameRule: (originalName: String, newName: String) -> Unit
) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(context.applicationContext as android.app.Application, viewModel)
    )

    val detailsState by viewModel.findTransactionDetailsById(transactionId).collectAsState(initial = null)
    val details = detailsState

    val splits by viewModel.getSplitDetailsForTransaction(transactionId).collectAsState(initial = emptyList())

    val reparseResult = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("reparse_needed")?.observeAsState()
    val retroScanResult = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("start_retro_scan")?.observeAsState()

    val navigateBack: () -> Unit = { navController.popBackStack() }

    BackHandler {
        viewModel.onAttemptToLeaveScreen(onNavigationAllowed = navigateBack)
    }

    LaunchedEffect(reparseResult?.value) {
        if (reparseResult?.value == true) {
            viewModel.reparseTransactionFromSms(transactionId)
            navController.currentBackStackEntry?.savedStateHandle?.set("reparse_needed", false)
        }
    }

    LaunchedEffect(retroScanResult?.value) {
        if (retroScanResult?.value == true) {
            Toast.makeText(context, "Applying new rule to recent messages...", Toast.LENGTH_SHORT).show()
            settingsViewModel.rescanSmsWithNewRule { count ->
                Toast.makeText(context, "Found and saved $count new transaction(s)!", Toast.LENGTH_LONG).show()
            }
            navController.currentBackStackEntry?.savedStateHandle?.set("start_retro_scan", false)
        }
    }

    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val attachedImages by viewModel.transactionImages.collectAsState()
    val originalSms by viewModel.originalSmsText.collectAsState()
    val visitCount by viewModel.visitCount.collectAsState()
    val scope = rememberCoroutineScope()
    val retroUpdateSheetState by viewModel.retroUpdateSheetState.collectAsState()


    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf<Uri?>(null) }
    var showImageDeleteDialog by remember { mutableStateOf<TransactionImage?>(null) }

    var activeSheetContent by remember { mutableStateOf<SheetContent?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }


    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.attachPhotoToTransaction(transactionId, it)
        }
    }

    LaunchedEffect(transactionId) {
        NotificationManagerCompat.from(context).cancel(transactionId)
        viewModel.loadTransactionForDetailScreen(transactionId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectedTags()
            viewModel.clearOriginalSms()
        }
    }

    if (details == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val title = when (details.transaction.transactionType) {
            "expense" -> "Debit transaction"
            "income" -> "Credit transaction"
            else -> "Transaction Details"
        }
        // --- FIX: Use a local mutable state for the calendar to provide instant UI feedback ---
        var selectedDateTime by remember(details) {
            mutableStateOf(Calendar.getInstance().apply { timeInMillis = details.transaction.date })
        }


        fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5
        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        if (retroUpdateSheetState != null) {
            val retroSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.dismissRetroUpdateSheet()
                    navigateBack()
                },
                sheetState = retroSheetState,
                windowInsets = WindowInsets(0),
                containerColor = popupContainerColor,
                dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ) {
                RetrospectiveUpdateSheetContent(
                    state = retroUpdateSheetState!!,
                    onToggleSelection = viewModel::toggleRetroUpdateSelection,
                    onToggleSelectAll = viewModel::toggleRetroUpdateSelectAll,
                    onConfirm = {
                        viewModel.performBatchUpdate()
                        navigateBack()
                    },
                    onDismiss = {
                        viewModel.dismissRetroUpdateSheet()
                        navigateBack()
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.onAttemptToLeaveScreen(onNavigationAllowed = navigateBack) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (details.transaction.isSplit) {
                                    DropdownMenuItem(
                                        text = { Text("Un-split") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.unsplitTransaction(details.transaction)
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Un-split") }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                containerColor = Color.Transparent
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TransactionSpotlightHeader(
                                details = details,
                                // --- FIX: Pass the local state to the header ---
                                displayDate = selectedDateTime.time,
                                visitCount = visitCount,
                                isSplit = details.transaction.isSplit,
                                onDescriptionClick = {
                                    if (!details.transaction.isSplit) {
                                        activeSheetContent = SheetContent.Merchant
                                    }
                                },
                                onAmountClick = {
                                    if (!details.transaction.isSplit) {
                                        activeSheetContent = SheetContent.Amount
                                    } else {
                                        Toast.makeText(context, "Edit splits to change total amount.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCategoryClick = { activeSheetContent = SheetContent.Category },
                                onDateTimeClick = { showDatePicker = true },
                                onSplitClick = {
                                    navController.navigate("split_transaction/${details.transaction.id}")
                                }
                            )
                        }
                    }

                    if (details.transaction.originalAmount != null) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                CurrencyConversionInfoCard(transaction = details.transaction)
                            }
                        }
                    }

                    if (details.transaction.isSplit) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SplitSummaryCard(splits = splits)
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            AccountCardWithSwitch(
                                details = details,
                                onAccountClick = { activeSheetContent = SheetContent.Account },
                                onExcludeToggled = { isChecked ->
                                    viewModel.updateTransactionExclusion(details.transaction.id, !isChecked)
                                }
                            )
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            GlassPanel {
                                Column {
                                    NotesRow(
                                        details = details,
                                        onClick = { activeSheetContent = SheetContent.Notes }
                                    )
                                    if (selectedTags.isNotEmpty() || details.transaction.notes?.isNotBlank() == true) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    }
                                    TagsRow(
                                        selectedTags = selectedTags,
                                        onClick = { activeSheetContent = SheetContent.Tags }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            AttachmentRow(
                                images = attachedImages,
                                onAddClick = { imagePickerLauncher.launch("image/*") },
                                onViewClick = { showImageViewer = it },
                                onDeleteClick = { showImageDeleteDialog = it }
                            )
                        }
                    }


                    if (details.transaction.sourceSmsId != null) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val smsMessage = viewModel.getOriginalSmsMessage(details.transaction.sourceSmsId!!)
                                            if (smsMessage != null) {
                                                val potentialTxn = PotentialTransaction(
                                                    sourceSmsId = smsMessage.id,
                                                    smsSender = smsMessage.sender,
                                                    amount = details.transaction.amount,
                                                    transactionType = details.transaction.transactionType,
                                                    merchantName = details.transaction.description,
                                                    originalMessage = smsMessage.body,
                                                    sourceSmsHash = details.transaction.sourceSmsHash
                                                )
                                                val json = Gson().toJson(potentialTxn)
                                                val encodedJson = URLEncoder.encode(json, "UTF-8")
                                                navController.navigate("rule_creation_screen?potentialTransactionJson=$encodedJson")
                                            } else {
                                                Toast.makeText(context, "Original SMS not found.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Fix Parsing")
                                }
                            }
                        }
                    }

                    if (!originalSms.isNullOrBlank()) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                GlassPanel(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Message,
                                                contentDescription = "Original SMS",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Original SMS Message",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = originalSms!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeSheetContent != null) {
                    ModalBottomSheet(
                        onDismissRequest = { activeSheetContent = null },
                        sheetState = sheetState,
                        windowInsets = WindowInsets(0),
                        containerColor = popupContainerColor,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ) {
                        TransactionEditSheetContent(
                            sheetContent = activeSheetContent!!,
                            details = details,
                            viewModel = viewModel,
                            accountViewModel = accountViewModel,
                            onSaveRenameRule = onSaveRenameRule,
                            accounts = accounts,
                            categories = categories,
                            allTags = allTags,
                            selectedTags = selectedTags,
                            onDismiss = { activeSheetContent = null },
                            onAddNewAccount = {
                                activeSheetContent = null
                                showCreateAccountDialog = true
                            },
                            onAddNewCategory = {
                                activeSheetContent = null
                                showCreateCategoryDialog = true
                            }
                        )
                    }
                }

                if (showCreateAccountDialog) {
                    CreateAccountDialog(
                        onDismiss = { showCreateAccountDialog = false },
                        onConfirm = { name, type ->
                            viewModel.createAccount(name, type) { newAccount ->
                                viewModel.updateTransactionAccount(transactionId, newAccount.id)
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
                                viewModel.updateTransactionCategory(transactionId, newCategory.id)
                            }
                            showCreateCategoryDialog = false
                        }
                    )
                }

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTime.timeInMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let {
                                    // --- FIX: Update local state immediately ---
                                    selectedDateTime = Calendar.getInstance().apply { timeInMillis = it }
                                }
                                showDatePicker = false
                                showTimePicker = true
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
                        // --- FIX: Add explicit container color ---
                        colors = DatePickerDefaults.colors(containerColor = popupContainerColor)
                    ) { DatePicker(state = datePickerState) }
                }
                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(initialHour = selectedDateTime.get(Calendar.HOUR_OF_DAY), initialMinute = selectedDateTime.get(Calendar.MINUTE))
                    // --- FIX: Wrap TimePicker in an AlertDialog with explicit color ---
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
                                val finalDateTime = (selectedDateTime.clone() as Calendar).apply {
                                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(Calendar.MINUTE, timePickerState.minute)
                                }
                                selectedDateTime = finalDateTime
                                viewModel.updateTransactionDate(transactionId, finalDateTime.timeInMillis)
                                showTimePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        containerColor = popupContainerColor,
                        title = { Text("Delete Transaction?", color = MaterialTheme.colorScheme.onSurface) },
                        text = { Text("Are you sure you want to permanently delete this transaction? This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteTransaction(details.transaction)
                                    showDeleteDialog = false
                                    navigateBack()
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
                    )
                }

                if (showImageViewer != null) {
                    Dialog(onDismissRequest = { showImageViewer = null }) {
                        AsyncImage(
                            model = showImageViewer,
                            contentDescription = "Full screen image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }

                if (showImageDeleteDialog != null) {
                    AlertDialog(
                        onDismissRequest = { showImageDeleteDialog = null },
                        containerColor = popupContainerColor,
                        title = { Text("Delete Attachment?", color = MaterialTheme.colorScheme.onSurface) },
                        text = { Text("Are you sure you want to delete this attachment? This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteTransactionImage(showImageDeleteDialog!!)
                                    showImageDeleteDialog = null
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showImageDeleteDialog = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitSummaryCard(splits: List<SplitTransactionDetails>) {
    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Split Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                splits.forEach { splitDetail ->
                    SplitSummaryItem(splitDetail)
                }
            }
        }
    }
}

@Composable
private fun SplitSummaryItem(details: SplitTransactionDetails) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    CategoryIconHelper.getIconBackgroundColor(
                        details.categoryColorKey ?: "gray_light"
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CategoryIconHelper.getIcon(details.categoryIconKey ?: "category"),
                contentDescription = details.categoryName,
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = details.categoryName ?: "Uncategorized",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = currencyFormat.format(details.splitTransaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun CurrencyConversionInfoCard(transaction: Transaction) {
    val homeCurrencySymbol = "₹"
    val foreignCurrencySymbol = CurrencyHelper.getCurrencySymbol(transaction.currencyCode)
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 } }

    GlassPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Currency Conversion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Original Amount:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${foreignCurrencySymbol}${numberFormat.format(transaction.originalAmount)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Conversion Rate:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "1 ${transaction.currencyCode} = $homeCurrencySymbol${numberFormat.format(transaction.conversionRate)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Converted Amount:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$homeCurrencySymbol${numberFormat.format(transaction.amount)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DynamicCategoryBackground(category: Category, isSplit: Boolean) {
    val color = CategoryIconHelper.getIconBackgroundColor(category.colorKey)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isSplit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = "Split Transaction Background",
                modifier = Modifier.size(250.dp),
                tint = color.copy(alpha = 0.15f)
            )
        } else {
            val letter = if (category.name == "Uncategorized") "?" else category.name.firstOrNull()?.uppercase() ?: "?"
            Text(
                text = letter,
                fontSize = 250.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun TransactionSpotlightHeader(
    details: TransactionDetails,
    displayDate: Date,
    visitCount: Int,
    isSplit: Boolean,
    onDescriptionClick: () -> Unit,
    onAmountClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDateTimeClick: () -> Unit,
    onSplitClick: () -> Unit
) {
    val displayCategory = if (isSplit) {
        Category(name = "Multiple Categories", iconKey = "call_split", colorKey = "gray_light")
    } else {
        details.toCategory()
    }

    val headerDescription = if (isSplit) "Split Transaction" else details.transaction.description

    val categoryColor = CategoryIconHelper.getIconBackgroundColor(displayCategory.colorKey)
    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMMM yy, h:mm a", Locale.getDefault()) }

    val animatedAmount by animateFloatAsState(
        targetValue = details.transaction.amount.toFloat(),
        animationSpec = tween(1500, easing = EaseOutCubic),
        label = "AmountAnimation"
    )

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isPredefined = CategoryIconHelper.getCategoryBackground(displayCategory.iconKey) != R.drawable.bg_cat_general
            if (isPredefined && !isSplit) {
                Image(
                    painter = painterResource(id = CategoryIconHelper.getCategoryBackground(displayCategory.iconKey)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    alpha = 0.3f
                )
            } else {
                DynamicCategoryBackground(category = displayCategory, isSplit = isSplit)
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                drawIntoCanvas {
                    val paint = Paint().asFrameworkPaint()
                    val radius = size.width * 0.8f
                    paint.color = android.graphics.Color.TRANSPARENT
                    paint.setShadowLayer(
                        radius,
                        0f,
                        0f,
                        categoryColor
                            .copy(alpha = 0.4f)
                            .toArgb()
                    )
                    it.drawCircle(center, radius / 2, Paint().apply { this.color = Color.Transparent })
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = headerDescription,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(onClick = onDescriptionClick)
                            .padding(horizontal = 16.dp)
                    )
                    if (details.transaction.isSplit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Split Transaction",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "₹${"%,.2f".format(animatedAmount)}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onAmountClick)
                )
                Spacer(Modifier.height(16.dp))
                if (!isSplit) {
                    ChipWithIcon(
                        text = displayCategory.name,
                        onClick = onCategoryClick,
                        category = displayCategory
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSplitClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                ) {
                    val icon = if (isSplit) Icons.Default.Edit else Icons.AutoMirrored.Filled.CallSplit
                    val text = if (isSplit) "Edit Splits" else "Split Transaction"
                    Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text)
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        // --- FIX: Use the local displayDate state ---
                        text = dateFormatter.format(displayDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.clickable(onClick = onDateTimeClick)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Transaction Source",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = details.transaction.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (visitCount > 1) {
                AssistChip(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    onClick = { /* No action needed */ },
                    label = { Text("$visitCount visits") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}


@Composable
private fun AccountCardWithSwitch(
    details: TransactionDetails,
    onAccountClick: () -> Unit,
    onExcludeToggled: (Boolean) -> Unit
) {
    val isExcluded = details.transaction.isExcluded
    val switchLabel = details.transaction.transactionType.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    GlassPanel {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = BankLogoHelper.getLogoForAccount(details.accountName ?: "")),
                contentDescription = "${details.accountName} Logo",
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .clickable(onClick = onAccountClick)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = details.accountName ?: "N/A",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                modifier = Modifier.weight(0.3f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = switchLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Switch(
                    checked = !isExcluded,
                    onCheckedChange = onExcludeToggled
                )
            }
        }
    }
}


@Composable
private fun NotesRow(details: TransactionDetails, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Notes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Notes", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Text(
            details.transaction.notes ?: "Tap to add",
            fontWeight = if (details.transaction.notes.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
            color = if (details.transaction.notes.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Default.Edit, contentDescription = "Edit Notes", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsRow(selectedTags: Set<Tag>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.NewLabel, contentDescription = "Tags", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text("Tags", color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            if(selectedTags.isEmpty()){
                Text("Tap to add", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedTags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) })
                    }
                }
            }
        }
        Icon(Icons.Default.Edit, contentDescription = "Edit Tags", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttachmentRow(
    images: List<TransactionImage>,
    onAddClick: () -> Unit,
    onViewClick: (Uri) -> Unit,
    onDeleteClick: (TransactionImage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Attachment, contentDescription = "Attachments", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text("Actions", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onAddClick) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Attachment", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        if (images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images) { image ->
                    Box {
                        AsyncImage(
                            model = File(image.imageUri),
                            contentDescription = "Transaction Attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onViewClick(File(image.imageUri).toUri()) }
                        )
                        IconButton(
                            onClick = { onDeleteClick(image) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete Attachment",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun TransactionEditSheetContent(
    sheetContent: SheetContent,
    details: TransactionDetails,
    viewModel: TransactionViewModel,
    accountViewModel: AccountViewModel,
    onSaveRenameRule: (originalName: String, newName: String) -> Unit,
    accounts: List<Account>,
    categories: List<Category>,
    allTags: List<Tag>,
    selectedTags: Set<Tag>,
    onDismiss: () -> Unit,
    onAddNewAccount: () -> Unit,
    onAddNewCategory: () -> Unit
) {
    val transactionId = details.transaction.id

    when (sheetContent) {
        is SheetContent.Merchant -> {
            MerchantPredictionSheet(
                viewModel = viewModel,
                initialDescription = details.transaction.description,
                onPredictionSelected = { prediction ->
                    viewModel.updateTransactionDescription(transactionId, prediction.description)
                    prediction.categoryId?.let { catId ->
                        viewModel.updateTransactionCategory(transactionId, catId)
                    }
                    onDismiss()
                },
                onManualSave = { newDescription ->
                    viewModel.updateTransactionDescription(transactionId, newDescription)
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        is SheetContent.Amount -> {
            EditTextFieldSheet(
                title = "Edit Amount",
                initialValue = "%.2f".format(details.transaction.amount),
                keyboardType = KeyboardType.Number,
                onConfirm = {
                    viewModel.updateTransactionAmount(transactionId, it)
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        is SheetContent.Notes -> {
            EditTextFieldSheet(
                title = "Edit Notes",
                initialValue = details.transaction.notes ?: "",
                onConfirm = {
                    viewModel.updateTransactionNotes(transactionId, it)
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        is SheetContent.Account -> {
            AccountPickerSheet(
                title = "Select Account",
                currentAccountId = details.transaction.accountId,
                items = accounts,
                onItemSelected = {
                    viewModel.updateTransactionAccount(transactionId, it.id)
                    onDismiss()
                },
                onDismiss = onDismiss,
                onAddNew = onAddNewAccount,
                accountViewModel = accountViewModel
            )
        }
        is SheetContent.Category -> {
            CategoryPickerSheet(
                title = "Select Category",
                items = categories,
                onItemSelected = {
                    viewModel.updateTransactionCategory(transactionId, it.id)
                    onDismiss()
                },
                onDismiss = onDismiss,
                onAddNew = onAddNewCategory
            )
        }
        is SheetContent.Tags -> {
            TagPickerSheet(
                allTags = allTags,
                selectedTags = selectedTags,
                onTagSelected = viewModel::onTagSelected,
                onAddNewTag = viewModel::addTagOnTheGo,
                onConfirm = {
                    viewModel.updateTagsForTransaction(transactionId)
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AccountPickerSheet(
    title: String,
    currentAccountId: Int,
    items: List<Account>,
    onItemSelected: (Account) -> Unit,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    accountViewModel: AccountViewModel
) {
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var editingName by remember { mutableStateOf("") }

    val currentAccount = items.find { it.id == currentAccountId }
    val otherAccounts = items.filter { it.id != currentAccountId }

    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight()) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        currentAccount?.let { account ->
            AccountPickerItem(
                account = account,
                isEditing = editingAccount?.id == account.id,
                editingName = editingName,
                onEditingNameChange = { editingName = it },
                onEditClick = {
                    editingAccount = account
                    editingName = account.name
                },
                onSaveClick = {
                    accountViewModel.renameAccount(account.id, editingName)
                    editingAccount = null
                },
                onCancelClick = { editingAccount = null },
                onSelectClick = { onItemSelected(account) },
                isCurrent = true
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        }

        LazyColumn {
            items(otherAccounts) { account ->
                AccountPickerItem(
                    account = account,
                    isEditing = editingAccount?.id == account.id,
                    editingName = editingName,
                    onEditingNameChange = { editingName = it },
                    onEditClick = {
                        editingAccount = account
                        editingName = account.name
                    },
                    onSaveClick = {
                        accountViewModel.renameAccount(account.id, editingName)
                        editingAccount = null
                    },
                    onCancelClick = { editingAccount = null },
                    onSelectClick = { onItemSelected(account) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Create New Account", color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = "Create New Account", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable(onClick = onAddNew)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountPickerItem(
    account: Account,
    isEditing: Boolean,
    editingName: String,
    onEditingNameChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSelectClick: () -> Unit,
    isCurrent: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }

    if (isEditing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = editingName,
                onValueChange = onEditingNameChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text("Account Name") }
            )
            IconButton(onClick = onSaveClick, enabled = editingName.isNotBlank()) {
                Icon(Icons.Default.Check, contentDescription = "Save Name", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCancelClick) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    } else {
        val colors = if (isCurrent) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                supportingColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ListItem(
            colors = colors,
            headlineContent = {
                Text(account.name, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
            },
            supportingContent = { if (isCurrent) Text("Currently Selected") },
            modifier = Modifier.clickable(onClick = onSelectClick),
            trailingContent = {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Account Name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun EditTextFieldSheet(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    additionalContent: @Composable (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Value") },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("value_input")
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        additionalContent?.invoke()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                onConfirm(text)
            }) { Text("Save") }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
internal fun CategoryPickerSheet(
    title: String,
    items: List<Category>,
    onItemSelected: (Category) -> Unit,
    onDismiss: () -> Unit,
    onAddNew: (() -> Unit)? = null
) {
    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight()) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        // --- UPDATED: Replaced the LazyVerticalGrid with the new reusable component ---
        CategorySelectionGrid(
            categories = items,
            onCategorySelected = onItemSelected,
            onAddNew = onAddNew
        )
        Spacer(Modifier.height(16.dp))
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
            .fillMaxHeight()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Manage Tags", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

        // --- FIX: This column is now scrollable ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
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
private fun CategoryIconDisplay(category: Category) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(CategoryIconHelper.getIconBackgroundColor(category.colorKey)),
        contentAlignment = Alignment.Center
    ) {
        if (category.name == "Uncategorized") {
            Icon(
                imageVector = CategoryIconHelper.getIcon("help_outline"),
                contentDescription = category.name,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        } else if (category.iconKey == "letter_default") {
            Text(
                text = category.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        } else {
            Icon(
                imageVector = CategoryIconHelper.getIcon(category.iconKey),
                contentDescription = category.name,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun TransactionDetails.toCategory(): Category {
    return if (this.categoryName == null || this.categoryName == "Uncategorized") {
        Category(
            id = 0,
            name = "Uncategorized",
            iconKey = "help_outline",
            colorKey = "red_light"
        )
    } else {
        Category(
            id = this.transaction.categoryId ?: 0,
            name = this.categoryName,
            iconKey = this.categoryIconKey ?: "category",
            colorKey = this.categoryColorKey ?: "gray_light"
        )
    }
}

@Composable
private fun ChipWithIcon(
    text: String,
    onClick: () -> Unit,
    category: Category
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                CategoryIconHelper
                    .getIconBackgroundColor(category.colorKey)
                    .copy(alpha = 0.9f)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (category.name == "Uncategorized") {
            Icon(
                imageVector = CategoryIconHelper.getIcon("help_outline"),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        } else if (category.iconKey == "letter_default") {
            Text(
                text = category.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        } else {
            Icon(
                imageVector = CategoryIconHelper.getIcon(category.iconKey),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RetrospectiveUpdateSheetContent(
    state: RetroUpdateSheetState,
    onToggleSelection: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val changeType = if (state.newDescription != null) "description" else "category"

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            "Update Similar Transactions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "You've changed the $changeType for transactions like '${state.originalDescription}'. Apply this change to other similar transactions?",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val allSelected = state.selectedIds.size == state.similarTransactions.size
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleSelectAll() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkmarkColor = MaterialTheme.colorScheme.surface
                    )
                )
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.similarTransactions, key = { it.id }) { transaction ->
                    SelectableTransactionItem(
                        transaction = transaction,
                        isSelected = transaction.id in state.selectedIds,
                        onToggle = { onToggleSelection(transaction.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Just This One")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = state.selectedIds.isNotEmpty()
            ) {
                Text("Update ${state.selectedIds.size} Items")
            }
        }
    }
}

@Composable
private fun SelectableTransactionItem(
    transaction: Transaction,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM, yy", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.surface
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dateFormatter.format(Date(transaction.date)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "₹${"%,.2f".format(transaction.amount)}",
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}