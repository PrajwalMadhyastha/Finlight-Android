package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FirstLaunchSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val internalDataStore: DataStore<Preferences>,
) : IFirstLaunchSettingsRepository {
    constructor(context: Context) : this(
        dataStore = context.financeSettingsDataStore,
        internalDataStore = context.internalSettingsDataStore,
    )

    companion object {
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        private val KEY_IS_FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("is_first_launch_complete")
    }

    override fun getHasSeenOnboarding(): Flow<Boolean> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_HAS_SEEN_ONBOARDING] ?: false
                } catch (_: ClassCastException) {
                    false
                }
            }
            .catch {
                emit(false)
            }
            .distinctUntilChanged()
    }

    override suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    override fun getIsFirstLaunchComplete(): Flow<Boolean> {
        return internalDataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_IS_FIRST_LAUNCH_COMPLETE] ?: false
                } catch (_: ClassCastException) {
                    false
                }
            }
            .catch {
                emit(false)
            }
            .distinctUntilChanged()
    }

    override suspend fun setFirstLaunchComplete() {
        internalDataStore.edit { preferences ->
            preferences[KEY_IS_FIRST_LAUNCH_COMPLETE] = true
        }
    }
}
