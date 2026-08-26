package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class BackupSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_BACKUP_ENABLED = booleanPreferencesKey("google_drive_backup_enabled")
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_AUTO_BACKUP_NOTIFICATION_ENABLED = booleanPreferencesKey("auto_backup_notification_enabled")
        private val KEY_LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    }

    suspend fun saveBackupEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BACKUP_ENABLED] = isEnabled
        }
    }

    fun getBackupEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_BACKUP_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    suspend fun saveAutoBackupEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_BACKUP_ENABLED] = isEnabled
        }
    }

    fun getAutoBackupEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_AUTO_BACKUP_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    suspend fun saveAutoBackupNotificationEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_BACKUP_NOTIFICATION_ENABLED] = isEnabled
        }
    }

    fun getAutoBackupNotificationEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_AUTO_BACKUP_NOTIFICATION_ENABLED] ?: false
            }
            .distinctUntilChanged()
    }

    suspend fun saveLastBackupTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }

    fun getLastBackupTimestamp(): Flow<Long> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_LAST_BACKUP_TIMESTAMP] ?: 0L
            }
            .distinctUntilChanged()
    }
}
