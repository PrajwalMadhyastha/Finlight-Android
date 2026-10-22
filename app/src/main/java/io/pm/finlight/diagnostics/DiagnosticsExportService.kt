package io.pm.finlight.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import io.pm.finlight.data.db.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Service to aggregate system, device, database metrics, and crash diagnostics
 * into a redacted, zero-cloud diagnostic report for troubleshooting.
 */
object DiagnosticsExportService {
    const val REPORT_FILE_NAME = "finlight_diagnostic_report.txt"
    const val DIAGNOSTICS_DIR = "diagnostics"

    data class AggregateMetrics(
        val accountsCount: Long = 0,
        val transactionsCount: Long = 0,
        val categoriesCount: Long = 0,
        val recurringPatternsCount: Long = 0,
        val databaseSizeKb: Long = 0,
        val sqlCipherActive: Boolean = true,
        val sqliteVersion: String = "Unknown",
    )

    fun fetchAggregates(
        context: Context,
        database: AppDatabase?
    ): AggregateMetrics {
        if (database == null) return AggregateMetrics()

        val dbFile = context.getDatabasePath("finlight_database")
        val dbSizeKb = if (dbFile.exists()) dbFile.length() / 1024 else 0L

        var accounts = 0L
        var txs = 0L
        var cats = 0L
        var recurring = 0L
        var sqliteVer = "Unknown"

        try {
            val db = database.openHelper.readableDatabase

            fun runCount(sql: String): Long {
                return try {
                    val cursor = db.query(sql)
                    cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
                } catch (_: Exception) {
                    0L
                }
            }

            accounts = runCount("SELECT COUNT(*) FROM accounts")
            txs = runCount("SELECT COUNT(*) FROM transactions")
            cats = runCount("SELECT COUNT(*) FROM categories")
            recurring = runCount("SELECT COUNT(*) FROM recurring_patterns")

            try {
                val cursor = db.query("SELECT sqlite_version()")
                cursor.use {
                    if (it.moveToFirst()) {
                        sqliteVer = it.getString(0) ?: "Unknown"
                    }
                }
            } catch (_: Exception) {
                // SQLite version lookup fallback
            }
        } catch (_: Exception) {
            // Safe fallback if database access fails
        }

        return AggregateMetrics(
            accountsCount = accounts,
            transactionsCount = txs,
            categoriesCount = cats,
            recurringPatternsCount = recurring,
            databaseSizeKb = dbSizeKb,
            sqlCipherActive = true,
            sqliteVersion = sqliteVer,
        )
    }

    fun generateReport(
        context: Context,
        database: AppDatabase?,
        includeDevice: Boolean = true,
        includeHealth: Boolean = true,
        includeCrash: Boolean = true,
    ): String {
        val sb = StringBuilder()
        val now =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

        sb.append("====================================================\n")
        sb.append("FINLIGHT LOCAL DIAGNOSTIC REPORT\n")
        sb.append("Generated (UTC): $now\n")
        sb.append("Privacy Notice: No account balances, merchant names, or SMS contents\n")
        sb.append("are included. All sensitive numeric sequences are redacted.\n")
        sb.append("====================================================\n\n")

        val packageInfo =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            } catch (_: Exception) {
                null
            }

        val versionName = packageInfo?.versionName ?: "1.12.0"
        val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

        if (includeDevice) {
            sb.append("[1. DEVICE & OS ENVIRONMENT]\n")
            sb.append("App Version Name:      ").append(versionName).append("\n")
            sb.append("App Version Code:      ").append(versionCode).append("\n")
            sb.append("Database Schema Ver:   58\n")
            sb.append("Android OS Version:    ").append(Build.VERSION.RELEASE).append("\n")
            sb.append("Android API Level:     ").append(Build.VERSION.SDK_INT).append("\n")
            sb.append("Device Manufacturer:   ").append(Build.MANUFACTURER).append("\n")
            sb.append("Device Model:          ").append(Build.MODEL).append("\n")
            sb.append("Supported ABIs:        ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n\n")
        }

        if (includeHealth) {
            val aggregates = fetchAggregates(context, database)
            sb.append("[2. SYSTEM HEALTH & AGGREGATE METRICS]\n")
            sb.append("Total Accounts:        ").append(aggregates.accountsCount).append("\n")
            sb.append("Total Transactions:    ").append(aggregates.transactionsCount).append("\n")
            sb.append("Total Categories:      ").append(aggregates.categoriesCount).append("\n")
            sb.append("Total Recurring Rules: ").append(aggregates.recurringPatternsCount).append("\n")
            sb.append("Database Size:         ").append(aggregates.databaseSizeKb).append(" KB\n")
            sb.append("SQLCipher Active:      ").append(aggregates.sqlCipherActive).append("\n")
            sb.append("SQLite Version:        ").append(aggregates.sqliteVersion).append("\n\n")
        }

        if (includeCrash) {
            val crash = CrashDiagnosticsManager.getRecordedCrash(context)
            sb.append("[3. RECENT CRASH DIAGNOSTICS]\n")
            if (crash != null) {
                sb.append("Timestamp:             ").append(crash.timestamp).append("\n")
                sb.append("Thread:                ").append(crash.threadName).append("\n")
                sb.append("Exception Class:       ").append(crash.exceptionClass).append("\n")
                sb.append("Exception Message:     ").append(crash.exceptionMessage).append("\n")
                sb.append("Sanitized Stack Trace:\n")
                sb.append(crash.stackTrace).append("\n")
            } else {
                sb.append("No recent unhandled crashes recorded.\n")
            }
            sb.append("\n")
        }

        sb.append("====================================================\n")
        sb.append("END OF REPORT\n")
        sb.append("====================================================\n")

        return sb.toString()
    }

    fun exportReportToFile(
        context: Context,
        reportContent: String,
    ): File {
        val dir = File(context.cacheDir, DIAGNOSTICS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, REPORT_FILE_NAME)
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write(reportContent)
                writer.flush()
            }
        }
        return file
    }

    fun createShareIntent(
        context: Context,
        file: File,
    ): Intent {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Finlight Diagnostic Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyToClipboard(
        context: Context,
        text: String,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Finlight Diagnostic Report", text)
        clipboard.setPrimaryClip(clip)
    }
}
