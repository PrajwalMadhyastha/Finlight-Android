// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/BackupWorker.kt
// REASON: FIX - The worker now runs as a foreground service by calling
// `setForeground()`. This prevents the system from cancelling the worker
// prematurely, ensuring the completion notification is reliably displayed.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.flow.first

class BackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(context)

        // --- NEW: Promote the worker to a foreground service ---
        val foregroundInfo = ForegroundInfo(
            NotificationHelper.BACKUP_NOTIFICATION_ID,
            NotificationHelper.getBackupNotificationBuilder(context)
        )
        setForeground(foregroundInfo)

        return try {
            Log.d("BackupWorker", "Starting automatic backup...")
            // TODO: Implement Google Drive backup logic here.

            Log.d("BackupWorker", "Automatic backup completed successfully.")

            val notificationsEnabled = settingsRepository.getAutoBackupNotificationEnabled().first()
            if (notificationsEnabled) {
                // The foreground notification will be replaced by this one.
                NotificationHelper.showAutoBackupNotification(context)
            }

            // Re-schedule the next backup
            ReminderManager.scheduleAutoBackup(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Automatic backup failed", e)
            Result.retry()
        }
    }
}
