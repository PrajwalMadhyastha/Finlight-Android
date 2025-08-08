// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SettingsViewModel.kt
// REASON: FEATURE - Reworked the SMS scanning feature. The ViewModel now
// differentiates between parsed transactions with a known category and those
// without. Transactions with a category are now automatically imported, while
// only those needing categorization are sent to the review screen. This aligns
// with the new, more efficient user workflow for bulk imports.
// =================================================================================
package io.pm.finlight

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.utils.ReminderManager
import io.pm.finlight.utils.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val transactionViewModel: TransactionViewModel
) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val transactionRepository = TransactionRepository(db.transactionDao())
    private val merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao())
    private val context = application
    private val accountRepository = AccountRepository(db.accountDao())
    private val categoryRepository = CategoryRepository(db.categoryDao())
    private val tagDao = db.tagDao()
    private val splitTransactionDao = db.splitTransactionDao()


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

    val autoBackupTime: StateFlow<Pair<Int, Int>> =
        settingsRepository.getAutoBackupTime().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Pair(2, 0)
        )

    val autoBackupNotificationEnabled: StateFlow<Boolean> =
        settingsRepository.getAutoBackupNotificationEnabled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
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

    fun rescanSmsWithNewRule(onComplete: (Int) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            onComplete(0)
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
            var newTransactionsFound = 0
            try {
                // Scan last 30 days
                val startDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
                val rawMessages = withContext(Dispatchers.IO) {
                    SmsRepository(context).fetchAllSms(startDate)
                }

                val existingMappings = withContext(Dispatchers.IO) {
                    merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })
                }
                val existingSmsHashes = withContext(Dispatchers.IO) {
                    transactionRepository.getAllSmsHashes().first().toSet()
                }

                val parsedList = withContext(Dispatchers.Default) {
                    rawMessages.map { sms ->
                        async {
                            SmsParser.parse(
                                sms,
                                existingMappings,
                                db.customSmsRuleDao(),
                                db.merchantRenameRuleDao(),
                                db.ignoreRuleDao(),
                                db.merchantCategoryMappingDao()
                            )
                        }
                    }.awaitAll().filterNotNull()
                }

                val newPotentialTransactions = parsedList.filter { potential ->
                    !existingSmsHashes.contains(potential.sourceSmsHash)
                }

                for (potentialTxn in newPotentialTransactions) {
                    val success = transactionViewModel.autoSaveSmsTransaction(potentialTxn)
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

    fun setAutoCaptureNotificationEnabled(enabled: Boolean) {
        settingsRepository.saveAutoCaptureNotificationEnabled(enabled)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        settingsRepository.saveAutoBackupEnabled(enabled)
        if (enabled) ReminderManager.scheduleAutoBackup(context) else ReminderManager.cancelAutoBackup(context)
    }

    fun saveAutoBackupTime(hour: Int, minute: Int) {
        settingsRepository.saveAutoBackupTime(hour, minute)
        if (autoBackupEnabled.value) {
            ReminderManager.scheduleAutoBackup(context)
        }
    }

    fun setAutoBackupNotificationEnabled(enabled: Boolean) {
        settingsRepository.saveAutoBackupNotificationEnabled(enabled)
    }


    fun saveSelectedTheme(theme: AppTheme) {
        settingsRepository.saveSelectedTheme(theme)
    }

    fun startFullSmsScan(startDate: Long?, onScanComplete: (needsReview: Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "SMS Read permission is required for this feature.", Toast.LENGTH_LONG).show()
            onScanComplete(false)
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
            var autoImportedCount = 0
            var needsReviewCount = 0
            try {
                val rawMessages = withContext(Dispatchers.IO) {
                    SmsRepository(context).fetchAllSms(startDate)
                }

                val existingMappings = withContext(Dispatchers.IO) {
                    merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })
                }

                val existingSmsHashes = withContext(Dispatchers.IO) {
                    transactionRepository.getAllSmsHashes().first().toSet()
                }

                val parsedList = withContext(Dispatchers.Default) {
                    rawMessages.map { sms ->
                        async {
                            SmsParser.parse(
                                sms,
                                existingMappings,
                                db.customSmsRuleDao(),
                                db.merchantRenameRuleDao(),
                                db.ignoreRuleDao(),
                                db.merchantCategoryMappingDao()
                            )
                        }
                    }.awaitAll().filterNotNull()
                }

                val newPotentialTransactions = parsedList.filter { potential ->
                    !existingSmsHashes.contains(potential.sourceSmsHash)
                }

                val (autoImportList, reviewList) = newPotentialTransactions.partition { it.categoryId != null }

                for (potentialTxn in autoImportList) {
                    val success = transactionViewModel.autoSaveSmsTransaction(potentialTxn)
                    if (success) {
                        autoImportedCount++
                    }
                }

                _potentialTransactions.value = reviewList
                needsReviewCount = reviewList.size

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during SMS scan for review", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "An error occurred during the scan.", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isScanning.value = false
                withContext(Dispatchers.Main) {
                    val message = when {
                        autoImportedCount > 0 && needsReviewCount > 0 -> "Auto-imported $autoImportedCount transactions. $needsReviewCount need review."
                        autoImportedCount > 0 -> "Successfully auto-imported $autoImportedCount transactions."
                        needsReviewCount > 0 -> "$needsReviewCount transactions need your review."
                        else -> "No new transactions found."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                onScanComplete(needsReviewCount > 0)
            }
        }
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
        viewModelScope.launch(Dispatchers.IO) {
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
            withContext(Dispatchers.IO) {
                try {
                    val report = generateValidationReport(uri)
                    _csvValidationReport.value = report
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "CSV validation failed", e)
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

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
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
                        withContext(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val header = _csvValidationReport.value?.header ?: run {
                Log.e("CsvImport", "Header not found in validation report. Aborting.")
                return@launch
            }
            val rows = rowsToImport.map { it.rowData }
            val isFinlightExport = header.contains("Id") && header.contains("ParentId")

            val learnedMappings = mutableMapOf<String, Int>()

            db.withTransaction {
                if (isFinlightExport) {
                    importFinlightCsv(header, rows, learnedMappings)
                } else {
                    importGenericCsv(header, rows, learnedMappings)
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
        learnedMappings: MutableMap<String, Int>
    ) {
        val idMap = mutableMapOf<String, Long>()
        val parents = rows.filter { it[header.indexOf("ParentId")].isBlank() }
        val children = rows.filter { it[header.indexOf("ParentId")].isNotBlank() }

        for (row in parents) {
            val oldId = row[header.indexOf("Id")]
            val isSplit = row[header.indexOf("Category")] == "Split Transaction"
            val transaction = createTransactionFromRow(row, header, isSplit = isSplit, learnedMappings = learnedMappings)
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
            val split = createSplitFromRow(row, header, newParentId)
            splitTransactionDao.insertAll(listOf(split))
        }
    }

    private suspend fun importGenericCsv(
        header: List<String>,
        rows: List<List<String>>,
        learnedMappings: MutableMap<String, Int>
    ) {
        for (row in rows) {
            val transaction = createTransactionFromRow(row, header, isSplit = false, learnedMappings = learnedMappings)
            transactionRepository.insertTransactionWithTags(transaction, getTagsFromRow(row, header))
        }
    }

    private suspend fun createTransactionFromRow(
        row: List<String>,
        header: List<String>,
        isSplit: Boolean,
        learnedMappings: MutableMap<String, Int>
    ): Transaction {
        val h = header.associateWith { header.indexOf(it) }.withDefault { -1 }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val date = dateFormat.parse(row[h.getValue("Date")])?.time ?: Date().time
        val description = row[h.getValue("Description")]
        val amount = row[h.getValue("Amount")].toDouble()
        val type = row[h.getValue("Type")].lowercase(Locale.getDefault())
        val categoryName = row[h.getValue("Category")]
        val accountName = row[h.getValue("Account")]
        val notes = row.getOrNull(h.getValue("Notes"))
        val isExcluded = row.getOrNull(h.getValue("IsExcluded")).toBoolean()

        val category = if (isSplit) null else findOrCreateCategory(categoryName)
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

    private suspend fun createSplitFromRow(row: List<String>, header: List<String>, parentId: Int): SplitTransaction {
        val h = header.associateWith { header.indexOf(it) }.withDefault { -1 }

        val amount = row[h.getValue("Amount")].toDouble()
        val categoryName = row[h.getValue("Category")]
        val notes = row.getOrNull(h.getValue("Notes"))

        val category = findOrCreateCategory(categoryName)

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

    private suspend fun findOrCreateCategory(name: String): Category {
        var category = categoryRepository.allCategories.first().find { it.name.equals(name, ignoreCase = true) }
        if (category == null) {
            val newId = categoryRepository.insert(Category(name = name))
            category = Category(id = newId.toInt(), name = name)
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
}
