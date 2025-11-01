// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/BackupWorker.kt
// REASON: REFACTOR - This worker now contains the database snapshot logic
// previously held by the (now deleted) SnapshotWorker. This consolidates all
// backup-related logic into a single worker that runs at the hardcoded 2 AM time.
// =================================================================================
package io.pm.finlight

import android.app.backup.BackupManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.data.DataExportService
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
            Log.d("BackupWorker", "Starting daily database snapshot...")

            // --- NEW: Logic moved from SnapshotWorker ---
            // 1. Create the compressed JSON snapshot
            val success = DataExportService.createBackupSnapshot(context)
            if (success) {
                Log.d("BackupWorker", "Database snapshot created successfully.")
                // 2. Notify the BackupManager that our data has changed
                BackupManager(context).dataChanged()
                Log.d("BackupWorker", "BackupManager notified of data change.")
            } else {
                Log.e("BackupWorker", "Failed to create database snapshot.")
            }
            // --- End of new logic ---


            // TODO: Implement Google Drive backup logic here.


            val notificationsEnabled = settingsRepository.getAutoBackupNotificationEnabled().first()
            if (notificationsEnabled) {
                NotificationHelper.showAutoBackupNotification(context)
            }

            // Re-schedule the next backup
            ReminderManager.scheduleAutoBackup(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Automatic backup worker failed", e)
            Result.retry()
        }
    }
}
