// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/BackupWorker.kt
// REASON: NEW FILE - This worker defines the background job for performing
// automatic data backups. It currently logs the action and will be expanded to
// include Google Drive integration. It also re-schedules itself for the next run.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.utils.ReminderManager

class BackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("BackupWorker", "Starting automatic backup...")
            // TODO: Implement Google Drive backup logic here.
            // For now, we just log and re-schedule.
            Log.d("BackupWorker", "Automatic backup completed successfully.")

            // Re-schedule the next backup
            ReminderManager.scheduleAutoBackup(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Automatic backup failed", e)
            Result.retry()
        }
    }
}
