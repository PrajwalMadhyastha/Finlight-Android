package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.pm.finlight.utils.FormatUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationSettingsRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_DAILY_REPORT_ENABLED = "daily_report_enabled"
        private const val KEY_DAILY_REPORT_HOUR = "daily_report_hour"
        private const val KEY_DAILY_REPORT_MINUTE = "daily_report_minute"
        private const val KEY_WEEKLY_SUMMARY_ENABLED = "weekly_summary_enabled"
        private const val KEY_WEEKLY_REPORT_DAY = "weekly_report_day"
        private const val KEY_WEEKLY_REPORT_HOUR = "weekly_report_hour"
        private const val KEY_WEEKLY_REPORT_MINUTE = "weekly_report_minute"
        private const val KEY_MONTHLY_SUMMARY_ENABLED = "monthly_summary_enabled"
        private const val KEY_MONTHLY_REPORT_DAY = "monthly_report_day"
        private const val KEY_MONTHLY_REPORT_HOUR = "monthly_report_hour"
        private const val KEY_MONTHLY_REPORT_MINUTE = "monthly_report_minute"
        private const val KEY_AUTOCAPTURE_NOTIFICATION_ENABLED = "autocapture_notification_enabled"
        private const val KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED = "unknown_transaction_popup_enabled"
        private const val KEY_LAST_MONTH_SUMMARY_DISMISSED = "last_month_summary_dismissed_"
    }

    fun saveDailyReportEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_DAILY_REPORT_ENABLED, isEnabled)
        }
    }

    fun getDailyReportEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_DAILY_REPORT_ENABLED) {
                        trySend(prefs.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_DAILY_REPORT_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveDailyReportTime(
        hour: Int,
        minute: Int,
    ) {
        prefs.edit {
            putInt(KEY_DAILY_REPORT_HOUR, hour)
            putInt(KEY_DAILY_REPORT_MINUTE, minute)
        }
    }

    fun getDailyReportTime(): Flow<Pair<Int, Int>> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (changedKey == KEY_DAILY_REPORT_HOUR || changedKey == KEY_DAILY_REPORT_MINUTE) {
                        trySend(
                            Pair(
                                sharedPreferences.getInt(KEY_DAILY_REPORT_HOUR, 23),
                                sharedPreferences.getInt(KEY_DAILY_REPORT_MINUTE, 0),
                            ),
                        )
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(
                Pair(
                    prefs.getInt(KEY_DAILY_REPORT_HOUR, 23),
                    prefs.getInt(KEY_DAILY_REPORT_MINUTE, 0),
                ),
            )
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveWeeklySummaryEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_WEEKLY_SUMMARY_ENABLED, isEnabled)
        }
    }

    fun getWeeklySummaryEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_WEEKLY_SUMMARY_ENABLED) {
                        trySend(prefs.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_WEEKLY_SUMMARY_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveWeeklyReportTime(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    ) {
        prefs.edit {
            putInt(KEY_WEEKLY_REPORT_DAY, dayOfWeek)
            putInt(KEY_WEEKLY_REPORT_HOUR, hour)
            putInt(KEY_WEEKLY_REPORT_MINUTE, minute)
        }
    }

    fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_WEEKLY_REPORT_DAY || key == KEY_WEEKLY_REPORT_HOUR || key == KEY_WEEKLY_REPORT_MINUTE) {
                        trySend(
                            Triple(
                                sp.getInt(KEY_WEEKLY_REPORT_DAY, Calendar.SUNDAY),
                                sp.getInt(KEY_WEEKLY_REPORT_HOUR, 9),
                                sp.getInt(KEY_WEEKLY_REPORT_MINUTE, 0),
                            ),
                        )
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(
                Triple(
                    prefs.getInt(KEY_WEEKLY_REPORT_DAY, Calendar.SUNDAY),
                    prefs.getInt(KEY_WEEKLY_REPORT_HOUR, 9),
                    prefs.getInt(KEY_WEEKLY_REPORT_MINUTE, 0),
                ),
            )
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveMonthlySummaryEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_MONTHLY_SUMMARY_ENABLED, isEnabled)
        }
    }

    fun getMonthlySummaryEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_MONTHLY_SUMMARY_ENABLED) {
                        trySend(prefs.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_MONTHLY_SUMMARY_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveMonthlyReportTime(
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    ) {
        prefs.edit {
            putInt(KEY_MONTHLY_REPORT_DAY, dayOfMonth)
            putInt(KEY_MONTHLY_REPORT_HOUR, hour)
            putInt(KEY_MONTHLY_REPORT_MINUTE, minute)
        }
    }

    fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_MONTHLY_REPORT_DAY || key == KEY_MONTHLY_REPORT_HOUR || key == KEY_MONTHLY_REPORT_MINUTE) {
                        trySend(
                            Triple(
                                sp.getInt(KEY_MONTHLY_REPORT_DAY, 1),
                                sp.getInt(KEY_MONTHLY_REPORT_HOUR, 9),
                                sp.getInt(KEY_MONTHLY_REPORT_MINUTE, 0),
                            ),
                        )
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(
                Triple(
                    prefs.getInt(KEY_MONTHLY_REPORT_DAY, 1),
                    prefs.getInt(KEY_MONTHLY_REPORT_HOUR, 9),
                    prefs.getInt(KEY_MONTHLY_REPORT_MINUTE, 0),
                ),
            )
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTOCAPTURE_NOTIFICATION_ENABLED, isEnabled) }
    }

    fun getAutoCaptureNotificationEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_AUTOCAPTURE_NOTIFICATION_ENABLED) {
                        trySend(sp.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_AUTOCAPTURE_NOTIFICATION_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun isAutoCaptureNotificationEnabledBlocking(): Boolean {
        return prefs.getBoolean(KEY_AUTOCAPTURE_NOTIFICATION_ENABLED, true)
    }

    fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED, isEnabled)
        }
    }

    fun getUnknownTransactionPopupEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED) {
                        trySend(prefs.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun isUnknownTransactionPopupEnabledBlocking(): Boolean {
        return prefs.getBoolean(KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED, true)
    }

    fun setLastMonthSummaryDismissed() {
        val monthKey = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault()).format(Date())
        prefs.edit {
            putBoolean(KEY_LAST_MONTH_SUMMARY_DISMISSED + monthKey, true)
        }
    }

    fun hasLastMonthSummaryBeenDismissed(): Boolean {
        val monthKey = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault()).format(Date())
        return prefs.getBoolean(KEY_LAST_MONTH_SUMMARY_DISMISSED + monthKey, false)
    }
}
