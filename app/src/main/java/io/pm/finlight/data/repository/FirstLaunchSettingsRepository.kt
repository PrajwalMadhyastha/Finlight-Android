package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class FirstLaunchSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val internalDataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        dataStore = context.financeSettingsDataStore,
        internalDataStore = context.internalSettingsDataStore,
    )

    companion object {
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        private val KEY_IS_FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("is_first_launch_complete")
    }

    fun getHasSeenOnboarding(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_HAS_SEEN_ONBOARDING] ?: false
            }
            .distinctUntilChanged()
    }

    suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    fun getIsFirstLaunchComplete(): Flow<Boolean> {
        return internalDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_IS_FIRST_LAUNCH_COMPLETE] ?: false
            }
            .distinctUntilChanged()
    }

    suspend fun setFirstLaunchComplete() {
        internalDataStore.edit { preferences ->
            preferences[KEY_IS_FIRST_LAUNCH_COMPLETE] = true
        }
    }
}

