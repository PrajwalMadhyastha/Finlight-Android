package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AppConfigRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROFILE_PICTURE_URI = "profile_picture_uri"
        private const val KEY_SELECTED_THEME = "selected_app_theme"
        private const val KEY_HOME_CURRENCY = "home_currency_code"
    }

    fun saveUserName(name: String) {
        prefs.edit {
            putString(KEY_USER_NAME, name)
        }
    }

    fun getUserName(): Flow<String> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (changedKey == KEY_USER_NAME) {
                        trySend(sharedPreferences.getString(KEY_USER_NAME, "User") ?: "User")
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getString(KEY_USER_NAME, "User") ?: "User")
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveProfilePictureUri(uriString: String?) {
        prefs.edit {
            putString(KEY_PROFILE_PICTURE_URI, uriString)
        }
    }

    fun getProfilePictureUri(): Flow<String?> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (changedKey == KEY_PROFILE_PICTURE_URI) {
                        trySend(sharedPreferences.getString(KEY_PROFILE_PICTURE_URI, null))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getString(KEY_PROFILE_PICTURE_URI, null))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveSelectedTheme(theme: AppTheme) {
        prefs.edit {
            putString(KEY_SELECTED_THEME, theme.key)
        }
    }

    fun getSelectedTheme(): Flow<AppTheme> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_SELECTED_THEME) {
                        val themeKey = sp.getString(key, AppTheme.SYSTEM_DEFAULT.key)
                        trySend(AppTheme.fromKey(themeKey))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val initialThemeKey = prefs.getString(KEY_SELECTED_THEME, AppTheme.SYSTEM_DEFAULT.key)
            trySend(AppTheme.fromKey(initialThemeKey))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveHomeCurrency(currencyCode: String) {
        prefs.edit {
            putString(KEY_HOME_CURRENCY, currencyCode)
        }
    }

    fun getHomeCurrency(): Flow<String> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_HOME_CURRENCY) {
                        trySend(sp.getString(key, "INR") ?: "INR")
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getString(KEY_HOME_CURRENCY, "INR") ?: "INR")
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
}
