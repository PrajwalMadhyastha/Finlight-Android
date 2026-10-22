package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppConfigRepository(
    private val dataStore: DataStore<Preferences>,
) : IAppConfigRepository {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_PROFILE_PICTURE_URI = stringPreferencesKey("profile_picture_uri")
        private val KEY_SELECTED_THEME = stringPreferencesKey("selected_app_theme")
        private val KEY_HOME_CURRENCY = stringPreferencesKey("home_currency_code")
    }

    override suspend fun saveUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_NAME] = name
        }
    }

    override fun getUserName(): Flow<String> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_USER_NAME] ?: "User"
                } catch (_: ClassCastException) {
                    "User"
                }
            }
            .catch {
                emit("User")
            }
            .distinctUntilChanged()
    }

    override suspend fun saveProfilePictureUri(uriString: String?) {
        dataStore.edit { preferences ->
            if (uriString != null) {
                preferences[KEY_PROFILE_PICTURE_URI] = uriString
            } else {
                preferences.remove(KEY_PROFILE_PICTURE_URI)
            }
        }
    }

    override fun getProfilePictureUri(): Flow<String?> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_PROFILE_PICTURE_URI]
                } catch (_: ClassCastException) {
                    null
                }
            }
            .catch {
                emit(null)
            }
            .distinctUntilChanged()
    }

    override suspend fun saveSelectedTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_THEME] = theme.key
        }
    }

    override fun getSelectedTheme(): Flow<AppTheme> {
        return dataStore.data
            .map { preferences ->
                val themeKey =
                    try {
                        preferences[KEY_SELECTED_THEME] ?: AppTheme.SYSTEM_DEFAULT.key
                    } catch (_: ClassCastException) {
                        AppTheme.SYSTEM_DEFAULT.key
                    }
                AppTheme.fromKey(themeKey)
            }
            .catch {
                emit(AppTheme.SYSTEM_DEFAULT)
            }
            .distinctUntilChanged()
    }

    override suspend fun saveHomeCurrency(currencyCode: String) {
        dataStore.edit { preferences ->
            preferences[KEY_HOME_CURRENCY] = currencyCode
        }
    }

    override fun getHomeCurrency(): Flow<String> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_HOME_CURRENCY] ?: "INR"
                } catch (_: ClassCastException) {
                    "INR"
                }
            }
            .catch {
                emit("INR")
            }
            .distinctUntilChanged()
    }
}
