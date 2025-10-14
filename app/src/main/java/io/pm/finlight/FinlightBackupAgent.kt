// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/FinlightBackupAgent.kt
// REASON: FEATURE (Backup Time) - The `onBackup` method now saves the current
// timestamp to SharedPreferences. This provides a reliable signal that the Android
// Backup Manager has initiated the backup process, giving the user high confidence
// that their data has been recently backed up.
// =================================================================================
package io.pm.finlight

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FileBackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log

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
        // --- NEW: Save the timestamp of this backup operation ---
        val settingsRepository = SettingsRepository(applicationContext)
        settingsRepository.saveLastBackupTimestamp(System.currentTimeMillis())
        Log.i(TAG, "onBackup: Last backup timestamp saved.")
        // --- End of new code ---
        super.onBackup(oldState, data, newState)
        Log.d(TAG, "onBackup: Backup process finished.")
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        Log.d(TAG, "onRestore: Starting restore process...")
        super.onRestore(data, appVersionCode, newState)
        Log.d(TAG, "onRestore: Restore process finished.")
    }
}