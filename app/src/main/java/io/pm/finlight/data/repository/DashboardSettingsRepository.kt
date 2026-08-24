package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class DashboardSettingsRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson = Gson(),
) {
    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        gson = Gson(),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val KEY_DASHBOARD_CARD_ORDER = "dashboard_card_order"
        private const val KEY_DASHBOARD_VISIBLE_CARDS = "dashboard_visible_cards"
    }

    fun saveDashboardLayout(
        order: List<DashboardCardType>,
        visible: Set<DashboardCardType>,
    ) {
        val orderJson = gson.toJson(order.map { it.name })
        val visibleJson = gson.toJson(visible.map { it.name })
        prefs.edit {
            putString(KEY_DASHBOARD_CARD_ORDER, orderJson)
            putString(KEY_DASHBOARD_VISIBLE_CARDS, visibleJson)
        }
    }

    fun getDashboardCardOrder(): Flow<List<DashboardCardType>> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
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
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
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
            val savedList =
                names.mapNotNull { name ->
                    runCatching {
                        if (name == "RECENT_ACTIVITY") {
                            DashboardCardType.RECENT_TRANSACTIONS
                        } else {
                            DashboardCardType.valueOf(name)
                        }
                    }.getOrNull()
                }
            val missingCards = DashboardCardType.entries.filter { it !in savedList }
            savedList + missingCards
        } else {
            listOf(
                DashboardCardType.HERO_BUDGET,
                DashboardCardType.QUICK_ACTIONS,
                DashboardCardType.RECENT_TRANSACTIONS,
                DashboardCardType.SPENDING_CONSISTENCY,
                DashboardCardType.FINANCIAL_SIMULATORS,
                DashboardCardType.BUDGET_WATCH,
                DashboardCardType.ACCOUNTS_CAROUSEL,
                DashboardCardType.UPCOMING_PAYMENTS,
                DashboardCardType.RECURRING_SUGGESTIONS,
                DashboardCardType.SAVINGS_GOALS,
            )
        }
    }

    private fun loadVisibleCards(sp: SharedPreferences): Set<DashboardCardType> {
        val json = sp.getString(KEY_DASHBOARD_VISIBLE_CARDS, null)
        return if (json != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            val names: Set<String> = gson.fromJson(json, type)
            val savedVisible =
                names.mapNotNull { name ->
                    runCatching {
                        if (name == "RECENT_ACTIVITY") {
                            DashboardCardType.RECENT_TRANSACTIONS
                        } else {
                            DashboardCardType.valueOf(name)
                        }
                    }.getOrNull()
                }.toSet()

            val orderJson = sp.getString(KEY_DASHBOARD_CARD_ORDER, null)
            val knownCards =
                if (orderJson != null) {
                    val orderType = object : TypeToken<List<String>>() {}.type
                    val orderNames: List<String> = gson.fromJson(orderJson, orderType)
                    orderNames.mapNotNull { name ->
                        runCatching {
                            if (name == "RECENT_ACTIVITY") {
                                DashboardCardType.RECENT_TRANSACTIONS
                            } else {
                                DashboardCardType.valueOf(name)
                            }
                        }.getOrNull()
                    }.toSet()
                } else {
                    emptySet()
                }

            val newCards = DashboardCardType.entries.filter { it !in knownCards }
            savedVisible + newCards
        } else {
            DashboardCardType.entries.toSet()
        }
    }
}
