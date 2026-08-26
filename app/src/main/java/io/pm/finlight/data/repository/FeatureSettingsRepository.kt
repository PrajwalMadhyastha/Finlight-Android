package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

class FeatureSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private val KEY_RECURRING_TRANSACTIONS_ENABLED = booleanPreferencesKey("recurring_transactions_enabled")
        private val KEY_EXCLUDED_INCOME_MONTHS = stringSetPreferencesKey("excluded_income_months")
        private val KEY_EXCLUDED_EXPENSE_MONTHS = stringSetPreferencesKey("excluded_expense_months")
        private val KEY_GOAL_INCOME_THRESHOLD = intPreferencesKey("goal_income_threshold")
        private val KEY_GOAL_NUDGES_ENABLED = booleanPreferencesKey("goal_nudges_enabled")
    }

    suspend fun saveRecurringTransactionsEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_RECURRING_TRANSACTIONS_ENABLED] = isEnabled
        }
    }

    fun getRecurringTransactionsEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_RECURRING_TRANSACTIONS_ENABLED] ?: false
            }
            .distinctUntilChanged()
    }

    suspend fun saveGoalIncomeThreshold(amount: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_GOAL_INCOME_THRESHOLD] = amount
        }
    }

    fun getGoalIncomeThreshold(): Flow<Int> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_GOAL_INCOME_THRESHOLD] ?: 5000
            }
            .distinctUntilChanged()
    }

    suspend fun saveGoalNudgesEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_GOAL_NUDGES_ENABLED] = isEnabled
        }
    }

    fun getGoalNudgesEnabled(): Flow<Boolean> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_GOAL_NUDGES_ENABLED] ?: true
            }
            .distinctUntilChanged()
    }

    fun isGoalNudgesEnabledBlocking(): Boolean {
        return runBlocking {
            try {
                dataStore.data.first()[KEY_GOAL_NUDGES_ENABLED] ?: true
            } catch (e: Exception) {
                true
            }
        }
    }

    fun getExcludedIncomeMonths(): Flow<Set<String>> = getSetFlow(KEY_EXCLUDED_INCOME_MONTHS)

    fun getExcludedExpenseMonths(): Flow<Set<String>> = getSetFlow(KEY_EXCLUDED_EXPENSE_MONTHS)

    suspend fun toggleIncomeMonthExclusion(monthKey: String) {
        toggleInSet(KEY_EXCLUDED_INCOME_MONTHS, monthKey)
    }

    suspend fun toggleExpenseMonthExclusion(monthKey: String) {
        toggleInSet(KEY_EXCLUDED_EXPENSE_MONTHS, monthKey)
    }

    private fun getSetFlow(key: Preferences.Key<Set<String>>): Flow<Set<String>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: emptySet()
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
