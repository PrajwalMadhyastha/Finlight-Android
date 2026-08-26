package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Calendar
import java.util.Locale

class BudgetSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        context.financeSettingsDataStore,
    )

    companion object {
        private const val KEY_BUDGET_PREFIX = "overall_budget_"

        private fun getBudgetKey(
            year: Int,
            month: Int,
        ): String {
            return String.format(Locale.ROOT, "%s%d_%02d", KEY_BUDGET_PREFIX, year, month)
        }
    }

    suspend fun saveOverallBudgetForCurrentMonth(amount: Float) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        saveOverallBudgetForMonth(year, month, amount)
    }

    suspend fun saveOverallBudgetForMonth(
        year: Int,
        month: Int,
        amount: Float,
    ) {
        val prefKey = floatPreferencesKey(getBudgetKey(year, month))
        dataStore.edit { preferences ->
            preferences[prefKey] = amount
        }
    }

    fun getOverallBudgetsForYear(year: Int): Map<Int, Float> {
        val budgets = mutableMapOf<Int, Float>()
        val preferences =
            runBlocking {
                try {
                    dataStore.data.first()
                } catch (e: Exception) {
                    emptyPreferences()
                }
            }
        for (month in 1..12) {
            val prefKey = floatPreferencesKey(getBudgetKey(year, month))
            if (preferences.contains(prefKey)) {
                preferences[prefKey]?.let { budgets[month] = it }
            }
        }
        return budgets
    }

    fun getOverallBudgetForMonthBlocking(
        year: Int,
        month: Int,
    ): Float? {
        val preferences =
            runBlocking {
                try {
                    dataStore.data.first()
                } catch (e: Exception) {
                    emptyPreferences()
                }
            }
        return findCarriedOverBudget(preferences, year, month)
    }

    fun getOverallBudgetForMonth(
        year: Int,
        month: Int,
    ): Flow<Float?> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                findCarriedOverBudget(preferences, year, month)
            }
            .distinctUntilChanged()
    }

    private fun findCarriedOverBudget(
        preferences: Preferences,
        year: Int,
        month: Int,
    ): Float? {
        val currentMonthKey = floatPreferencesKey(getBudgetKey(year, month))
        if (preferences.contains(currentMonthKey)) {
            return preferences[currentMonthKey]
        }

        val searchCal =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
            }

        for (i in 0..11) {
            searchCal.add(Calendar.MONTH, -1)
            val prevYear = searchCal.get(Calendar.YEAR)
            val prevMonth = searchCal.get(Calendar.MONTH) + 1
            val prevKey = floatPreferencesKey(getBudgetKey(prevYear, prevMonth))
            if (preferences.contains(prevKey)) {
                return preferences[prevKey]
            }
        }

        return null
    }
}
