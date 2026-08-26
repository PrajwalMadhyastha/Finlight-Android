package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class SecuritySettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ISecuritySettingsRepository {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")
        private val KEY_SIMULATOR_PRIVACY_MODE_ENABLED = booleanPreferencesKey("simulator_privacy_mode_enabled")
    }

    override suspend fun saveAppLockEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_APP_LOCK_ENABLED] = isEnabled
        }
    }

    override fun getAppLockEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_APP_LOCK_ENABLED] ?: false
            }
            .distinctUntilChanged()
    }

    override suspend fun savePrivacyModeEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_PRIVACY_MODE_ENABLED] = isEnabled
        }
    }

    override fun getPrivacyModeEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_PRIVACY_MODE_ENABLED] ?: false
            }
            .distinctUntilChanged()
    }

    override suspend fun saveSimulatorPrivacyModeEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SIMULATOR_PRIVACY_MODE_ENABLED] = isEnabled
        }
    }

    override fun getSimulatorPrivacyModeEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_SIMULATOR_PRIVACY_MODE_ENABLED] ?: false
            }
            .distinctUntilChanged()
    }
}
