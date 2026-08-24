package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SecuritySettingsRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_PRIVACY_MODE_ENABLED = "privacy_mode_enabled"
        private const val KEY_SIMULATOR_PRIVACY_MODE_ENABLED = "simulator_privacy_mode_enabled"
    }

    fun saveAppLockEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_APP_LOCK_ENABLED, isEnabled)
        }
    }

    fun getAppLockEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (changedKey == KEY_APP_LOCK_ENABLED) {
                        trySend(sharedPreferences.getBoolean(KEY_APP_LOCK_ENABLED, false))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun isAppLockEnabledBlocking(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun savePrivacyModeEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_PRIVACY_MODE_ENABLED, isEnabled)
        }
    }

    fun getPrivacyModeEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_PRIVACY_MODE_ENABLED) {
                        trySend(sp.getBoolean(key, false))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_PRIVACY_MODE_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveSimulatorPrivacyModeEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_SIMULATOR_PRIVACY_MODE_ENABLED, isEnabled)
        }
    }

    fun getSimulatorPrivacyModeEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_SIMULATOR_PRIVACY_MODE_ENABLED) {
                        trySend(sp.getBoolean(key, false))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_SIMULATOR_PRIVACY_MODE_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
}
