// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SettingsViewModel.kt
// REASON: FIX - The `commitCsvImport` function signature has been changed from
// accepting a Uri to accepting a List<ReviewableRow>. This resolves the
// argument type mismatch error and makes the import logic more robust by
// operating on the validated (and potentially modified) list of rows from the
// UI, rather than re-reading the original file. The `generateValidationReport`
// function is also updated to capture and store the CSV header.
// =================================================================================
package io.pm.finlight

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.utils.ReminderManager
import io.pm.finlight.utils.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed class ScanResult {
    data class Success(val count: Int) : ScanResult()
    object Error : ScanResult()
}


class SettingsViewModel(application: Application) : AndroidViewModel(application) {
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

    private val _potentialTransactions = MutableStateFlow<List<PotentialTransaction>>(emptyList())
    val potentialTransactions: StateFlow<List<PotentialTransaction>> = _potentialTransactions.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
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

    init {
        smsScanStartDate =
            settingsRepository.getSmsScanStartDate()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0L,
                )
    }

    fun saveSelectedTheme(theme: AppTheme) {
        settingsRepository.saveSelectedTheme(theme)
    }

    fun rescanSmsForReview(startDate: Long?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
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
                    rawMessages.mapNotNull { sms ->
                        SmsParser.parse(
                            sms,
                            existingMappings,
                            db.customSmsRuleDao(),
                            db.merchantRenameRuleDao(),
                            db.ignoreRuleDao(),
                            db.merchantCategoryMappingDao()
                        )
                    }
                }

                val newPotentialTransactions = parsedList.filter { potential ->
                    !existingSmsHashes.contains(potential.sourceSmsHash)
                }

                _potentialTransactions.value = newPotentialTransactions
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during SMS scan for review", e)
            } finally {
                _isScanning.value = false
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
        var lineNumber = 0 // Start at 0 to account for header

        getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            val lineIterator = lines.iterator()
            if (lineIterator.hasNext()) {
                val headerLine = lineIterator.next()
                header = headerLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim().removeSurrounding("\"") }
            }

            while (lineIterator.hasNext()) {
                lineNumber++ // This is now the actual line number in the file (starting from 2)
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
            dateFormat.parse(tokens[2]) // Date is now at index 2
        } catch (
            e: Exception,
        ) {
            return ReviewableRow(lineNumber, tokens, CsvRowStatus.INVALID_DATE, "Invalid date format.")
        }

        val amount = tokens[4].toDoubleOrNull() // Amount is now at index 4
        if (amount == null || amount <= 0) return ReviewableRow(lineNumber, tokens, CsvRowStatus.INVALID_AMOUNT, "Invalid amount.")

        val categoryName = tokens[6] // Category is now at index 6
        val accountName = tokens[7] // Account is now at index 7

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

            db.withTransaction {
                if (isFinlightExport) {
                    importFinlightCsv(header, rows)
                } else {
                    importGenericCsv(header, rows)
                }
            }
        }
    }

    private suspend fun importFinlightCsv(header: List<String>, rows: List<List<String>>) {
        val idMap = mutableMapOf<String, Long>() // Map CSV ID to new DB ID
        val parents = rows.filter { it[header.indexOf("ParentId")].isBlank() }
        val children = rows.filter { it[header.indexOf("ParentId")].isNotBlank() }

        // Pass 1: Import parents and standard transactions
        for (row in parents) {
            val oldId = row[header.indexOf("Id")]
            val isSplit = row[header.indexOf("Category")] == "Split Transaction"
            val transaction = createTransactionFromRow(row, header, isSplit = isSplit)
            val newId = transactionRepository.insertTransactionWithTags(transaction, getTagsFromRow(row, header))
            idMap[oldId] = newId
        }

        // Pass 2: Import child splits
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

    private suspend fun importGenericCsv(header: List<String>, rows: List<List<String>>) {
        for (row in rows) {
            val transaction = createTransactionFromRow(row, header, isSplit = false)
            transactionRepository.insertTransactionWithTags(transaction, getTagsFromRow(row, header))
        }
    }

    private suspend fun createTransactionFromRow(row: List<String>, header: List<String>, isSplit: Boolean): Transaction {
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
