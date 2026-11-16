package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FeatureSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : IFeatureSettingsRepository {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_RECURRING_TRANSACTIONS_ENABLED = booleanPreferencesKey("recurring_transactions_enabled")
        private val KEY_EXCLUDED_INCOME_MONTHS = stringSetPreferencesKey("excluded_income_months")
        private val KEY_EXCLUDED_EXPENSE_MONTHS = stringSetPreferencesKey("excluded_expense_months")
        private val KEY_GOAL_INCOME_THRESHOLD = intPreferencesKey("goal_income_threshold")
        private val KEY_GOAL_NUDGES_ENABLED = booleanPreferencesKey("goal_nudges_enabled")
        private val KEY_LAST_SURPLUS_PROMPT_YEAR = intPreferencesKey("last_surplus_prompt_year")
        private val KEY_LAST_SURPLUS_PROMPT_MONTH = intPreferencesKey("last_surplus_prompt_month")
        private val KEY_NOTIFIED_PATTERN_SIGNATURES = stringSetPreferencesKey("notified_pattern_signatures")
    }

    override suspend fun saveRecurringTransactionsEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_RECURRING_TRANSACTIONS_ENABLED] = isEnabled
        }
    }

    override fun getRecurringTransactionsEnabled(): Flow<Boolean> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_RECURRING_TRANSACTIONS_ENABLED] ?: false
                } catch (_: ClassCastException) {
                    false
                }
            }
            .catch {
                emit(false)
            }
            .distinctUntilChanged()
    }

    override suspend fun saveGoalIncomeThreshold(amount: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_GOAL_INCOME_THRESHOLD] = amount
        }
    }

    override fun getGoalIncomeThreshold(): Flow<Int> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_GOAL_INCOME_THRESHOLD] ?: 5000
                } catch (_: ClassCastException) {
                    5000
                }
            }
            .catch {
                emit(5000)
            }
            .distinctUntilChanged()
    }

    override suspend fun saveGoalNudgesEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_GOAL_NUDGES_ENABLED] = isEnabled
        }
    }

    override fun getGoalNudgesEnabled(): Flow<Boolean> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[KEY_GOAL_NUDGES_ENABLED] ?: true
                } catch (_: ClassCastException) {
                    true
                }
            }
            .catch {
                emit(true)
            }
            .distinctUntilChanged()
    }

    override suspend fun saveLastSurplusPrompt(
        year: Int,
        month: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_SURPLUS_PROMPT_YEAR] = year
            preferences[KEY_LAST_SURPLUS_PROMPT_MONTH] = month
        }
    }

    override fun getLastSurplusPrompt(): Flow<Pair<Int, Int>?> {
        return dataStore.data
            .map { preferences ->
                try {
                    val year = preferences[KEY_LAST_SURPLUS_PROMPT_YEAR]
                    val month = preferences[KEY_LAST_SURPLUS_PROMPT_MONTH]
                    if (year != null && month != null) {
                        Pair(year, month)
                    } else {
                        null
                    }
                } catch (_: ClassCastException) {
                    null
                }
            }
            .catch {
                emit(null)
            }
            .distinctUntilChanged()
    }

    override fun getExcludedIncomeMonths(): Flow<Set<String>> = getSetFlow(KEY_EXCLUDED_INCOME_MONTHS)

    override fun getExcludedExpenseMonths(): Flow<Set<String>> = getSetFlow(KEY_EXCLUDED_EXPENSE_MONTHS)

    override suspend fun toggleIncomeMonthExclusion(monthKey: String) {
        toggleInSet(KEY_EXCLUDED_INCOME_MONTHS, monthKey)
    }

    override suspend fun toggleExpenseMonthExclusion(monthKey: String) {
        toggleInSet(KEY_EXCLUDED_EXPENSE_MONTHS, monthKey)
    }

    override suspend fun getNotifiedPatternSignatures(): Set<String> {
        return try {
            val preferences = dataStore.data.first()
            preferences[KEY_NOTIFIED_PATTERN_SIGNATURES] ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    override suspend fun markPatternNotified(signature: String) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_NOTIFIED_PATTERN_SIGNATURES] ?: emptySet()
            preferences[KEY_NOTIFIED_PATTERN_SIGNATURES] = current + signature
        }
    }

    override suspend fun clearNotifiedPatternSignatures() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_NOTIFIED_PATTERN_SIGNATURES)
        }
    }

    private fun getSetFlow(key: Preferences.Key<Set<String>>): Flow<Set<String>> {
        return dataStore.data
            .map { preferences ->
                try {
                    preferences[key] ?: emptySet()
                } catch (_: ClassCastException) {
                    emptySet()
                }
            }
            .catch {
                emit(emptySet())
            }
            .distinctUntilChanged()
    }

    private suspend fun toggleInSet(
        key: Preferences.Key<Set<String>>,
        value: String,
    ) {
        dataStore.edit { preferences ->
            val current = preferences[key] ?: emptySet()
            val newSet =
                if (current.contains(value)) {
                    current.toMutableSet().apply { remove(value) }
                } else {
                    current.toMutableSet().apply { add(value) }
                }
            preferences[key] = newSet
        }
    }
}
