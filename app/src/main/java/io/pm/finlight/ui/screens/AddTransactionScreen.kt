package io.pm.finlight.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.components.TransactionTypeToggle
import io.pm.finlight.ui.screens.addtransaction.AddTransactionBottomSheet
import io.pm.finlight.ui.screens.addtransaction.AddTransactionDialogs
import io.pm.finlight.ui.screens.addtransaction.AmountComposer
import io.pm.finlight.ui.screens.addtransaction.ComposerSheet
import io.pm.finlight.ui.screens.addtransaction.OrbitalChipsSection
import io.pm.finlight.ui.screens.addtransaction.PredictionCarousel
import io.pm.finlight.ui.screens.addtransaction.QuickFillCarousel
import io.pm.finlight.ui.screens.addtransaction.SpotlightBackground
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.GoalViewModel
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

private fun parseCsvInitialData(json: String): Map<String, String>? =
    runCatching {
        val typeToken = object : TypeToken<Map<String, String>>() {}.type
        Gson().fromJson<Map<String, String>>(URLDecoder.decode(json, "UTF-8"), typeToken)
    }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    viewModel: TransactionViewModel,
    goalViewModel: GoalViewModel,
    isCsvEdit: Boolean = false,
    initialDataJson: String? = null,
    initialTransactionType: String? = null,
) {
    val context = LocalContext.current

    val description by viewModel.addTransactionDescription.collectAsState()
    val amount by viewModel.addTransactionAmount.collectAsState()
    val selectedCategory by viewModel.addTransactionCategory.collectAsState()
    val selectedAccount by viewModel.addTransactionAccount.collectAsState()

    var transactionType by rememberSaveable {
        mutableStateOf(
            initialTransactionType?.let { TransactionType.fromStringOrNull(it) } ?: TransactionType.EXPENSE,
        )
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var attachedImageUris by rememberSaveable(
        stateSaver =
            Saver<List<Uri>, ArrayList<String>>(
                save = { uris -> ArrayList(uris.map { it.toString() }) },
                restore = { list -> list.map { Uri.parse(it) } },
            ),
    ) { mutableStateOf<List<Uri>>(emptyList()) }

    var hasInteracted by remember { mutableStateOf(false) }
    val isAmountEntered by remember(amount) { derivedStateOf { (amount.toDoubleOrNull() ?: 0.0) > 0.0 } }
    val isDescriptionEntered by remember(description) { derivedStateOf { description.isNotBlank() } }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
        ) { uris: List<Uri> ->
            attachedImageUris = attachedImageUris + uris
        }

    val accounts by viewModel.allAccounts.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    val activeGoals by goalViewModel.activeGoals.collectAsState()
    val goalIncomeThreshold by viewModel.goalIncomeThreshold.collectAsState()
    var showGoalAllocationDialog by remember { mutableStateOf<Long?>(null) }

    val handleSaveComplete: (Long?) -> Unit = { savedTxnId ->
        val amtDouble = amount.toDoubleOrNull() ?: 0.0
        if (transactionType == TransactionType.INCOME &&
            amtDouble >= goalIncomeThreshold &&
            activeGoals.isNotEmpty() &&
            savedTxnId != null
        ) {
            showGoalAllocationDialog = savedTxnId
        } else {
            viewModel.clearAddTransactionState()
            navController.popBackStack()
        }
    }

    val travelModeSettings by viewModel.travelModeSettings.collectAsState()
    val categoryNudgeData by viewModel.showCategoryNudge.collectAsState()
    val suggestedCategory by viewModel.suggestedCategory.collectAsState()

    val recentManualTransactions by viewModel.recentManualTransactions.collectAsState()
    val historyManualTransactions by viewModel.historyManualTransactions.collectAsState()
    val merchantPredictions by viewModel.merchantPredictions.collectAsState()

    val selectedDateTime by rememberSaveable(
        stateSaver =
            Saver<Calendar, Long>(
                save = { it.timeInMillis },
                restore = { Calendar.getInstance().apply { timeInMillis = it } },
            ),
    ) { mutableStateOf(Calendar.getInstance()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ComposerSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    val isSaveEnabled = isAmountEntered
    var isDefaultAccountApplied by rememberSaveable { mutableStateOf(false) }
    var isScreenInitialized by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        viewModel.clearAddTransactionState()
        navController.popBackStack()
    }

    LaunchedEffect(initialDataJson, accounts, categories, defaultAccount, isCsvEdit) {
        if (isDefaultAccountApplied) return@LaunchedEffect

        if (isCsvEdit && initialDataJson != null) {
            val initialDataMap = parseCsvInitialData(initialDataJson)
            if (initialDataMap != null) {
                val dateFormat = FormatUtils.getFormatter("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                initialDataMap["Date"]?.let {
                    try {
                        selectedDateTime.time = dateFormat.parse(it) ?: Date()
                    } catch (_: Exception) {
                    }
                }
                viewModel.onAddTransactionDescriptionChanged(initialDataMap["Description"] ?: "")
                viewModel.onAddTransactionAmountChanged((initialDataMap["Amount"] ?: "").replace(".0", ""))
                transactionType = initialDataMap["Type"]?.let { TransactionType.fromStringOrNull(it) } ?: TransactionType.EXPENSE
                val categoryName = initialDataMap["Category"] ?: ""
                val accountName = initialDataMap["Account"] ?: ""
                notes = initialDataMap["Notes"] ?: ""

                viewModel.onAddTransactionCategoryChanged(categories.find { it.name.equals(categoryName, ignoreCase = true) })

                val csvAccount = accounts.find { it.name.equals(accountName, ignoreCase = true) }
                if (csvAccount != null) {
                    viewModel.onAddTransactionAccountChanged(csvAccount)
                } else {
                    viewModel.onAddTransactionAccountChanged(
                        defaultAccount ?: accounts.find { it.name.equals("Cash Spends", ignoreCase = true) },
                    )
                }
                isDefaultAccountApplied = true
            } else {
                Toast.makeText(context, "Error loading row data", Toast.LENGTH_SHORT).show()
            }
        } else if (!isCsvEdit) {
            if (defaultAccount != null) {
                viewModel.onAddTransactionAccountChanged(defaultAccount)
                isDefaultAccountApplied = true
            } else if (accounts.isNotEmpty()) {
                val cashAccount = accounts.find { it.name.equals("Cash Spends", ignoreCase = true) }
                if (cashAccount != null) {
                    viewModel.onAddTransactionAccountChanged(cashAccount)
                    isDefaultAccountApplied = true
                }
            }
        }
    }

    LaunchedEffect(suggestedCategory) {
        suggestedCategory?.let { viewModel.onAddTransactionCategoryChanged(it) }
    }

    LaunchedEffect(Unit) {
        if (!isScreenInitialized) {
            if (!viewModel.hasRestoredManualTransactionState()) {
                viewModel.clearAddTransactionState()
            }
            isScreenInitialized = true
        }
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
        label = "CategoryColorAnimation",
    )

    val isTravelModeActive =
        remember(travelModeSettings, selectedDateTime) {
            travelModeSettings?.let {
                it.isEnabled &&
                    selectedDateTime.timeInMillis >= it.startDate &&
                    selectedDateTime.timeInMillis <= it.endDate
            } ?: false
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SpotlightBackground(color = animatedCategoryColor)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Compose Transaction") },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.clearAddTransactionState()
                            navController.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        HelpActionIcon(helpKey = "add_transaction")
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))

                AmountComposer(
                    amount = amount,
                    onAmountChange = { newValue ->
                        if (!hasInteracted) hasInteracted = true
                        if (newValue.length > 15) return@AmountComposer

                        val regex = Regex("^\\d*\\.?\\d{0,2}\$")
                        if (newValue.isEmpty() || regex.matches(newValue)) {
                            if ((newValue.toDoubleOrNull() ?: 0.0) <= 1_000_000_000.0) {
                                viewModel.onAddTransactionAmountChanged(newValue)
                            } else {
                                Toast.makeText(context, "Maximum limit of 1 Billion (1,000,000,000) reached.", Toast.LENGTH_SHORT).show()
                            }
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
                    hasInteractedWithNumpad = hasInteracted,
                )

                if (!isDescriptionEntered && recentManualTransactions.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    QuickFillCarousel(
                        recentTransactions = recentManualTransactions,
                        onQuickFillSelected = { txn ->
                            viewModel.onQuickFillSelected(txn)
                            transactionType = txn.transaction.transactionType
                        },
                        onViewAllClick = { activeSheet = ComposerSheet.History },
                    )
                } else if (isDescriptionEntered && merchantPredictions.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    PredictionCarousel(
                        predictions = merchantPredictions,
                        onPredictionSelected = { prediction ->
                            viewModel.onAddTransactionDescriptionChanged(prediction.description)
                            if (!hasInteracted) hasInteracted = true
                            prediction.categoryId?.let { catId ->
                                val cat = categories.find { it.id == catId }
                                viewModel.onAddTransactionCategoryChanged(cat)
                            }
                            prediction.accountId?.let { accId ->
                                val acc = accounts.find { it.id == accId }
                                viewModel.onAddTransactionAccountChanged(acc)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))

                TransactionTypeToggle(
                    selectedType = transactionType,
                    onTypeSelected = { transactionType = it },
                )

                Spacer(Modifier.height(24.dp))
                OrbitalChipsSection(
                    selectedCategory = selectedCategory,
                    selectedAccount = selectedAccount,
                    selectedDateTime = selectedDateTime.time,
                    notes = notes,
                    tags = selectedTags,
                    imageCount = attachedImageUris.size,
                    onCategoryClick = {
                        viewModel.onUserManuallySelectedCategory()
                        activeSheet = ComposerSheet.Category
                    },
                    onAccountClick = { activeSheet = ComposerSheet.Account },
                    onDateClick = { showDatePicker = true },
                    onNotesClick = { activeSheet = ComposerSheet.Notes },
                    onTagsClick = { activeSheet = ComposerSheet.Tags },
                    onAttachmentClick = { imagePickerLauncher.launch("image/*") },
                )
                Spacer(Modifier.height(16.dp))

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
                            onSaveComplete = handleSaveComplete,
                        )
                    },
                    enabled = isSaveEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                ) {
                    Text("Save Transaction", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AddTransactionBottomSheet(
        activeSheet = activeSheet,
        onDismissSheet = { activeSheet = null },
        sheetState = sheetState,
        containerColor = popupContainerColor,
        accounts = accounts,
        categories = categories,
        selectedCategory = selectedCategory,
        allTags = allTags,
        selectedTags = selectedTags,
        notes = notes,
        historyTransactions = historyManualTransactions,
        merchantDescription = description,
        viewModel = viewModel,
        isCategoryNudgeActive = categoryNudgeData != null,
        onCategoryNudgeSave = { txnId -> handleSaveComplete(txnId) },
        onNotesConfirmed = { notes = it },
        onQuickFillSelected = { txn ->
            viewModel.onQuickFillSelected(txn)
            transactionType = txn.transaction.transactionType
        },
        onOpenCreateAccount = { showCreateAccountDialog = true },
        onOpenCreateCategory = { showCreateCategoryDialog = true },
    )

    AddTransactionDialogs(
        showDatePicker = showDatePicker,
        onDismissDatePicker = { showDatePicker = false },
        showTimePicker = showTimePicker,
        onDismissTimePicker = { showTimePicker = false },
        showCreateAccountDialog = showCreateAccountDialog,
        onDismissCreateAccount = { showCreateAccountDialog = false },
        showCreateCategoryDialog = showCreateCategoryDialog,
        onDismissCreateCategory = { showCreateCategoryDialog = false },
        showGoalAllocationDialog = showGoalAllocationDialog,
        onDismissGoalAllocation = {
            showGoalAllocationDialog = null
            viewModel.clearAddTransactionState()
            navController.popBackStack()
        },
        selectedDateTime = selectedDateTime,
        amount = amount.toDoubleOrNull() ?: 0.0,
        popupContainerColor = popupContainerColor,
        activeGoals = activeGoals,
        isCategoryNudgeActive = categoryNudgeData != null,
        viewModel = viewModel,
        goalViewModel = goalViewModel,
        navController = navController,
        onOpenTimePicker = { showTimePicker = true },
    )
}
