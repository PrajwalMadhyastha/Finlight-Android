package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Calendar

class SmsRuleSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ISmsRuleSettingsRepository {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_SMS_SCAN_START_DATE = longPreferencesKey("sms_scan_start_date")
        private val KEY_IGNORE_RULES_CHECKSUM = intPreferencesKey("ignore_rules_checksum")
        private val KEY_DISMISSED_MERGES = stringSetPreferencesKey("dismissed_merge_suggestions")
    }

    override suspend fun saveSmsScanStartDate(date: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_SMS_SCAN_START_DATE] = date
        }
    }

    override fun getSmsScanStartDate(): Flow<Long> {
        val thirtyDaysAgo =
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -30)
            }.timeInMillis
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_SMS_SCAN_START_DATE] ?: thirtyDaysAgo
                } catch (_: ClassCastException) {
                    thirtyDaysAgo
                }
            }
            .catch {
                emit(thirtyDaysAgo)
            }
            .distinctUntilChanged()
    }

    override suspend fun saveIgnoreRulesChecksum(checksum: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_IGNORE_RULES_CHECKSUM] = checksum
        }
    }

    override suspend fun getIgnoreRulesChecksum(): Int {
        return try {
            val preferences = dataStore.data.first()
            try {
                preferences[KEY_IGNORE_RULES_CHECKSUM] ?: 0
            } catch (_: ClassCastException) {
                0
            }
        } catch (e: IOException) {
            0
        }
    }

    override fun getDismissedMergeSuggestions(): Flow<Set<String>> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_DISMISSED_MERGES] ?: emptySet()
                } catch (_: Exception) {
                    emptySet()
                }
            }
            .catch {
                emit(emptySet())
            }
            .distinctUntilChanged()
    }

    override suspend fun addDismissedMergeSuggestion(suggestionKey: String) {
        dataStore.edit { preferences ->
            val currentDismissed = preferences[KEY_DISMISSED_MERGES] ?: emptySet()
            preferences[KEY_DISMISSED_MERGES] = currentDismissed + suggestionKey
        }
    }
}
