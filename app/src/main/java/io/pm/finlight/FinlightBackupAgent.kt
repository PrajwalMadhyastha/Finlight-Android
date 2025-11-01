// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/FinlightBackupAgent.kt
//=================================================================
package io.pm.finlight

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FileBackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor
import android.util.Log
import io.pm.finlight.utils.NotificationHelper

class FinlightBackupAgent : BackupAgentHelper() {

    companion object {
        // A unique tag for Logcat filtering
        private const val TAG = "FinlightBackup"

        // The name of our SharedPreferences file
        private const val PREFS_NAME = "finance_app_settings"
        private const val PREFS_BACKUP_KEY = "finlight_prefs"

        // The specific snapshot file we want to back up
        private const val SNAPSHOT_FILE_NAME = "backup_snapshot.gz"
        private const val FILES_BACKUP_KEY = "finlight_files"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: BackupAgent is being created.")

        // Helper for backing up our main SharedPreferences
        SharedPreferencesBackupHelper(this, PREFS_NAME).also {
            addHelper(PREFS_BACKUP_KEY, it)
            Log.d(TAG, "onCreate: SharedPreferencesBackupHelper added for '$PREFS_NAME'.")
        }

        // Helper for backing up the specific snapshot file from our internal storage
        FileBackupHelper(this, SNAPSHOT_FILE_NAME).also {
            addHelper(FILES_BACKUP_KEY, it)
            Log.d(TAG, "onCreate: FileBackupHelper added for '$SNAPSHOT_FILE_NAME'.")
        }
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
        Log.d(TAG, "onBackup: Starting backup process...")
        val settingsRepository = SettingsRepository(applicationContext)

        // --- UPDATED: Capture timestamp to use for both saving and notification ---
        val backupTime = System.currentTimeMillis()

        // 1. Save the timestamp
        settingsRepository.saveLastBackupTimestamp(backupTime)
        Log.i(TAG, "onBackup: Last backup timestamp saved.")

        // 2. Let the system helpers do their work
        super.onBackup(oldState, data, newState)

        // 3. (NEW) Trigger notification AFTER the backup is complete
        try {
            val notificationsEnabled = settingsRepository.isAutoBackupNotificationEnabledBlocking()
            if (notificationsEnabled) {
                // --- UPDATED: Pass the timestamp to the notification helper ---
                NotificationHelper.showAutoBackupNotification(applicationContext, backupTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send backup notification", e)
        }

        Log.d(TAG, "onBackup: Backup process finished.")
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        Log.d(TAG, "onRestore: Starting restore process...")
        super.onRestore(data, appVersionCode, newState)
        Log.d(TAG, "onRestore: Restore process finished.")
    }
}

