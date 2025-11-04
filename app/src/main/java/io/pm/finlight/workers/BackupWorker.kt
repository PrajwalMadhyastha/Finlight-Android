// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/BackupWorker.kt
// REASON: REFACTOR - This worker's sole responsibility is now to create the
// snapshot, notify the system, and reschedule itself. The user-facing
// notification logic has been removed and moved to the FinlightBackupAgent,
// which runs *after* the system backup is complete.
//
// REFACTOR (8-Hour Periodic): Removed the manual rescheduling call
// to `ReminderManager.scheduleAutoBackup`. This worker is now a
// PeriodicWorkRequest, so the system handles rescheduling automatically.
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
        return try {
            Log.d("BackupWorker", "Starting periodic database snapshot...")

            // --- Logic moved from SnapshotWorker ---
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
            // --- End of moved logic ---


            // --- DELETED: Notification logic was removed from this worker ---

            // The worker is now periodic, so it does not need to reschedule itself.
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Automatic backup worker failed", e)
            Result.retry()
        }
    }
}
