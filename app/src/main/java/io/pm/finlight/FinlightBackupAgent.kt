// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/FinlightBackupAgent.kt
// =================================================================
package io.pm.finlight

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FileBackupHelper
import android.os.ParcelFileDescriptor
import android.util.Log
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FinlightBackupAgent : BackupAgentHelper() {
    companion object {
        // A unique tag for Logcat filtering
        private const val TAG = "FinlightBackup"

        // Relative paths from filesDir for DataStore files
        private const val DATASTORE_PREFS_FILE = "datastore/finance_app_settings.preferences_pb"
        private const val DATASTORE_BACKUP_KEY = "finlight_datastore_prefs"

        // The specific snapshot file we want to back up
        private const val SNAPSHOT_FILE_NAME = "backup_snapshot.gz"
        private const val FILES_BACKUP_KEY = "finlight_files"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing BackupAgentHelper...")

        // Helper for backing up user DataStore preferences (internal lifecycle state is excluded)
        FileBackupHelper(this, DATASTORE_PREFS_FILE).also {
            addHelper(DATASTORE_BACKUP_KEY, it)
            Log.d(TAG, "onCreate: FileBackupHelper added for DataStore files.")
        }

        // Helper for backing up the specific snapshot file from our internal storage
        FileBackupHelper(this, SNAPSHOT_FILE_NAME).also {
            addHelper(FILES_BACKUP_KEY, it)
            Log.d(TAG, "onCreate: FileBackupHelper added for '$SNAPSHOT_FILE_NAME'.")
        }
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) {
        Log.d(TAG, "onBackup: Starting backup process...")
        val backupSettingsRepository = ServiceLocator.provideBackupSettingsRepository(applicationContext)

        // Capture timestamp to use for both saving and notification
        val backupTime = System.currentTimeMillis()

        // 1. Save the timestamp
        runBlocking {
            backupSettingsRepository.saveLastBackupTimestamp(backupTime)
        }
        Log.i(TAG, "onBackup: Last backup timestamp saved.")

        // 2. Let the system helpers do their work
        try {
            super.onBackup(oldState, data, newState)
        } catch (e: Exception) {
            Log.e(TAG, "onBackup: System backup helper failed", e)
        }

        // 3. Trigger notification AFTER the backup is complete
        try {
            val notificationsEnabled =
                runBlocking {
                    backupSettingsRepository.getAutoBackupNotificationEnabled().first()
                }
            if (notificationsEnabled) {
                NotificationHelper.showAutoBackupNotification(applicationContext, backupTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send backup notification", e)
        }

        Log.d(TAG, "onBackup: Backup process finished.")
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) {
        Log.d(TAG, "onRestore: Starting restore process...")
        try {
            super.onRestore(data, appVersionCode, newState)
        } catch (e: Exception) {
            Log.e(TAG, "onRestore: System restore helper failed", e)
        }
        Log.d(TAG, "onRestore: Restore process finished.")
    }
}
