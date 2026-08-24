package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Calendar

class SmsRuleSettingsRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_SMS_SCAN_START_DATE = "sms_scan_start_date"
        private const val KEY_IGNORE_RULES_CHECKSUM = "ignore_rules_checksum"
        private const val KEY_DISMISSED_MERGES = "dismissed_merge_suggestions"
    }

    fun saveSmsScanStartDate(date: Long) {
        prefs.edit {
            putLong(KEY_SMS_SCAN_START_DATE, date)
        }
    }

    fun getSmsScanStartDate(): Flow<Long> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (changedKey == KEY_SMS_SCAN_START_DATE) {
                        trySend(sharedPreferences.getLong(KEY_SMS_SCAN_START_DATE, 0L))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
            trySend(prefs.getLong(KEY_SMS_SCAN_START_DATE, thirtyDaysAgo))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveIgnoreRulesChecksum(checksum: Int) {
        prefs.edit {
            putInt(KEY_IGNORE_RULES_CHECKSUM, checksum)
        }
    }

    fun getIgnoreRulesChecksum(): Int {
        return prefs.getInt(KEY_IGNORE_RULES_CHECKSUM, 0)
    }

    fun getDismissedMergeSuggestions(): Flow<Set<String>> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_DISMISSED_MERGES) {
                        trySend(sp.getStringSet(key, emptySet()) ?: emptySet())
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getStringSet(KEY_DISMISSED_MERGES, emptySet()) ?: emptySet())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun addDismissedMergeSuggestion(suggestionKey: String) {
        val currentDismissed = prefs.getStringSet(KEY_DISMISSED_MERGES, emptySet()) ?: emptySet()
        val newDismissed = currentDismissed.toMutableSet().apply { add(suggestionKey) }
        prefs.edit {
            putStringSet(KEY_DISMISSED_MERGES, newDismissed)
        }
    }
}
