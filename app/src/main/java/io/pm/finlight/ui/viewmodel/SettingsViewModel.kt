// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SettingsViewModel.kt
//
// REASON: FEATURE (Bulk Import Progress)
// - Added `_totalSmsToScan` and `_processedSmsCount` StateFlows to track import progress.
// - Refactored `startSmsScanAndIdentifyMappings` to process messages in chunks of 100.
// - The function now updates the progress flows after each chunk, allowing the UI
//   to display a determinate progress bar (e.g., "Scanning 300 / 1500").
// - The `finally` block is updated to reset these new state flows.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.Manifest
import android.app.Application
import android.app.backup.BackupManager
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.ReminderManager
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.DefaultDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed class ScanResult {
    data class Success(val count: Int) : ScanResult()
    object Error : ScanResult()
}

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val db: AppDatabase,
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val smsRepository: SmsRepository,
    private val transactionViewModel: TransactionViewModel,
    private val smsClassifier: SmsClassifier,
    private val transactionRunner: TransactionRunner,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AndroidViewModel(application) {

    private val context = application
    private val tagDao = db.tagDao()
    private val splitTransactionDao = db.splitTransactionDao()
    private val backupManager = BackupManager(context)

    private val _uiEvent = Channel<String>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _showBackupSuccessDialog = MutableStateFlow(false)
    val showBackupSuccessDialog = _showBackupSuccessDialog.asStateFlow()


    val smsScanStartDate: StateFlow<Long>

    private val _csvValidationReport = MutableStateFlow<CsvValidationReport?>(null)
    val csvValidationReport: StateFlow<CsvValidationReport?> = _csvValidationReport.asStateFlow()

    val dailyReportEnabled: StateFlow<Boolean> =
        settingsRepository.getDailyReportEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val weeklySummaryEnabled: StateFlow<Boolean> =
        settingsRepository.getWeeklySummaryEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    val monthlySummaryEnabled: StateFlow<Boolean> =
        settingsRepository.getMonthlySummaryEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val appLockEnabled: StateFlow<Boolean> =
        settingsRepository.getAppLockEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val unknownTransactionPopupEnabled: StateFlow<Boolean> =
        settingsRepository.getUnknownTransactionPopupEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    val autoCaptureNotificationEnabled: StateFlow<Boolean> =
        settingsRepository.getAutoCaptureNotificationEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _potentialTransactions = MutableStateFlow<List<PotentialTransaction>>(emptyList())
    val potentialTransactions: StateFlow<List<PotentialTransaction>> = _potentialTransactions.asStateFlow()

    private val _isScanning = MutableStateFlow<Boolean>(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // --- NEW: StateFlows for bulk import progress ---
    private val _totalSmsToScan = MutableStateFlow(0)
    val totalSmsToScan: StateFlow<Int> = _totalSmsToScan.asStateFlow()

    private val _processedSmsCount = MutableStateFlow(0)
    val processedSmsCount: StateFlow<Int> = _processedSmsCount.asStateFlow()


    val dailyReportTime: StateFlow<Pair<Int, Int>> =
        settingsRepository.getDailyReportTime().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Pair(9, 0)
        )

    val weeklyReportTime: StateFlow<Triple<Int, Int, Int>> =
        settingsRepository.getWeeklyReportTime().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Triple(Calendar.MONDAY, 9, 0)
        )

    val monthlyReportTime: StateFlow<Triple<Int, Int, Int>> =
        settingsRepository.getMonthlyReportTime().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Triple(1, 9, 0)
        )

    val selectedTheme: StateFlow<AppTheme> =
        settingsRepository.getSelectedTheme().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.SYSTEM_DEFAULT
        )

    val autoBackupEnabled: StateFlow<Boolean> =
        settingsRepository.getAutoBackupEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val autoBackupNotificationEnabled: StateFlow<Boolean> =
        settingsRepository.getAutoBackupNotificationEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false // Changed default value to false
        )

    val privacyModeEnabled: StateFlow<Boolean> =
        settingsRepository.getPrivacyModeEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val lastBackupTimestamp: StateFlow<Long> =
        settingsRepository.getLastBackupTimestamp().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    init {
        smsScanStartDate =
            settingsRepository.getSmsScanStartDate()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0L,
                )
    }

    override fun onCleared() {
        super.onCleared()
        smsClassifier.close()
    }

    fun dismissBackupSuccessDialog() {
        _showBackupSuccessDialog.value = false
    }

    fun setPrivacyModeEnabled(enabled: Boolean) {
        settingsRepository.savePrivacyModeEnabled(enabled)
    }

    suspend fun applyLearningAndAutoImport(mapping: MerchantCategoryMapping): Int {
        var autoImportedCount = 0
        val remainingForReview = mutableListOf<PotentialTransaction>()

        val currentList = _potentialTransactions.value

        for (txn in currentList) {
            if (txn.merchantName != null && txn.merchantName.equals(mapping.parsedName, ignoreCase = true)) {
                val success = transactionViewModel.autoSaveSmsTransaction(txn.copy(categoryId = mapping.categoryId), source = "Imported")
                if (success) {
                    autoImportedCount++
                }
            } else {
                remainingForReview.add(txn)
            }
        }

        _potentialTransactions.value = remainingForReview
        return autoImportedCount
    }

    fun rescanSmsWithNewRule(onComplete: (Int) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            onComplete(0)
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
            var newTransactionsFound = 0
            try {
                val startDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
                val rawMessages = withContext(dispatchers.io) {
                    smsRepository.fetchAllSms(startDate)
                }

                val existingMappings = withContext(dispatchers.io) {
                    merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })
                }
                val existingSmsHashes = withContext(dispatchers.io) {
                    transactionRepository.getAllSmsHashes().first().toSet()
                }

                val categoryFinderProvider = object : CategoryFinderProvider {
                    override fun getCategoryIdByName(name: String): Int? {
                        return CategoryIconHelper.getCategoryIdByName(name)
                    }
                }
                val customSmsRuleProvider = object : CustomSmsRuleProvider {
                    override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
                }
                val merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
                    override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()
                }
                val ignoreRuleProvider = object : IgnoreRuleProvider {
                    override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
                }
                val merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
                    override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)
                }
                val smsParseTemplateProvider = object : SmsParseTemplateProvider {
                    override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()
                    override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> = db.smsParseTemplateDao().getTemplatesBySignature(signature)
                }

                val parsedList = withContext(dispatchers.default) {
                    rawMessages.map { sms ->
                        async {
                            SmsParser.parse(
                                sms,
                                existingMappings,
                                customSmsRuleProvider,
                                merchantRenameRuleProvider,
                                ignoreRuleProvider,
                                merchantCategoryMappingProvider,
                                categoryFinderProvider,
                                smsParseTemplateProvider
                            )
                        }
                    }.awaitAll().filterNotNull()
                }

                val newPotentialTransactions = parsedList.filter { potential ->
                    !existingSmsHashes.contains(potential.sourceSmsHash)
                }

                for (potentialTxn in newPotentialTransactions) {
                    val success = transactionViewModel.autoSaveSmsTransaction(potentialTxn, source = "Imported")
                    if (success) {
                        newTransactionsFound++
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during retro SMS scan", e)
            } finally {
                _isScanning.value = false
                onComplete(newTransactionsFound)
            }
        }
    }

    // --- REFACTORED: This function now implements chunked processing with progress reporting ---
    fun startSmsScanAndIdentifyMappings(startDate: Long?, onComplete: (importedCount: Int) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            viewModelScope.launch { _uiEvent.send("SMS Read permission is required.") }
            onComplete(0) // Return 0 imported
            return
        }

        viewModelScope.launch {
            // 1. Set initial state
            _isScanning.value = true
            _processedSmsCount.value = 0
            _totalSmsToScan.value = 0
            var autoImportedCount = 0

            try {
                // 2. Fetch all messages
                val rawMessages = withContext(dispatchers.io) { smsRepository.fetchAllSms(startDate) }

                // 3. Update total count to show the UI
                _totalSmsToScan.value = rawMessages.size
                if (rawMessages.isEmpty()) {
                    _uiEvent.send("No SMS messages found to scan.")
                    onComplete(0)
                    return@launch // Exit early
                }

                // 4. Get all DB lookups *before* the loop
                val existingMappings = withContext(dispatchers.io) { merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName }) }
                val existingSmsHashes = withContext(dispatchers.io) { transactionRepository.getAllSmsHashes().first().toSet() }

                val categoryFinderProvider = object : CategoryFinderProvider {
                    override fun getCategoryIdByName(name: String): Int? = CategoryIconHelper.getCategoryIdByName(name)
                }
                val customSmsRuleProvider = object : CustomSmsRuleProvider {
                    override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
                }
                val merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
                    override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()
                }
                val ignoreRuleProvider = object : IgnoreRuleProvider {
                    override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
                }
                val merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
                    override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)
                }
                val smsParseTemplateProvider = object : SmsParseTemplateProvider {
                    override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()
                    override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> = db.smsParseTemplateDao().getTemplatesBySignature(signature)
                }

                // 5. Chunk the list
                val chunks = rawMessages.chunked(100) // Process 100 messages at a time

                // 6. Loop through chunks
                for (chunk in chunks) {
                    // Process one chunk in parallel
                    val parsedList = withContext(dispatchers.default) {
                        chunk.map { sms ->
                            async {
                                // Run the full parsing pipeline (Custom Rules, ML, Heuristics)
                                var parseResult = SmsParser.parseWithOnlyCustomRules(
                                    sms = sms,
                                    customSmsRuleProvider = customSmsRuleProvider,
                                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                                    categoryFinderProvider = categoryFinderProvider
                                )

                                if (parseResult == null) {
                                    val transactionConfidence = smsClassifier.classify(sms.body)
                                    if (transactionConfidence >= 0.1) {
                                        parseResult = SmsParser.parseWithReason(
                                            sms = sms,
                                            mappings = existingMappings,
                                            customSmsRuleProvider = customSmsRuleProvider,
                                            merchantRenameRuleProvider = merchantRenameRuleProvider,
                                            ignoreRuleProvider = ignoreRuleProvider,
                                            merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                                            categoryFinderProvider = categoryFinderProvider,
                                            smsParseTemplateProvider = smsParseTemplateProvider
                                        )
                                    }
                                }
                                (parseResult as? ParseResult.Success)?.transaction
                            }
                        }.awaitAll().filterNotNull()
                    }

                    // Filter and Save this chunk
                    val newPotentialTransactions = parsedList.filter { !existingSmsHashes.contains(it.sourceSmsHash) }

                    for (potentialTxn in newPotentialTransactions) {
                        if (transactionViewModel.autoSaveSmsTransaction(potentialTxn, source = "Imported")) {
                            autoImportedCount++
                        }
                    }

                    // --- !! REPORT PROGRESS !! ---
                    _processedSmsCount.update { it + chunk.size }
                }

                // Report final count
                val message = if (autoImportedCount > 0) "Successfully imported $autoImportedCount new transactions." else "No new transactions found."
                _uiEvent.send(message)
                onComplete(autoImportedCount)

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during SMS scan", e)
                _uiEvent.send("An error occurred during scan.")
                onComplete(0) // Return 0 imported on error
            } finally {
                // 7. Reset state
                _isScanning.value = false
                _totalSmsToScan.value = 0
                _processedSmsCount.value = 0
            }
        }
    }

    fun setAutoCaptureNotificationEnabled(enabled: Boolean) {
        settingsRepository.saveAutoCaptureNotificationEnabled(enabled)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        settingsRepository.saveAutoBackupEnabled(enabled)
        if (enabled) ReminderManager.scheduleAutoBackup(context) else ReminderManager.cancelAutoBackup(context)
    }

    fun setAutoBackupNotificationEnabled(enabled: Boolean) {
        settingsRepository.saveAutoBackupNotificationEnabled(enabled)
    }


    fun saveSelectedTheme(theme: AppTheme) {
        settingsRepository.saveSelectedTheme(theme)
    }

    fun dismissPotentialTransaction(transaction: PotentialTransaction) {
        _potentialTransactions.value = _potentialTransactions.value.filter { it != transaction }
    }

    fun onTransactionApproved(smsId: Long) {
        _potentialTransactions.update { currentList ->
            currentList.filterNot { it.sourceSmsId == smsId }
        }
    }

    fun onTransactionLinked(smsId: Long) {
        _potentialTransactions.update { currentList ->
            currentList.filterNot { it.sourceSmsId == smsId }
        }
    }

    fun saveMerchantRenameRule(originalName: String, newName: String) {
        if (originalName.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            if (originalName.equals(newName, ignoreCase = true)) {
                db.merchantRenameRuleDao().deleteByOriginalName(originalName)
            } else {
                val rule = MerchantRenameRule(originalName = originalName, newName = newName)
                db.merchantRenameRuleDao().insert(rule)
            }
        }
    }

    fun saveSmsScanStartDate(date: Long) {
        viewModelScope.launch {
            settingsRepository.saveSmsScanStartDate(date)
        }
    }

    fun setDailyReportEnabled(enabled: Boolean) {
        settingsRepository.saveDailyReportEnabled(enabled)
        if (enabled) ReminderManager.scheduleDailyReport(context) else ReminderManager.cancelDailyReport(context)
    }

    fun saveDailyReportTime(hour: Int, minute: Int) {
        settingsRepository.saveDailyReportTime(hour, minute)
        if (dailyReportEnabled.value) {
            ReminderManager.scheduleDailyReport(context)
        }
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        settingsRepository.saveWeeklySummaryEnabled(enabled)
        if (enabled) ReminderManager.scheduleWeeklySummary(context) else ReminderManager.cancelWeeklySummary(context)
    }

    fun saveWeeklyReportTime(dayOfWeek: Int, hour: Int, minute: Int) {
        settingsRepository.saveWeeklyReportTime(dayOfWeek, hour, minute)
        if (weeklySummaryEnabled.value) {
            ReminderManager.scheduleWeeklySummary(context)
        }
    }

    fun setMonthlySummaryEnabled(enabled: Boolean) {
        settingsRepository.saveMonthlySummaryEnabled(enabled)
        if (enabled) {
            ReminderManager.scheduleMonthlySummary(context)
        } else {
            ReminderManager.cancelMonthlySummary(context)
        }
    }

    fun saveMonthlyReportTime(dayOfMonth: Int, hour: Int, minute: Int) {
        settingsRepository.saveMonthlyReportTime(dayOfMonth, hour, minute)
        if (monthlySummaryEnabled.value) {
            ReminderManager.scheduleMonthlySummary(context)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        settingsRepository.saveAppLockEnabled(enabled)
    }

    fun setUnknownTransactionPopupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveUnknownTransactionPopupEnabled(enabled)
        }
    }

    fun validateCsvFile(uri: Uri) {
        viewModelScope.launch {
            _csvValidationReport.value = null
            withContext(dispatchers.io) {
                try {
                    val report = generateValidationReport(uri)
                    _csvValidationReport.value = report
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "CSV validation failed for URI: $uri", e)
                }
            }
        }
    }

    private suspend fun generateValidationReport(
        uri: Uri,
        initialData: List<ReviewableRow>? = null,
    ): CsvValidationReport {
        val accountsMap = db.accountDao().getAllAccounts().first().associateBy { it.name }
        val categoriesMap = db.categoryDao().getAllCategories().first().associateBy { it.name }

        if (initialData != null) {
            val revalidatedRows =
                initialData.map {
                    createReviewableRow(it.lineNumber, it.rowData, accountsMap, categoriesMap)
                }
            return CsvValidationReport(header = _csvValidationReport.value?.header ?: emptyList(), reviewableRows = revalidatedRows, totalRowCount = revalidatedRows.size)
        }

        val reviewableRows = mutableListOf<ReviewableRow>()
        var header = emptyList<String>()
        var lineNumber = 0

        getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            val lineIterator = lines.iterator()
            if (lineIterator.hasNext()) {
                val headerLine = lineIterator.next()
                header = headerLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim().removeSurrounding("\"") }
            }

            while (lineIterator.hasNext()) {
                lineNumber++
                val line = lineIterator.next()
                val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim().removeSurrounding("\"") }
                reviewableRows.add(createReviewableRow(lineNumber + 1, tokens, accountsMap, categoriesMap))
            }
        }
        return CsvValidationReport(header, reviewableRows, lineNumber)
    }


    private fun createReviewableRow(
        lineNumber: Int,
        tokens: List<String>,
        accounts: Map<String, Account>,
        categories: Map<String, Category>,
    ): ReviewableRow {
        if (tokens.size < 8) return ReviewableRow(lineNumber, tokens, CsvRowStatus.INVALID_COLUMN_COUNT, "Invalid column count. Expected at least 8.")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        try {
            dateFormat.parse(tokens[2])
        } catch (
            e: Exception,
        ) {
            return ReviewableRow(lineNumber, tokens, CsvRowStatus.INVALID_DATE, "Invalid date format.")
        }

        val amount = tokens[4].toDoubleOrNull()
        if (amount == null || amount <= 0) return ReviewableRow(lineNumber, tokens, CsvRowStatus.INVALID_AMOUNT, "Invalid amount.")

        val categoryName = tokens[6]
        val accountName = tokens[7]

        val categoryExists = categories.containsKey(categoryName)
        val accountExists = accounts.containsKey(accountName)

        val status =
            when {
                !accountExists && !categoryExists -> CsvRowStatus.NEEDS_BOTH_CREATION
                !accountExists -> CsvRowStatus.NEEDS_ACCOUNT_CREATION
                !categoryExists -> CsvRowStatus.NEEDS_CATEGORY_CREATION
                else -> CsvRowStatus.VALID
            }
        val message =
            when (status) {
                CsvRowStatus.VALID -> "Ready to import."
                CsvRowStatus.NEEDS_BOTH_CREATION -> "New Account & Category will be created."
                CsvRowStatus.NEEDS_ACCOUNT_CREATION -> "New Account '$accountName' will be created."
                CsvRowStatus.NEEDS_CATEGORY_CREATION -> "New Category '$categoryName' will be created."
                else -> "This row has errors and will be skipped."
            }
        return ReviewableRow(lineNumber, tokens, status, message)
    }

    fun removeRowFromReport(rowToRemove: ReviewableRow) {
        _csvValidationReport.value?.let { currentReport ->
            val updatedRows = currentReport.reviewableRows.filter { it.lineNumber != rowToRemove.lineNumber }
            _csvValidationReport.value = currentReport.copy(reviewableRows = updatedRows)
        }
    }

    fun updateAndRevalidateRow(
        lineNumber: Int,
        correctedData: List<String>,
    ) {
        viewModelScope.launch {
            _csvValidationReport.value?.let { currentReport ->
                val currentRows = currentReport.reviewableRows.toMutableList()
                val indexToUpdate = currentRows.indexOfFirst { it.lineNumber == lineNumber }

                if (indexToUpdate != -1) {
                    val revalidatedRow =
                        withContext(dispatchers.io) {
                            val accountsMap = db.accountDao().getAllAccounts().first().associateBy { it.name }
                            val categoriesMap = db.categoryDao().getAllCategories().first().associateBy { it.name }
                            createReviewableRow(lineNumber, correctedData, accountsMap, categoriesMap)
                        }
                    currentRows[indexToUpdate] = revalidatedRow
                    _csvValidationReport.value = currentReport.copy(reviewableRows = currentRows)
                }
            }
        }
    }

    fun commitCsvImport(rowsToImport: List<ReviewableRow>) {
        viewModelScope.launch(dispatchers.io) {
            val header = _csvValidationReport.value?.header ?: run {
                Log.e("CsvImport", "Header not found in validation report. Aborting.")
                return@launch
            }
            val rows = rowsToImport.map { it.rowData }
            val isFinlightExport = header.contains("Id") && header.contains("ParentId")

            val learnedMappings = mutableMapOf<String, Int>()
            val allCategories = db.categoryDao().getAllCategories().first()
            val usedColorKeys = allCategories.mapNotNull { it.colorKey }.toMutableList()

            transactionRunner.run(db) {
                if (isFinlightExport) {
                    importFinlightCsv(header, rows, learnedMappings, usedColorKeys)
                } else {
                    importGenericCsv(header, rows, learnedMappings, usedColorKeys)
                }

                if (learnedMappings.isNotEmpty()) {
                    val newMappings = learnedMappings.map { (merchant, categoryId) ->
                        MerchantCategoryMapping(parsedName = merchant, categoryId = categoryId)
                    }
                    db.merchantCategoryMappingDao().insertAll(newMappings)
                    Log.d("CsvImport", "Learned and saved ${newMappings.size} new merchant-category mappings.")
                }
            }
        }
    }

    private suspend fun importFinlightCsv(
        header: List<String>,
        rows: List<List<String>>,
        learnedMappings: MutableMap<String, Int>,
        usedColorKeys: MutableList<String>
    ) {
        val idMap = mutableMapOf<String, Long>()
        val parents = rows.filter { it[header.indexOf("ParentId")].isBlank() }
        val children = rows.filter { it[header.indexOf("ParentId")].isNotBlank() }

        for (row in parents) {
            val oldId = row[header.indexOf("Id")]
            val isSplit = row[header.indexOf("Category")] == "Split Transaction"
            val transaction = createTransactionFromRow(row, header, isSplit = isSplit, learnedMappings = learnedMappings, usedColorKeys = usedColorKeys)
            val newId = transactionRepository.insertTransactionWithTags(transaction, getTagsFromRow(row, header))
            idMap[oldId] = newId
        }

        for (row in children) {
            val parentIdCsv = row[header.indexOf("ParentId")]
            val newParentId = idMap[parentIdCsv]?.toInt()
            if (newParentId == null) {
                Log.w("CsvImport", "Could not find parent for split row: $row")
                continue
            }
            val split = createSplitFromRow(row, header, newParentId, usedColorKeys)
            splitTransactionDao.insertAll(listOf(split))
        }
    }

    private suspend fun importGenericCsv(
        header: List<String>,
        rows: List<List<String>>,
        learnedMappings: MutableMap<String, Int>,
        usedColorKeys: MutableList<String>
    ) {
        for (row in rows) {
            val transaction = createTransactionFromRow(row, header, isSplit = false, learnedMappings = learnedMappings, usedColorKeys = usedColorKeys)
            transactionRepository.insertTransactionWithTags(transaction, getTagsFromRow(row, header))
        }
    }

    private suspend fun createTransactionFromRow(
        row: List<String>,
        header: List<String>,
        isSplit: Boolean,
        learnedMappings: MutableMap<String, Int>,
        usedColorKeys: MutableList<String>
    ): Transaction {
        val h = header.associateWith { header.indexOf(it) }.withDefault { -1 }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val date = dateFormat.parse(row[h.getValue("Date")])?.time ?: Date().time
        val description = row[h.getValue("Description")]
        val amount = row[h.getValue("Amount")].toDouble()
        val type = row[h.getValue("Type")].lowercase(Locale.getDefault())
        val categoryName = row[h.getValue("Category")]
        val accountName = row[h.getValue("Account")]
        val notes = row.getOrNull(h.getValue("Notes"))
        val isExcluded = row.getOrNull(h.getValue("IsExcluded")).toBoolean()

        val category = if (isSplit) null else findOrCreateCategory(categoryName, usedColorKeys)
        val account = findOrCreateAccount(accountName)

        if (!isSplit && description.isNotBlank() && category != null) {
            learnedMappings[description] = category.id
        }

        return Transaction(
            date = date,
            description = description,
            amount = amount,
            transactionType = type,
            categoryId = category?.id,
            accountId = account.id,
            notes = notes,
            isExcluded = isExcluded,
            source = "Imported",
            isSplit = isSplit
        )
    }

    private suspend fun createSplitFromRow(row: List<String>, header: List<String>, parentId: Int, usedColorKeys: MutableList<String>): SplitTransaction {
        val h = header.associateWith { header.indexOf(it) }.withDefault { -1 }

        val amount = row[h.getValue("Amount")].toDouble()
        val categoryName = row[h.getValue("Category")]
        val notes = row.getOrNull(h.getValue("Notes"))

        val category = findOrCreateCategory(categoryName, usedColorKeys)

        return SplitTransaction(
            parentTransactionId = parentId,
            amount = amount,
            categoryId = category.id,
            notes = notes
        )
    }

    private suspend fun getTagsFromRow(row: List<String>, header: List<String>): Set<Tag> {
        val h = header.associateWith { header.indexOf(it) }.withDefault { -1 }
        val tagsString = row.getOrNull(h.getValue("Tags"))
        val tagsToAssociate = mutableSetOf<Tag>()
        if (!tagsString.isNullOrBlank()) {
            val tagNames = tagsString.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            for (tagName in tagNames) {
                var tag = tagDao.findByName(tagName)
                if (tag == null) {
                    val newTagId = tagDao.insert(Tag(name = tagName))
                    tag = Tag(id = newTagId.toInt(), name = tagName)
                }
                tagsToAssociate.add(tag)
            }
        }
        return tagsToAssociate
    }

    private suspend fun findOrCreateCategory(name: String, usedColorKeys: MutableList<String>): Category {
        var category = categoryRepository.allCategories.first().find { it.name.equals(name, ignoreCase = true) }
        if (category == null) {
            val nextColor = CategoryIconHelper.getNextAvailableColor(usedColorKeys)
            usedColorKeys.add(nextColor)
            val newId = categoryRepository.insert(Category(name = name, iconKey = "letter_default", colorKey = nextColor))
            category = Category(id = newId.toInt(), name = name, iconKey = "letter_default", colorKey = nextColor)
        }
        return category
    }

    private suspend fun findOrCreateAccount(name: String): Account {
        var account = accountRepository.allAccounts.first().find { it.name.equals(name, ignoreCase = true) }
        if (account == null) {
            val newId = accountRepository.insert(Account(name = name, type = "Imported"))
            account = Account(id = newId.toInt(), name = name, type = "Imported")
        }
        return account
    }


    fun clearCsvValidationReport() {
        _csvValidationReport.value = null
    }

    fun createBackupSnapshot() {
        viewModelScope.launch {
            val success = withContext(dispatchers.io) {
                DataExportService.createBackupSnapshot(context)
            }
            if (success) {
                backupManager.dataChanged()
                _showBackupSuccessDialog.value = true
            } else {
                _uiEvent.send("Failed to create snapshot.")
            }
        }
    }
}
