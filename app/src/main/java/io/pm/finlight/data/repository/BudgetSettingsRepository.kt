package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Calendar
import java.util.Locale

class BudgetSettingsRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_BUDGET_PREFIX = "overall_budget_"
    }

    private fun getBudgetKey(
        year: Int,
        month: Int,
    ): String {
        return String.format(Locale.ROOT, "%s%d_%02d", KEY_BUDGET_PREFIX, year, month)
    }

    fun saveOverallBudgetForCurrentMonth(amount: Float) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        saveOverallBudgetForMonth(year, month, amount)
    }

    fun saveOverallBudgetForMonth(
        year: Int,
        month: Int,
        amount: Float,
    ) {
        val key = getBudgetKey(year, month)
        prefs.edit {
            putFloat(key, amount)
        }
    }

    fun getOverallBudgetsForYear(year: Int): Map<Int, Float> {
        val budgets = mutableMapOf<Int, Float>()
        for (month in 1..12) {
            val key = getBudgetKey(year, month)
            if (prefs.contains(key)) {
                budgets[month] = prefs.getFloat(key, 0f)
            }
        }
        return budgets
    }

    fun getOverallBudgetForMonthBlocking(
        year: Int,
        month: Int,
    ): Float? {
        val currentMonthKey = getBudgetKey(year, month)

        if (prefs.contains(currentMonthKey)) {
            return prefs.getFloat(currentMonthKey, 0f)
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
            val prevKey = getBudgetKey(prevYear, prevMonth)
            if (prefs.contains(prevKey)) {
                return prefs.getFloat(prevKey, 0f)
            }
        }

        return null
    }

    fun getOverallBudgetForMonth(
        year: Int,
        month: Int,
    ): Flow<Float?> {
        return callbackFlow {
            fun findCarriedOverBudget(): Float? {
                val currentMonthKey = getBudgetKey(year, month)
                if (prefs.contains(currentMonthKey)) {
                    return prefs.getFloat(currentMonthKey, 0f)
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
                    val prevKey = getBudgetKey(prevYear, prevMonth)
                    if (prefs.contains(prevKey)) {
                        return prefs.getFloat(prevKey, 0f)
                    }
                }
                return null
            }

            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key?.startsWith(KEY_BUDGET_PREFIX) == true) {
                        trySend(findCarriedOverBudget())
                    }
                }

            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(findCarriedOverBudget())

            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }
}
