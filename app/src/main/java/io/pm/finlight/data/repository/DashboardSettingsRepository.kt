package io.pm.finlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class DashboardSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) {
    constructor(context: Context) : this(
        dataStore = context.financeSettingsDataStore,
        gson = Gson(),
    )

    companion object {
        private val KEY_DASHBOARD_CARD_ORDER = stringPreferencesKey("dashboard_card_order")
        private val KEY_DASHBOARD_VISIBLE_CARDS = stringPreferencesKey("dashboard_visible_cards")
    }

    suspend fun saveDashboardLayout(
        order: List<DashboardCardType>,
        visible: Set<DashboardCardType>,
    ) {
        val orderJson = gson.toJson(order.map { it.name })
        val visibleJson = gson.toJson(visible.map { it.name })
        dataStore.edit { preferences ->
            preferences[KEY_DASHBOARD_CARD_ORDER] = orderJson
            preferences[KEY_DASHBOARD_VISIBLE_CARDS] = visibleJson
        }
    }

    fun getDashboardCardOrder(): Flow<List<DashboardCardType>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                loadCardOrder(preferences[KEY_DASHBOARD_CARD_ORDER])
            }
            .distinctUntilChanged()
    }

    fun getDashboardVisibleCards(): Flow<Set<DashboardCardType>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                loadVisibleCards(
                    visibleJson = preferences[KEY_DASHBOARD_VISIBLE_CARDS],
                    orderJson = preferences[KEY_DASHBOARD_CARD_ORDER],
                )
            }
            .distinctUntilChanged()
    }

    private fun loadCardOrder(json: String?): List<DashboardCardType> {
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

    private fun loadVisibleCards(
        visibleJson: String?,
        orderJson: String?,
    ): Set<DashboardCardType> {
        return if (visibleJson != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            val names: Set<String> = gson.fromJson(visibleJson, type)
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
