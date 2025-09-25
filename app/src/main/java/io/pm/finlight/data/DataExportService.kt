// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/DataExportService.kt
// REASON: FEATURE - Added createBackupSnapshot function to generate a
// compressed (Gzip) JSON file of the database. This creates a highly compact
// and resilient data snapshot suitable for inclusion in Android's Auto Backup
// as a failsafe against database migration failures.
// FEATURE - Added restoreFromBackupSnapshot function to handle the core logic
// of checking for, decompressing, and importing the JSON snapshot on app startup.
// REFACTOR - The JSON import logic has been centralized into a private
// `importDataFromJsonString` function, which is now used by both the manual
// "Import from JSON" feature and the new automatic restore process.
// =================================================================================
package io.pm.finlight.data

import android.content.Context
import android.net.Uri
import android.util.Log
import io.pm.finlight.AppDataBackup
import io.pm.finlight.TransactionDetails
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.collections.forEach

object DataExportService {
    private val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }

    // --- NEW: Function to create a compressed JSON snapshot for backup ---
    suspend fun createBackupSnapshot(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = exportToJsonString(context) ?: return@withContext false

                // Compress the JSON string using Gzip
                val outputStream = ByteArrayOutputStream()
                GZIPOutputStream(outputStream).use { gzip ->
                    gzip.write(jsonString.toByteArray())
                }
                val compressedData = outputStream.toByteArray()

                // Save the compressed data to a specific file in internal storage
                val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
                FileOutputStream(snapshotFile).use { fos ->
                    fos.write(compressedData)
                }
                true
            } catch (e: Exception) {
                Log.e("DataExportService", "Failed to create compressed backup snapshot", e)
                false
            }
        }
    }

    // --- NEW: Function to restore data from the snapshot file ---
    suspend fun restoreFromBackupSnapshot(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            if (!snapshotFile.exists()) {
                Log.d("DataExportService", "No backup snapshot found. Proceeding with normal startup.")
                return@withContext false // No snapshot to restore
            }

            Log.d("DataExportService", "Backup snapshot found. Starting restore process.")
            try {
                // Decompress the Gzip file
                val jsonString = GZIPInputStream(FileInputStream(snapshotFile)).bufferedReader().use { it.readText() }

                // Import the data from the JSON string
                val success = importDataFromJsonString(context, jsonString)

                if (success) {
                    // CRITICAL: Delete the file after a successful restore to prevent re-importing on every app launch
                    snapshotFile.delete()
                    Log.d("DataExportService", "Restore successful. Snapshot file deleted.")
                } else {
                    Log.e("DataExportService", "Restore failed during data import phase.")
                }
                return@withContext success
            } catch (e: Exception) {
                Log.e("DataExportService", "Failed to restore from backup snapshot", e)
                // Attempt to delete the corrupted file to prevent future errors
                snapshotFile.delete()
                return@withContext false
            }
        }
    }

    // --- UPDATED: Add "Id" and "ParentId" to the CSV template header ---
    fun getCsvTemplateString(): String {
        return "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags\n"
    }

    suspend fun exportToJsonString(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)

                val backupData =
                    AppDataBackup(
                        transactions = db.transactionDao().getAllTransactionsSimple().first(),
                        accounts = db.accountDao().getAllAccounts().first(),
                        categories = db.categoryDao().getAllCategories().first(),
                        budgets = db.budgetDao().getAllBudgets().first(),
                        merchantMappings = db.merchantMappingDao().getAllMappings().first(),
                        // --- NEW: Include split transactions in the backup ---
                        splitTransactions = db.splitTransactionDao().getAllSplits().first()
                    )

                json.encodeToString(backupData)
            } catch (e: Exception) {
                Log.e("DataExportService", "Error exporting to JSON", e)
                null
            }
        }
    }

    // --- UPDATED: Refactored to use the new common import function ---
    suspend fun importDataFromJson(
        context: Context,
        uri: Uri,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()
                    .use { it?.readText() }
                if (jsonString == null) {
                    Log.e("DataExportService", "Failed to read JSON from URI.")
                    return@withContext false
                }
                importDataFromJsonString(context, jsonString)
            } catch (e: Exception) {
                Log.e("DataExportService", "Error importing from JSON URI", e)
                false
            }
        }
    }

    // --- NEW: Centralized import logic that takes a JSON string ---
    private suspend fun importDataFromJsonString(context: Context, jsonString: String): Boolean {
        return try {
            val backupData = json.decodeFromString<AppDataBackup>(jsonString)
            val db = AppDatabase.getInstance(context)

            // Clear all data in the correct order (respecting foreign keys)
            db.splitTransactionDao().deleteAll()
            db.transactionDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.budgetDao().deleteAll()
            db.merchantMappingDao().deleteAll()

            // Insert new data
            db.accountDao().insertAll(backupData.accounts)
            db.categoryDao().insertAll(backupData.categories)
            db.budgetDao().insertAll(backupData.budgets)
            db.merchantMappingDao().insertAll(backupData.merchantMappings)
            db.transactionDao().insertAll(backupData.transactions)
            db.splitTransactionDao().insertAll(backupData.splitTransactions)
            true
        } catch (e: Exception) {
            Log.e("DataExportService", "Error processing JSON string during import", e)
            false
        }
    }


    suspend fun exportToCsvString(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val transactionDao = db.transactionDao()
                val splitTransactionDao = db.splitTransactionDao()
                val transactions = transactionDao.getAllTransactions().first()
                val csvBuilder = StringBuilder()

                csvBuilder.append(getCsvTemplateString())

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                transactions.forEach { details: TransactionDetails ->
                    val transaction = details.transaction
                    val date = dateFormat.format(Date(transaction.date))
                    val description = escapeCsvField(transaction.description)
                    val amount = transaction.amount.toString()
                    val type = transaction.transactionType
                    val account = escapeCsvField(details.accountName ?: "N/A")
                    val notes = escapeCsvField(transaction.notes ?: "")
                    val isExcluded = transaction.isExcluded.toString()
                    val tags = transactionDao.getTagsForTransactionSimple(transaction.id)
                    val tagsString = tags.joinToString("|") { it.name }
                    val escapedTags = escapeCsvField(tagsString)

                    if (transaction.isSplit) {
                        // This is a parent transaction
                        val category = "Split Transaction" // Parent has a special category
                        csvBuilder.append("${transaction.id},,$date,$description,$amount,$type,$category,$account,$notes,$isExcluded,$escapedTags\n")

                        // Now fetch and append its children
                        val splits = splitTransactionDao.getSplitsForParentSimple(transaction.id)
                        splits.forEach { splitDetails ->
                            val split = splitDetails.splitTransaction
                            // Use notes as description for splits, fallback to category name
                            val splitDescription = escapeCsvField(split.notes ?: splitDetails.categoryName ?: "")
                            val splitAmount = split.amount.toString()
                            val splitCategory = escapeCsvField(splitDetails.categoryName ?: "N/A")
                            // Child rows have no ID of their own in this context, but link to the parent
                            csvBuilder.append(",${transaction.id},${dateFormat.format(Date(transaction.date))},$splitDescription,$splitAmount,$type,$splitCategory,$account,${escapeCsvField(split.notes ?: "")},$isExcluded,\n")
                        }
                    } else {
                        // This is a standard, non-split transaction
                        val category = escapeCsvField(details.categoryName ?: "N/A")
                        csvBuilder.append("${transaction.id},,$date,$description,$amount,$type,$category,$account,$notes,$isExcluded,$escapedTags\n")
                    }
                }
                csvBuilder.toString()
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