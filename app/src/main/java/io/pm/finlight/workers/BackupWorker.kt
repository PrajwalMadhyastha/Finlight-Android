// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/BackupWorker.kt
// REASON: FIX - The worker has been correctly re-implemented to run as a
// foreground service. It now calls `setForeground()` at the beginning of
// `doWork()`, using a persistent notification from NotificationHelper. This
// prevents the OS from killing the process during a backup and resolves the
// ForegroundServiceStartNotAllowedException crash.
// =================================================================================
package io.pm.finlight

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class BackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(context)

        // --- Create and show the foreground notification ---
        val notificationBuilder = NotificationHelper.getBackupNotificationBuilder(context)
        val foregroundInfo = ForegroundInfo(NotificationHelper.BACKUP_NOTIFICATION_ID, notificationBuilder.build())
        setForeground(foregroundInfo)

        return try {
            Log.d("BackupWorker", "Starting automatic backup as a foreground service...")
            // TODO: Implement Google Drive backup logic here.
            // For now, we'll simulate a long-running task.
            delay(5000) // Simulate a 5-second backup process

            Log.d("BackupWorker", "Automatic backup completed successfully.")

            val notificationsEnabled = settingsRepository.getAutoBackupNotificationEnabled().first()
            if (notificationsEnabled) {
                // Show a completion notification
                val completionBuilder = NotificationHelper.getBackupNotificationBuilder(context)
                    .setContentText("Backup completed successfully.")
                    .setOngoing(false) // Make it dismissible
                    .setProgress(0, 0, false) // Remove progress bar
                NotificationManagerCompat.from(context).notify(NotificationHelper.BACKUP_NOTIFICATION_ID, completionBuilder.build())
            }

            // Re-schedule the next backup
            ReminderManager.scheduleAutoBackup(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Automatic backup failed", e)
            // Show a failure notification
            val failureBuilder = NotificationHelper.getBackupNotificationBuilder(context)
                .setContentText("Backup failed. Please check your connection.")
                .setOngoing(false)
                .setProgress(0, 0, false)
            NotificationManagerCompat.from(context).notify(NotificationHelper.BACKUP_NOTIFICATION_ID, failureBuilder.build())

            Result.retry()
        }
    }
}
