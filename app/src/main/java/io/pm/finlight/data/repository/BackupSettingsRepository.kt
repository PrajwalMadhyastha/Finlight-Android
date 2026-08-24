package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BackupSettingsRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_BACKUP_ENABLED = "google_drive_backup_enabled"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_BACKUP_NOTIFICATION_ENABLED = "auto_backup_notification_enabled"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
    }

    fun saveBackupEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_BACKUP_ENABLED, isEnabled)
        }
    }

    fun getBackupEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (changedKey == KEY_BACKUP_ENABLED) {
                        trySend(sharedPreferences.getBoolean(KEY_BACKUP_ENABLED, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_BACKUP_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveAutoBackupEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP_ENABLED, isEnabled) }
    }

    fun getAutoBackupEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_AUTO_BACKUP_ENABLED) {
                        trySend(sp.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveAutoBackupNotificationEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP_NOTIFICATION_ENABLED, isEnabled) }
    }

    fun getAutoBackupNotificationEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_AUTO_BACKUP_NOTIFICATION_ENABLED) {
                        trySend(sp.getBoolean(key, false))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_AUTO_BACKUP_NOTIFICATION_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun isAutoBackupNotificationEnabledBlocking(): Boolean {
        return prefs.getBoolean(KEY_AUTO_BACKUP_NOTIFICATION_ENABLED, false)
    }

    fun saveLastBackupTimestamp(timestamp: Long) {
        prefs.edit {
            putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp)
        }
    }

    fun getLastBackupTimestamp(): Flow<Long> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_LAST_BACKUP_TIMESTAMP) {
                        trySend(sp.getLong(key, 0L))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
}
