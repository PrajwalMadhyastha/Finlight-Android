package io.pm.finlight.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data representation of an uncaught crash captured locally.
 * Stored offline with zero cloud transmission.
 */
data class CrashReport(
    val timestamp: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidApi: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val threadName: String,
    val exceptionClass: String,
    val exceptionMessage: String,
    val stackTrace: String,
)

/**
 * Intercepts uncaught exceptions globally, redacts sensitive financial PII,
 * and records the crash report synchronously to local storage for recovery and diagnostic export.
 */
object CrashDiagnosticsManager {
    const val CRASH_REPORT_FILE_NAME = "last_crash_report.json"

    private val isInstalled = AtomicBoolean(false)

    fun install(context: Context) {
        if (!isInstalled.compareAndSet(false, true)) return
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, thread, throwable)
            } catch (_: Throwable) {
                // Fail-safe: never throw inside the uncaught exception handler
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    @VisibleForTesting
    internal fun resetInstalledStateForTesting() {
        isInstalled.set(false)
    }

    fun recordCrash(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
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

            val versionName = packageInfo?.versionName ?: "unknown"
            val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

            val rawMessage = throwable.message ?: throwable.localizedMessage ?: ""
            val sanitizedMessage = PiiSanitizer.sanitize(rawMessage)
            val sanitizedStackTrace = PiiSanitizer.sanitizeThrowable(throwable)

            val isoDate =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

            val report =
                CrashReport(
                    timestamp = isoDate,
                    appVersionName = versionName,
                    appVersionCode = versionCode,
                    androidApi = Build.VERSION.SDK_INT,
                    deviceManufacturer = Build.MANUFACTURER ?: "unknown",
                    deviceModel = Build.MODEL ?: "unknown",
                    threadName = thread.name,
                    exceptionClass = throwable.javaClass.name,
                    exceptionMessage = sanitizedMessage,
                    stackTrace = sanitizedStackTrace,
                )

            val file = File(context.filesDir, CRASH_REPORT_FILE_NAME)
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    Gson().toJson(report, writer)
                    writer.flush()
                }
                fos.fd.sync()
            }
        } catch (_: Throwable) {
            // Guarantee no crash loop during crash recording
        }
    }

    fun hasRecordedCrash(context: Context): Boolean {
        val file = File(context.filesDir, CRASH_REPORT_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun getRecordedCrash(context: Context): CrashReport? {
        val file = File(context.filesDir, CRASH_REPORT_FILE_NAME)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            FileInputStream(file).use { fis ->
                InputStreamReader(fis, Charsets.UTF_8).use { reader ->
                    Gson().fromJson(reader, CrashReport::class.java)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCrash(context: Context): Boolean {
        val file = File(context.filesDir, CRASH_REPORT_FILE_NAME)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}
