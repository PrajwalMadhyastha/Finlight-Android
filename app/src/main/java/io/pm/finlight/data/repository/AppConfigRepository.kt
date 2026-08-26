package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class AppConfigRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_PROFILE_PICTURE_URI = stringPreferencesKey("profile_picture_uri")
        private val KEY_SELECTED_THEME = stringPreferencesKey("selected_app_theme")
        private val KEY_HOME_CURRENCY = stringPreferencesKey("home_currency_code")
    }

    suspend fun saveUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_NAME] = name
        }
    }

    fun getUserName(): Flow<String> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_USER_NAME] ?: "User"
            }
            .distinctUntilChanged()
    }

    suspend fun saveProfilePictureUri(uriString: String?) {
        dataStore.edit { preferences ->
            if (uriString != null) {
                preferences[KEY_PROFILE_PICTURE_URI] = uriString
            } else {
                preferences.remove(KEY_PROFILE_PICTURE_URI)
            }
        }
    }

    fun getProfilePictureUri(): Flow<String?> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_PROFILE_PICTURE_URI]
            }
            .distinctUntilChanged()
    }

    suspend fun saveSelectedTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_THEME] = theme.key
        }
    }

    fun getSelectedTheme(): Flow<AppTheme> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val themeKey = preferences[KEY_SELECTED_THEME] ?: AppTheme.SYSTEM_DEFAULT.key
                AppTheme.fromKey(themeKey)
            }
            .distinctUntilChanged()
    }

    suspend fun saveHomeCurrency(currencyCode: String) {
        dataStore.edit { preferences ->
            preferences[KEY_HOME_CURRENCY] = currencyCode
        }
    }

    fun getHomeCurrency(): Flow<String> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_HOME_CURRENCY] ?: "INR"
            }
            .distinctUntilChanged()
    }
}
