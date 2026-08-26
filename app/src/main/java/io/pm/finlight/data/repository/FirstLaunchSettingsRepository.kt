package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    fun hasSeenOnboarding(): Boolean {
        return runBlocking {
            try {
                dataStore.data.first()[KEY_HAS_SEEN_ONBOARDING] ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    /**
     * Checks if the app has completed its first launch sequence.
     * This is a blocking call and should only be used during app startup.
     */
    fun isFirstLaunchCompleteBlocking(): Boolean {
        return runBlocking {
            try {
                internalDataStore.data.first()[KEY_IS_FIRST_LAUNCH_COMPLETE] ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Sets the flag indicating that the first launch sequence is complete.
     */
    suspend fun setFirstLaunchComplete() {
        internalDataStore.edit { preferences ->
            preferences[KEY_IS_FIRST_LAUNCH_COMPLETE] = true
        }
    }
}
