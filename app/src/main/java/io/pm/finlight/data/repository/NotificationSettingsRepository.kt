package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : INotificationSettingsRepository {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_DAILY_REPORT_ENABLED = booleanPreferencesKey("daily_report_enabled")
        private val KEY_DAILY_REPORT_HOUR = intPreferencesKey("daily_report_hour")
        private val KEY_DAILY_REPORT_MINUTE = intPreferencesKey("daily_report_minute")
        private val KEY_WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        private val KEY_WEEKLY_REPORT_DAY = intPreferencesKey("weekly_report_day")
        private val KEY_WEEKLY_REPORT_HOUR = intPreferencesKey("weekly_report_hour")
        private val KEY_WEEKLY_REPORT_MINUTE = intPreferencesKey("weekly_report_minute")
        private val KEY_MONTHLY_SUMMARY_ENABLED = booleanPreferencesKey("monthly_summary_enabled")
        private val KEY_MONTHLY_REPORT_DAY = intPreferencesKey("monthly_report_day")
        private val KEY_MONTHLY_REPORT_HOUR = intPreferencesKey("monthly_report_hour")
        private val KEY_MONTHLY_REPORT_MINUTE = intPreferencesKey("monthly_report_minute")
        private val KEY_AUTOCAPTURE_NOTIFICATION_ENABLED = booleanPreferencesKey("autocapture_notification_enabled")
        private val KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED = booleanPreferencesKey("unknown_transaction_popup_enabled")
        private const val KEY_LAST_MONTH_SUMMARY_DISMISSED_PREFIX = "last_month_summary_dismissed_"
    }

    override suspend fun saveDailyReportEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_DAILY_REPORT_ENABLED] = isEnabled
        }
    }

    override fun getDailyReportEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_DAILY_REPORT_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    override suspend fun saveDailyReportTime(
        hour: Int,
        minute: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_DAILY_REPORT_HOUR] = hour
            preferences[KEY_DAILY_REPORT_MINUTE] = minute
        }
    }

    override fun getDailyReportTime(): Flow<Pair<Int, Int>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                Pair(
                    preferences[KEY_DAILY_REPORT_HOUR] ?: 23,
                    preferences[KEY_DAILY_REPORT_MINUTE] ?: 0,
                )
            }
            .distinctUntilChanged()
    }

    override suspend fun saveWeeklySummaryEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_WEEKLY_SUMMARY_ENABLED] = isEnabled
        }
    }

    override fun getWeeklySummaryEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_WEEKLY_SUMMARY_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    override suspend fun saveWeeklyReportTime(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_WEEKLY_REPORT_DAY] = dayOfWeek
            preferences[KEY_WEEKLY_REPORT_HOUR] = hour
            preferences[KEY_WEEKLY_REPORT_MINUTE] = minute
        }
    }

    override fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                Triple(
                    preferences[KEY_WEEKLY_REPORT_DAY] ?: Calendar.SUNDAY,
                    preferences[KEY_WEEKLY_REPORT_HOUR] ?: 9,
                    preferences[KEY_WEEKLY_REPORT_MINUTE] ?: 0,
                )
            }
            .distinctUntilChanged()
    }

    override suspend fun saveMonthlySummaryEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_SUMMARY_ENABLED] = isEnabled
        }
    }

    override fun getMonthlySummaryEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_MONTHLY_SUMMARY_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    override suspend fun saveMonthlyReportTime(
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REPORT_DAY] = dayOfMonth
            preferences[KEY_MONTHLY_REPORT_HOUR] = hour
            preferences[KEY_MONTHLY_REPORT_MINUTE] = minute
        }
    }

    override fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                Triple(
                    preferences[KEY_MONTHLY_REPORT_DAY] ?: 1,
                    preferences[KEY_MONTHLY_REPORT_HOUR] ?: 9,
                    preferences[KEY_MONTHLY_REPORT_MINUTE] ?: 0,
                )
            }
            .distinctUntilChanged()
    }

    override suspend fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTOCAPTURE_NOTIFICATION_ENABLED] = isEnabled
        }
    }

    override fun getAutoCaptureNotificationEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_AUTOCAPTURE_NOTIFICATION_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    override suspend fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED] = isEnabled
        }
    }

    override fun getUnknownTransactionPopupEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    override suspend fun setLastMonthSummaryDismissed() {
        val monthKey = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault()).format(Date())
        val prefKey = booleanPreferencesKey(KEY_LAST_MONTH_SUMMARY_DISMISSED_PREFIX + monthKey)
        dataStore.edit { preferences ->
            preferences[prefKey] = true
        }
    }

    override fun hasLastMonthSummaryBeenDismissed(): Flow<Boolean> {
        val monthKey = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault()).format(Date())
        val prefKey = booleanPreferencesKey(KEY_LAST_MONTH_SUMMARY_DISMISSED_PREFIX + monthKey)
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[prefKey] ?: false
            }
            .distinctUntilChanged()
    }
}
