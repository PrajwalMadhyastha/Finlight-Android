// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/DataExportService.kt
// REASON: FIX (Issue #324) - Resolved audit findings by enforcing foreign key
// compliant deletion/insertion ordering, enclosing database restoration in a
// Room transaction, safeguarding stream lifecycle and file descriptors,
// synchronizing import with snapshotMutex, and centralizing snapshot file paths.
// =================================================================================
package io.pm.finlight.data

import android.content.Context
import android.net.Uri
import android.util.Log
import io.pm.finlight.TransactionDetails
import io.pm.finlight.data.db.AppDatabase
import androidx.room.withTransaction
import io.pm.finlight.data.model.AppDataBackup
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.collections.forEach

@OptIn(ExperimentalSerializationApi::class)
object DataExportService {
    const val SNAPSHOT_FILE_NAME = "backup_snapshot.gz"

    private val snapshotMutex = Mutex()
    private val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    fun getSnapshotFile(context: Context): File = File(context.filesDir, SNAPSHOT_FILE_NAME)

    suspend fun createBackupSnapshot(context: Context): Boolean {
        return snapshotMutex.withLock {
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
            withContext(dispatcherProvider.io) {
                val snapshotFile = getSnapshotFile(context)
                val tempFile = File(context.filesDir, "$SNAPSHOT_FILE_NAME.tmp")
                try {
                    writeBackupDataToTemp(context, tempFile)
                    commitTempSnapshot(tempFile, snapshotFile)
                } catch (e: CancellationException) {
                    safeDelete(tempFile)
                    throw e
                } catch (e: Exception) {
                    Log.e("DataExportService", "Failed to create compressed backup snapshot", e)
                    safeDelete(tempFile)
                    false
                }
            }
        }
    }

    private suspend fun writeBackupDataToTemp(
        context: Context,
        tempFile: File,
    ) {
        val finalBackupData = buildBackupData(context)
        GZIPOutputStream(FileOutputStream(tempFile).buffered()).use { gzip ->
            json.encodeToStream(finalBackupData, gzip)
        }
    }

    private fun commitTempSnapshot(
        tempFile: File,
        snapshotFile: File,
    ): Boolean {
        if (tempFile.renameTo(snapshotFile)) {
            return true
        }
        if (snapshotFile.exists() && !safeDelete(snapshotFile)) {
            safeDelete(tempFile)
            return false
        }
        if (!tempFile.renameTo(snapshotFile)) {
            safeDelete(tempFile)
            return false
        }
        return true
    }

    suspend fun restoreFromBackupSnapshot(context: Context): Boolean {
        return snapshotMutex.withLock {
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
            withContext(dispatcherProvider.io) {
                val snapshotFile = getSnapshotFile(context)
                if (!snapshotFile.exists()) {
                    Log.d("DataExportService", "No backup snapshot found. Proceeding with normal startup.")
                    return@withContext false // No snapshot to restore
                }

                Log.d("DataExportService", "Backup snapshot found. Starting restore process.")
                try {
                    val backupData = readBackupFromSnapshot(snapshotFile)
                    val success = restoreBackupData(context, backupData)
                    handleSnapshotPostRestore(snapshotFile, success)
                    success
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("DataExportService", "Failed to restore from backup snapshot", e)
                    safeDelete(snapshotFile)
                    false
                }
            }
        }
    }

    private fun readBackupFromSnapshot(snapshotFile: File): AppDataBackup {
        return GZIPInputStream(FileInputStream(snapshotFile).buffered()).use { gzipStream ->
            json.decodeFromStream<AppDataBackup>(gzipStream)
        }
    }

    private fun handleSnapshotPostRestore(
        snapshotFile: File,
        success: Boolean,
    ) {
        if (!success) {
            Log.e("DataExportService", "Restore failed during data import phase.")
            return
        }
        if (safeDelete(snapshotFile)) {
            Log.d("DataExportService", "Restore successful. Snapshot file deleted.")
        } else {
            Log.w("DataExportService", "Restore successful, but failed to delete snapshot file.")
        }
    }

    private fun safeDelete(file: File): Boolean {
        if (!file.exists()) {
            return true
        }
        val deleted = file.delete()
        if (!deleted) {
            Log.w("DataExportService", "Failed to delete file: ${file.path}")
        }
        return deleted
    }

    fun getCsvTemplateString(): String {
        return "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags\n"
    }

    internal suspend fun buildBackupData(context: Context): AppDataBackup {
        val db = AppDatabase.getInstance(context)

        val backupData =
            AppDataBackup(
                transactions = db.transactionQueryDao().getAllTransactionsSimple().first(),
                accounts = db.accountDao().getAllAccountsSnapshot(),
                categories = db.categoryDao().getAllCategoriesSnapshot(),
                budgets = db.budgetDao().getAllBudgets().first(),
                merchantMappings = db.merchantMappingDao().getAllMappings().first(),
                splitTransactions = db.splitTransactionDao().getAllSplits().first(),
                // --- Phase 1: Export Core Parsing Intelligence ---
                customSmsRules = db.customSmsRuleDao().getAllRulesList(),
                merchantRenameRules = db.merchantRenameRuleDao().getAllRulesList(),
                merchantCategoryMappings = db.merchantCategoryMappingDao().getAll(),
                ignoreRules = db.ignoreRuleDao().getAllList(),
                smsParseTemplates = db.smsParseTemplateDao().getAllTemplates(),
                // --- Phase 2: Export Remaining App Intelligence ---
                tags = db.tagDao().getAllTagsList(),
                transactionTagCrossRefs = db.transactionQueryDao().getAllCrossRefs(),
                goals = db.goalDao().getAll(),
                goalTransactionLinks = db.goalTransactionLinkDao().getAll(),
                trips = db.tripDao().getAll(),
                accountAliases = db.accountAliasDao().getAll(),
                // --- Phase 3: Export App-Learned Recurring Patterns ---
                recurringPatterns = db.recurringPatternDao().getAllPatterns(),
                // --- Phase 5: Export SMS Lifecycle & Merge History ---
                deletedSmsHashes = db.deletedSmsHashDao().getAll(),
                mergeRecords = db.mergeRecordDao().getAll(),
            )

        // --- Phase 4 & 6: Export User Profile, Budgets, & Preferences ---
        val prefs =
            try {
                context.financeSettingsDataStore.data.first()
            } catch (e: Exception) {
                null
            }
        val userName = prefs?.get(stringPreferencesKey("user_name"))
        val homeCurrency = prefs?.get(stringPreferencesKey("home_currency_code"))
        val overallBudgets = mutableMapOf<String, Float>()
        prefs?.asMap()?.forEach { (key, value) ->
            if (key.name.startsWith("overall_budget_") && value is Float) {
                val yearMonth = key.name.removePrefix("overall_budget_")
                overallBudgets[yearMonth] = value
            }
        }
        val calendar = Calendar.getInstance()
        val currentMonthKey = String.format(Locale.ROOT, "%d_%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        val currentBudget = overallBudgets[currentMonthKey] ?: overallBudgets.entries.maxByOrNull { it.key }?.value

        val selectedAppTheme = prefs?.get(stringPreferencesKey("selected_app_theme"))
        val dashboardCardOrder = prefs?.get(stringPreferencesKey("dashboard_card_order"))
        val travelModeSettings = prefs?.get(stringPreferencesKey("travel_mode_settings"))
        val smsScanStartDate = prefs?.get(longPreferencesKey("sms_scan_start_date"))
        val dismissedMergeSuggestions = prefs?.get(stringSetPreferencesKey("dismissed_merge_suggestions")) ?: emptySet()
        val excludedIncomeMonths = prefs?.get(stringSetPreferencesKey("excluded_income_months")) ?: emptySet()
        val excludedExpenseMonths = prefs?.get(stringSetPreferencesKey("excluded_expense_months")) ?: emptySet()
        val appLockEnabled = prefs?.get(booleanPreferencesKey("app_lock_enabled"))
        val privacyModeEnabled = prefs?.get(booleanPreferencesKey("privacy_mode_enabled"))

        val dailyReportEnabled = prefs?.get(booleanPreferencesKey("daily_report_enabled"))
        val dailyReportHour = prefs?.get(intPreferencesKey("daily_report_hour"))
        val dailyReportMinute = prefs?.get(intPreferencesKey("daily_report_minute"))
        val weeklySummaryEnabled = prefs?.get(booleanPreferencesKey("weekly_summary_enabled"))
        val weeklyReportDay = prefs?.get(intPreferencesKey("weekly_report_day"))
        val weeklyReportHour = prefs?.get(intPreferencesKey("weekly_report_hour"))
        val weeklyReportMinute = prefs?.get(intPreferencesKey("weekly_report_minute"))
        val monthlySummaryEnabled = prefs?.get(booleanPreferencesKey("monthly_summary_enabled"))
        val monthlyReportDay = prefs?.get(intPreferencesKey("monthly_report_day"))
        val monthlyReportHour = prefs?.get(intPreferencesKey("monthly_report_hour"))
        val monthlyReportMinute = prefs?.get(intPreferencesKey("monthly_report_minute"))
        val autocaptureNotificationEnabled = prefs?.get(booleanPreferencesKey("autocapture_notification_enabled"))
        val unknownTransactionPopupEnabled = prefs?.get(booleanPreferencesKey("unknown_transaction_popup_enabled"))

        val profilePictureUri = prefs?.get(stringPreferencesKey("profile_picture_uri"))
        val profilePictureBase64 =
            if (profilePictureUri != null) {
                try {
                    val picFile = File(profilePictureUri)
                    if (picFile.exists() && picFile.length() in 1..(5 * 1024 * 1024)) {
                        Base64.encodeToString(picFile.readBytes(), Base64.NO_WRAP)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w("DataExportService", "Failed to encode profile picture", e)
                    null
                }
            } else {
                null
            }

        return backupData.copy(
            userName = userName,
            homeCurrency = homeCurrency,
            overallBudget = currentBudget,
            overallBudgets = overallBudgets,
            selectedAppTheme = selectedAppTheme,
            dashboardCardOrder = dashboardCardOrder,
            travelModeSettings = travelModeSettings,
            smsScanStartDate = smsScanStartDate,
            dismissedMergeSuggestions = dismissedMergeSuggestions,
            excludedIncomeMonths = excludedIncomeMonths,
            excludedExpenseMonths = excludedExpenseMonths,
            appLockEnabled = appLockEnabled,
            privacyModeEnabled = privacyModeEnabled,
            dailyReportEnabled = dailyReportEnabled,
            dailyReportHour = dailyReportHour,
            dailyReportMinute = dailyReportMinute,
            weeklySummaryEnabled = weeklySummaryEnabled,
            weeklyReportDay = weeklyReportDay,
            weeklyReportHour = weeklyReportHour,
            weeklyReportMinute = weeklyReportMinute,
            monthlySummaryEnabled = monthlySummaryEnabled,
            monthlyReportDay = monthlyReportDay,
            monthlyReportHour = monthlyReportHour,
            monthlyReportMinute = monthlyReportMinute,
            autocaptureNotificationEnabled = autocaptureNotificationEnabled,
            unknownTransactionPopupEnabled = unknownTransactionPopupEnabled,
            profilePictureBase64 = profilePictureBase64,
        )
    }

    suspend fun exportToJson(
        context: Context,
        outputStream: OutputStream,
    ): Boolean {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
        return withContext(dispatcherProvider.io) {
            try {
                val finalBackupData = buildBackupData(context)
                outputStream.buffered().use { bos ->
                    json.encodeToStream(finalBackupData, bos)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DataExportService", "Error exporting to JSON stream", e)
                false
            }
        }
    }

    suspend fun exportToJsonString(context: Context): String? {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
        return withContext(dispatcherProvider.io) {
            try {
                val finalBackupData = buildBackupData(context)
                json.encodeToString(finalBackupData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DataExportService", "Error exporting to JSON", e)
                null
            }
        }
    }

    suspend fun importDataFromJson(
        context: Context,
        uri: Uri,
    ): Boolean {
        return snapshotMutex.withLock {
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
            withContext(dispatcherProvider.io) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        Log.e("DataExportService", "Failed to open InputStream from URI.")
                        return@withContext false
                    }
                    val backupData =
                        inputStream.buffered().use { stream ->
                            json.decodeFromStream<AppDataBackup>(stream)
                        }

                    restoreBackupData(context, backupData)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("DataExportService", "Error importing from JSON URI", e)
                    false
                }
            }
        }
    }

    private suspend fun restoreBackupData(
        context: Context,
        backupData: AppDataBackup,
    ): Boolean {
        return try {
            val db = AppDatabase.getInstance(context)
            db.withTransaction {
                clearDatabase(db)
                insertBackupEntities(db, backupData)
            }
            restorePreferences(context, backupData)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DataExportService", "Error restoring backup data into database", e)
            false
        }
    }

    private suspend fun clearDatabase(db: AppDatabase) {
        // Clear all data in the correct order (respecting foreign keys)
        db.mergeRecordDao().deleteAll()
        db.goalTransactionLinkDao().deleteAll()
        db.splitTransactionDao().deleteAll()
        db.transactionWriteDao().deleteAll() // Deletes transactions and their tag cross-refs via cascade
        db.tripDao().deleteAll()
        db.goalDao().deleteAll()
        db.accountAliasDao().deleteAll()
        db.tagDao().deleteAll()
        db.accountDao().deleteAll()
        db.categoryDao().deleteAll()
        db.budgetDao().deleteAll()
        db.merchantMappingDao().deleteAll()
        // --- Phase 1: Clear Core Parsing Intelligence Tables ---
        db.customSmsRuleDao().deleteAll()
        db.merchantRenameRuleDao().deleteAll()
        db.merchantCategoryMappingDao().deleteAll()
        db.ignoreRuleDao().deleteAll()
        db.smsParseTemplateDao().deleteAll()
        // --- Phase 3: Clear App-Learned Recurring Patterns ---
        db.recurringPatternDao().deleteAll()
        // --- Phase 5: Clear SMS Lifecycle Deny-List ---
        db.deletedSmsHashDao().deleteAll()
    }

    private suspend fun insertBackupEntities(
        db: AppDatabase,
        backupData: AppDataBackup,
    ) {
        // Base entities (no foreign key dependencies)
        db.accountDao().insertAll(backupData.accounts)
        db.categoryDao().insertAll(backupData.categories)
        db.tagDao().insertAll(backupData.tags)
        db.budgetDao().insertAll(backupData.budgets)
        db.merchantMappingDao().insertAll(backupData.merchantMappings)

        // Entities depending on base entities
        db.accountAliasDao().insertAll(backupData.accountAliases)
        db.goalDao().insertAll(backupData.goals)
        db.tripDao().insertAll(backupData.trips)
        db.transactionWriteDao().insertAll(backupData.transactions)

        // Entities depending on transactions and tags/goals
        db.splitTransactionDao().insertAll(backupData.splitTransactions)
        db.transactionWriteDao().addTagsToTransaction(backupData.transactionTagCrossRefs)
        db.goalTransactionLinkDao().insertAll(backupData.goalTransactionLinks)
        db.mergeRecordDao().insertAll(backupData.mergeRecords)

        // --- Phase 1: Insert Core Parsing Intelligence Data ---
        db.customSmsRuleDao().insertAll(backupData.customSmsRules)
        db.merchantRenameRuleDao().insertAll(backupData.merchantRenameRules)
        db.merchantCategoryMappingDao().insertAll(backupData.merchantCategoryMappings)
        db.ignoreRuleDao().insertAll(backupData.ignoreRules)
        db.smsParseTemplateDao().insertAll(backupData.smsParseTemplates)
        // --- Phase 3: Insert App-Learned Recurring Patterns ---
        backupData.recurringPatterns.forEach { db.recurringPatternDao().insert(it) }

        // --- Phase 5: Insert SMS Lifecycle Deny-List ---
        db.deletedSmsHashDao().insertAll(backupData.deletedSmsHashes)
    }

    private suspend fun restorePreferences(
        context: Context,
        backupData: AppDataBackup
    ) {
        try {
            context.financeSettingsDataStore.edit { prefs ->
                restoreGeneralPreferences(context, prefs, backupData)
                restoreNotificationAndReportPreferences(prefs, backupData)
                restoreProfilePicture(context, prefs, backupData.profilePictureBase64)
            }
        } catch (e: Exception) {
            Log.e("DataExportService", "Failed to restore user settings preferences", e)
        }
    }

    private fun restoreGeneralPreferences(
        context: Context,
        prefs: MutablePreferences,
        backupData: AppDataBackup,
    ) {
        backupData.userName?.let { name ->
            if (name.isNotBlank()) {
                prefs[stringPreferencesKey("user_name")] = name
            }
        }
        backupData.homeCurrency?.let { currency ->
            if (currency.isNotBlank()) {
                prefs[stringPreferencesKey("home_currency_code")] = currency
            }
        }
        backupData.overallBudgets.forEach { (yearMonth, amount) ->
            prefs[floatPreferencesKey("overall_budget_$yearMonth")] = amount
        }
        if (backupData.overallBudgets.isEmpty() && backupData.overallBudget != null) {
            val cal = Calendar.getInstance()
            val currentKey =
                String.format(
                    Locale.ROOT,
                    "overall_budget_%d_%02d",
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                )
            prefs[floatPreferencesKey(currentKey)] = backupData.overallBudget
        }

        backupData.selectedAppTheme?.let { prefs[stringPreferencesKey("selected_app_theme")] = it }
        backupData.dashboardCardOrder?.let { prefs[stringPreferencesKey("dashboard_card_order")] = it }
        backupData.travelModeSettings?.let { prefs[stringPreferencesKey("travel_mode_settings")] = it }
        backupData.smsScanStartDate?.let { prefs[longPreferencesKey("sms_scan_start_date")] = it }
        if (backupData.dismissedMergeSuggestions.isNotEmpty()) {
            prefs[stringSetPreferencesKey("dismissed_merge_suggestions")] = backupData.dismissedMergeSuggestions
        }
        if (backupData.excludedIncomeMonths.isNotEmpty()) {
            prefs[stringSetPreferencesKey("excluded_income_months")] = backupData.excludedIncomeMonths
        }
        if (backupData.excludedExpenseMonths.isNotEmpty()) {
            prefs[stringSetPreferencesKey("excluded_expense_months")] = backupData.excludedExpenseMonths
        }
        backupData.appLockEnabled?.let { enabled ->
            prefs[booleanPreferencesKey("app_lock_enabled")] = enabled && canAuthenticateOnDevice(context)
        }
        backupData.privacyModeEnabled?.let { prefs[booleanPreferencesKey("privacy_mode_enabled")] = it }
    }

    private fun canAuthenticateOnDevice(context: Context): Boolean {
        return try {
            val biometricManager = androidx.biometric.BiometricManager.from(context)
            val authenticators =
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            biometricManager.canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun restoreNotificationAndReportPreferences(
        prefs: MutablePreferences,
        backupData: AppDataBackup,
    ) {
        backupData.dailyReportEnabled?.let { prefs[booleanPreferencesKey("daily_report_enabled")] = it }
        backupData.dailyReportHour?.let { prefs[intPreferencesKey("daily_report_hour")] = it }
        backupData.dailyReportMinute?.let { prefs[intPreferencesKey("daily_report_minute")] = it }
        backupData.weeklySummaryEnabled?.let { prefs[booleanPreferencesKey("weekly_summary_enabled")] = it }
        backupData.weeklyReportDay?.let { prefs[intPreferencesKey("weekly_report_day")] = it }
        backupData.weeklyReportHour?.let { prefs[intPreferencesKey("weekly_report_hour")] = it }
        backupData.weeklyReportMinute?.let { prefs[intPreferencesKey("weekly_report_minute")] = it }
        backupData.monthlySummaryEnabled?.let { prefs[booleanPreferencesKey("monthly_summary_enabled")] = it }
        backupData.monthlyReportDay?.let { prefs[intPreferencesKey("monthly_report_day")] = it }
        backupData.monthlyReportHour?.let { prefs[intPreferencesKey("monthly_report_hour")] = it }
        backupData.monthlyReportMinute?.let { prefs[intPreferencesKey("monthly_report_minute")] = it }
        backupData.autocaptureNotificationEnabled?.let { prefs[booleanPreferencesKey("autocapture_notification_enabled")] = it }
        backupData.unknownTransactionPopupEnabled?.let { prefs[booleanPreferencesKey("unknown_transaction_popup_enabled")] = it }
    }

    private fun restoreProfilePicture(
        context: Context,
        prefs: MutablePreferences,
        base64Str: String?,
    ) {
        if (base64Str == null) return
        try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val profileDir = File(context.filesDir, "profile")
            if (!profileDir.exists()) profileDir.mkdirs()
            val picFile = File(profileDir, "profile_picture.jpg")
            picFile.writeBytes(bytes)
            prefs[stringPreferencesKey("profile_picture_uri")] = picFile.absolutePath
        } catch (e: Exception) {
            Log.w("DataExportService", "Failed to restore profile picture", e)
        }
    }

    suspend fun exportToCsvString(context: Context): String? {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
        return withContext(dispatcherProvider.io) {
            try {
                val db = AppDatabase.getInstance(context)
                val transactionQueryDao = db.transactionQueryDao()
                val splitTransactionDao = db.splitTransactionDao()
                val transactions = transactionQueryDao.getAllTransactions().first()
                val csvBuilder = StringBuilder()

                csvBuilder.append(getCsvTemplateString())

                val dateFormat = FormatUtils.getFormatter("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                transactions.forEach { details: TransactionDetails ->
                    val transaction = details.transaction
                    val date = dateFormat.format(Date(transaction.date))
                    val description = escapeCsvField(transaction.description)
                    val amount = transaction.amount.toString()
                    val type = transaction.transactionType.name.lowercase()
                    val account = escapeCsvField(details.accountName ?: "N/A")
                    val notes = escapeCsvField(transaction.notes ?: "")
                    val isExcluded = transaction.isExcluded.toString()
                    val tags = transactionQueryDao.getTagsForTransactionSimple(transaction.id)
                    val tagsString = tags.joinToString("|") { it.name }
                    val escapedTags = escapeCsvField(tagsString)

                    if (transaction.isSplit) {
                        val parentRow =
                            listOf(
                                transaction.id.toString(),
                                // ParentId
                                "",
                                date,
                                description,
                                amount,
                                type,
                                // Category for parent
                                "Split Transaction",
                                account,
                                notes,
                                isExcluded,
                                escapedTags,
                            ).joinToString(",")
                        csvBuilder.appendLine(parentRow)

                        // Now fetch and append its children
                        val splits = splitTransactionDao.getSplitsForParentSimple(transaction.id)
                        splits.forEach { splitDetails ->
                            val split = splitDetails.splitTransaction
                            val splitDescription = escapeCsvField(split.notes ?: splitDetails.categoryName ?: "")
                            val splitAmount = split.amount.toString()
                            val splitCategory = escapeCsvField(splitDetails.categoryName ?: "N/A")

                            // Child rows have no ID of their own in this context, but link to the parent
                            val childRow =
                                listOf(
                                    // Id
                                    "",
                                    // ParentId
                                    transaction.id.toString(),
                                    dateFormat.format(Date(transaction.date)),
                                    splitDescription,
                                    splitAmount,
                                    type,
                                    splitCategory,
                                    account,
                                    escapeCsvField(split.notes ?: ""),
                                    isExcluded,
                                    // <-- THE FIX: Add an empty string for the missing Tags column
                                    "",
                                ).joinToString(",")
                            csvBuilder.appendLine(childRow)
                        }
                    } else {
                        // This is a standard, non-split transaction
                        val category = escapeCsvField(details.categoryName ?: "N/A")
                        val row =
                            listOf(
                                transaction.id.toString(),
                                // ParentId
                                "",
                                date,
                                description,
                                amount,
                                type,
                                category,
                                account,
                                notes,
                                isExcluded,
                                escapedTags,
                            ).joinToString(",")
                        csvBuilder.appendLine(row)
                    }
                }
                csvBuilder.toString()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DataExportService", "Error exporting to CSV", e)
                null
            }
        }
    }

    private fun escapeCsvField(field: String): String {
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"${field.replace("\"", "\"\"")}\""
        }
        return field
    }
}
