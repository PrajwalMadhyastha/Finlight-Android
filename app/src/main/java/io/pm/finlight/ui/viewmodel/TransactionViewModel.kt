package io.pm.finlight

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.model.ManualTransactionData
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.data.model.TransactionFilterState
import io.pm.finlight.data.model.toTransactionType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.ui.viewmodel.AnalysisTransactionType
import io.pm.finlight.ui.viewmodel.delegate.TransactionBatchOperationDelegate
import io.pm.finlight.ui.viewmodel.delegate.TransactionMergeDelegate
import io.pm.finlight.ui.viewmodel.delegate.TransactionReimbursementDelegate
import io.pm.finlight.ui.viewmodel.delegate.TransactionSplitDelegate
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.DateUtils
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.HeuristicCategorizer
import io.pm.finlight.utils.ImageAttachmentHelper
import io.pm.finlight.utils.SmsProviderHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private const val TAG = "TransactionViewModel"

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionViewModel(
    application: Application,
    val transactionRepository: ITransactionRepository,
    val accountRepository: IAccountRepository,
    val categoryRepository: ICategoryRepository,
    private val tagRepository: ITagRepository,
    private val settingsRepository: ISettingsRepository,
    private val smsRepository: ISmsRepository,
    private val merchantRenameRuleRepository: IMerchantRenameRuleRepository,
    private val merchantCategoryMappingRepository: IMerchantCategoryMappingRepository,
    private val merchantMappingRepository: IMerchantMappingRepository,
    private val splitTransactionRepository: ISplitTransactionRepository,
    private val resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase,
    private val mergeTransactionsUseCase: MergeTransactionsUseCase,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val manageReimbursementUseCase: ManageReimbursementUseCase = ServiceLocator.provideManageReimbursementUseCase(application),
    private val autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase = ServiceLocator.provideAutoSaveSmsTransactionUseCase(application),
    private val customSmsRuleRepository: ICustomSmsRuleRepository = ServiceLocator.provideCustomSmsRuleRepository(application),
    private val ignoreRuleRepository: IIgnoreRuleRepository = ServiceLocator.provideIgnoreRuleRepository(application),
    private val smsParseTemplateRepository: ISmsParseTemplateRepository = ServiceLocator.provideSmsParseTemplateRepository(application),
    initialReimbursementDelegate: TransactionReimbursementDelegate? = null,
    initialMergeDelegate: TransactionMergeDelegate? = null,
    initialBatchOperationDelegate: TransactionBatchOperationDelegate? = null,
    initialSplitDelegate: TransactionSplitDelegate? = null,
) : AndroidViewModel(application) {
    private val context = application

    val reimbursementDelegate =
        initialReimbursementDelegate ?: TransactionReimbursementDelegate(
            viewModelScope, transactionRepository, manageReimbursementUseCase, dispatcherProvider,
        )
    val mergeDelegate =
        initialMergeDelegate ?: TransactionMergeDelegate(
            viewModelScope, mergeTransactionsUseCase, dispatcherProvider,
        )
    val batchOperationDelegate =
        initialBatchOperationDelegate ?: TransactionBatchOperationDelegate(
            viewModelScope, transactionRepository, merchantRenameRuleRepository,
            merchantCategoryMappingRepository, smsRepository, smsParseTemplateRepository, dispatcherProvider,
        )
    val splitDelegate =
        initialSplitDelegate ?: TransactionSplitDelegate(
            viewModelScope, splitTransactionRepository, transactionRepository, dispatcherProvider,
        )

    private var areTagsLoadedForCurrentTxn = false
    private var currentTxnIdForTags: Int? = null
    private var initialTransactionStateForRetroUpdate: Transaction? = null

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _filterState = MutableStateFlow(TransactionFilterState())
    val filterState = _filterState.asStateFlow()

    private val _showFilterSheet = MutableStateFlow(false)
    val showFilterSheet = _showFilterSheet.asStateFlow()

    private val _transactionForCategoryChange = MutableStateFlow<TransactionDetails?>(null)
    val transactionForCategoryChange = _transactionForCategoryChange.asStateFlow()

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    // -------------------------------------------------------------------------
    // Delegated State
    // -------------------------------------------------------------------------
    val reimbursementsForCurrentExpense = reimbursementDelegate.reimbursementsForCurrentExpense
    val candidateReimbursements = reimbursementDelegate.candidateReimbursements
    val linkedExpenseForCurrentIncome = reimbursementDelegate.linkedExpenseForCurrentIncome
    val showReimbursementPicker = reimbursementDelegate.showReimbursementPicker
    val canLinkAsReimbursement by lazy { reimbursementDelegate.canLinkAsReimbursement(selectedTransactionIds, transactionsForSelectedMonth) }

    val showReviewMergeSheet = mergeDelegate.showReviewMergeSheet
    val anchorTransactionId = mergeDelegate.anchorTransactionId
    val mergedTransactionBreakdown = mergeDelegate.mergedTransactionBreakdown
    val mergeValidationError = mergeDelegate.mergeValidationError
    val canManualMerge by lazy { mergeDelegate.canManualMerge(selectedTransactionIds, transactionsForSelectedMonth) }

    val isSelectionModeActive = batchOperationDelegate.isSelectionModeActive
    val selectedTransactionIds = batchOperationDelegate.selectedTransactionIds
    val showDeleteConfirmation = batchOperationDelegate.showDeleteConfirmation
    val showShareSheet = batchOperationDelegate.showShareSheet
    val shareableFields = batchOperationDelegate.shareableFields
    val retroUpdateSheetState = batchOperationDelegate.retroUpdateSheetState
    val canonicalNudgeState = batchOperationDelegate.canonicalNudgeState
    val navigateBackEvent = batchOperationDelegate.navigateBackEvent

    private val combinedState: Flow<Pair<Calendar, TransactionFilterState>> =
        _selectedMonth.combine(_filterState) { month, filters -> Pair(month, filters) }

    val hasSeenOnboarding: Flow<Boolean> = settingsRepository.getHasSeenOnboarding()
    val goalIncomeThreshold = settingsRepository.getGoalIncomeThreshold().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5000)

    val transactionsForSelectedMonth: StateFlow<List<TransactionDetails>>
    val monthlyIncome: StateFlow<Double>
    val monthlyExpenses: StateFlow<Double>
    val categorySpendingForSelectedMonth: StateFlow<List<CategorySpending>>
    val merchantSpendingForSelectedMonth: StateFlow<List<MerchantSpendingSummary>>
    val overallMonthlyBudget: StateFlow<Float>
    val amountRemaining: StateFlow<Float>
    val allAccounts = accountRepository.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allCategories: Flow<List<Category>> = categoryRepository.allCategories
    val allTags = tagRepository.allTags.onEach {}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError = _validationError.asStateFlow()
    private val _selectedTags = MutableStateFlow<Set<Tag>>(emptySet())
    val selectedTags = _selectedTags.asStateFlow()
    private val _transactionImages = MutableStateFlow<List<TransactionImage>>(emptyList())
    val transactionImages = _transactionImages.asStateFlow()
    val monthlySummaries: StateFlow<List<MonthlySummaryItem>>

    private val _defaultAccount = MutableStateFlow<Account?>(null)
    val defaultAccount = _defaultAccount.asStateFlow()
    private val _originalSmsText = MutableStateFlow<String?>(null)
    val originalSmsText = _originalSmsText.asStateFlow()
    private val _visitCount = MutableStateFlow(0)
    val visitCount = _visitCount.asStateFlow()

    val travelModeSettings = settingsRepository.getTravelModeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isPrivacyModeEnabled: StateFlow<Boolean> =
        settingsRepository.getPrivacyModeEnabled()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )
    private val _merchantSearchQuery = MutableStateFlow("")
    val merchantPredictions: StateFlow<List<MerchantPrediction>>

    private val _showCategoryNudge = MutableStateFlow<ManualTransactionData?>(null)
    val showCategoryNudge = _showCategoryNudge.asStateFlow()
    private val _addTransactionDescription = MutableStateFlow("")
    val addTransactionDescription = _addTransactionDescription.asStateFlow()
    private val _addTransactionAmount = MutableStateFlow("")
    val addTransactionAmount = _addTransactionAmount.asStateFlow()
    private val _addTransactionCategory = MutableStateFlow<Category?>(null)
    val addTransactionCategory = _addTransactionCategory.asStateFlow()
    private val _addTransactionAccount = MutableStateFlow<Account?>(null)
    val addTransactionAccount = _addTransactionAccount.asStateFlow()
    private val _userManuallySelectedCategory = MutableStateFlow(false)

    val suggestedCategory: StateFlow<Category?> =
        _addTransactionDescription
            .debounce(400)
            .combine(_userManuallySelectedCategory) { description, manualSelect -> description to manualSelect }
            .flatMapLatest { (description, manualSelect) ->
                if (description.length > 2 && !manualSelect) {
                    flow {
                        val allCategoriesList = categoryRepository.getAllCategoriesSnapshot()
                        emit(HeuristicCategorizer.findCategoryForDescription(description, allCategoriesList))
                    }
                } else {
                    flowOf<Category?>(null)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentManualTransactions: StateFlow<List<TransactionDetails>> =
        transactionRepository.getRecentManualTransactions(limit = 10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyManualTransactions: StateFlow<List<TransactionDetails>> =
        transactionRepository.getRecentManualTransactions(limit = 50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        merchantPredictions =
            _merchantSearchQuery.debounce(300).flatMapLatest { query ->
                if (query.length > 1) transactionRepository.searchMerchants(query) else flowOf(emptyList())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        transactionsForSelectedMonth =
            combinedState.flatMapLatest { (calendar, filters) ->
                val (monthStart, monthEnd) = DateUtils.getCurrentMonthDateRange(calendar)
                transactionRepository.getTransactionDetailsForRange(
                    monthStart, monthEnd,
                    filters.keyword.takeIf { it.isNotBlank() },
                    filters.account?.id, filters.category?.id,
                ).catch { e ->
                    Log.e(TAG, "Failed to load transactions for month", e)
                    _uiEvent.send("Failed to load transactions.")
                    emit(emptyList())
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val financialSummaryFlow =
            _selectedMonth.flatMapLatest { calendar ->
                val (monthStart, monthEnd) = DateUtils.getCurrentMonthDateRange(calendar)
                transactionRepository.getFinancialSummaryForRangeFlow(monthStart, monthEnd)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        monthlyIncome = financialSummaryFlow.map { it?.totalIncome ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
        monthlyExpenses = financialSummaryFlow.map { it?.totalExpenses ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        categorySpendingForSelectedMonth =
            combinedState.flatMapLatest { (calendar, filters) ->
                val (monthStart, monthEnd) = DateUtils.getCurrentMonthDateRange(calendar)
                transactionRepository.getSpendingByCategoryForMonth(
                    monthStart, monthEnd,
                    filters.keyword.takeIf { it.isNotBlank() },
                    filters.account?.id, filters.category?.id, filters.transactionType.toTransactionType(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        merchantSpendingForSelectedMonth =
            combinedState.flatMapLatest { (calendar, filters) ->
                val (monthStart, monthEnd) = DateUtils.getCurrentMonthDateRange(calendar)
                transactionRepository.getSpendingByMerchantForMonth(
                    monthStart, monthEnd,
                    filters.keyword.takeIf { it.isNotBlank() },
                    filters.account?.id, filters.category?.id, filters.transactionType.toTransactionType(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        monthlySummaries =
            transactionRepository.getFirstTransactionDate().flatMapLatest { firstTxnDate ->
                val startDate = firstTxnDate ?: System.currentTimeMillis()
                transactionRepository.getMonthlyTrends(startDate).map { MonthlySummaryItem.fromTrends(it, startDate) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        overallMonthlyBudget =
            _selectedMonth.flatMapLatest {
                settingsRepository.getOverallBudgetForMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
            }.map { it ?: 0f }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        amountRemaining =
            combine(overallMonthlyBudget, monthlyExpenses) { budget, expenses -> budget - expenses.toFloat() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        viewModelScope.launch {
            _defaultAccount.value = accountRepository.findByName("Cash Spends")
        }
    }

    fun loadTransactionForDetailScreen(transactionId: Int) {
        viewModelScope.launch {
            initialTransactionStateForRetroUpdate = transactionRepository.getTransactionById(transactionId).first()
            initialTransactionStateForRetroUpdate?.let {
                loadTagsForTransaction(it.id)
                loadImagesForTransaction(it.id)
                loadOriginalSms(it.sourceSmsId)
                loadVisitCount(it.description)
                reimbursementDelegate.loadReimbursementData(it)
            }
        }
        mergeDelegate.loadMergedTransactionBreakdown(transactionId)
    }

    // -------------------------------------------------------------------------
    // Delegated Forwarders
    // -------------------------------------------------------------------------
    fun openReimbursementPicker(expenseId: Int) = reimbursementDelegate.openReimbursementPicker(expenseId)

    fun dismissReimbursementPicker() = reimbursementDelegate.dismissReimbursementPicker()

    fun linkReimbursement(
        incomeId: Int,
        expenseId: Int
    ) = reimbursementDelegate.linkReimbursement(incomeId, expenseId)

    fun unlinkReimbursement(incomeId: Int) = reimbursementDelegate.unlinkReimbursement(incomeId)

    fun resetDetailReimbursementState() = reimbursementDelegate.resetDetailReimbursementState()

    fun resetMergedTransactionBreakdown() = mergeDelegate.resetBreakdown()

    fun linkReimbursementFromSelection() =
        reimbursementDelegate.linkReimbursementFromSelection(
            selectedTransactionIds.value, transactionsForSelectedMonth.value, { _uiEvent.send(it) }, { clearSelectionMode() },
        )

    fun openReviewMergeSheet() = mergeDelegate.openReviewMergeSheet(selectedTransactionIds.value, transactionsForSelectedMonth.value)

    fun dismissReviewMergeSheet() = mergeDelegate.dismissReviewMergeSheet()

    fun setAnchorTransaction(transactionId: Int) = mergeDelegate.setAnchorTransaction(transactionId, selectedTransactionIds.value)

    fun confirmManualMerge() =
        mergeDelegate.confirmManualMerge(selectedTransactionIds.value, { _uiEvent.send(it) }, { clearSelectionMode() })

    fun observeMergeRecord(parentTxnId: Int) = mergeDelegate.observeMergeRecord(parentTxnId)

    fun unmergeTransaction(parentTxnId: Int) = mergeDelegate.unmergeTransaction(parentTxnId) { _uiEvent.send(it) }

    fun enterSelectionMode(initialTransactionId: Int) = batchOperationDelegate.enterSelectionMode(initialTransactionId)

    fun toggleTransactionSelection(transactionId: Int) = batchOperationDelegate.toggleTransactionSelection(transactionId)

    fun clearSelectionMode() = batchOperationDelegate.clearSelectionMode()

    fun onDeleteSelectionClick() = batchOperationDelegate.onDeleteSelectionClick()

    fun onCancelDeleteSelection() = batchOperationDelegate.onCancelDeleteSelection()

    fun onConfirmDeleteSelection() = batchOperationDelegate.onConfirmDeleteSelection { _uiEvent.send(it) }

    fun onShareClick() = batchOperationDelegate.onShareClick()

    fun onShareSheetDismiss() = batchOperationDelegate.onShareSheetDismiss()

    fun onShareableFieldToggled(field: ShareableField) = batchOperationDelegate.onShareableFieldToggled(field)

    fun generateAndShareSnapshot(context: Context) = batchOperationDelegate.generateAndShareSnapshot(context, transactionsForSelectedMonth.value)

    fun onAttemptToLeaveScreen(onNavigationAllowed: () -> Unit) =
        batchOperationDelegate.onAttemptToLeaveScreen(initialTransactionStateForRetroUpdate, onNavigationAllowed)

    fun dismissRetroUpdateSheet() = batchOperationDelegate.dismissRetroUpdateSheet()

    fun toggleRetroUpdateSelection(id: Int) = batchOperationDelegate.toggleRetroUpdateSelection(id)

    fun toggleRetroUpdateSelectAll() = batchOperationDelegate.toggleRetroUpdateSelectAll()

    fun toggleUpdateFutureTransactions() = batchOperationDelegate.toggleUpdateFutureTransactions()

    fun performBatchUpdate() = batchOperationDelegate.performBatchUpdate(initialTransactionStateForRetroUpdate) { _uiEvent.send(it) }

    fun onRetroSheetSkipped() = batchOperationDelegate.onRetroSheetSkipped()

    fun toggleCanonicalVariant(rawName: String) = batchOperationDelegate.toggleCanonicalVariant(rawName)

    fun confirmCanonicalNudge() = batchOperationDelegate.confirmCanonicalNudge { _uiEvent.send(it) }

    fun dismissCanonicalNudge() = batchOperationDelegate.dismissCanonicalNudge()

    fun getSplitDetailsForTransaction(transactionId: Int): Flow<List<SplitTransactionDetails>> = splitDelegate.getSplitDetailsForTransaction(transactionId)

    fun saveTransactionSplits(
        parentTransactionId: Int,
        splitItems: List<SplitItem>,
        onComplete: () -> Unit,
    ) = splitDelegate.saveTransactionSplits(parentTransactionId, splitItems, onComplete)

    fun unsplitTransaction(transaction: Transaction) = splitDelegate.unsplitTransaction(transaction)

    // -------------------------------------------------------------------------
    // Add Transaction & Category / Merchant Search Logic
    // -------------------------------------------------------------------------
    fun onMerchantSearchQueryChanged(query: String) {
        _merchantSearchQuery.value = query
    }

    fun clearMerchantSearch() {
        _merchantSearchQuery.value = ""
    }

    fun requestCategoryChange(details: TransactionDetails) {
        _transactionForCategoryChange.value = details
    }

    fun cancelCategoryChange() {
        _transactionForCategoryChange.value = null
    }

    fun findTransactionDetailsById(id: Int): Flow<TransactionDetails?> = transactionRepository.getTransactionDetailsById(id)

    private fun loadVisitCount(description: String) {
        viewModelScope.launch { transactionRepository.getTransactionCountForMerchant(description).collect { _visitCount.value = it } }
    }

    fun onAddTransactionDescriptionChanged(description: String) {
        _addTransactionDescription.value = description
        _merchantSearchQuery.value = description
        if (description.isBlank()) _userManuallySelectedCategory.value = false
    }

    fun onAddTransactionAmountChanged(amount: String) {
        _addTransactionAmount.value = amount
    }

    fun onAddTransactionCategoryChanged(category: Category?) {
        _addTransactionCategory.value = category
        if (category != null) _userManuallySelectedCategory.value = true
    }

    fun onAddTransactionAccountChanged(account: Account?) {
        _addTransactionAccount.value = account
    }

    fun onUserManuallySelectedCategory() {
        _userManuallySelectedCategory.value = true
    }

    fun onQuickFillSelected(transactionDetails: TransactionDetails) {
        _addTransactionDescription.value = transactionDetails.transaction.description
        val amount = transactionDetails.transaction.amount
        _addTransactionAmount.value = if (amount % 1.0 == 0.0) amount.toInt().toString() else "%.2f".format(amount)

        viewModelScope.launch {
            val category = transactionDetails.transaction.categoryId?.let { categoryRepository.getCategoryById(it) }
            val account = accountRepository.getAccountByIdSync(transactionDetails.transaction.accountId)
            _addTransactionCategory.value = category
            _userManuallySelectedCategory.value = true
            _addTransactionAccount.value = account
            _selectedTags.value = transactionRepository.getTagsForTransactionSimple(transactionDetails.transaction.id).toSet()
        }
    }

    fun onSaveTapped(
        description: String,
        amountStr: String,
        accountId: Int?,
        categoryId: Int?,
        notes: String?,
        date: Long,
        transactionType: TransactionType,
        imageUris: List<Uri>,
        onSaveComplete: (Long?) -> Unit,
    ) {
        viewModelScope.launch {
            _validationError.value = null

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                _validationError.value = "Please enter a valid, positive amount."
                return@launch
            }
            if (amount > 1_000_000_000.0) {
                _validationError.value = "Maximum limit of 1 Billion (1,000,000,000) reached."
                return@launch
            }
            if (accountId == null) {
                _validationError.value = "An account must be selected."
                return@launch
            }

            val finalDescription = description.ifBlank { "Unknown" }

            val transactionData =
                ManualTransactionData(
                    description = finalDescription,
                    amountStr = amountStr,
                    accountId = accountId,
                    notes = notes,
                    date = date,
                    transactionType = transactionType,
                    imageUris = imageUris,
                    tags = _selectedTags.value,
                )

            if (categoryId == null && description.isNotBlank()) {
                _showCategoryNudge.value = transactionData
            } else {
                saveManualTransaction(transactionData, categoryId)?.let(onSaveComplete)
            }
        }
    }

    fun onSaveTapped(
        description: String,
        amountStr: String,
        accountId: Int?,
        categoryId: Int?,
        notes: String?,
        date: Long,
        transactionType: String,
        imageUris: List<Uri>,
        onSaveComplete: (Long?) -> Unit,
    ) = onSaveTapped(
        description, amountStr, accountId, categoryId, notes, date,
        TransactionType.fromString(transactionType), imageUris, onSaveComplete,
    )

    fun saveWithSelectedCategory(
        categoryId: Int?,
        onComplete: (Long?) -> Unit,
    ) {
        viewModelScope.launch {
            val transactionData = _showCategoryNudge.value
            if (transactionData != null) {
                saveManualTransaction(transactionData, categoryId)?.let(onComplete)
            }
            _showCategoryNudge.value = null
        }
    }

    private suspend fun saveManualTransaction(
        data: ManualTransactionData,
        categoryId: Int?,
    ): Long? {
        _validationError.value = null
        val transactionToSave = data.toTransaction(categoryId, travelModeSettings.value)
        return try {
            val savedImagePaths = data.imageUris.mapNotNull { saveImageToInternalStorage(it) }
            val finalTags = resolveTravelModeTagUseCase.getFinalTags(transactionToSave.date, data.tags, travelModeSettings.value)
            transactionRepository.insertTransactionWithTagsAndImages(transactionToSave, finalTags, savedImagePaths)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transaction", e)
            _validationError.value = "An error occurred while saving."
            null
        }
    }

    fun clearAddTransactionState() {
        _selectedTags.value = emptySet()
        _addTransactionDescription.value = ""
        _addTransactionAmount.value = ""
        _addTransactionCategory.value = null
        if (_addTransactionAccount.value != _defaultAccount.value) _addTransactionAccount.value = null
        _userManuallySelectedCategory.value = false
        _validationError.value = null
        _showCategoryNudge.value = null
        clearMerchantSearch()
    }

    private fun loadOriginalSms(sourceSmsId: Long?) {
        if (sourceSmsId == null) {
            _originalSmsText.value = null
            return
        }
        viewModelScope.launch { _originalSmsText.value = getOriginalSmsMessage(sourceSmsId)?.body }
    }

    fun clearOriginalSms() {
        _originalSmsText.value = null
    }

    suspend fun getOriginalSmsMessage(smsId: Long): SmsMessage? = smsRepository.getSmsDetailsById(smsId)

    // -------------------------------------------------------------------------
    // SMS Reparsing Logic
    // -------------------------------------------------------------------------
    fun reparseTransactionFromSms(transactionId: Int) {
        val logTag = "ReparseSms"
        Log.d(logTag, "--- Starting reparse for transactionId: $transactionId ---")
        viewModelScope.launch {
            val transaction =
                transactionRepository.getTransactionById(transactionId).first() ?: run {
                    Log.w(logTag, "FAILURE: Transaction with ID $transactionId not found.")
                    return@launch
                }

            val sourceSmsId =
                transaction.sourceSmsId ?: run {
                    Log.w(logTag, "FAILURE: Transaction $transactionId has no sourceSmsId.")
                    return@launch
                }

            val smsMessage =
                smsRepository.getSmsDetailsById(sourceSmsId) ?: run {
                    Log.w(logTag, "FAILURE: Could not find original SMS for sourceSmsId: $sourceSmsId")
                    return@launch
                }

            val existingMappings = merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })

            val parseResult =
                SmsParser.parseWithReason(
                    sms = smsMessage,
                    mappings = existingMappings,
                    customSmsRuleProvider = SmsProviderHelper.getCustomSmsRuleProvider(customSmsRuleRepository),
                    merchantRenameRuleProvider = SmsProviderHelper.getMerchantRenameRuleProvider(merchantRenameRuleRepository),
                    ignoreRuleProvider = SmsProviderHelper.getIgnoreRuleProvider(ignoreRuleRepository),
                    merchantCategoryMappingProvider = SmsProviderHelper.getMerchantCategoryMappingProvider(merchantCategoryMappingRepository),
                    categoryFinderProvider = SmsProviderHelper.getCategoryFinderProvider(),
                    smsParseTemplateProvider = smsParseTemplateRepository,
                )

            if (parseResult is ParseResult.Success) {
                parseResult.newlyDiscoveredRenameAlias?.let { (oldName, newName) ->
                    merchantRenameRuleRepository.insert(MerchantRenameRule(oldName, newName))
                }
                parseResult.newlyDiscoveredCategoryAlias?.let { (merchant, catId) ->
                    merchantCategoryMappingRepository.insert(MerchantCategoryMapping(merchant, catId))
                }

                val potentialTxn = parseResult.transaction
                val merchant = potentialTxn.merchantName
                if (merchant != null && merchant != transaction.description) {
                    transactionRepository.updateDescription(transactionId, merchant)
                }

                potentialTxn.categoryId?.let {
                    if (it != transaction.categoryId) {
                        transactionRepository.updateCategoryId(transactionId, it)
                    }
                }

                potentialTxn.potentialAccount?.let { parsedAccount ->
                    val sanitizedName = SmsParser.sanitizeAccountName(parsedAccount.formattedName)
                    val currentAccount = accountRepository.getAccountByIdSync(transaction.accountId)
                    if (currentAccount?.name?.equals(sanitizedName, ignoreCase = true) == false) {
                        val account = accountRepository.findOrCreateByName(sanitizedName, parsedAccount.accountType)
                        transactionRepository.updateAccountId(transactionId, account.id)
                    }
                }
            }
            Log.d(logTag, "--- Reparse finished for transactionId: $transactionId ---")
        }
    }

    // -------------------------------------------------------------------------
    // Filters & Navigation
    // -------------------------------------------------------------------------
    fun updateFilterKeyword(keyword: String) {
        _filterState.update { it.copy(keyword = keyword) }
    }

    fun updateFilterAccount(account: Account?) {
        _filterState.update { it.copy(account = account) }
    }

    fun updateFilterCategory(category: Category?) {
        _filterState.update { it.copy(category = category) }
    }

    fun updateFilterTransactionType(type: AnalysisTransactionType) {
        _filterState.update { it.copy(transactionType = type) }
    }

    fun clearFilters() {
        _filterState.value = TransactionFilterState()
    }

    fun onFilterClick() {
        _showFilterSheet.value = true
    }

    fun onFilterSheetDismiss() {
        _showFilterSheet.value = false
    }

    fun setSelectedMonth(calendar: Calendar) {
        _selectedMonth.value = calendar
    }

    // -------------------------------------------------------------------------
    // Account & Category Creation
    // -------------------------------------------------------------------------
    fun createAccount(
        name: String,
        type: String,
        onAccountCreated: (Account) -> Unit
    ) {
        if (name.isBlank() || type.isBlank()) return
        viewModelScope.launch {
            if (accountRepository.findByName(name) != null) {
                _validationError.value = "An account named '$name' already exists."
                return@launch
            }
            val newAccountId = accountRepository.insert(Account(name = name, type = type))
            accountRepository.getAccountByIdSync(newAccountId.toInt())?.let(onAccountCreated)
        }
    }

    fun createCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        onCategoryCreated: (Category) -> Unit
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (categoryRepository.findByName(name) != null) {
                _validationError.value = "A category named '$name' already exists."
                return@launch
            }
            val usedColorKeys = categoryRepository.getAllCategoriesSnapshot().map { it.colorKey }
            val finalIconKey = if (iconKey == "category") "letter_default" else iconKey
            val finalColorKey = if (colorKey == "gray_light") CategoryIconHelper.getNextAvailableColor(usedColorKeys) else colorKey
            val newCategoryId = categoryRepository.insert(Category(name = name, iconKey = finalIconKey, colorKey = finalColorKey))
            categoryRepository.getCategoryById(newCategoryId.toInt())?.let(onCategoryCreated)
        }
    }

    // -------------------------------------------------------------------------
    // Images & Attachments
    // -------------------------------------------------------------------------
    fun attachPhotoToTransaction(
        transactionId: Int,
        sourceUri: Uri,
    ) = viewModelScope.launch {
        saveImageToInternalStorage(sourceUri)?.let { transactionRepository.addImageToTransaction(transactionId, it) }
    }

    fun deleteTransactionImage(image: TransactionImage) {
        viewModelScope.launch {
            transactionRepository.deleteImage(image)
            withContext(dispatcherProvider.io) {
                try {
                    if (!File(image.imageUri).delete()) Log.w(TAG, "Failed to delete image file: ${image.imageUri}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete image file: ${image.imageUri}", e)
                }
            }
        }
    }

    private fun loadImagesForTransaction(transactionId: Int) =
        viewModelScope.launch {
            transactionRepository.getImagesForTransaction(transactionId).collect { _transactionImages.value = it }
        }

    private suspend fun saveImageToInternalStorage(sourceUri: Uri): String? =
        ImageAttachmentHelper.saveImageToInternalStorage(context, sourceUri, dispatcherProvider)

    // -------------------------------------------------------------------------
    // Single Transaction In-Place Edits
    // -------------------------------------------------------------------------
    fun updateTransactionDescription(
        id: Int,
        newDescription: String
    ) =
        viewModelScope.launch {
            try {
                if (newDescription.isNotBlank()) {
                    val transaction = transactionRepository.getTransactionById(id).firstOrNull()
                    if (transaction?.originalDescription?.equals(newDescription, ignoreCase = true) == true) {
                        merchantRenameRuleRepository.deleteByOriginalName(transaction.originalDescription)
                    }
                    transactionRepository.updateDescription(id, newDescription)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update transaction description", e)
                _uiEvent.send("Failed to update description. Please try again.")
            }
        }

    fun updateTransactionAmount(
        id: Int,
        amountStr: String
    ) =
        viewModelScope.launch {
            amountStr.toDoubleOrNull()?.let {
                if (it > 1_000_000_000.0) {
                    _validationError.value = "Maximum limit of 1 Billion (1,000,000,000) reached."
                    return@launch
                }
                if (it > 0) transactionRepository.updateManualAmountEdit(id, it)
            }
        }

    fun updateTransactionNotes(
        id: Int,
        notes: String
    ) =
        viewModelScope.launch { transactionRepository.updateNotes(id, notes.takeIf { it.isNotBlank() }) }

    fun updateTransactionCategory(
        id: Int,
        categoryId: Int?
    ) =
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(id).first() ?: return@launch
            transactionRepository.updateCategoryId(id, categoryId)
            val originalDescription = transaction.originalDescription
            if (categoryId != null && !originalDescription.isNullOrBlank()) {
                merchantCategoryMappingRepository.insert(MerchantCategoryMapping(parsedName = originalDescription, categoryId = categoryId))
            }
        }

    fun updateTransactionAccount(
        id: Int,
        accountId: Int
    ) =
        viewModelScope.launch { transactionRepository.updateAccountId(id, accountId) }

    fun updateTransactionDate(
        id: Int,
        date: Long
    ) =
        viewModelScope.launch { transactionRepository.updateDate(id, date) }

    fun updateTransactionExclusion(
        id: Int,
        isExcluded: Boolean
    ) =
        viewModelScope.launch { transactionRepository.updateExclusionStatus(id, isExcluded) }

    fun updateTransactionType(
        id: Int,
        transactionType: TransactionType
    ) =
        viewModelScope.launch {
            try {
                transactionRepository.updateTransactionType(id, transactionType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update transaction type", e)
                _uiEvent.send("Failed to update transaction type.")
            }
        }

    fun updateAnchorDetailsInPlace(
        id: Int,
        description: String,
        categoryId: Int?,
        notes: String
    ) =
        viewModelScope.launch {
            transactionRepository.updateDescription(id, description)
            transactionRepository.updateCategoryId(id, categoryId)
            transactionRepository.updateNotes(id, notes)
        }

    fun markAsReviewed(transactionId: Int) =
        viewModelScope.launch {
            try {
                transactionRepository.clearReviewFlag(transactionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear review flag", e)
                _uiEvent.send("Failed to mark as reviewed.")
            }
        }

    fun updateTagsForTransaction(transactionId: Int) =
        viewModelScope.launch {
            try {
                transactionRepository.updateTagsForTransaction(transactionId, _selectedTags.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update tags", e)
                _uiEvent.send("Failed to update tags. Please try again.")
            }
        }

    fun onTagSelected(tag: Tag) {
        _selectedTags.update { if (tag in it) it - tag else it + tag }
    }

    fun addTagOnTheGo(tagName: String) {
        if (tagName.isBlank()) return
        viewModelScope.launch {
            try {
                if (tagRepository.findByName(tagName) != null) {
                    _validationError.value = "A tag named '$tagName' already exists."
                    return@launch
                }
                val newTag = Tag(name = tagName)
                val newId = tagRepository.insert(newTag)
                if (newId != -1L) _selectedTags.update { it + newTag.copy(id = newId.toInt()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add new tag", e)
                _uiEvent.send("Failed to add new tag. Please try again.")
            }
        }
    }

    private fun loadTagsForTransaction(transactionId: Int) {
        if (currentTxnIdForTags == transactionId && areTagsLoadedForCurrentTxn) return
        viewModelScope.launch {
            _selectedTags.value = transactionRepository.getTagsForTransaction(transactionId).first().toSet()
            areTagsLoadedForCurrentTxn = true
            currentTxnIdForTags = transactionId
        }
    }

    fun clearSelectedTags() {
        _selectedTags.value = emptySet()
        areTagsLoadedForCurrentTxn = false
        currentTxnIdForTags = null
    }

    suspend fun approveSmsTransaction(
        potentialTxn: PotentialTransaction,
        description: String,
        categoryId: Int?,
        notes: String?,
        tags: Set<Tag>,
        isForeign: Boolean,
    ): Boolean =
        withContext(dispatcherProvider.io) {
            try {
                if (potentialTxn.amount <= 0.0 || potentialTxn.amount.isNaN() || potentialTxn.amount.isInfinite()) {
                    Log.e(TAG, "Invalid transaction amount: ${potentialTxn.amount}. Approve dropped.")
                    return@withContext false
                }

                if (autoSaveSmsTransactionUseCase.isDuplicateOrDeleted(potentialTxn)) return@withContext false

                val accountName = SmsParser.sanitizeAccountName(potentialTxn.potentialAccount?.formattedName)
                val accountType = potentialTxn.potentialAccount?.accountType ?: "General"
                val account = accountRepository.findOrCreateByName(accountName, accountType)

                val currentTravel = travelModeSettings.value
                if (isForeign && currentTravel == null) {
                    Log.e(TAG, "Attempted to save foreign SMS transaction, but Travel Mode is not configured.")
                    return@withContext false
                }
                val rawRate = currentTravel?.conversionRate?.toDouble()
                val rate = if (isForeign && rawRate != null && rawRate > 0.0 && !rawRate.isNaN() && !rawRate.isInfinite()) rawRate else 1.0
                val currencyCode = if (isForeign) currentTravel?.currencyCode else null
                val originalAmt = if (isForeign) potentialTxn.amount else null

                val transactionToSave =
                    Transaction(
                        description = description,
                        originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                        categoryId = categoryId,
                        amount = potentialTxn.amount * rate,
                        date = potentialTxn.date,
                        accountId = account.id,
                        notes = notes,
                        transactionType = TransactionType.fromString(potentialTxn.transactionType),
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                        source = "Imported",
                        originalAmount = originalAmt,
                        currencyCode = currencyCode,
                        conversionRate = if (isForeign) rate else null,
                    )

                val finalTags = resolveTravelModeTagUseCase.getFinalTags(transactionToSave.date, tags, currentTravel)
                val newId = transactionRepository.insertTransactionWithTags(transactionToSave, finalTags)
                if (newId <= 0L) return@withContext false

                val merchantName = potentialTxn.merchantName
                if (categoryId != null && merchantName != null) {
                    merchantCategoryMappingRepository.insert(MerchantCategoryMapping(parsedName = merchantName, categoryId = categoryId))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to approve SMS transaction", e)
                false
            }
        }

    suspend fun autoSaveSmsTransaction(
        potentialTxn: PotentialTransaction,
        source: String = "Auto-Captured"
    ): Boolean =
        autoSaveSmsTransactionUseCase(potentialTxn = potentialTxn, source = source, travelSettings = travelModeSettings.value)

    fun deleteTransaction(transaction: Transaction) =
        viewModelScope.launch {
            try {
                transaction.sourceSmsHash?.let { hash -> transactionRepository.insertDeletedSmsHash(hash) }
                transactionRepository.delete(transaction)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete transaction", e)
                _uiEvent.send("Failed to delete transaction. Please try again.")
            }
        }

    fun clearError() {
        _validationError.value = null
    }
}
