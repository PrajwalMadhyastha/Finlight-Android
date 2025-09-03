// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/SnapshotWorker.kt
// REASON: NEW FILE - This CoroutineWorker is responsible for creating a
// compressed JSON snapshot of the database. It runs daily as a background task,
// calling the DataExportService to perform the snapshot and then rescheduling
// itself for the next day, ensuring the backup is always recent.
// =================================================================================
package io.pm.finlight.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.data.DataExportService
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SnapshotWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SnapshotWorker", "Starting daily database snapshot...")

                val success = DataExportService.createBackupSnapshot(context)

                if (success) {
                    Log.d("SnapshotWorker", "Database snapshot created successfully.")
                } else {
                    Log.e("SnapshotWorker", "Failed to create database snapshot.")
                }

                // Reschedule the worker for the next day.
                ReminderManager.scheduleSnapshotWorker(context)

                Result.success()
            } catch (e: Exception) {
                Log.e("SnapshotWorker", "Snapshot worker failed", e)
                Result.retry()
            }
        }
    }
}
