// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TransactionViewModel.kt
// REASON: REFACTOR - The retrospective update logic has been deferred. Instead of
// prompting the user immediately after an edit, the ViewModel now caches the
// transaction's initial state. A new `onAttemptToLeaveScreen` function is called
// when the user navigates back, which compares the initial and final states. If
// changes are found and similar transactions exist, only then is the prompt shown.
// This reduces user friction and allows for a single, consolidated prompt if both
// the description and category are changed.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.ShareImageGenerator
import io.pm.finlight.utils.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "TransactionViewModel"

data class TransactionFilterState(
    val keyword: String = "",
    val account: Account? = null,
    val category: Category? = null
)

data class RetroUpdateSheetState(
    val originalDescription: String,
    val newDescription: String? = null,
    val newCategoryId: Int? = null,
    val similarTransactions: List<Transaction> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val transactionRepository: TransactionRepository
    val accountRepository: AccountRepository
    val categoryRepository: CategoryRepository
    private val tagRepository: TagRepository
    private val settingsRepository: SettingsRepository
    private val smsRepository: SmsRepository
    private val merchantRenameRuleRepository: MerchantRenameRuleRepository
    private val merchantCategoryMappingRepository: MerchantCategoryMappingRepository
    private val merchantMappingRepository: MerchantMappingRepository
    private val splitTransactionRepository: SplitTransactionRepository
    private val context = application

    private val db = AppDatabase.getInstance(application)
    private var areTagsLoadedForCurrentTxn = false
    private var currentTxnIdForTags: Int? = null

    // --- NEW: Caches the initial state of the transaction for comparison on exit ---
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

    private val _showShareSheet = MutableStateFlow(false)
    val showShareSheet: StateFlow<Boolean> = _showShareSheet.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _shareableFields = MutableStateFlow(
        setOf(ShareableField.Date, ShareableField.Description, ShareableField.Amount, ShareableField.Category, ShareableField.Tags)
    )
    val shareableFields: StateFlow<Set<ShareableField>> = _shareableFields.asStateFlow()


    private val combinedState: Flow<Pair<Calendar, TransactionFilterState>> =
        _selectedMonth.combine(_filterState) { month, filters ->
            Pair(month, filters)
        }

    private val merchantAliases: StateFlow<Map<String, String>>

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

    val travelModeSettings: StateFlow<TravelModeSettings?>

    private val _merchantSearchQuery = MutableStateFlow("")
    val merchantPredictions: StateFlow<List<MerchantPrediction>>

    init {
        transactionRepository = TransactionRepository(db.transactionDao())
        accountRepository = AccountRepository(db.accountDao())
        categoryRepository = CategoryRepository(db.categoryDao())
        tagRepository = TagRepository(db.tagDao(), db.transactionDao())
        settingsRepository = SettingsRepository(application)
        smsRepository = SmsRepository(application)
        merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
        merchantCategoryMappingRepository = MerchantCategoryMappingRepository(db.merchantCategoryMappingDao())
        merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao())
        splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao())

        merchantPredictions = _merchantSearchQuery
            .debounce(300)
            .flatMapLatest { query ->
                if (query.length > 1) {
                    transactionRepository.searchMerchants(query)
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


        travelModeSettings = settingsRepository.getTravelModeSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

        merchantAliases = merchantRenameRuleRepository.getAliasesAsMap()
            .map { it.mapKeys { (key, _) -> key.lowercase(Locale.getDefault()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        transactionsForSelectedMonth = combinedState.flatMapLatest { (calendar, filters) ->
            val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            transactionRepository.getTransactionDetailsForRange(monthStart, monthEnd, filters.keyword.takeIf { it.isNotBlank() }, filters.account?.id, filters.category?.id)
        }.combine(merchantAliases) { transactions, aliases ->
            applyAliases(transactions, aliases)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val financialSummaryFlow = _selectedMonth.flatMapLatest { calendar ->
            val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            transactionRepository.getFinancialSummaryForRangeFlow(monthStart, monthEnd)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        monthlyIncome = financialSummaryFlow.map { it?.totalIncome ?: 0.0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        monthlyExpenses = financialSummaryFlow.map { it?.totalExpenses ?: 0.0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

        categorySpendingForSelectedMonth = combinedState.flatMapLatest { (calendar, filters) ->
            val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            transactionRepository.getSpendingByCategoryForMonth(monthStart, monthEnd, filters.keyword.takeIf { it.isNotBlank() }, filters.account?.id, filters.category?.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        merchantSpendingForSelectedMonth = combinedState.flatMapLatest { (calendar, filters) ->
            val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            transactionRepository.getSpendingByMerchantForMonth(monthStart, monthEnd, filters.keyword.takeIf { it.isNotBlank() }, filters.account?.id, filters.category?.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allAccounts = accountRepository.allAccounts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allCategories = categoryRepository.allCategories
        allTags = tagRepository.allTags.onEach {
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        monthlySummaries = transactionRepository.getMonthlyTrends(startOfYear)
            .map { trends ->
                val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val monthMap = trends.filter {
                    (dateFormat.parse(it.monthYear) ?: Date()).let { date ->
                        val cal = Calendar.getInstance().apply { time = date }
                        cal.get(Calendar.YEAR) == currentYear
                    }
                }.associate {
                    val cal = Calendar.getInstance().apply { time = dateFormat.parse(it.monthYear) ?: Date() }
                    (cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)) to it.totalExpenses
                }

                (0..11).map { monthIndex ->
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.MONTH, monthIndex)
                    }
                    val key = cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
                    val spent = monthMap[key] ?: 0.0
                    MonthlySummaryItem(calendar = cal, totalSpent = spent)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        overallMonthlyBudget = _selectedMonth.flatMapLatest { settingsRepository.getOverallBudgetForMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
        amountRemaining = combine(overallMonthlyBudget, monthlyExpenses) { budget, expenses -> budget - expenses.toFloat() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

        viewModelScope.launch {
            _defaultAccount.value = db.accountDao().findByName("Cash Spends")
        }
    }

    // --- NEW: Caches the initial transaction state and loads all related data for the detail screen ---
    fun loadTransactionForDetailScreen(transactionId: Int) {
        viewModelScope.launch {
            initialTransactionStateForRetroUpdate = transactionRepository.getTransactionById(transactionId).first()
            initialTransactionStateForRetroUpdate?.let {
                loadTagsForTransaction(it.id)
                loadImagesForTransaction(it.id)
                loadOriginalSms(it.sourceSmsId)
                loadVisitCount(it.originalDescription, it.description)
            }
        }
    }

    // --- NEW: Checks for changes on exit and triggers the retro update prompt if needed ---
    fun onAttemptToLeaveScreen(onNavigationAllowed: () -> Unit) {
        viewModelScope.launch {
            val initial = initialTransactionStateForRetroUpdate ?: run {
                onNavigationAllowed()
                return@launch
            }
            val current = transactionRepository.getTransactionById(initial.id).first() ?: run {
                onNavigationAllowed()
                return@launch
            }

            val descriptionChanged = initial.description != current.description
            val categoryChanged = initial.categoryId != current.categoryId

            if (!descriptionChanged && !categoryChanged) {
                onNavigationAllowed()
                return@launch
            }

            val originalDescriptionForSearch = initial.originalDescription ?: initial.description
            val similar = transactionRepository.findSimilarTransactions(originalDescriptionForSearch, initial.id)

            if (similar.isNotEmpty()) {
                _retroUpdateSheetState.value = RetroUpdateSheetState(
                    originalDescription = originalDescriptionForSearch,
                    newDescription = if (descriptionChanged) current.description else null,
                    newCategoryId = if (categoryChanged) current.categoryId else null,
                    similarTransactions = similar,
                    selectedIds = similar.map { it.id }.toSet(),
                    isLoading = false
                )
            } else {
                onNavigationAllowed()
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
            val idsToDelete = _selectedTransactionIds.value.toList()
            if (idsToDelete.isNotEmpty()) {
                transactionRepository.deleteByIds(idsToDelete)
            }
            _showDeleteConfirmation.value = false
            clearSelectionMode()
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
                val transactionsWithData = withContext(Dispatchers.IO) {
                    selectedTransactionsDetails.map { details ->
                        val tags = transactionRepository.getTagsForTransactionSimple(details.transaction.id)
                        ShareImageGenerator.TransactionSnapshotData(details = details, tags = tags)
                    }
                }

                ShareImageGenerator.shareTransactionsAsImage(
                    context = context,
                    transactionsWithData = transactionsWithData,
                    fields = _shareableFields.value
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

    fun saveTransactionSplits(parentTransactionId: Int, splitItems: List<SplitItem>, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val parentTxn = transactionRepository.getTransactionById(parentTransactionId).firstOrNull() ?: return@launch
                val conversionRate = parentTxn.conversionRate ?: 1.0

                db.withTransaction {
                    db.transactionDao().markAsSplit(parentTransactionId, true)
                    db.splitTransactionDao().deleteSplitsForParent(parentTransactionId)

                    val newSplits = splitItems.map {
                        val originalAmount = it.amount.toDoubleOrNull() ?: 0.0
                        SplitTransaction(
                            parentTransactionId = parentTransactionId,
                            amount = originalAmount * conversionRate,
                            originalAmount = if (parentTxn.currencyCode != null) originalAmount else null,
                            categoryId = it.category?.id,
                            notes = it.notes
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

    private fun applyAliases(transactions: List<TransactionDetails>, aliases: Map<String, String>): List<TransactionDetails> {
        return transactions.map { details ->
            val key = (details.transaction.originalDescription ?: details.transaction.description).lowercase(Locale.getDefault())
            val newDescription = aliases[key] ?: details.transaction.description
            details.copy(transaction = details.transaction.copy(description = newDescription))
        }
    }

    fun findTransactionDetailsById(id: Int): Flow<TransactionDetails?> {
        return transactionRepository.getTransactionDetailsById(id)
            .combine(merchantAliases) { details, aliases ->
                details?.let { applyAliases(listOf(it), aliases).firstOrNull() }
            }
    }

    private fun loadVisitCount(originalDescription: String?, fallbackDescription: String) {
        val descriptionToQuery = originalDescription ?: fallbackDescription
        viewModelScope.launch {
            transactionRepository.getTransactionCountForMerchant(descriptionToQuery).collect { count ->
                _visitCount.value = count
            }
        }
    }

    suspend fun addTransaction(
        description: String,
        categoryId: Int?,
        amountStr: String,
        accountId: Int,
        notes: String?,
        date: Long,
        transactionType: String,
        imageUris: List<Uri>
    ): Boolean {
        _validationError.value = null

        if (description.isBlank()) {
            _validationError.value = "Description cannot be empty."
            return false
        }
        val enteredAmount = amountStr.toDoubleOrNull()
        if (enteredAmount == null || enteredAmount <= 0.0) {
            _validationError.value = "Please enter a valid, positive amount."
            return false
        }
        if (categoryId == null) {
            _validationError.value = "Please select a category."
            return false
        }

        val travelSettings = travelModeSettings.value
        val isTravelMode = travelSettings?.isEnabled == true &&
                date >= travelSettings.startDate &&
                date <= travelSettings.endDate

        val transactionToSave = if (isTravelMode) {
            Transaction(
                description = description,
                originalDescription = description,
                categoryId = categoryId,
                amount = enteredAmount * travelSettings!!.conversionRate,
                date = date,
                accountId = accountId,
                notes = notes,
                transactionType = transactionType,
                isExcluded = false,
                sourceSmsId = null,
                sourceSmsHash = null,
                source = "Added Manually",
                originalAmount = enteredAmount,
                currencyCode = travelSettings.currencyCode,
                conversionRate = travelSettings.conversionRate.toDouble()
            )
        } else {
            Transaction(
                description = description,
                originalDescription = description,
                categoryId = categoryId,
                amount = enteredAmount,
                date = date,
                accountId = accountId,
                notes = notes,
                transactionType = transactionType,
                isExcluded = false,
                sourceSmsId = null,
                sourceSmsHash = null,
                source = "Added Manually"
            )
        }

        return try {
            withContext(Dispatchers.IO) {
                val savedImagePaths = imageUris.mapNotNull { uri ->
                    saveImageToInternalStorage(uri)
                }
                transactionRepository.insertTransactionWithTagsAndImages(
                    transactionToSave,
                    _selectedTags.value,
                    savedImagePaths
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transaction", e)
            _validationError.value = "An error occurred while saving."
            false
        }
    }

    fun clearAddTransactionState() {
        _selectedTags.value = emptySet()
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
        viewModelScope.launch(Dispatchers.IO) {
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

            val potentialTxn = SmsParser.parse(
                smsMessage,
                existingMappings,
                db.customSmsRuleDao(),
                db.merchantRenameRuleDao(),
                db.ignoreRuleDao(),
                db.merchantCategoryMappingDao()
            )

            if (potentialTxn != null) {
                if (potentialTxn.merchantName != null && potentialTxn.merchantName != transaction.description) {
                    transactionRepository.updateDescription(transactionId, potentialTxn.merchantName)
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
                        } else {
                        }
                    } else {
                        Log.d(logTag, "Account names are the same. No update needed.")
                    }
                } ?: Log.d(logTag, "No potential account was parsed from the SMS.")
            } else {
                Log.d(logTag, "SmsParser returned null. No updates to perform.")
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

    fun createAccount(name: String, type: String, onAccountCreated: (Account) -> Unit) {
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

    fun createCategory(name: String, iconKey: String, colorKey: String, onCategoryCreated: (Category) -> Unit) {
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

    fun attachPhotoToTransaction(transactionId: Int, sourceUri: Uri) {
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
                    File(image.imageUri).delete()
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
                val fileName = "txn_attach_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
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

    // --- REFACTORED: No longer triggers the retro update sheet directly ---
    fun updateTransactionDescription(id: Int, newDescription: String) = viewModelScope.launch(Dispatchers.IO) {
        if (newDescription.isNotBlank()) {
            transactionRepository.updateDescription(id, newDescription)
        }
    }

    fun updateTransactionAmount(id: Int, amountStr: String) = viewModelScope.launch {
        amountStr.toDoubleOrNull()?.let {
            if (it > 0) {
                transactionRepository.updateAmount(id, it)
            }
        }
    }

    fun updateTransactionNotes(id: Int, notes: String) = viewModelScope.launch {
        transactionRepository.updateNotes(id, notes.takeIf { it.isNotBlank() })
    }

    // --- REFACTORED: No longer triggers the retro update sheet directly ---
    fun updateTransactionCategory(id: Int, categoryId: Int?) = viewModelScope.launch(Dispatchers.IO) {
        val transaction = transactionRepository.getTransactionById(id).first() ?: return@launch
        transactionRepository.updateCategoryId(id, categoryId)

        if (categoryId != null && transaction.sourceSmsId != null && !transaction.originalDescription.isNullOrBlank()) {
            val mapping = MerchantCategoryMapping(
                parsedName = transaction.originalDescription,
                categoryId = categoryId
            )
            merchantCategoryMappingRepository.insert(mapping)
        }
    }


    fun updateTransactionAccount(id: Int, accountId: Int) = viewModelScope.launch {
        transactionRepository.updateAccountId(id, accountId)
    }

    fun updateTransactionDate(id: Int, date: Long) = viewModelScope.launch {
        transactionRepository.updateDate(id, date)
    }

    fun updateTransactionExclusion(id: Int, isExcluded: Boolean) = viewModelScope.launch {
        transactionRepository.updateExclusionStatus(id, isExcluded)
    }

    fun updateTagsForTransaction(transactionId: Int) = viewModelScope.launch {
        transactionRepository.updateTagsForTransaction(transactionId, _selectedTags.value)
    }

    fun onTagSelected(tag: Tag) {
        _selectedTags.update { if (tag in it) it - tag else it + tag }
    }

    fun addTagOnTheGo(tagName: String) {
        if (tagName.isNotBlank()) {
            viewModelScope.launch {
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
        isForeign: Boolean
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

                val transactionToSave = if (isForeign) {
                    val travelSettings = settingsRepository.getTravelModeSettings().first()
                    if (travelSettings == null) {
                        Log.e(TAG, "Attempted to save foreign SMS transaction, but Travel Mode is not configured.")
                        return@withContext false
                    }
                    Transaction(
                        description = description,
                        originalDescription = potentialTxn.merchantName,
                        categoryId = categoryId,
                        amount = potentialTxn.amount * travelSettings.conversionRate,
                        date = System.currentTimeMillis(),
                        accountId = account.id,
                        notes = notes,
                        transactionType = potentialTxn.transactionType,
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                        source = "Imported",
                        originalAmount = potentialTxn.amount,
                        currencyCode = travelSettings.currencyCode,
                        conversionRate = travelSettings.conversionRate.toDouble()
                    )
                } else {
                    Transaction(
                        description = description,
                        originalDescription = potentialTxn.merchantName,
                        categoryId = categoryId,
                        amount = potentialTxn.amount,
                        date = System.currentTimeMillis(),
                        accountId = account.id,
                        notes = notes,
                        transactionType = potentialTxn.transactionType,
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                        source = "Imported"
                    )
                }

                transactionRepository.insertTransactionWithTags(transactionToSave, tags)

                if (categoryId != null && potentialTxn.merchantName != null) {
                    val mapping = MerchantCategoryMapping(
                        parsedName = potentialTxn.merchantName,
                        categoryId = categoryId
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

    fun deleteTransaction(transaction: Transaction) =
        viewModelScope.launch {
            transactionRepository.delete(transaction)
        }

    fun unsplitTransaction(transaction: Transaction) {
        viewModelScope.launch {
            db.withTransaction {
                val firstSplitCategory = db.splitTransactionDao().getSplitsForParent(transaction.id).firstOrNull()?.firstOrNull()?.splitTransaction?.categoryId
                db.splitTransactionDao().deleteSplitsForParent(transaction.id)
                val originalDescription = transaction.originalDescription ?: transaction.description
                db.transactionDao().unmarkAsSplit(transaction.id, originalDescription, firstSplitCategory)
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
                selectedIds = currentState.selectedIds.toMutableSet().apply {
                    if (id in this) remove(id) else add(id)
                }
            )
        }
    }

    fun toggleRetroUpdateSelectAll() {
        _retroUpdateSheetState.update { currentState ->
            currentState?.let {
                if (it.selectedIds.size == it.similarTransactions.size) {
                    it.copy(selectedIds = emptySet()) // Deselect all
                } else {
                    it.copy(selectedIds = it.similarTransactions.map { t -> t.id }.toSet()) // Select all
                }
            }
        }
    }

    fun performBatchUpdate() {
        viewModelScope.launch {
            val state = _retroUpdateSheetState.value ?: return@launch
            val idsToUpdate = state.selectedIds.toList()
            if (idsToUpdate.isEmpty()) return@launch

            state.newDescription?.let {
                transactionRepository.updateDescriptionForIds(idsToUpdate, it)
            }
            state.newCategoryId?.let {
                transactionRepository.updateCategoryForIds(idsToUpdate, it)
            }
            dismissRetroUpdateSheet()
        }
    }
}
