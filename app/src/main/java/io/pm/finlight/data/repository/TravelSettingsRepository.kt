package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class TravelSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) : ITravelSettingsRepository {
    constructor(context: Context) : this(
        dataStore = context.financeSettingsDataStore,
        gson = Gson(),
    )

    companion object {
        private val KEY_TRAVEL_MODE_SETTINGS = stringPreferencesKey("travel_mode_settings")
    }

    override suspend fun saveTravelModeSettings(settings: TravelModeSettings?) {
        val json = if (settings == null) null else gson.toJson(settings)
        dataStore.edit { preferences ->
            if (json != null) {
                preferences[KEY_TRAVEL_MODE_SETTINGS] = json
            } else {
                preferences.remove(KEY_TRAVEL_MODE_SETTINGS)
            }
        }
    }

    override fun getTravelModeSettings(): Flow<TravelModeSettings?> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val json = preferences[KEY_TRAVEL_MODE_SETTINGS]
                var settings = if (json == null) null else gson.fromJson(json, TravelModeSettings::class.java)

                if (settings != null && System.currentTimeMillis() > settings.endDate) {
                    saveTravelModeSettings(null)
                    settings = null
                }
                settings
            }
            .distinctUntilChanged()
    }
}
