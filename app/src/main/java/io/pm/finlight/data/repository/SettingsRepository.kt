// FILE: ./app/src/main/java/io/pm/finlight/data/repository/SettingsRepository.kt
// =================================================================================
// REASON: FIX - Added functions to save and retrieve a checksum for the default
// ignore rules. This provides the persistence layer needed for the new "checksum"
// seeding strategy, allowing the AppDatabase callback to detect when the rules
// in the source code have been updated.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * An enum to distinguish between domestic and international travel modes.
 */
enum class TripType {
    DOMESTIC,
    INTERNATIONAL
}

data class TravelModeSettings(
    val isEnabled: Boolean,
    val tripName: String,
    val tripType: TripType,
    val startDate: Long,
    val endDate: Long,
    val currencyCode: String?,
    val conversionRate: Float?
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    // --- NEW: A separate, non-backed-up preferences file for internal state ---
    private val internalPrefs: SharedPreferences =
        context.getSharedPreferences(INTERNAL_PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val INTERNAL_PREF_NAME = "finlight_internal_state" // Must match data_extraction_rules.xml
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROFILE_PICTURE_URI = "profile_picture_uri"
        private const val KEY_BUDGET_PREFIX = "overall_budget_"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_WEEKLY_SUMMARY_ENABLED = "weekly_summary_enabled"
        private const val KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED = "unknown_transaction_popup_enabled"
        private const val KEY_DAILY_REPORT_ENABLED = "daily_report_enabled"
        private const val KEY_SMS_SCAN_START_DATE = "sms_scan_start_date"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_BACKUP_ENABLED = "google_drive_backup_enabled"
        private const val KEY_DAILY_REPORT_HOUR = "daily_report_hour"
        private const val KEY_DAILY_REPORT_MINUTE = "daily_report_minute"
        private const val KEY_WEEKLY_REPORT_DAY = "weekly_report_day"
        private const val KEY_WEEKLY_REPORT_HOUR = "weekly_report_hour"
        private const val KEY_WEEKLY_REPORT_MINUTE = "weekly_report_minute"
        private const val KEY_MONTHLY_REPORT_DAY = "monthly_report_day"
        private const val KEY_MONTHLY_REPORT_HOUR = "monthly_report_hour"
        private const val KEY_MONTHLY_REPORT_MINUTE = "monthly_report_minute"
        private const val KEY_MONTHLY_SUMMARY_ENABLED = "monthly_summary_enabled"
        private const val KEY_DASHBOARD_CARD_ORDER = "dashboard_card_order"
        private const val KEY_DASHBOARD_VISIBLE_CARDS = "dashboard_visible_cards"
        private const val KEY_SELECTED_THEME = "selected_app_theme"
        private const val KEY_HOME_CURRENCY = "home_currency_code"
        private const val KEY_TRAVEL_MODE_SETTINGS = "travel_mode_settings"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_BACKUP_HOUR = "auto_backup_hour"
        private const val KEY_AUTO_BACKUP_MINUTE = "auto_backup_minute"
        private const val KEY_AUTO_BACKUP_NOTIFICATION_ENABLED = "auto_backup_notification_enabled"
        private const val KEY_AUTOCAPTURE_NOTIFICATION_ENABLED = "autocapture_notification_enabled"
        private const val KEY_LAST_MONTH_SUMMARY_DISMISSED = "last_month_summary_dismissed_"
        private const val KEY_DISMISSED_MERGES = "dismissed_merge_suggestions"
        private const val KEY_PRIVACY_MODE_ENABLED = "privacy_mode_enabled"
        // --- NEW: Key for the first launch flag ---
        private const val KEY_IS_FIRST_LAUNCH_COMPLETE = "is_first_launch_complete"
        // --- NEW: Key for the ignore rules checksum ---
        private const val KEY_IGNORE_RULES_CHECKSUM = "ignore_rules_checksum"
    }

    // --- NEW: Functions to manage the ignore rules checksum ---
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
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
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

    fun setLastMonthSummaryDismissed() {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        prefs.edit {
            putBoolean("$KEY_LAST_MONTH_SUMMARY_DISMISSED$monthKey", true)
        }
    }

    fun hasLastMonthSummaryBeenDismissed(): Boolean {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        return prefs.getBoolean("$KEY_LAST_MONTH_SUMMARY_DISMISSED$monthKey", false)
    }

    fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTOCAPTURE_NOTIFICATION_ENABLED, isEnabled) }
    }

    fun getAutoCaptureNotificationEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
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

    fun saveAutoBackupEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP_ENABLED, isEnabled) }
    }

    fun getAutoBackupEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_AUTO_BACKUP_ENABLED) {
                    trySend(sp.getBoolean(key, true))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveAutoBackupNotificationEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP_NOTIFICATION_ENABLED, isEnabled) }
    }

    fun getAutoBackupNotificationEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_AUTO_BACKUP_NOTIFICATION_ENABLED) {
                    trySend(sp.getBoolean(key, false))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_AUTO_BACKUP_NOTIFICATION_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveAutoBackupTime(hour: Int, minute: Int) {
        prefs.edit {
            putInt(KEY_AUTO_BACKUP_HOUR, hour)
            putInt(KEY_AUTO_BACKUP_MINUTE, minute)
        }
    }

    fun getAutoBackupTime(): Flow<Pair<Int, Int>> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_AUTO_BACKUP_HOUR || key == KEY_AUTO_BACKUP_MINUTE) {
                    trySend(Pair(sp.getInt(KEY_AUTO_BACKUP_HOUR, 2), sp.getInt(KEY_AUTO_BACKUP_MINUTE, 0)))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Pair(prefs.getInt(KEY_AUTO_BACKUP_HOUR, 2), prefs.getInt(KEY_AUTO_BACKUP_MINUTE, 0)))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveHomeCurrency(currencyCode: String) {
        prefs.edit {
            putString(KEY_HOME_CURRENCY, currencyCode)
        }
    }

    fun getHomeCurrency(): Flow<String> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_HOME_CURRENCY) {
                    trySend(sp.getString(key, "INR") ?: "INR")
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getString(KEY_HOME_CURRENCY, "INR") ?: "INR")
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveTravelModeSettings(settings: TravelModeSettings?) {
        val json = if (settings == null) null else gson.toJson(settings)
        prefs.edit {
            putString(KEY_TRAVEL_MODE_SETTINGS, json)
        }
    }

    fun getTravelModeSettings(): Flow<TravelModeSettings?> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_TRAVEL_MODE_SETTINGS) {
                    val json = sp.getString(key, null)
                    val settings = if (json == null) null else gson.fromJson(json, TravelModeSettings::class.java)
                    trySend(settings)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val initialJson = prefs.getString(KEY_TRAVEL_MODE_SETTINGS, null)
            val initialSettings = if (initialJson == null) null else gson.fromJson(initialJson, TravelModeSettings::class.java)
            trySend(initialSettings)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun getOverallBudgetForMonthBlocking(year: Int, month: Int): Float {
        val currentMonthKey = getBudgetKey(year, month)

        if (prefs.contains(currentMonthKey)) {
            return prefs.getFloat(currentMonthKey, 0f)
        }

        val searchCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
        }

        for (i in 0..11) {
            searchCal.add(Calendar.MONTH, -1)
            val prevYear = searchCal.get(Calendar.YEAR)
            val prevMonth = searchCal.get(Calendar.MONTH) + 1
            val prevKey = getBudgetKey(prevYear, prevMonth)
            if (prefs.contains(prevKey)) {
                return prefs.getFloat(prevKey, 0f)
            }
        }

        return 0f
    }

    fun saveSelectedTheme(theme: AppTheme) {
        prefs.edit {
            putString(KEY_SELECTED_THEME, theme.key)
        }
    }

    fun getSelectedTheme(): Flow<AppTheme> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_SELECTED_THEME) {
                    val themeKey = sp.getString(key, AppTheme.SYSTEM_DEFAULT.key)
                    trySend(AppTheme.fromKey(themeKey))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val initialThemeKey = prefs.getString(KEY_SELECTED_THEME, AppTheme.SYSTEM_DEFAULT.key)
            trySend(AppTheme.fromKey(initialThemeKey))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveDashboardLayout(order: List<DashboardCardType>, visible: Set<DashboardCardType>) {
        val orderJson = gson.toJson(order.map { it.name })
        val visibleJson = gson.toJson(visible.map { it.name })
        prefs.edit {
            putString(KEY_DASHBOARD_CARD_ORDER, orderJson)
            putString(KEY_DASHBOARD_VISIBLE_CARDS, visibleJson)
        }
    }

    fun getDashboardCardOrder(): Flow<List<DashboardCardType>> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_DASHBOARD_CARD_ORDER) {
                    trySend(loadCardOrder(sp))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(loadCardOrder(prefs))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun getDashboardVisibleCards(): Flow<Set<DashboardCardType>> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_DASHBOARD_VISIBLE_CARDS) {
                    trySend(loadVisibleCards(sp))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(loadVisibleCards(prefs))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    private fun loadCardOrder(sp: SharedPreferences): List<DashboardCardType> {
        val json = sp.getString(KEY_DASHBOARD_CARD_ORDER, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            val names: List<String> = gson.fromJson(json, type)
            names.mapNotNull { name ->
                runCatching {
                    if (name == "RECENT_ACTIVITY") {
                        DashboardCardType.RECENT_TRANSACTIONS
                    } else {
                        DashboardCardType.valueOf(name)
                    }
                }.getOrNull()
            }
        } else {
            listOf(
                DashboardCardType.HERO_BUDGET,
                DashboardCardType.QUICK_ACTIONS,
                DashboardCardType.RECENT_TRANSACTIONS,
                DashboardCardType.SPENDING_CONSISTENCY,
                DashboardCardType.BUDGET_WATCH,
                DashboardCardType.ACCOUNTS_CAROUSEL
            )
        }
    }

    private fun loadVisibleCards(sp: SharedPreferences): Set<DashboardCardType> {
        val json = sp.getString(KEY_DASHBOARD_VISIBLE_CARDS, null)
        return if (json != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            val names: Set<String> = gson.fromJson(json, type)
            names.mapNotNull { name ->
                runCatching {
                    if (name == "RECENT_ACTIVITY") {
                        DashboardCardType.RECENT_TRANSACTIONS
                    } else {
                        DashboardCardType.valueOf(name)
                    }
                }.getOrNull()
            }.toSet()
        } else {
            DashboardCardType.entries.toSet()
        }
    }

    fun saveBackupEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_BACKUP_ENABLED, isEnabled)
        }
    }

    fun getBackupEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (changedKey == KEY_BACKUP_ENABLED) {
                    trySend(sharedPreferences.getBoolean(KEY_BACKUP_ENABLED, true))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_BACKUP_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveUserName(name: String) {
        prefs.edit {
            putString(KEY_USER_NAME, name)
        }
    }

    fun getUserName(): Flow<String> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (changedKey == KEY_USER_NAME) {
                    trySend(sharedPreferences.getString(KEY_USER_NAME, "User") ?: "User")
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getString(KEY_USER_NAME, "User") ?: "User")
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveProfilePictureUri(uriString: String?) {
        prefs.edit {
            putString(KEY_PROFILE_PICTURE_URI, uriString)
        }
    }

    fun getProfilePictureUri(): Flow<String?> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (changedKey == KEY_PROFILE_PICTURE_URI) {
                    trySend(sharedPreferences.getString(KEY_PROFILE_PICTURE_URI, null))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getString(KEY_PROFILE_PICTURE_URI, null))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        prefs.edit {
            putBoolean(KEY_HAS_SEEN_ONBOARDING, hasSeen)
        }
    }

    private fun getBudgetKey(year: Int, month: Int): String {
        return String.format(Locale.ROOT, "%s%d_%02d", KEY_BUDGET_PREFIX, year, month)
    }

    fun saveOverallBudgetForCurrentMonth(amount: Float) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val key = getBudgetKey(year, month)
        prefs.edit {
            putFloat(key, amount)
        }
    }

    fun saveSmsScanStartDate(date: Long) {
        prefs.edit {
            putLong(KEY_SMS_SCAN_START_DATE, date)
        }
    }

    fun getSmsScanStartDate(): Flow<Long> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
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

    fun saveAppLockEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_APP_LOCK_ENABLED, isEnabled)
        }
    }

    fun saveDailyReportEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_DAILY_REPORT_ENABLED, isEnabled)
        }
    }

    fun getDailyReportEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_DAILY_REPORT_ENABLED) {
                    trySend(prefs.getBoolean(key, true))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_DAILY_REPORT_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun getAppLockEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (changedKey == KEY_APP_LOCK_ENABLED) {
                    trySend(sharedPreferences.getBoolean(KEY_APP_LOCK_ENABLED, false))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun isAppLockEnabledBlocking(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    // --- BUG FIX: Replaced the flawed logic with the robust, looping carry-forward logic ---
    fun getOverallBudgetForMonth(year: Int, month: Int): Flow<Float> {
        return callbackFlow {
            // Helper function to find the correct carried-over budget.
            // This is the same logic as the blocking version.
            fun findCarriedOverBudget(): Float {
                val currentMonthKey = getBudgetKey(year, month)
                if (prefs.contains(currentMonthKey)) {
                    return prefs.getFloat(currentMonthKey, 0f)
                }
                val searchCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                }
                for (i in 0..11) { // Look back a maximum of 12 months
                    searchCal.add(Calendar.MONTH, -1)
                    val prevYear = searchCal.get(Calendar.YEAR)
                    val prevMonth = searchCal.get(Calendar.MONTH) + 1
                    val prevKey = getBudgetKey(prevYear, prevMonth)
                    if (prefs.contains(prevKey)) {
                        return prefs.getFloat(prevKey, 0f)
                    }
                }
                return 0f
            }

            // The listener now re-runs the entire lookback logic whenever ANY budget key changes.
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key?.startsWith(KEY_BUDGET_PREFIX) == true) {
                    trySend(findCarriedOverBudget())
                }
            }

            prefs.registerOnSharedPreferenceChangeListener(listener)
            // Emit the initial correct value.
            trySend(findCarriedOverBudget())

            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    fun saveWeeklySummaryEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_WEEKLY_SUMMARY_ENABLED, isEnabled)
        }
    }

    fun getWeeklySummaryEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_WEEKLY_SUMMARY_ENABLED) {
                    trySend(prefs.getBoolean(key, true))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_WEEKLY_SUMMARY_ENABLED, true))
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
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_MONTHLY_SUMMARY_ENABLED) {
                    trySend(prefs.getBoolean(key, true))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_MONTHLY_SUMMARY_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_UNKNOWN_TRANSACTION_POPUP_ENABLED, isEnabled)
        }
    }

    fun getUnknownTransactionPopupEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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

    fun saveDailyReportTime(hour: Int, minute: Int) {
        prefs.edit {
            putInt(KEY_DAILY_REPORT_HOUR, hour)
            putInt(KEY_DAILY_REPORT_MINUTE, minute)
        }
    }

    fun getDailyReportTime(): Flow<Pair<Int, Int>> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (changedKey == KEY_DAILY_REPORT_HOUR || changedKey == KEY_DAILY_REPORT_MINUTE) {
                    trySend(
                        Pair(
                            sharedPreferences.getInt(KEY_DAILY_REPORT_HOUR, 23),
                            sharedPreferences.getInt(KEY_DAILY_REPORT_MINUTE, 0)
                        )
                    )
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(
                Pair(
                    prefs.getInt(KEY_DAILY_REPORT_HOUR, 23),
                    prefs.getInt(KEY_DAILY_REPORT_MINUTE, 0)
                )
            )
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveWeeklyReportTime(dayOfWeek: Int, hour: Int, minute: Int) {
        prefs.edit {
            putInt(KEY_WEEKLY_REPORT_DAY, dayOfWeek)
            putInt(KEY_WEEKLY_REPORT_HOUR, hour)
            putInt(KEY_WEEKLY_REPORT_MINUTE, minute)
        }
    }

    fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_WEEKLY_REPORT_DAY || key == KEY_WEEKLY_REPORT_HOUR || key == KEY_WEEKLY_REPORT_MINUTE) {
                    trySend(Triple(
                        sp.getInt(KEY_WEEKLY_REPORT_DAY, Calendar.SUNDAY),
                        sp.getInt(KEY_WEEKLY_REPORT_HOUR, 9),
                        sp.getInt(KEY_WEEKLY_REPORT_MINUTE, 0)
                    ))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Triple(
                prefs.getInt(KEY_WEEKLY_REPORT_DAY, Calendar.SUNDAY),
                prefs.getInt(KEY_WEEKLY_REPORT_HOUR, 9),
                prefs.getInt(KEY_WEEKLY_REPORT_MINUTE, 0)
            ))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveMonthlyReportTime(dayOfMonth: Int, hour: Int, minute: Int) {
        prefs.edit {
            putInt(KEY_MONTHLY_REPORT_DAY, dayOfMonth)
            putInt(KEY_MONTHLY_REPORT_HOUR, hour)
            putInt(KEY_MONTHLY_REPORT_MINUTE, minute)
        }
    }

    fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_MONTHLY_REPORT_DAY || key == KEY_MONTHLY_REPORT_HOUR || key == KEY_MONTHLY_REPORT_MINUTE) {
                    trySend(Triple(
                        sp.getInt(KEY_MONTHLY_REPORT_DAY, 1),
                        sp.getInt(KEY_MONTHLY_REPORT_HOUR, 9),
                        sp.getInt(KEY_MONTHLY_REPORT_MINUTE, 0)
                    ))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Triple(
                prefs.getInt(KEY_MONTHLY_REPORT_DAY, 1),
                prefs.getInt(KEY_MONTHLY_REPORT_HOUR, 9),
                prefs.getInt(KEY_MONTHLY_REPORT_MINUTE, 0)
            ))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun savePrivacyModeEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_PRIVACY_MODE_ENABLED, isEnabled)
        }
    }

    fun getPrivacyModeEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == KEY_PRIVACY_MODE_ENABLED) {
                    trySend(sp.getBoolean(key, false))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            // Default to false (disabled)
            trySend(prefs.getBoolean(KEY_PRIVACY_MODE_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    // --- NEW: Functions to manage the first launch flag ---

    /**
     * Checks if the app has completed its first launch sequence.
     * This is a blocking call and should only be used during app startup.
     */
    fun isFirstLaunchCompleteBlocking(): Boolean {
        return internalPrefs.getBoolean(KEY_IS_FIRST_LAUNCH_COMPLETE, false)
    }

    /**
     * Sets the flag indicating that the first launch sequence is complete.
     */
    fun setFirstLaunchComplete() {
        internalPrefs.edit {
            putBoolean(KEY_IS_FIRST_LAUNCH_COMPLETE, true)
        }
    }
}