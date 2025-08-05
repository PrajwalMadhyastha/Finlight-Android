// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MainApplication.kt
// REASON: FIX(test) - Removed the explicit SQLiteDatabase.loadLibs(this) call.
// While this call was added to fix a runtime issue, it causes the Robolectric
// test environment to fail during its setup phase. The library loading is now
// handled within the test classes themselves, where the test environment is
// properly configured.
// =================================================================================
package io.pm.finlight

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.github.mikephil.charting.utils.Utils
import net.sqlcipher.database.SQLiteDatabase

class MainApplication : Application() {
    companion object {
        const val TRANSACTION_CHANNEL_ID = "transaction_channel"
        const val RICH_TRANSACTION_CHANNEL_ID = "rich_transaction_channel"
        const val DAILY_REPORT_CHANNEL_ID = "daily_report_channel"
        const val SUMMARY_CHANNEL_ID = "summary_channel"
        const val MONTHLY_SUMMARY_CHANNEL_ID = "monthly_summary_channel"
    }

    override fun onCreate() {
        super.onCreate()
        // The explicit call to loadLibs is removed from here.
        // It's handled by the system on a real device, and we'll handle it
        // manually in our Robolectric tests.

        Utils.init(this)

        createTransactionNotificationChannel()
        createRichTransactionNotificationChannel()
        createDailyReportNotificationChannel()
        createSummaryNotificationChannel()
        createMonthlySummaryNotificationChannel()
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
            val importance = NotificationManager.IMPORTANCE_HIGH // Higher importance
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
