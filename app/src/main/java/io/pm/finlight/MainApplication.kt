// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MainApplication.kt
// REASON: MIGRATION - Added a call to System.loadLibrary("sqlcipher"). This is
// now required to manually load the native SQLCipher libraries into memory
// before any database operations are performed.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.github.mikephil.charting.utils.Utils
import io.pm.finlight.ml.MlModelFactory

class MainApplication :
    Application(),
    ImageLoaderFactory {
    companion object {
        const val TRANSACTION_CHANNEL_ID = "transaction_channel"
        const val RICH_TRANSACTION_CHANNEL_ID = "rich_transaction_channel"
        const val DAILY_REPORT_CHANNEL_ID = "daily_report_channel"
        const val SUMMARY_CHANNEL_ID = "summary_channel"
        const val MONTHLY_SUMMARY_CHANNEL_ID = "monthly_summary_channel"
        const val BACKUP_CHANNEL_ID = "backup_channel"
        const val GOALS_CHANNEL_ID = "goals_channel"
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Limit to 15% of app memory
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            // App moved to background or foreground critical memory pressure: purge decoded bitmaps
            Coil.imageLoader(this).memoryCache?.clear()
            // Clear vocabulary cache if loaded
            MlModelFactory.clearVocabCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Coil.imageLoader(this).memoryCache?.clear()
        MlModelFactory.clearVocabCache()
    }

    override fun onCreate() {
        super.onCreate()

        // --- MIGRATION: Manually load the native SQLCipher library ---
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: UnsatisfiedLinkError) {
            // Ignored in Robolectric/JVM unit test environments
        }

        Utils.init(this)

        createTransactionNotificationChannel()
        createRichTransactionNotificationChannel()
        createDailyReportNotificationChannel()
        createSummaryNotificationChannel()
        createMonthlySummaryNotificationChannel()
        createBackupNotificationChannel()
        createGoalsNotificationChannel()
    }

    private fun createGoalsNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Savings Goals"
            val descriptionText = "Notifications for savings goal progress and nudges."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(GOALS_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createBackupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Data Backups"
            val descriptionText = "Notifications for automatic data backup status."
            val importance = NotificationManager.IMPORTANCE_LOW // Low importance for background tasks
            val channel =
                NotificationChannel(BACKUP_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createTransactionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Transactions"
            val descriptionText = "Notifications for newly detected transactions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(TRANSACTION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createRichTransactionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Detailed Transactions"
            val descriptionText = "Richer, more detailed notifications for auto-saved transactions."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel(RICH_TRANSACTION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createDailyReportNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Reports"
            val descriptionText = "Daily summary of your spending."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(DAILY_REPORT_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSummaryNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Weekly Summaries"
            val descriptionText = "A weekly summary of your financial activity."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(SUMMARY_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMonthlySummaryNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Monthly Summaries"
            val descriptionText = "A monthly summary of your financial activity."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(MONTHLY_SUMMARY_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
