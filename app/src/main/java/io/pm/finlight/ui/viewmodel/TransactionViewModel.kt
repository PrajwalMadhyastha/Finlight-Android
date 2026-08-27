// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TransactionViewModel.kt
// REASON: FEATURE (Quick Fill) - Added `recentManualTransactions` StateFlow to
// expose suggestions to the UI. Added `onQuickFillSelected` function to populate
// the AddTransaction state fields when a suggestion is clicked. This significantly
// speeds up manual entry for recurring expenses.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.pm.finlight.core.utils.StringSimilarity
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.data.model.MergedTransactionItem
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.ui.viewmodel.AnalysisTransactionType
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.FormatUtils
import io.pm.finlight.utils.HeuristicCategorizer
import io.pm.finlight.utils.applyAliases
import io.pm.finlight.utils.ShareImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "TransactionViewModel"

data class TransactionFilterState(
    val keyword: String = "",
    val account: Account? = null,
    val category: Category? = null,
    val transactionType: AnalysisTransactionType = AnalysisTransactionType.EXPENSE,
)

data class RetroUpdateSheetState(
    val originalDescription: String,
    val newDescription: String? = null,
    val newCategoryId: Int? = null,
    val similarTransactions: List<Transaction> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    /** Total number of transactions sharing this originalDescription, INCLUDING the one
     *  currently being edited (which is excluded from [similarTransactions]). Used to
     *  correctly determine whether the user selected ALL affected transactions so a global
     *  rename rule can be saved or deleted. */
    val totalMatchingCount: Int = 0,
    // --- FIX (#224/#225): Controls whether to upsert the MerchantRenameRule /
    // MerchantCategoryMapping for future SMS. Independent of past-transaction selection.
    val updateFutureTransactions: Boolean = true,
)

data class ManualTransactionData(
    val description: String,
    val amountStr: String,
    val accountId: Int,
    val notes: String?,
    val date: Long,
    val transactionType: TransactionType,
    val imageUris: List<Uri>,
    val tags: Set<Tag>,
)

/**
 * Represents a single cross-account variant of a merchant: a raw extracted name that
 * maps to the same canonical merchant but came from a different bank's SMS format.
 */
data class CanonicalVariant(
    val rawName: String,
    val transactionCount: Int,
    val transactionIds: List<Int>,
)

/**
 * State for the canonical nudge sheet shown after a rename rule is saved.
 * Surfaces historical transactions from other accounts whose raw merchant name
 * is canonically equivalent but was not yet renamed.
 *
 * @param canonicalName The user-chosen display name (e.g. "Swiggy").
 * @param variants Unmatched raw merchant names that token-contain the canonical name.
 * @param selectedRawNames The set of raw names the user has checked for bulk-rename.
 */
data class CanonicalNudgeSheetState(
    val canonicalName: String,
    val variants: List<CanonicalVariant>,
    val selectedRawNames: Set<String> = variants.map { it.rawName }.toSet(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionViewModel(
    application: Application,
    private val db: AppDatabase,
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
    private val smsParseTemplateDao: SmsParseTemplateDao,
    private val resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase,
    private val mergeTransactionsUseCase: MergeTransactionsUseCase =
        MergeTransactionsUseCase(
            transactionQueryDao = db.transactionQueryDao(),
            transactionWriteDao = db.transactionWriteDao(),
            transactionReimbursementDao = db.transactionReimbursementDao(),
            mergeRecordDao = db.mergeRecordDao(),
            deletedSmsHashDao = db.deletedSmsHashDao(),
            db = db,
        ),
) : AndroidViewModel(application) {
    private val context = application

    private var areTagsLoadedForCurrentTxn = false
    private var currentTxnIdForTags: Int? = null

    private var initialTransactionStateForRetroUpdate: Transaction? = null

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth: StateFlow<Calendar> = _selectedMonth.asStateFlow()

    private val _filterState = MutableStateFlow(TransactionFilterState())
    val filterState: StateFlow<TransactionFilterState> = _filterState.asStateFlow()

    private val _showFilterSheet = MutableStateFlow(false)
    val showFilterSheet: StateFlow<Boolean> = _showFilterSheet.asStateFlow()

    private val _transactionForCategoryChange = MutableStateFlow<TransactionDetails?>(null)
    val transactionForCategoryChange: StateFlow<TransactionDetails?> = _transactionForCategoryChange.asStateFlow()

    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive: StateFlow<Boolean> = _isSelectionModeActive.asStateFlow()

    private val _selectedTransactionIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTransactionIds: StateFlow<Set<Int>> = _selectedTransactionIds.asStateFlow()

    // --- NEW: True when selection has exactly 1 expense + 1+ incomes (enables "Link Repayment" action) ---
    val canLinkAsReimbursement: StateFlow<Boolean> by lazy {
        combine(_selectedTransactionIds, transactionsForSelectedMonth) { ids, txns ->
            if (ids.size < 2) return@combine false
            val selected = txns.filter { it.transaction.id in ids }
            val expenseCount = selected.count { it.transaction.transactionType == TransactionType.EXPENSE }
            val incomeCount = selected.count { it.transaction.transactionType == TransactionType.INCOME }
            expenseCount == 1 && incomeCount >= 1
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    }

    // --- NEW: Manual Merge — validation state for the Merge action button ───

    /**
     * Emits a human-readable explanation of why the Merge action is disabled,
     * or null when it is valid. Observed by the UI to show a Snackbar.
     */
    private val _mergeValidationError = MutableStateFlow<String?>(null)
    val mergeValidationError: StateFlow<String?> = _mergeValidationError.asStateFlow()

    /**
     * True when the selection is a valid candidate for manual merging:
     *  - At least 2 transactions selected.
     *  - No split transactions in the selection.
     *  - No PENDING/SKIPPED draft transactions in the selection.
     *
     * Cross-account merges are now permitted. The anchor's account absorbs the
     * total; the detail screen surfaces per-account contributions via
     * [mergedAccountBreakdown] and [MergedAccountsCard].
     */
    val canManualMerge: StateFlow<Boolean> by lazy {
        combine(_selectedTransactionIds, transactionsForSelectedMonth) { ids, txns ->
            if (ids.size < 2) return@combine false
            val selected = txns.filter { it.transaction.id in ids }
            val hasSplit = selected.any { it.transaction.isSplit }
            val hasPending = selected.any { it.transaction.status == TransactionStatus.PENDING || it.transaction.status == TransactionStatus.SKIPPED }
            !hasSplit && !hasPending
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    }

    /** Shown when the user taps Merge — displays the review sheet before committing. */
    private val _showReviewMergeSheet = MutableStateFlow(false)
    val showReviewMergeSheet: StateFlow<Boolean> = _showReviewMergeSheet.asStateFlow()

    /**
     * The ID of the anchor transaction chosen in the review sheet.
     * Defaults to the transaction with the largest amount when the sheet opens.
     */
    private val _anchorTransactionId = MutableStateFlow<Int?>(null)
    val anchorTransactionId: StateFlow<Int?> = _anchorTransactionId.asStateFlow()

    /**
     * Per-account contribution breakdown for a merged transaction.
     * Populated when [loadTransactionForDetailScreen] is called.
     * Empty for transactions that have never been merged, or for same-account merges
     * where the UI falls back to the normal single-account [AccountCard].
     */
    private val _mergedTransactionBreakdown = MutableStateFlow<List<io.pm.finlight.data.model.MergedTransactionItem>>(emptyList())
    val mergedTransactionBreakdown: StateFlow<List<io.pm.finlight.data.model.MergedTransactionItem>> = _mergedTransactionBreakdown.asStateFlow()

    private val _showShareSheet = MutableStateFlow(false)
    val showShareSheet: StateFlow<Boolean> = _showShareSheet.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _shareableFields =
        MutableStateFlow(
            setOf(ShareableField.Date, ShareableField.Description, ShareableField.Amount, ShareableField.Category, ShareableField.Tags),
        )
    val shareableFields: StateFlow<Set<ShareableField>> = _shareableFields.asStateFlow()

    private val combinedState: Flow<Pair<Calendar, TransactionFilterState>> =
        _selectedMonth.combine(_filterState) { month, filters ->
            Pair(month, filters)
        }

    val hasSeenOnboarding: Flow<Boolean> = settingsRepository.getHasSeenOnboarding()

    val goalIncomeThreshold: StateFlow<Int> =
        settingsRepository.getGoalIncomeThreshold()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5000)

    val merchantAliases: StateFlow<Map<String, String>>

    val transactionsForSelectedMonth: StateFlow<List<TransactionDetails>>
    val monthlyIncome: StateFlow<Double>
    val monthlyExpenses: StateFlow<Double>
    val categorySpendingForSelectedMonth: StateFlow<List<CategorySpending>>
    val merchantSpendingForSelectedMonth: StateFlow<List<MerchantSpendingSummary>>
    val overallMonthlyBudget: StateFlow<Float>
    val amountRemaining: StateFlow<Float>
    val allAccounts: StateFlow<List<Account>>
    val allCategories: Flow<List<Category>>
    val allTags: StateFlow<List<Tag>>
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError = _validationError.asStateFlow()
    private val _selectedTags = MutableStateFlow<Set<Tag>>(emptySet())
    val selectedTags = _selectedTags.asStateFlow()
    private val _transactionImages = MutableStateFlow<List<TransactionImage>>(emptyList())
    val transactionImages: StateFlow<List<TransactionImage>> = _transactionImages.asStateFlow()
    val monthlySummaries: StateFlow<List<MonthlySummaryItem>>

    private val _defaultAccount = MutableStateFlow<Account?>(null)
    val defaultAccount: StateFlow<Account?> = _defaultAccount.asStateFlow()

    private val _originalSmsText = MutableStateFlow<String?>(null)
    val originalSmsText: StateFlow<String?> = _originalSmsText.asStateFlow()

    private val _visitCount = MutableStateFlow(0)
    val visitCount: StateFlow<Int> = _visitCount.asStateFlow()

    private val _retroUpdateSheetState = MutableStateFlow<RetroUpdateSheetState?>(null)
    val retroUpdateSheetState = _retroUpdateSheetState.asStateFlow()

    /** State for the cross-account canonical nudge sheet. Non-null means the sheet is visible. */
    private val _canonicalNudgeState = MutableStateFlow<CanonicalNudgeSheetState?>(null)
    val canonicalNudgeState = _canonicalNudgeState.asStateFlow()

    // --- NEW: Reimbursement feature state ---
    private val _reimbursementsForCurrentExpense = MutableStateFlow<List<TransactionDetails>>(emptyList())
    val reimbursementsForCurrentExpense: StateFlow<List<TransactionDetails>> = _reimbursementsForCurrentExpense.asStateFlow()

    private val _candidateReimbursements = MutableStateFlow<List<TransactionDetails>>(emptyList())
    val candidateReimbursements: StateFlow<List<TransactionDetails>> = _candidateReimbursements.asStateFlow()

    private val _linkedExpenseForCurrentIncome = MutableStateFlow<TransactionDetails?>(null)
    val linkedExpenseForCurrentIncome: StateFlow<TransactionDetails?> = _linkedExpenseForCurrentIncome.asStateFlow()

    private val _showReimbursementPicker = MutableStateFlow(false)
    val showReimbursementPicker: StateFlow<Boolean> = _showReimbursementPicker.asStateFlow()

    /**
     * Emits [Unit] whenever the ViewModel determines that navigation back is safe.
     * Replaces direct [navigateBack] calls from the UI so the two-sheet flow
     * (retro sheet → canonical nudge) can be sequenced by the ViewModel.
     */
    private val _navigateBackEvent = Channel<Unit>(Channel.CONFLATED)
    val navigateBackEvent = _navigateBackEvent.receiveAsFlow()

    val travelModeSettings: StateFlow<TravelModeSettings?>

    private val _merchantSearchQuery = MutableStateFlow("")
    val merchantPredictions: StateFlow<List<MerchantPrediction>>

    private val _showCategoryNudge = MutableStateFlow<ManualTransactionData?>(null)
    val showCategoryNudge = _showCategoryNudge.asStateFlow()

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    // --- NEW: Expose Add Transaction Description for state management ---
    private val _addTransactionDescription = MutableStateFlow("")
    val addTransactionDescription = _addTransactionDescription.asStateFlow()

    // --- NEW: Expose Amount for state management ---
    private val _addTransactionAmount = MutableStateFlow("")
    val addTransactionAmount = _addTransactionAmount.asStateFlow()

    // --- NEW: Expose Selected Category for state management ---
    private val _addTransactionCategory = MutableStateFlow<Category?>(null)
    val addTransactionCategory = _addTransactionCategory.asStateFlow()

    // --- NEW: Expose Selected Account for state management ---
    private val _addTransactionAccount = MutableStateFlow<Account?>(null)
    val addTransactionAccount = _addTransactionAccount.asStateFlow()

    private val _userManuallySelectedCategory = MutableStateFlow(false)

    // --- NEW: Expose privacy mode state ---
    val isPrivacyModeEnabled: StateFlow<Boolean> =
        settingsRepository.getPrivacyModeEnabled()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    val suggestedCategory: StateFlow<Category?> =
        _addTransactionDescription
            .debounce(400)
            .combine(_userManuallySelectedCategory) { description, manualSelect ->
                description to manualSelect
            }
            .flatMapLatest { (description, manualSelect) ->
                if (description.length > 2 && !manualSelect) {
                    flow {
                        val allCategoriesList = allCategories.first()
                        emit(HeuristicCategorizer.findCategoryForDescription(description, allCategoriesList))
                    }
                } else {
                    flowOf<Category?>(null)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- NEW: Flow for Recent Manual Transactions (Suggestions) ---
    val recentManualTransactions: StateFlow<List<TransactionDetails>> =
        transactionRepository.getRecentManualTransactions(limit = 10)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // --- NEW: Flow for History Sheet (Larger limit) ---
    val historyManualTransactions: StateFlow<List<TransactionDetails>> =
        transactionRepository.getRecentManualTransactions(limit = 50)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    init {
        merchantPredictions =
            _merchantSearchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.length > 1) {
                        transactionRepository.searchMerchants(query)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        travelModeSettings =
            settingsRepository.getTravelModeSettings()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = null,
                )

        merchantAliases =
            merchantRenameRuleRepository.getAliasesAsMap()
                .map { it.mapKeys { (key, _) -> key.lowercase(Locale.getDefault()) } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        transactionsForSelectedMonth =
            combinedState.flatMapLatest { (calendar, filters) ->
                val monthStart =
                    (calendar.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                val monthEnd =
                    (calendar.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.DAY_OF_MONTH, -1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis
                transactionRepository.getTransactionDetailsForRange(
                    monthStart, monthEnd,
                    filters.keyword.takeIf {
                        it.isNotBlank()
                    },
                    filters.account?.id, filters.category?.id,
                )
                    .catch { e ->
                        Log.e(TAG, "Failed to load transactions for month", e)
                        _uiEvent.send("Failed to load transactions.")
                        emit(emptyList())
                    }
            }.combine(merchantAliases) { transactions, aliases ->
                transactions.applyAliases(aliases)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val financialSummaryFlow =
            _selectedMonth.flatMapLatest { calendar ->
                val monthStart =
                    (calendar.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                val monthEnd =
                    (calendar.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.DAY_OF_MONTH, -1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis
                transactionRepository.getFinancialSummaryForRangeFlow(monthStart, monthEnd)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        monthlyIncome =
            financialSummaryFlow.map { it?.totalIncome ?: 0.0 }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyExpenses =
            financialSummaryFlow.map { it?.totalExpenses ?: 0.0 }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        categorySpendingForSelectedMonth =
            combinedState.flatMapLatest { (calendar, filters) ->
                val monthStart =
                    (calendar.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                val monthEnd =
                    (calendar.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.DAY_OF_MONTH, -1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis
                val typeEnum =
                    when (filters.transactionType) {
                        AnalysisTransactionType.EXPENSE -> TransactionType.EXPENSE
                        AnalysisTransactionType.INCOME -> TransactionType.INCOME
                        AnalysisTransactionType.ALL -> null
                    }
                transactionRepository.getSpendingByCategoryForMonth(
                    monthStart, monthEnd,
                    filters.keyword.takeIf {
                        it.isNotBlank()
                    },
                    filters.account?.id, filters.category?.id, typeEnum,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        merchantSpendingForSelectedMonth =
            combinedState.flatMapLatest { (calendar, filters) ->
                val monthStart =
                    (calendar.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                val monthEnd =
                    (calendar.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.DAY_OF_MONTH, -1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis
                val typeEnum =
                    when (filters.transactionType) {
                        AnalysisTransactionType.EXPENSE -> TransactionType.EXPENSE
                        AnalysisTransactionType.INCOME -> TransactionType.INCOME
                        AnalysisTransactionType.ALL -> null
                    }
                transactionRepository.getSpendingByMerchantForMonth(
                    monthStart, monthEnd,
                    filters.keyword.takeIf {
                        it.isNotBlank()
                    },
                    filters.account?.id, filters.category?.id, typeEnum,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allAccounts =
            accountRepository.allAccounts.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )
        allCategories = categoryRepository.allCategories
        allTags =
            tagRepository.allTags.onEach {
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        monthlySummaries =
            transactionRepository.getFirstTransactionDate().flatMapLatest { firstTransactionDate ->
                val startDate = firstTransactionDate ?: System.currentTimeMillis()

                transactionRepository.getMonthlyTrends(startDate)
                    .map { trends ->
                        val dateFormat = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault())
                        val monthMap =
                            trends.associate {
                                val cal = Calendar.getInstance().apply { time = dateFormat.parse(it.monthYear) ?: Date() }
                                (cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)) to it.totalExpenses
                            }

                        val monthList = mutableListOf<MonthlySummaryItem>()
                        val startCal = Calendar.getInstance().apply { timeInMillis = startDate }
                        startCal.set(Calendar.DAY_OF_MONTH, 1)
                        val endCal = Calendar.getInstance()

                        while (startCal.before(endCal) || (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH))) {
                            val key = startCal.get(Calendar.YEAR) * 100 + startCal.get(Calendar.MONTH)
                            val spent = monthMap[key] ?: 0.0
                            monthList.add(MonthlySummaryItem(calendar = startCal.clone() as Calendar, totalSpent = spent))
                            startCal.add(Calendar.MONTH, 1)
                        }
                        monthList.reversed()
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        overallMonthlyBudget =
            _selectedMonth.flatMapLatest {
                settingsRepository.getOverallBudgetForMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
            }
                // --- UPDATED: Handle nullable Float? and map null to 0f ---
                .map { it ?: 0f }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        amountRemaining = combine(overallMonthlyBudget, monthlyExpenses) { budget, expenses -> budget - expenses.toFloat() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        viewModelScope.launch {
            _defaultAccount.value = db.accountDao().findByName("Cash Spends")
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

                // --- Reimbursement data ---
                if (it.transactionType == TransactionType.EXPENSE) {
                    transactionRepository.getReimbursementsForExpense(it.id).collect { reimbursements ->
                        _reimbursementsForCurrentExpense.value = reimbursements
                    }
                } else if (it.transactionType == TransactionType.INCOME && it.parentReimbursementId != null) {
                    transactionRepository.getLinkedExpenseForReimbursement(it.id).collect { expense ->
                        _linkedExpenseForCurrentIncome.value = expense
                    }
                }
            }
        }

        // Load merged account breakdown separately so it doesn't block the reimbursement flow.
        // This is a one-shot suspend call — the detail screen never stays open across an
        // unmerge (navigation pops back), so a Flow is unnecessary.
        viewModelScope.launch(Dispatchers.IO) {
            _mergedTransactionBreakdown.value =
                mergeTransactionsUseCase.getMergedTransactionBreakdown(transactionId)
        }
    }

    fun openReimbursementPicker(expenseId: Int) {
        viewModelScope.launch {
            transactionRepository.getCandidateReimbursements(expenseId).collect { candidates ->
                _candidateReimbursements.value = candidates
            }
        }
        _showReimbursementPicker.value = true
    }

    fun dismissReimbursementPicker() {
        _showReimbursementPicker.value = false
    }

    fun linkReimbursement(
        incomeId: Int,
        expenseId: Int
    ) {
        viewModelScope.launch {
            transactionRepository.linkReimbursement(incomeId, expenseId)
            _showReimbursementPicker.value = false
        }
    }

    fun unlinkReimbursement(incomeId: Int) {
        viewModelScope.launch {
            transactionRepository.unlinkReimbursement(incomeId)
        }
    }

    // --- NEW: Manual Merge Actions ---

    fun openReviewMergeSheet() {
        val ids = _selectedTransactionIds.value
        if (ids.size < 2) return

        viewModelScope.launch {
            val allTxns = transactionsForSelectedMonth.value
            val selected = allTxns.filter { it.transaction.id in ids }

            // Default anchor is the one with the largest absolute amount
            val largestTxn = selected.maxByOrNull { it.transaction.amount }
            if (largestTxn != null) {
                _anchorTransactionId.value = largestTxn.transaction.id
            }

            _showReviewMergeSheet.value = true
        }
    }

    fun dismissReviewMergeSheet() {
        _showReviewMergeSheet.value = false
        _anchorTransactionId.value = null
    }

    fun setAnchorTransaction(transactionId: Int) {
        if (_selectedTransactionIds.value.contains(transactionId)) {
            _anchorTransactionId.value = transactionId
        }
    }

    fun confirmManualMerge() {
        viewModelScope.launch {
            val anchorId = _anchorTransactionId.value
            val selectedIds = _selectedTransactionIds.value

            if (anchorId == null || selectedIds.size < 2 || !selectedIds.contains(anchorId)) {
                _uiEvent.send("Invalid merge configuration.")
                return@launch
            }

            val childIds = selectedIds.filter { it != anchorId }

            try {
                mergeTransactionsUseCase.manualMerge(anchorId, childIds)
                _uiEvent.send("Successfully merged ${selectedIds.size} transactions.")
                dismissReviewMergeSheet()
                clearSelectionMode()
            } catch (e: Exception) {
                _uiEvent.send("Failed to merge transactions.")
            }
        }
    }

    /**
     * Called from the multi-select action bar in the Transaction List tab.
     * Requires exactly 1 expense and 1+ income transactions to be selected.
     * The expense becomes the parent; all selected income transactions are linked as reimbursements.
     */
    fun linkReimbursementFromSelection() {
        viewModelScope.launch {
            val allTxns = transactionsForSelectedMonth.value
            val selected = allTxns.filter { it.transaction.id in _selectedTransactionIds.value }
            val expenses = selected.filter { it.transaction.transactionType == TransactionType.EXPENSE }
            val incomes = selected.filter { it.transaction.transactionType == TransactionType.INCOME }
            if (expenses.size != 1 || incomes.isEmpty()) {
                _uiEvent.send("Select exactly 1 expense and 1 or more income transactions.")
                return@launch
            }
            val expenseId = expenses.first().transaction.id
            incomes.forEach { income ->
                transactionRepository.linkReimbursement(income.transaction.id, expenseId)
            }
            _uiEvent.send("${incomes.size} repayment(s) linked.")
            clearSelectionMode()
        }
    }

    fun onAttemptToLeaveScreen(onNavigationAllowed: () -> Unit) {
        viewModelScope.launch {
            val initial =
                initialTransactionStateForRetroUpdate ?: run {
                    onNavigationAllowed()
                    return@launch
                }
            val current =
                transactionRepository.getTransactionById(initial.id).first() ?: run {
                    onNavigationAllowed()
                    return@launch
                }

            val descriptionChanged = !initial.description.equals(current.description, ignoreCase = true)
            val categoryChanged = initial.categoryId != current.categoryId

            if (!descriptionChanged && !categoryChanged) {
                onNavigationAllowed()
                return@launch
            }

            val originalDescriptionForSearch = initial.originalDescription ?: initial.description
            val similar = transactionRepository.findSimilarTransactions(originalDescriptionForSearch, initial.id)

            // --- FIX: Filter out past transactions that already have the new name or category
            val actionableSimilar =
                similar.filter { txn ->
                    val needsDescUpdate = descriptionChanged && !txn.description.equals(current.description, ignoreCase = true)
                    val needsCatUpdate = categoryChanged && txn.categoryId != current.categoryId
                    needsDescUpdate || needsCatUpdate
                }

            if (actionableSimilar.isNotEmpty()) {
                _retroUpdateSheetState.value =
                    RetroUpdateSheetState(
                        originalDescription = originalDescriptionForSearch,
                        newDescription = if (descriptionChanged) current.description else null,
                        newCategoryId = if (categoryChanged) current.categoryId else null,
                        similarTransactions = actionableSimilar,
                        selectedIds = actionableSimilar.map { it.id }.toSet(),
                        isLoading = false,
                        // Keep original count in case needed for metrics
                        totalMatchingCount = similar.size + 1,
                        updateFutureTransactions = true,
                    )
            } else {
                // --- FIX (#224/#225): Show the sheet even with no past history so the user
                // has explicit control over whether a future rule is created, instead of
                // silently saving one. The sheet renders without the past-transaction list.
                _retroUpdateSheetState.value =
                    RetroUpdateSheetState(
                        originalDescription = originalDescriptionForSearch,
                        newDescription = if (descriptionChanged) current.description else null,
                        newCategoryId = if (categoryChanged) current.categoryId else null,
                        similarTransactions = emptyList(),
                        selectedIds = emptySet(),
                        isLoading = false,
                        totalMatchingCount = 1,
                        updateFutureTransactions = true,
                    )
            }
        }
    }

    fun onMerchantSearchQueryChanged(query: String) {
        _merchantSearchQuery.value = query
    }

    fun clearMerchantSearch() {
        _merchantSearchQuery.value = ""
    }

    fun enterSelectionMode(initialTransactionId: Int) {
        _isSelectionModeActive.value = true
        _selectedTransactionIds.value = setOf(initialTransactionId)
    }

    fun toggleTransactionSelection(transactionId: Int) {
        _selectedTransactionIds.update { currentSelection ->
            if (transactionId in currentSelection) {
                currentSelection - transactionId
            } else {
                currentSelection + transactionId
            }
        }
    }

    fun clearSelectionMode() {
        _isSelectionModeActive.value = false
        _selectedTransactionIds.value = emptySet()
    }

    fun onDeleteSelectionClick() {
        _showDeleteConfirmation.value = true
    }

    fun onConfirmDeleteSelection() {
        viewModelScope.launch {
            try {
                val idsToDelete = _selectedTransactionIds.value.toList()
                if (idsToDelete.isNotEmpty()) {
                    // Record all SMS hashes for the to-be-deleted transactions so
                    // SmsCatchupWorker never re-creates them.
                    val hashes = db.transactionQueryDao().getSmsHashesByIds(idsToDelete)
                    hashes.forEach { hash ->
                        db.deletedSmsHashDao().insert(
                            io.pm.finlight.data.db.entity.DeletedSmsHash(hash),
                        )
                    }
                    transactionRepository.deleteByIds(idsToDelete)
                }
                _showDeleteConfirmation.value = false
                clearSelectionMode()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete selected transactions", e)
                _uiEvent.send("Failed to delete transactions. Please try again.")
            }
        }
    }

    fun onCancelDeleteSelection() {
        _showDeleteConfirmation.value = false
    }

    fun onShareClick() {
        _showShareSheet.value = true
    }

    fun onShareSheetDismiss() {
        _showShareSheet.value = false
    }

    fun onShareableFieldToggled(field: ShareableField) {
        _shareableFields.update { currentFields ->
            if (field in currentFields) {
                currentFields - field
            } else {
                currentFields + field
            }
        }
    }

    fun generateAndShareSnapshot(context: Context) {
        viewModelScope.launch {
            val selectedIds = _selectedTransactionIds.value
            if (selectedIds.isEmpty()) return@launch

            val allTransactions = transactionsForSelectedMonth.first()
            val selectedTransactionsDetails = allTransactions.filter { it.transaction.id in selectedIds }

            if (selectedTransactionsDetails.isNotEmpty()) {
                val transactionsWithData =
                    withContext(Dispatchers.IO) {
                        selectedTransactionsDetails.map { details ->
                            val tags = transactionRepository.getTagsForTransactionSimple(details.transaction.id)
                            ShareImageGenerator.TransactionSnapshotData(details = details, tags = tags)
                        }
                    }

                ShareImageGenerator.shareTransactionsAsImage(
                    context = context,
                    transactionsWithData = transactionsWithData,
                    fields = _shareableFields.value,
                )
            }
            onShareSheetDismiss()
            clearSelectionMode()
        }
    }

    fun requestCategoryChange(details: TransactionDetails) {
        _transactionForCategoryChange.value = details
    }

    fun cancelCategoryChange() {
        _transactionForCategoryChange.value = null
    }

    fun getSplitDetailsForTransaction(transactionId: Int): Flow<List<SplitTransactionDetails>> {
        return splitTransactionRepository.getSplitsForParent(transactionId)
    }

    fun saveTransactionSplits(
        parentTransactionId: Int,
        splitItems: List<SplitItem>,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val parentTxn = transactionRepository.getTransactionById(parentTransactionId).firstOrNull() ?: return@launch
                val conversionRate = parentTxn.conversionRate ?: 1.0

                db.withTransaction {
                    db.transactionWriteDao().markAsSplit(parentTransactionId, true)
                    db.splitTransactionDao().deleteSplitsForParent(parentTransactionId)

                    val newSplits =
                        splitItems.map {
                            val originalAmount = it.amount.toDoubleOrNull() ?: 0.0
                            SplitTransaction(
                                parentTransactionId = parentTransactionId,
                                amount = originalAmount * conversionRate,
                                originalAmount = if (parentTxn.currencyCode != null) originalAmount else null,
                                categoryId = it.category?.id,
                                notes = it.notes,
                            )
                        }
                    db.splitTransactionDao().insertAll(newSplits)
                }
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving transaction splits", e)
            }
        }
    }

    fun findTransactionDetailsById(id: Int): Flow<TransactionDetails?> {
        return transactionRepository.getTransactionDetailsById(id)
            .combine(merchantAliases) { details, aliases ->
                details?.let { listOf(it).applyAliases(aliases).firstOrNull() }
            }
    }

    private fun loadVisitCount(
        description: String,
    ) {
        viewModelScope.launch {
            transactionRepository.getTransactionCountForMerchant(description).collect { count ->
                _visitCount.value = count
            }
        }
    }

    // --- NEW: State update methods for AddTransactionScreen inputs ---
    fun onAddTransactionDescriptionChanged(description: String) {
        _addTransactionDescription.value = description
        _merchantSearchQuery.value = description // Trigger predictions
        if (description.isBlank()) {
            _userManuallySelectedCategory.value = false
        }
    }

    fun onAddTransactionAmountChanged(amount: String) {
        _addTransactionAmount.value = amount
    }

    fun onAddTransactionCategoryChanged(category: Category?) {
        _addTransactionCategory.value = category
        if (category != null) {
            _userManuallySelectedCategory.value = true
        }
    }

    fun onAddTransactionAccountChanged(account: Account?) {
        _addTransactionAccount.value = account
    }

    fun onUserManuallySelectedCategory() {
        _userManuallySelectedCategory.value = true
    }

    // --- NEW: Handler for Quick Fill selection ---
    fun onQuickFillSelected(transactionDetails: TransactionDetails) {
        _addTransactionDescription.value = transactionDetails.transaction.description

        // Format amount: remove .0 if integer
        val amount = transactionDetails.transaction.amount
        val formattedAmount =
            if (amount % 1.0 == 0.0) {
                amount.toInt().toString()
            } else {
                "%.2f".format(amount)
            }
        _addTransactionAmount.value = formattedAmount

        // Populate Categories and Accounts
        // Note: We need to find the actual objects from the lists loaded in the VM
        // This logic is best handled by the UI observing the state changes, or we can look them up here
        // For simplicity, we assume the UI will re-resolve the ID to the object
        viewModelScope.launch {
            val categories = allCategories.first()
            val accounts = allAccounts.first()

            val category = categories.find { it.id == transactionDetails.transaction.categoryId }
            _addTransactionCategory.value = category
            _userManuallySelectedCategory.value = true // Prevent auto-categorizer from overwriting

            val account = accounts.find { it.id == transactionDetails.transaction.accountId }
            _addTransactionAccount.value = account

            // Load tags
            val tags = transactionRepository.getTagsForTransactionSimple(transactionDetails.transaction.id)
            _selectedTags.value = tags.toSet()
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
                val transactionId = saveManualTransaction(transactionData, categoryId)
                if (transactionId != null) {
                    onSaveComplete(transactionId)
                }
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
    ) {
        onSaveTapped(
            description = description,
            amountStr = amountStr,
            accountId = accountId,
            categoryId = categoryId,
            notes = notes,
            date = date,
            transactionType = TransactionType.fromString(transactionType),
            imageUris = imageUris,
            onSaveComplete = onSaveComplete,
        )
    }

    fun saveWithSelectedCategory(
        categoryId: Int?,
        onComplete: (Long?) -> Unit,
    ) {
        viewModelScope.launch {
            val transactionData = _showCategoryNudge.value
            if (transactionData != null) {
                val transactionId = saveManualTransaction(transactionData, categoryId)
                if (transactionId != null) {
                    onComplete(transactionId)
                }
            }
            _showCategoryNudge.value = null
        }
    }

    private suspend fun saveManualTransaction(
        data: ManualTransactionData,
        categoryId: Int?,
    ): Long? {
        _validationError.value = null
        val enteredAmount = data.amountStr.toDoubleOrNull() ?: 0.0

        val travelSettings = travelModeSettings.value
        val isInternationalTravel =
            travelSettings?.isEnabled == true &&
                travelSettings.tripType == TripType.INTERNATIONAL &&
                data.date >= travelSettings.startDate &&
                data.date <= travelSettings.endDate

        val transactionToSave =
            if (isInternationalTravel) {
                Transaction(
                    description = data.description,
                    originalDescription = data.description,
                    categoryId = categoryId,
                    amount = enteredAmount * (travelSettings!!.conversionRate ?: 1f),
                    date = data.date,
                    accountId = data.accountId,
                    notes = data.notes,
                    transactionType = data.transactionType,
                    isExcluded = false,
                    sourceSmsId = null,
                    sourceSmsHash = null,
                    source = "Manual Entry",
                    originalAmount = enteredAmount,
                    currencyCode = travelSettings.currencyCode,
                    conversionRate = travelSettings.conversionRate?.toDouble(),
                )
            } else {
                Transaction(
                    description = data.description,
                    originalDescription = data.description,
                    categoryId = categoryId,
                    amount = enteredAmount,
                    date = data.date,
                    accountId = data.accountId,
                    notes = data.notes,
                    transactionType = data.transactionType,
                    isExcluded = false,
                    sourceSmsId = null,
                    sourceSmsHash = null,
                    source = "Manual Entry",
                    originalAmount = enteredAmount,
                )
            }

        return try {
            val savedImagePaths =
                data.imageUris.mapNotNull { uri ->
                    saveImageToInternalStorage(uri)
                }
            val finalTags = resolveTravelModeTagUseCase.getFinalTags(transactionToSave.date, data.tags)
            val newTransactionId =
                transactionRepository.insertTransactionWithTagsAndImages(
                    transactionToSave,
                    finalTags,
                    savedImagePaths,
                )
            newTransactionId
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
        // Do not clear account if it's default, otherwise clear
        if (_addTransactionAccount.value != _defaultAccount.value) {
            // Logic to reset to default handled in UI LaunchedEffect usually,
            // but here we just reset the specific manual override state
            _addTransactionAccount.value = null
        }
        _userManuallySelectedCategory.value = false
    }

    private fun loadOriginalSms(sourceSmsId: Long?) {
        if (sourceSmsId == null) {
            _originalSmsText.value = null
            return
        }
        viewModelScope.launch {
            val sms = getOriginalSmsMessage(sourceSmsId)
            _originalSmsText.value = sms?.body
        }
    }

    fun clearOriginalSms() {
        _originalSmsText.value = null
    }

    suspend fun getOriginalSmsMessage(smsId: Long): SmsMessage? {
        return withContext(Dispatchers.IO) {
            smsRepository.getSmsDetailsById(smsId)
        }
    }

    fun reparseTransactionFromSms(transactionId: Int) {
        viewModelScope.launch {
            val logTag = "ReparseLogic"

            val transaction = transactionRepository.getTransactionById(transactionId).first()
            if (transaction?.sourceSmsId == null) {
                Log.w(logTag, "FAILURE: Transaction or sourceSmsId is null.")
                return@launch
            }

            val smsMessage = smsRepository.getSmsDetailsById(transaction.sourceSmsId)
            if (smsMessage == null) {
                Log.w(logTag, "FAILURE: Could not find original SMS for sourceSmsId: ${transaction.sourceSmsId}")
                return@launch
            }

            val existingMappings = merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })

            val categoryFinderProvider =
                object : CategoryFinderProvider {
                    override fun getCategoryIdByName(name: String): Int? {
                        return CategoryIconHelper.getCategoryIdByName(name)
                    }
                }
            val customSmsRuleProvider =
                object : CustomSmsRuleProvider {
                    override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
                }
            val merchantRenameRuleProvider =
                object : MerchantRenameRuleProvider {
                    override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()

                    override suspend fun getAllRulesMap(): Map<String, String> {
                        return db.merchantRenameRuleDao().getAllRulesList().associateBy({ it.originalName.lowercase() }, { it.newName })
                    }
                }
            val ignoreRuleProvider =
                object : IgnoreRuleProvider {
                    override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
                }
            val merchantCategoryMappingProvider =
                object : MerchantCategoryMappingProvider {
                    override suspend fun getCategoryIdForMerchant(merchantName: String): Int? =
                        db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)

                    override suspend fun getAllMappings(): Map<String, Int> {
                        return db.merchantCategoryMappingDao().getAll().associateBy({ it.parsedName.lowercase() }, { it.categoryId })
                    }
                }
            val smsParseTemplateProvider =
                object : SmsParseTemplateProvider {
                    override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()

                    override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> =
                        db.smsParseTemplateDao().getTemplatesBySignature(
                            signature,
                        )
                }

            val parseResult =
                SmsParser.parseWithReason(
                    sms = smsMessage,
                    mappings = existingMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            if (parseResult is ParseResult.Success) {
                // --- AUTO-HEALING ---
                parseResult.newlyDiscoveredRenameAlias?.let { (oldName, newName) ->
                    db.merchantRenameRuleDao().insert(MerchantRenameRule(oldName, newName))
                }
                parseResult.newlyDiscoveredCategoryAlias?.let { (merchant, catId) ->
                    db.merchantCategoryMappingDao().insert(MerchantCategoryMapping(merchant, catId))
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
                    val currentAccount = accountRepository.getAccountById(transaction.accountId).first()
                    if (currentAccount?.name?.equals(parsedAccount.formattedName, ignoreCase = true) == false) {
                        var account = db.accountDao().findByName(parsedAccount.formattedName)
                        if (account == null) {
                            val newAccount = Account(name = parsedAccount.formattedName, type = parsedAccount.accountType)
                            val newId = accountRepository.insert(newAccount)
                            account = db.accountDao().getAccountById(newId.toInt()).first()
                        }
                        if (account != null) {
                            transactionRepository.updateAccountId(transactionId, account.id)
                        }
                    }
                }
            }
            Log.d(logTag, "--- Reparse finished for transactionId: $transactionId ---")
        }
    }

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

    fun createAccount(
        name: String,
        type: String,
        onAccountCreated: (Account) -> Unit,
    ) {
        if (name.isBlank() || type.isBlank()) return
        viewModelScope.launch {
            val existingAccount = db.accountDao().findByName(name)
            if (existingAccount != null) {
                _validationError.value = "An account named '$name' already exists."
                return@launch
            }

            val newAccountId = accountRepository.insert(Account(name = name, type = type))
            accountRepository.getAccountById(newAccountId.toInt()).first()?.let { newAccount ->
                onAccountCreated(newAccount)
            }
        }
    }

    fun createCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        onCategoryCreated: (Category) -> Unit,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val existingCategory = db.categoryDao().findByName(name)
            if (existingCategory != null) {
                _validationError.value = "A category named '$name' already exists."
                return@launch
            }

            val usedColorKeys = allCategories.first().map { it.colorKey }
            val finalIconKey = if (iconKey == "category") "letter_default" else iconKey
            val finalColorKey = if (colorKey == "gray_light") CategoryIconHelper.getNextAvailableColor(usedColorKeys) else colorKey

            val newCategory = Category(name = name, iconKey = finalIconKey, colorKey = finalColorKey)
            val newCategoryId = categoryRepository.insert(newCategory)
            categoryRepository.getCategoryById(newCategoryId.toInt())?.let { createdCategory ->
                onCategoryCreated(createdCategory)
            }
        }
    }

    fun attachPhotoToTransaction(
        transactionId: Int,
        sourceUri: Uri,
    ) {
        viewModelScope.launch {
            val localPath = saveImageToInternalStorage(sourceUri)
            if (localPath != null) {
                transactionRepository.addImageToTransaction(transactionId, localPath)
            }
        }
    }

    fun deleteTransactionImage(image: TransactionImage) {
        viewModelScope.launch {
            transactionRepository.deleteImage(image)
            withContext(Dispatchers.IO) {
                try {
                    if (!File(image.imageUri).delete()) {
                        Log.w(TAG, "Failed to delete image file: ${image.imageUri}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete image file: ${image.imageUri}", e)
                }
            }
        }
    }

    private fun loadImagesForTransaction(transactionId: Int) {
        viewModelScope.launch {
            transactionRepository.getImagesForTransaction(transactionId).collect {
                _transactionImages.value = it
            }
        }
    }

    private suspend fun saveImageToInternalStorage(sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                val filesDir = File(context.filesDir, "attachments")
                if (!filesDir.exists()) {
                    filesDir.mkdirs()
                }
                val fileName = "txn_attach_${System.currentTimeMillis()}.jpg"
                val file = File(filesDir, fileName)
                val outputStream = FileOutputStream(file)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error saving image to internal storage", e)
                null
            }
        }
    }

    fun updateTransactionDescription(
        id: Int,
        newDescription: String,
    ) = viewModelScope.launch {
        try {
            if (newDescription.isNotBlank()) {
                val transaction = transactionRepository.getTransactionById(id).firstOrNull()
                if (transaction != null && transaction.sourceSmsId != null && transaction.originalDescription != null) {
                    val original = transaction.originalDescription
                    if (original.isNotBlank() && !original.equals(newDescription, ignoreCase = true)) {
                        val originalSms = smsRepository.getSmsDetailsById(transaction.sourceSmsId)
                        if (originalSms != null) {
                            val merchantIndex = originalSms.body.indexOf(original)
                            if (merchantIndex != -1) {
                                createAndStoreTemplate(
                                    smsBody = originalSms.body,
                                    transaction = transaction,
                                    correctedMerchant = newDescription,
                                    originalMerchantStartIndex = merchantIndex,
                                    originalMerchantEndIndex = merchantIndex + original.length,
                                )
                            }
                        }
                    }
                }

                // We do not automatically update global MerchantRenameRule here.
                // Global rules should only be updated via performBatchUpdate when applying to all similar transactions.

                if (transaction != null && transaction.originalDescription != null) {
                    if (transaction.originalDescription.equals(newDescription, ignoreCase = true)) {
                        merchantRenameRuleRepository.deleteByOriginalName(transaction.originalDescription)
                    }
                }

                transactionRepository.updateDescription(id, newDescription)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update transaction description", e)
            _uiEvent.send("Failed to update description. Please try again.")
        }
    }

    private suspend fun createAndStoreTemplate(
        smsBody: String,
        transaction: Transaction,
        correctedMerchant: String,
        originalMerchantStartIndex: Int,
        originalMerchantEndIndex: Int,
    ) {
        val amountToFind = transaction.originalAmount ?: transaction.amount

        val amountRegex = "([\\d,]+\\.?\\d*)".toRegex()
        val allNumericValuesInSms = amountRegex.findAll(smsBody).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
        val matchingAmountValue = allNumericValuesInSms.find { it == amountToFind }

        if (matchingAmountValue == null) {
            Log.w(TAG, "Could not find the exact amount '$amountToFind' in the SMS body to create a template.")
            return
        }

        val amountStr =
            amountRegex.findAll(smsBody).find {
                it.value.replace(",", "").toDoubleOrNull() == matchingAmountValue
            }?.value ?: return

        val amountIndex = smsBody.indexOf(amountStr)

        if (amountIndex == -1) {
            Log.w(TAG, "Could not find amount index in SMS body for template creation.")
            return
        }

        val signature = SmsParser.generateSmsSignature(smsBody)

        val template =
            SmsParseTemplate(
                templateSignature = signature,
                correctedMerchantName = correctedMerchant,
                originalSmsBody = smsBody,
                originalAmountStartIndex = amountIndex,
                originalAmountEndIndex = amountIndex + amountStr.length,
                originalMerchantStartIndex = originalMerchantStartIndex,
                originalMerchantEndIndex = originalMerchantEndIndex,
            )

        smsParseTemplateDao.insert(template)
        Log.d(TAG, "Successfully created and stored a new SMS parse template.")
    }

    fun updateTransactionAmount(
        id: Int,
        amountStr: String,
    ) = viewModelScope.launch {
        amountStr.toDoubleOrNull()?.let {
            if (it > 1_000_000_000.0) {
                _validationError.value = "Maximum limit of 1 Billion (1,000,000,000) reached."
                return@launch
            }
            if (it > 0) {
                transactionRepository.updateManualAmountEdit(id, it)
            }
        }
    }

    fun updateTransactionNotes(
        id: Int,
        notes: String,
    ) = viewModelScope.launch {
        transactionRepository.updateNotes(id, notes.takeIf { it.isNotBlank() })
    }

    // --- THIS IS THE FIX ---
    // Removed `(Dispatchers.IO)` from the coroutine launch.
    fun updateTransactionCategory(
        id: Int,
        categoryId: Int?,
    ) = viewModelScope.launch {
        val transaction = transactionRepository.getTransactionById(id).first() ?: return@launch
        transactionRepository.updateCategoryId(id, categoryId)

        val originalDescription = transaction.originalDescription
        if (categoryId != null && !originalDescription.isNullOrBlank()) {
            val mapping =
                MerchantCategoryMapping(
                    parsedName = originalDescription,
                    categoryId = categoryId,
                )
            merchantCategoryMappingRepository.insert(mapping)
        }
    }

    fun updateTransactionAccount(
        id: Int,
        accountId: Int,
    ) = viewModelScope.launch {
        transactionRepository.updateAccountId(id, accountId)
    }

    fun updateTransactionDate(
        id: Int,
        date: Long,
    ) = viewModelScope.launch {
        transactionRepository.updateDate(id, date)
    }

    fun updateTransactionExclusion(
        id: Int,
        isExcluded: Boolean,
    ) = viewModelScope.launch {
        transactionRepository.updateExclusionStatus(id, isExcluded)
    }

    // --- NEW: Function to update transaction type ---
    fun updateTransactionType(
        id: Int,
        transactionType: TransactionType,
    ) = viewModelScope.launch {
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
    ) = viewModelScope.launch {
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
        if (tagName.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val existingTag = db.tagDao().findByName(tagName)
                    if (existingTag != null) {
                        _validationError.value = "A tag named '$tagName' already exists."
                        return@launch
                    }
                    val newTag = Tag(name = tagName)
                    val newId = tagRepository.insert(newTag)
                    if (newId != -1L) {
                        _selectedTags.update { it + newTag.copy(id = newId.toInt()) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add new tag", e)
                    _uiEvent.send("Failed to add new tag. Please try again.")
                }
            }
        }
    }

    private fun loadTagsForTransaction(transactionId: Int) {
        if (currentTxnIdForTags == transactionId && areTagsLoadedForCurrentTxn) {
            return
        }
        viewModelScope.launch {
            val initialTags = transactionRepository.getTagsForTransaction(transactionId).first()
            _selectedTags.value = initialTags.toSet()
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
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val accountName = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account"
                val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

                var account = db.accountDao().findByName(accountName)
                if (account == null) {
                    val newAccount = Account(name = accountName, type = accountType)
                    accountRepository.insert(newAccount)
                    account = db.accountDao().findByName(accountName)
                }

                if (account == null) return@withContext false

                val transactionToSave =
                    if (isForeign) {
                        val travelSettings = settingsRepository.getTravelModeSettings().first()
                        if (travelSettings == null) {
                            Log.e(TAG, "Attempted to save foreign SMS transaction, but Travel Mode is not configured.")
                            return@withContext false
                        }
                        Transaction(
                            description = description,
                            // FIX: Use the raw pre-rename name for originalDescription.
                            originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                            categoryId = categoryId,
                            amount = potentialTxn.amount * (travelSettings.conversionRate ?: 1f),
                            date = potentialTxn.date,
                            accountId = account.id,
                            notes = notes,
                            transactionType = TransactionType.fromString(potentialTxn.transactionType),
                            sourceSmsId = potentialTxn.sourceSmsId,
                            sourceSmsHash = potentialTxn.sourceSmsHash,
                            source = "Imported",
                            originalAmount = potentialTxn.amount,
                            currencyCode = travelSettings.currencyCode,
                            conversionRate = travelSettings.conversionRate?.toDouble(),
                        )
                    } else {
                        Transaction(
                            description = description,
                            // FIX: Use the raw pre-rename name for originalDescription.
                            originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                            categoryId = categoryId,
                            amount = potentialTxn.amount,
                            date = potentialTxn.date,
                            accountId = account.id,
                            notes = notes,
                            transactionType = TransactionType.fromString(potentialTxn.transactionType),
                            sourceSmsId = potentialTxn.sourceSmsId,
                            sourceSmsHash = potentialTxn.sourceSmsHash,
                            source = "Imported",
                        )
                    }

                val finalTags = resolveTravelModeTagUseCase.getFinalTags(transactionToSave.date, tags)
                transactionRepository.insertTransactionWithTags(transactionToSave, finalTags)

                val merchantName = potentialTxn.merchantName
                if (categoryId != null && merchantName != null) {
                    val mapping =
                        MerchantCategoryMapping(
                            parsedName = merchantName,
                            categoryId = categoryId,
                        )
                    merchantCategoryMappingRepository.insert(mapping)
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to approve SMS transaction", e)
                false
            }
        }
    }

    suspend fun autoSaveSmsTransaction(
        potentialTxn: PotentialTransaction,
        source: String = "Auto-Captured",
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val accountName = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account"
                val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

                var finalAccountId: Int? = null

                // 1. Check for an alias
                val alias = db.accountAliasDao().findByAlias(accountName)
                if (alias != null) {
                    finalAccountId = alias.destinationAccountId
                } else {
                    // 2. No alias, check for an exact account name match
                    var account = db.accountDao().findByName(accountName)
                    if (account == null) {
                        // 3. No exact match, create a new account
                        val newAccount = Account(name = accountName, type = accountType)
                        val newId = accountRepository.insert(newAccount)
                        // BUG FIX: AccountDao.insert uses OnConflictStrategy.IGNORE, which returns -1
                        // if the account already exists (e.g., created by a concurrent operation or
                        // a prior findByName cache miss). Passing -1 to getAccountById always returns
                        // null, silently dropping the transaction. Fall back to findByName instead.
                        account =
                            if (newId != -1L) {
                                db.accountDao().getAccountById(newId.toInt()).first()
                            } else {
                                Log.d(TAG, "Account '$accountName' already existed (IGNORE conflict). Fetching by name.")
                                db.accountDao().findByName(accountName)
                            }
                    }
                    finalAccountId = account?.id
                }

                if (finalAccountId == null) {
                    Log.e(TAG, "Auto-save failed: Could not find or create account '$accountName'")
                    return@withContext false
                }

                val transactionToSave =
                    Transaction(
                        description = potentialTxn.merchantName ?: "Unknown Merchant",
                        // FIX: Use the raw pre-rename name for originalDescription.
                        originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                        categoryId = potentialTxn.categoryId,
                        amount = potentialTxn.amount,
                        date = potentialTxn.date,
                        accountId = finalAccountId,
                        notes = null,
                        transactionType = TransactionType.fromString(potentialTxn.transactionType),
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                        source = source,
                    )

                val finalTags = resolveTravelModeTagUseCase.getFinalTags(transactionToSave.date, emptySet())
                transactionRepository.insertTransactionWithTags(transactionToSave, finalTags)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-save SMS transaction", e)
                false
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) =
        viewModelScope.launch {
            try {
                // If this transaction was created from an SMS, permanently record its hash
                // in the deny-list so SmsCatchupWorker never re-creates it.
                transaction.sourceSmsHash?.let { hash ->
                    db.deletedSmsHashDao().insert(
                        io.pm.finlight.data.db.entity.DeletedSmsHash(hash),
                    )
                }
                transactionRepository.delete(transaction)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete transaction", e)
                _uiEvent.send("Failed to delete transaction. Please try again.")
            }
        }

    fun unsplitTransaction(transaction: Transaction) {
        viewModelScope.launch {
            db.withTransaction {
                val firstSplitCategory =
                    db.splitTransactionDao().getSplitsForParentSimple(
                        transaction.id,
                    ).firstOrNull()?.splitTransaction?.categoryId
                db.splitTransactionDao().deleteSplitsForParent(transaction.id)
                val originalDescription = transaction.originalDescription ?: transaction.description
                db.transactionWriteDao().unmarkAsSplit(transaction.id, originalDescription, firstSplitCategory)
            }
        }
    }

    fun clearError() {
        _validationError.value = null
    }

    fun dismissRetroUpdateSheet() {
        _retroUpdateSheetState.value = null
    }

    fun toggleRetroUpdateSelection(id: Int) {
        _retroUpdateSheetState.update { currentState ->
            currentState?.copy(
                selectedIds =
                    currentState.selectedIds.toMutableSet().apply {
                        if (id in this) remove(id) else add(id)
                    },
            )
        }
    }

    fun toggleRetroUpdateSelectAll() {
        _retroUpdateSheetState.update { currentState ->
            currentState?.let {
                if (it.selectedIds.size == it.similarTransactions.size) {
                    it.copy(selectedIds = emptySet())
                } else {
                    it.copy(selectedIds = it.similarTransactions.map { t -> t.id }.toSet())
                }
            }
        }
    }

    /** Toggles the 'Update future transactions' switch on the Smart Update sheet. */
    fun toggleUpdateFutureTransactions() {
        _retroUpdateSheetState.update { it?.copy(updateFutureTransactions = !it.updateFutureTransactions) }
    }

    fun performBatchUpdate() {
        viewModelScope.launch {
            val state = _retroUpdateSheetState.value ?: return@launch
            val idsToUpdate = state.selectedIds.toList()

            // --- FIX (#224/#225): No longer early-return when idsToUpdate is empty.
            // The user may still want to save a future rule without touching past records.
            if (idsToUpdate.isEmpty() && !state.updateFutureTransactions) {
                _retroUpdateSheetState.value = null
                _navigateBackEvent.send(Unit)
                return@launch
            }

            try {
                var ruleSaved = false
                var savedCanonical: String? = null
                var savedOriginal: String? = null

                // --- FIX (#224/#225): Gate on explicit checkbox, not on isAllSelected.
                // This means any partial selection + future toggle ON will correctly save the rule.
                if (state.updateFutureTransactions) {
                    state.newDescription?.let { newDesc ->
                        val originalDesc = state.originalDescription
                        if (originalDesc.isNotBlank() && !originalDesc.equals(newDesc, ignoreCase = true)) {
                            val rule = MerchantRenameRule(originalName = originalDesc, newName = newDesc)
                            merchantRenameRuleRepository.insert(rule)
                            Log.d(TAG, "Smart Update: saved rename rule '$originalDesc' -> '$newDesc'.")
                            ruleSaved = true
                            savedCanonical = newDesc
                            savedOriginal = originalDesc
                        } else if (originalDesc.isNotBlank() && originalDesc.equals(newDesc, ignoreCase = true)) {
                            // User reverted name — remove any existing rule.
                            merchantRenameRuleRepository.deleteByOriginalName(originalDesc)
                        }
                    }
                    state.newCategoryId?.let { newCategoryId ->
                        val originalDesc = state.originalDescription
                        if (originalDesc.isNotBlank()) {
                            merchantCategoryMappingRepository.insert(
                                MerchantCategoryMapping(parsedName = originalDesc, categoryId = newCategoryId)
                            )
                            Log.d(TAG, "Smart Update: upserted category mapping '$originalDesc' -> $newCategoryId.")
                        }
                    }
                } else {
                    Log.d(TAG, "Smart Update: user opted out of future rule — skipping rule save.")
                }

                // Retroactive ledger updates for selected past transactions
                if (idsToUpdate.isNotEmpty()) {
                    state.newDescription?.let { transactionRepository.updateDescriptionForIds(idsToUpdate, it) }
                    state.newCategoryId?.let { transactionRepository.updateCategoryForIds(idsToUpdate, it) }
                    _uiEvent.send("Updated ${idsToUpdate.size} transaction(s).")
                }

                // Layer B: if a global rule was saved, scan for cross-account variants.
                // The canonical nudge (if shown) will emit navigateBackEvent when resolved.
                if (ruleSaved && savedCanonical != null && savedOriginal != null) {
                    val foundVariants = findCrossAccountVariants(savedCanonical!!, savedOriginal!!)
                    if (!foundVariants) _navigateBackEvent.send(Unit)
                } else {
                    _navigateBackEvent.send(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to perform batch update", e)
                _uiEvent.send("Batch update failed. Please try again.")
                _navigateBackEvent.send(Unit)
            } finally {
                _retroUpdateSheetState.value = null
            }
        }
    }

    /**
     * Called when the user explicitly skips the retro update sheet (dismiss or cancel).
     * Clears the sheet and triggers navigation since no global rule was saved.
     */
    fun onRetroSheetSkipped() {
        viewModelScope.launch {
            _retroUpdateSheetState.value = null
            _navigateBackEvent.send(Unit)
        }
    }

    // =========================================================================
    // --- Cross-Account Canonical Nudge (Layer B) ---
    // =========================================================================

    /**
     * Scans all distinct [Transaction.originalDescription] values in the database and
     * surfaces any that pass the canonical-subset check but do not already have a rename
     * rule. If variants are found, [_canonicalNudgeState] is populated and the function
     * returns `true` (navigation deferred). Returns `false` if no variants are found.
     *
     * @param canonicalName The user-chosen display name just saved (e.g. "Swiggy").
     * @param savedOriginalName The raw name the rule was saved for (e.g. "SWIGGY INFOTECH").
     */
    private suspend fun findCrossAccountVariants(
        canonicalName: String,
        savedOriginalName: String,
    ): Boolean {
        if (canonicalName.trim().length < 5) return false
        return try {
            val existingRules =
                merchantRenameRuleRepository
                    .getAliasesAsMap()
                    .first()
                    .keys
                    .map { it.lowercase() }
                    .toSet()

            val allOriginalDescs = transactionRepository.getDistinctOriginalDescriptions()
            val variants = mutableListOf<CanonicalVariant>()

            for (rawDesc in allOriginalDescs) {
                // Skip the original we already have a rule for.
                if (rawDesc.equals(savedOriginalName, ignoreCase = true)) continue
                // Skip any that already have a rename rule.
                if (rawDesc.lowercase() in existingRules) continue
                // Skip if it IS the canonical name.
                if (rawDesc.equals(canonicalName, ignoreCase = true)) continue

                if (StringSimilarity.isCanonicalSubset(canonicalName, rawDesc)) {
                    val ids = transactionRepository.getTransactionIdsByOriginalDescription(rawDesc)
                    if (ids.isNotEmpty()) {
                        variants.add(CanonicalVariant(rawDesc, ids.size, ids))
                    }
                }
            }

            if (variants.isNotEmpty()) {
                _canonicalNudgeState.value = CanonicalNudgeSheetState(canonicalName, variants)
                Log.d(TAG, "Canonical nudge: found ${variants.size} cross-account variant(s) for '$canonicalName'.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for cross-account variants", e)
            false
        }
    }

    /** Toggles the selection of a cross-account variant in the canonical nudge sheet. */
    fun toggleCanonicalVariant(rawName: String) {
        _canonicalNudgeState.update { state ->
            state?.copy(
                selectedRawNames =
                    state.selectedRawNames.toMutableSet().apply {
                        if (rawName in this) remove(rawName) else add(rawName)
                    },
            )
        }
    }

    /**
     * Applies the canonical rename to all selected variants: saves a new
     * [MerchantRenameRule] and bulk-updates the [Transaction.description] for each.
     * Emits [navigateBackEvent] when done.
     */
    fun confirmCanonicalNudge() {
        viewModelScope.launch {
            val state =
                _canonicalNudgeState.value ?: run {
                    _navigateBackEvent.send(Unit)
                    return@launch
                }
            try {
                val selected = state.variants.filter { it.rawName in state.selectedRawNames }
                for (variant in selected) {
                    merchantRenameRuleRepository.insert(
                        MerchantRenameRule(
                            originalName = variant.rawName,
                            newName = state.canonicalName,
                        ),
                    )
                    transactionRepository.updateDescriptionForIds(variant.transactionIds, state.canonicalName)
                    Log.d(
                        TAG,
                        "Canonical nudge: applied '${state.canonicalName}' to ${variant.transactionIds.size} txn(s) with raw name '${variant.rawName}'.",
                    )
                }
                if (selected.isNotEmpty()) {
                    _uiEvent.send("Applied '${state.canonicalName}' to ${selected.sumOf { it.transactionCount }} more transaction(s).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply canonical nudge", e)
                _uiEvent.send("Failed to apply rename to similar transactions.")
            } finally {
                _canonicalNudgeState.value = null
                _navigateBackEvent.send(Unit)
            }
        }
    }

    /** Dismisses the canonical nudge without applying any changes and triggers navigation. */
    fun dismissCanonicalNudge() {
        viewModelScope.launch {
            _canonicalNudgeState.value = null
            _navigateBackEvent.send(Unit)
        }
    }

    // ─── Unmerge ─────────────────────────────────────────────────────────────

    /**
     * Returns a [Flow] that emits the [MergeRecord] snapshot for [parentTxnId],
     * or null if the transaction was never merged (or was already unmerged).
     * The UI collects this to decide whether to show the "Unmerge" option.
     */
    fun observeMergeRecord(parentTxnId: Int) =
        mergeTransactionsUseCase.observeMergeRecord(parentTxnId)

    /**
     * Fully reverses the most recent merge for [parentTxnId].
     * On success, the parent is restored to its pre-merge state and the child
     * transaction is re-inserted as a fresh row.
     */
    fun unmergeTransaction(parentTxnId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mergeTransactionsUseCase.unmerge(parentTxnId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unmerge transaction $parentTxnId", e)
                _uiEvent.send("Failed to unmerge. Please try again.")
            }
        }
    }
}
