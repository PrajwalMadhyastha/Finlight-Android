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
//
// REFACTOR (Issue #386): Enforce thread containment via withContext(dispatcherProvider.io),
// cap retries (runAttemptCount >= 3 and runAttemptCount >= 2 on error), and ensure
// CancellationException is rethrown instead of swallowed.
// =================================================================================
package io.pm.finlight

import android.app.backup.BackupManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.data.DataExportService
import io.pm.finlight.di.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(applicationContext)
        return withContext(dispatcherProvider.io) {
            if (runAttemptCount >= 3) return@withContext Result.failure()
            try {
                Log.d("BackupWorker", "Starting periodic database snapshot...")

                val success = DataExportService.createBackupSnapshot(applicationContext)
                if (!success) {
                    Log.e("BackupWorker", "Failed to create database snapshot (attempt $runAttemptCount).")
                    return@withContext if (runAttemptCount >= 2) Result.failure() else Result.retry()
                }

                BackupManager(applicationContext).dataChanged()
                Log.d("BackupWorker", "BackupManager notified of data change.")
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BackupWorker", "Automatic backup worker failed", e)
                if (runAttemptCount >= 2) Result.failure() else Result.retry()
            }
        }
    }
}
