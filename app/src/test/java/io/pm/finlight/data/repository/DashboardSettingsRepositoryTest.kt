package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.gson.Gson
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.DashboardCardType
import io.pm.finlight.DashboardSettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DashboardSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: DashboardSettingsRepository
    private val gson = Gson()

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = DashboardSettingsRepository(dataStore)
    }

    @Test
    fun `getDashboardCardOrder emits default order`() =
        runTest {
            repository.getDashboardCardOrder().test {
                val defaultOrder = awaitItem()
                assertTrue(defaultOrder.isNotEmpty())
                assertEquals(DashboardCardType.HERO_BUDGET, defaultOrder.first())
                assertEquals(
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
                    ),
                    defaultOrder,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getDashboardVisibleCards emits default set`() =
        runTest {
            repository.getDashboardVisibleCards().test {
                val defaultVisible = awaitItem()
                assertEquals(DashboardCardType.entries.toSet(), defaultVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saveDashboardLayout updates order and visible cards`() =
        runTest {
            val customOrder =
                listOf(
                    DashboardCardType.ACCOUNTS_CAROUSEL,
                    DashboardCardType.HERO_BUDGET,
                )
            val customVisible = setOf(DashboardCardType.ACCOUNTS_CAROUSEL)

            repository.getDashboardCardOrder().test {
                awaitItem() // Initial
                repository.getDashboardVisibleCards().test {
                    awaitItem() // Initial

                    repository.saveDashboardLayout(customOrder, customVisible)

                    val updatedVisible = awaitItem()
                    assertTrue(updatedVisible.contains(DashboardCardType.ACCOUNTS_CAROUSEL))
                    cancelAndIgnoreRemainingEvents()
                }
                val updatedOrder = awaitItem()
                assertEquals(DashboardCardType.ACCOUNTS_CAROUSEL, updatedOrder[0])
                assertEquals(DashboardCardType.HERO_BUDGET, updatedOrder[1])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `migration of legacy RECENT_ACTIVITY card name`() =
        runTest {
            val legacyJson = gson.toJson(listOf("RECENT_ACTIVITY", "HERO_BUDGET"))
            context.financeSettingsDataStore.edit {
                it[stringPreferencesKey("dashboard_card_order")] = legacyJson
            }

            repository.getDashboardCardOrder().test {
                val order = awaitItem()
                assertEquals(DashboardCardType.RECENT_TRANSACTIONS, order[0])
                assertEquals(DashboardCardType.HERO_BUDGET, order[1])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `migration of legacy RECENT_ACTIVITY in visible cards`() =
        runTest {
            val legacyVisibleJson = gson.toJson(setOf("RECENT_ACTIVITY"))
            val legacyOrderJson = gson.toJson(listOf("RECENT_ACTIVITY"))
            context.financeSettingsDataStore.edit {
                it[stringPreferencesKey("dashboard_visible_cards")] = legacyVisibleJson
                it[stringPreferencesKey("dashboard_card_order")] = legacyOrderJson
            }

            repository.getDashboardVisibleCards().test {
                val visible = awaitItem()
                assertTrue(visible.contains(DashboardCardType.RECENT_TRANSACTIONS))
                cancelAndIgnoreRemainingEvents()
            }
        }
}
