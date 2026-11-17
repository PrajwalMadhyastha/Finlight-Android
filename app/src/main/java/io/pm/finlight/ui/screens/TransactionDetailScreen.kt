package io.pm.finlight.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.TransactionImage
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.components.LinkedAsReimbursementBadge
import io.pm.finlight.ui.components.TransactionPickerSheet
import io.pm.finlight.ui.components.UnifiedRelatedActivityCard
import io.pm.finlight.ui.screens.transactiondetail.AccountCard
import io.pm.finlight.ui.screens.transactiondetail.CanonicalNudgeSheet
import io.pm.finlight.ui.screens.transactiondetail.CurrencyConversionInfoCard
import io.pm.finlight.ui.screens.transactiondetail.MultiAccountBreakdownCard
import io.pm.finlight.ui.screens.transactiondetail.NotesRow
import io.pm.finlight.ui.screens.transactiondetail.OriginalSmsCard
import io.pm.finlight.ui.screens.transactiondetail.RetrospectiveUpdateSheet
import io.pm.finlight.ui.screens.transactiondetail.ReviewBannerCard
import io.pm.finlight.ui.screens.transactiondetail.SheetContent
import io.pm.finlight.ui.screens.transactiondetail.SplitSummaryCard
import io.pm.finlight.ui.screens.transactiondetail.TagsRow
import io.pm.finlight.ui.screens.transactiondetail.TransactionActionsCard
import io.pm.finlight.ui.screens.transactiondetail.TransactionCreateAccountDialog
import io.pm.finlight.ui.screens.transactiondetail.TransactionCreateCategoryDialog
import io.pm.finlight.ui.screens.transactiondetail.TransactionDateTimePickerFlow
import io.pm.finlight.ui.screens.transactiondetail.TransactionDeleteDialog
import io.pm.finlight.ui.screens.transactiondetail.TransactionEditBottomSheet
import io.pm.finlight.ui.screens.transactiondetail.TransactionImageDeleteDialog
import io.pm.finlight.ui.screens.transactiondetail.TransactionImageViewerDialog
import io.pm.finlight.ui.screens.transactiondetail.TransactionPropertiesCard
import io.pm.finlight.ui.screens.transactiondetail.TransactionSpotlightHeader
import io.pm.finlight.ui.screens.transactiondetail.TransactionUnmergeDialog
import io.pm.finlight.ui.screens.transactiondetail.navigateToRuleCreation
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    navController: NavController,
    transactionId: Int,
    viewModel: TransactionViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(),
    onSaveRenameRule: (originalName: String, newName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel =
        viewModel(
            factory = FinlightViewModelFactory.create(context.applicationContext as android.app.Application),
        )

    val detailsState by viewModel.findTransactionDetailsById(transactionId).collectAsState(initial = null)
    val details = detailsState

    val splits by viewModel.getSplitDetailsForTransaction(transactionId).collectAsState(initial = emptyList())
    val reparseResult = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("reparse_needed")?.observeAsState()
    val retroScanResult = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("start_retro_scan")?.observeAsState()
    val navigateBack: () -> Unit = { navController.popBackStack() }

    BackHandler { viewModel.onAttemptToLeaveScreen(onNavigationAllowed = navigateBack) }

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
    val canonicalNudgeState by viewModel.canonicalNudgeState.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    val reimbursements by viewModel.reimbursementsForCurrentExpense.collectAsState()
    val linkedExpense by viewModel.linkedExpenseForCurrentIncome.collectAsState()
    val showReimbursementPicker by viewModel.showReimbursementPicker.collectAsState()
    val candidateReimbursements by viewModel.candidateReimbursements.collectAsState()
    val mergedTransactionBreakdown by viewModel.mergedTransactionBreakdown.collectAsState()

    var showUnmergeDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) { viewModel.navigateBackEvent.collect { navigateBack() } }
    LaunchedEffect(Unit) { viewModel.uiEvent.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(validationError) {
        validationError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? -> uri?.let { viewModel.attachPhotoToTransaction(transactionId, it) } }

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
        val title =
            when (details.transaction.transactionType) {
                TransactionType.EXPENSE -> "Debit transaction"
                TransactionType.INCOME -> "Credit transaction"
                else -> "Transaction Details"
            }
        var selectedDateTime by remember(details) {
            mutableStateOf(Calendar.getInstance().apply { timeInMillis = details.transaction.date })
        }

        val isThemeDark = MaterialTheme.colorScheme.background.isDark()
        val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

        if (retroUpdateSheetState != null) {
            RetrospectiveUpdateSheet(
                state = retroUpdateSheetState!!,
                onToggleSelection = viewModel::toggleRetroUpdateSelection,
                onToggleSelectAll = viewModel::toggleRetroUpdateSelectAll,
                onToggleUpdateFuture = viewModel::toggleUpdateFutureTransactions,
                onConfirm = { viewModel.performBatchUpdate() },
                onDismiss = { viewModel.onRetroSheetSkipped() },
                containerColor = popupContainerColor,
            )
        }

        if (canonicalNudgeState != null) {
            CanonicalNudgeSheet(
                state = canonicalNudgeState!!,
                onToggleVariant = viewModel::toggleCanonicalVariant,
                onConfirm = { viewModel.confirmCanonicalNudge() },
                onDismiss = { viewModel.dismissCanonicalNudge() },
                containerColor = popupContainerColor,
            )
        }

        Box(modifier = modifier.fillMaxSize()) {
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
                            HelpActionIcon(helpKey = "transaction_detail")
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(popupContainerColor.copy(alpha = 1f)),
                            ) {
                                if (details.transaction.isSplit) {
                                    DropdownMenuItem(
                                        text = { Text("Un-split") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.unsplitTransaction(details.transaction)
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Un-split") },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete") },
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
                containerColor = Color.Transparent,
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.padding(innerPadding).testTag("transaction_detail_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (details.transaction.needsReview) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ReviewBannerCard(
                                    reason = "Suspicious amount detected. Please verify this transaction.",
                                    onReviewClick = { viewModel.markAsReviewed(details.transaction.id) },
                                )
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TransactionSpotlightHeader(
                                details = details,
                                displayDate = selectedDateTime.time,
                                visitCount = visitCount,
                                isSplit = details.transaction.isSplit,
                                hasMerged = mergedTransactionBreakdown.size > 1,
                                hasReimbursements = reimbursements.isNotEmpty(),
                                onDescriptionClick = {
                                    if (!details.transaction.isSplit) activeSheetContent = SheetContent.Merchant
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
                                onSplitClick = { navController.navigate("split_transaction/${details.transaction.id}") },
                                onVisitCountClick = { navController.navigate("search_screen?query=${details.transaction.description}") },
                            )
                        }
                    }

                    if (details.transaction.originalAmount != null &&
                        details.transaction.currencyCode != null &&
                        details.transaction.conversionRate != null
                    ) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                CurrencyConversionInfoCard(transaction = details.transaction)
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TransactionPropertiesCard(
                                details = details,
                                hasMerged = mergedTransactionBreakdown.size > 1,
                                hasReimbursements = reimbursements.isNotEmpty(),
                                onTypeSelected = { viewModel.updateTransactionType(details.transaction.id, it) },
                                onExcludeToggled = { viewModel.updateTransactionExclusion(details.transaction.id, it) },
                            )
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            val accountsInvolved = (mergedTransactionBreakdown.map { it.accountId } + reimbursements.mapNotNull { it.transaction.accountId }).toSet()
                            if (accountsInvolved.size > 1) {
                                MultiAccountBreakdownCard(
                                    entries = mergedTransactionBreakdown,
                                    reimbursements = reimbursements,
                                    onCardClick = {
                                        Toast.makeText(context, "Unmerge or unlink to reassign accounts.", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            } else {
                                AccountCard(
                                    details = details,
                                    readOnly = false,
                                    onAccountClick = { activeSheetContent = SheetContent.Account },
                                )
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

                    if (details.transaction.transactionType == TransactionType.EXPENSE || mergedTransactionBreakdown.size > 1) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                UnifiedRelatedActivityCard(
                                    currentAmount = details.transaction.amount,
                                    isExpense = details.transaction.transactionType == TransactionType.EXPENSE,
                                    reimbursements = reimbursements,
                                    mergedEntries = mergedTransactionBreakdown,
                                    onLinkClick = { viewModel.openReimbursementPicker(transactionId) },
                                    onUnlinkClick = { viewModel.unlinkReimbursement(it) },
                                    onUnmergeClick = { showUnmergeDialog = true },
                                )
                            }
                        }
                    }

                    if (details.transaction.transactionType == TransactionType.INCOME && linkedExpense != null) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                LinkedAsReimbursementBadge(
                                    linkedExpense = linkedExpense!!,
                                    onNavigateToExpense = { navController.navigate("transaction_detail/${linkedExpense!!.transaction.id}") },
                                    onUnlinkClick = { viewModel.unlinkReimbursement(transactionId) },
                                )
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            GlassPanel {
                                Column {
                                    NotesRow(
                                        details = details,
                                        onClick = { activeSheetContent = SheetContent.Notes },
                                    )
                                    if (selectedTags.isNotEmpty() || details.transaction.notes?.isNotBlank() == true) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    }
                                    TagsRow(
                                        selectedTags = selectedTags,
                                        onClick = { activeSheetContent = SheetContent.Tags },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TransactionActionsCard(
                                images = attachedImages,
                                onAddClick = { imagePickerLauncher.launch("image/*") },
                                onViewClick = { showImageViewer = it },
                                onDeleteClick = { showImageDeleteDialog = it },
                                sourceSmsId = details.transaction.sourceSmsId,
                                onFixParsingClick = {
                                    navigateToRuleCreation(context, navController, viewModel, scope, details)
                                },
                            )
                        }
                    }

                    if (!originalSms.isNullOrBlank()) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                OriginalSmsCard(originalSms = originalSms!!)
                            }
                        }
                    }
                }

                TransactionEditBottomSheet(
                    sheetContent = activeSheetContent,
                    details = details,
                    viewModel = viewModel,
                    accountViewModel = accountViewModel,
                    onSaveRenameRule = onSaveRenameRule,
                    accounts = accounts,
                    categories = categories,
                    allTags = allTags,
                    selectedTags = selectedTags,
                    sheetState = sheetState,
                    containerColor = popupContainerColor,
                    onDismiss = { activeSheetContent = null },
                    onAddNewAccount = {
                        activeSheetContent = null
                        showCreateAccountDialog = true
                    },
                    onAddNewCategory = {
                        activeSheetContent = null
                        showCreateCategoryDialog = true
                    },
                )

                TransactionCreateAccountDialog(
                    show = showCreateAccountDialog,
                    onDismiss = { showCreateAccountDialog = false },
                    onConfirm = { name, type, creditLimit, billingCycleDay ->
                        viewModel.createAccount(name, type, creditLimit, billingCycleDay) { newAccount ->
                            viewModel.updateTransactionAccount(transactionId, newAccount.id)
                        }
                        showCreateAccountDialog = false
                    },
                )

                TransactionCreateCategoryDialog(
                    show = showCreateCategoryDialog,
                    onDismiss = { showCreateCategoryDialog = false },
                    onConfirm = { name, iconKey, colorKey ->
                        viewModel.createCategory(name, iconKey, colorKey) { newCategory ->
                            viewModel.updateTransactionCategory(transactionId, newCategory.id)
                        }
                        showCreateCategoryDialog = false
                    },
                )

                TransactionDateTimePickerFlow(
                    showDatePicker = showDatePicker,
                    showTimePicker = showTimePicker,
                    initialDateTime = selectedDateTime,
                    popupContainerColor = popupContainerColor,
                    onDateSelected = {
                        selectedDateTime = it
                        showDatePicker = false
                        showTimePicker = true
                    },
                    onTimeSelected = {
                        selectedDateTime = it
                        viewModel.updateTransactionDate(transactionId, it.timeInMillis)
                        showTimePicker = false
                    },
                    onDismissDatePicker = { showDatePicker = false },
                    onDismissTimePicker = { showTimePicker = false },
                )

                TransactionDeleteDialog(
                    show = showDeleteDialog,
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        viewModel.deleteTransaction(details.transaction)
                        showDeleteDialog = false
                        navigateBack()
                    },
                )

                TransactionUnmergeDialog(
                    show = showUnmergeDialog,
                    popupContainerColor = popupContainerColor,
                    onDismiss = { showUnmergeDialog = false },
                    onConfirm = {
                        showUnmergeDialog = false
                        viewModel.unmergeTransaction(transactionId)
                        navigateBack()
                    },
                )

                TransactionImageViewerDialog(
                    imageUri = showImageViewer,
                    onDismiss = { showImageViewer = null },
                )

                TransactionImageDeleteDialog(
                    image = showImageDeleteDialog,
                    onDismiss = { showImageDeleteDialog = null },
                    onConfirm = {
                        viewModel.deleteTransactionImage(it)
                        showImageDeleteDialog = null
                    },
                )
            }
        }

        if (showReimbursementPicker) {
            TransactionPickerSheet(
                transactions = candidateReimbursements.map { it.transaction },
                onTransactionSelected = { viewModel.linkReimbursement(it.id, transactionId) },
                onDismiss = { viewModel.dismissReimbursementPicker() },
            )
        }
    }
}
