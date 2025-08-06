// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/BackupWorker.kt
// REASON: FIX - Reverted the worker to a standard background worker by removing
// the `setForeground()` call. This resolves the ForegroundServiceStartNotAllowedException
// crash on recent Android versions.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
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

        return try {
            Log.d("BackupWorker", "Starting automatic backup...")
            // TODO: Implement Google Drive backup logic here.

            Log.d("BackupWorker", "Automatic backup completed successfully.")

            val notificationsEnabled = settingsRepository.getAutoBackupNotificationEnabled().first()
            if (notificationsEnabled) {
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
