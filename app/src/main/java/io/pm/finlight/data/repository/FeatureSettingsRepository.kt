package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FeatureSettingsRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_RECURRING_TRANSACTIONS_ENABLED = "recurring_transactions_enabled"
        private const val KEY_EXCLUDED_INCOME_MONTHS = "excluded_income_months"
        private const val KEY_EXCLUDED_EXPENSE_MONTHS = "excluded_expense_months"
        private const val KEY_GOAL_INCOME_THRESHOLD = "goal_income_threshold"
        private const val KEY_GOAL_NUDGES_ENABLED = "goal_nudges_enabled"
    }

    fun saveRecurringTransactionsEnabled(isEnabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_RECURRING_TRANSACTIONS_ENABLED, isEnabled)
        }
    }

    fun getRecurringTransactionsEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_RECURRING_TRANSACTIONS_ENABLED) {
                        trySend(sp.getBoolean(key, false))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_RECURRING_TRANSACTIONS_ENABLED, false))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveGoalIncomeThreshold(amount: Int) {
        prefs.edit { putInt(KEY_GOAL_INCOME_THRESHOLD, amount) }
    }

    fun getGoalIncomeThreshold(): Flow<Int> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_GOAL_INCOME_THRESHOLD) {
                        trySend(sp.getInt(key, 5000))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getInt(KEY_GOAL_INCOME_THRESHOLD, 5000))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun saveGoalNudgesEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_GOAL_NUDGES_ENABLED, isEnabled) }
    }

    fun getGoalNudgesEnabled(): Flow<Boolean> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_GOAL_NUDGES_ENABLED) {
                        trySend(sp.getBoolean(key, true))
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getBoolean(KEY_GOAL_NUDGES_ENABLED, true))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun isGoalNudgesEnabledBlocking(): Boolean {
        return prefs.getBoolean(KEY_GOAL_NUDGES_ENABLED, true)
    }

    fun getExcludedIncomeMonths(): Flow<Set<String>> = getSetFlow(KEY_EXCLUDED_INCOME_MONTHS)

    fun getExcludedExpenseMonths(): Flow<Set<String>> = getSetFlow(KEY_EXCLUDED_EXPENSE_MONTHS)

    fun toggleIncomeMonthExclusion(monthKey: String) {
        toggleInSet(KEY_EXCLUDED_INCOME_MONTHS, monthKey)
    }

    fun toggleExpenseMonthExclusion(monthKey: String) {
        toggleInSet(KEY_EXCLUDED_EXPENSE_MONTHS, monthKey)
    }

    private fun getSetFlow(key: String): Flow<Set<String>> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, k ->
                    if (k == key) trySend(sp.getStringSet(key, emptySet()) ?: emptySet())
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(prefs.getStringSet(key, emptySet()) ?: emptySet())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    private fun toggleInSet(
        key: String,
        value: String,
    ) {
        val current = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val newSet =
            if (current.contains(value)) {
                current.toMutableSet().apply { remove(value) }
            } else {
                current.toMutableSet().apply { add(value) }
            }
        prefs.edit { putStringSet(key, newSet) }
    }
}
