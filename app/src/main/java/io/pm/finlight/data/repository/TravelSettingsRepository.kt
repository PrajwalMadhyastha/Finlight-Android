package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TravelSettingsRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson = Gson(),
) {
    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        gson = Gson(),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_TRAVEL_MODE_SETTINGS = "travel_mode_settings"
    }

    fun saveTravelModeSettings(settings: TravelModeSettings?) {
        val json = if (settings == null) null else gson.toJson(settings)
        prefs.edit {
            putString(KEY_TRAVEL_MODE_SETTINGS, json)
        }
    }

    fun getTravelModeSettings(): Flow<TravelModeSettings?> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_TRAVEL_MODE_SETTINGS) {
                        val json = sp.getString(key, null)
                        var settings = if (json == null) null else gson.fromJson(json, TravelModeSettings::class.java)

                        if (settings != null && System.currentTimeMillis() > settings.endDate) {
                            saveTravelModeSettings(null)
                            settings = null
                        }
                        trySend(settings)
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val initialJson = prefs.getString(KEY_TRAVEL_MODE_SETTINGS, null)
            var initialSettings = if (initialJson == null) null else gson.fromJson(initialJson, TravelModeSettings::class.java)

            if (initialSettings != null && System.currentTimeMillis() > initialSettings.endDate) {
                saveTravelModeSettings(null)
                initialSettings = null
            }
            trySend(initialSettings)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
}
