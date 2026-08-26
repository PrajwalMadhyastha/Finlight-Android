package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Calendar

class SmsRuleSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_SMS_SCAN_START_DATE = longPreferencesKey("sms_scan_start_date")
        private val KEY_IGNORE_RULES_CHECKSUM = intPreferencesKey("ignore_rules_checksum")
        private val KEY_DISMISSED_MERGES = stringSetPreferencesKey("dismissed_merge_suggestions")
    }

    suspend fun saveSmsScanStartDate(date: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_SMS_SCAN_START_DATE] = date
        }
    }

    fun getSmsScanStartDate(): Flow<Long> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val thirtyDaysAgo =
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -30)
                    }.timeInMillis
                preferences[KEY_SMS_SCAN_START_DATE] ?: thirtyDaysAgo
            }
            .distinctUntilChanged()
    }

    suspend fun saveIgnoreRulesChecksum(checksum: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_IGNORE_RULES_CHECKSUM] = checksum
        }
    }

    fun getIgnoreRulesChecksum(): Int {
        return runBlocking {
            try {
                dataStore.data.first()[KEY_IGNORE_RULES_CHECKSUM] ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }

    fun getDismissedMergeSuggestions(): Flow<Set<String>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_DISMISSED_MERGES] ?: emptySet()
            }
            .distinctUntilChanged()
    }

    suspend fun addDismissedMergeSuggestion(suggestionKey: String) {
        dataStore.edit { preferences ->
            val currentDismissed = preferences[KEY_DISMISSED_MERGES] ?: emptySet()
            preferences[KEY_DISMISSED_MERGES] = currentDismissed + suggestionKey
        }
    }
}
