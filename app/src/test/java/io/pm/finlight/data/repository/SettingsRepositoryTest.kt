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
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: SettingsRepository
    private val gson = Gson()

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        val internalDataStore = context.internalSettingsDataStore

        runTest {
            dataStore.edit { it.clear() }
            internalDataStore.edit { it.clear() }
        }

        repository = SettingsRepository(context)
    }

    @Test
    fun `save and get user name`() =
        runTest {
            val testName = "Jane Doe"
            repository.getUserName().test {
                assertEquals("User", awaitItem()) // Default value
                repository.saveUserName(testName)
                assertEquals(testName, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get profile picture uri`() =
        runTest {
            val testUri = "content://pictures/1"
            repository.getProfilePictureUri().test {
                assertNull(awaitItem()) // Initial state is null
                repository.saveProfilePictureUri(testUri)
                assertEquals(testUri, awaitItem())
                repository.saveProfilePictureUri(null) // Test clearing
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get app lock enabled`() =
        runTest {
            repository.getAppLockEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.saveAppLockEnabled(true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isAppLockEnabledBlocking works`() =
        runTest {
            assertEquals(false, repository.isAppLockEnabledBlocking())
            repository.saveAppLockEnabled(true)
            assertEquals(true, repository.isAppLockEnabledBlocking())
        }

    @Test
    fun `save and get home currency`() =
        runTest {
            val testCurrency = "USD"
            repository.getHomeCurrency().test {
                assertEquals("INR", awaitItem()) // Default
                repository.saveHomeCurrency(testCurrency)
                assertEquals(testCurrency, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get travel mode settings`() =
        runTest {
            val futureEndDate = System.currentTimeMillis() + 100000L
            val settings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 1L, futureEndDate, "USD", 83.5f)

            repository.getTravelModeSettings().test {
                assertNull(awaitItem()) // Initial
                repository.saveTravelModeSettings(settings)
                assertEquals(settings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getTravelModeSettings auto-expires past trips`() =
        runTest {
            val pastEndDate = 0L
            val expiredSettings = TravelModeSettings(true, "Old Trip", TripType.DOMESTIC, 1L, pastEndDate, null, null)

            val prefKey = stringPreferencesKey("travel_mode_settings")
            context.financeSettingsDataStore.edit {
                it[prefKey] = gson.toJson(expiredSettings)
            }

            repository.getTravelModeSettings().test {
                val item = awaitItem()
                assertEquals(null, item, "Item was $item")
                cancelAndIgnoreRemainingEvents()
            }

            // Verify it was actually removed from prefs
            assertNull(context.financeSettingsDataStore.data.first()[prefKey])
        }

    @Test
    fun `save and get selected theme`() =
        runTest {
            val theme = AppTheme.AURORA

            repository.getSelectedTheme().test {
                assertEquals(AppTheme.SYSTEM_DEFAULT, awaitItem()) // Initial
                repository.saveSelectedTheme(theme)
                assertEquals(theme, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getDashboardCardOrder_emitsDefaultOrder`() =
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
    fun `getDashboardVisibleCards_emitsDefaultSet`() =
        runTest {
            repository.getDashboardVisibleCards().test {
                val defaultVisible = awaitItem()
                assertTrue(defaultVisible.isNotEmpty())
                assertEquals(DashboardCardType.entries.toSet(), defaultVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saveDashboardLayout_updatesOrderAndVisibility`() =
        runTest {
            val newOrder = listOf(DashboardCardType.RECENT_TRANSACTIONS, DashboardCardType.HERO_BUDGET)
            val newVisible = setOf(DashboardCardType.RECENT_TRANSACTIONS)

            // Act
            repository.saveDashboardLayout(newOrder, newVisible)

            // Assert Order
            repository.getDashboardCardOrder().test {
                val missingCards = DashboardCardType.entries.filter { it !in newOrder }
                assertEquals(newOrder + missingCards, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // Assert Visibility
            repository.getDashboardVisibleCards().test {
                val missingCards = DashboardCardType.entries.filter { it !in newOrder }.toSet()
                assertEquals(newVisible + missingCards, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadCardOrder_appendsNewFeatures`() =
        runTest {
            val oldOrder = listOf(DashboardCardType.HERO_BUDGET)
            repository.saveDashboardLayout(oldOrder, setOf(DashboardCardType.HERO_BUDGET))

            repository.getDashboardCardOrder().test {
                val missingCards = DashboardCardType.entries.filter { it !in oldOrder }
                assertEquals(oldOrder + missingCards, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadVisibleCards_makesNewFeaturesVisible`() =
        runTest {
            val oldOrder = listOf(DashboardCardType.HERO_BUDGET)
            val oldVisible = setOf(DashboardCardType.HERO_BUDGET)
            repository.saveDashboardLayout(oldOrder, oldVisible)

            repository.getDashboardVisibleCards().test {
                val missingCards = DashboardCardType.entries.filter { it !in oldOrder }.toSet()
                assertEquals(oldVisible + missingCards, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get auto backup enabled`() =
        runTest {
            repository.getAutoBackupEnabled().test {
                assertEquals(true, awaitItem()) // Default
                repository.saveAutoBackupEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get auto backup notification enabled`() =
        runTest {
            repository.getAutoBackupNotificationEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.saveAutoBackupNotificationEnabled(true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get daily report enabled`() =
        runTest {
            repository.getDailyReportEnabled().test {
                assertEquals(true, awaitItem()) // Default
                repository.saveDailyReportEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get daily report time`() =
        runTest {
            repository.getDailyReportTime().test {
                assertEquals(Pair(23, 0), awaitItem()) // Default
                repository.saveDailyReportTime(8, 30)
                assertEquals(Pair(8, 30), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get weekly summary enabled`() =
        runTest {
            repository.getWeeklySummaryEnabled().test {
                assertEquals(true, awaitItem()) // Default
                repository.saveWeeklySummaryEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get weekly report time`() =
        runTest {
            repository.getWeeklyReportTime().test {
                assertEquals(Triple(Calendar.SUNDAY, 9, 0), awaitItem()) // Default
                repository.saveWeeklyReportTime(Calendar.TUESDAY, 10, 0)
                assertEquals(Triple(Calendar.TUESDAY, 10, 0), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get monthly summary enabled`() =
        runTest {
            repository.getMonthlySummaryEnabled().test {
                assertEquals(true, awaitItem()) // Default
                repository.saveMonthlySummaryEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get monthly report time`() =
        runTest {
            repository.getMonthlyReportTime().test {
                assertEquals(Triple(1, 9, 0), awaitItem()) // Default
                repository.saveMonthlyReportTime(15, 12, 0)
                assertEquals(Triple(15, 12, 0), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get popup setting`() =
        runTest {
            repository.getUnknownTransactionPopupEnabled().test {
                assertEquals(true, awaitItem()) // Default
                repository.saveUnknownTransactionPopupEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            // Test blocking getter
            assertEquals(false, repository.isUnknownTransactionPopupEnabledBlocking())
        }

    @Test
    fun `save and get privacy mode`() =
        runTest {
            repository.getPrivacyModeEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.savePrivacyModeEnabled(true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get simulator privacy mode`() =
        runTest {
            repository.getSimulatorPrivacyModeEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.saveSimulatorPrivacyModeEnabled(true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get last backup timestamp`() =
        runTest {
            val timestamp = System.currentTimeMillis()
            repository.getLastBackupTimestamp().test {
                assertEquals(0L, awaitItem()) // Default
                repository.saveLastBackupTimestamp(timestamp)
                assertEquals(timestamp, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `first launch flag is managed correctly in internal prefs`() =
        runTest {
            // Initial state
            assertFalse(repository.isFirstLaunchCompleteBlocking())

            // Set the flag
            repository.setFirstLaunchComplete()

            // Verify
            assertTrue(repository.isFirstLaunchCompleteBlocking())
        }

    @Test
    fun `last month summary dismissal is stored and checked correctly`() =
        runTest {
            assertFalse(repository.hasLastMonthSummaryBeenDismissed(), "Should not be dismissed initially")

            repository.setLastMonthSummaryDismissed()

            assertTrue(repository.hasLastMonthSummaryBeenDismissed(), "Should be dismissed after setting")
        }

    // --- Tests for Budget Carry-over Logic ---

    @Test
    fun `getOverallBudgetForMonth returns null when no budget is set`() =
        runTest {
            repository.getOverallBudgetForMonth(2025, 10).test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(repository.getOverallBudgetForMonthBlocking(2025, 10))
        }

    @Test
    fun `getOverallBudgetForMonth returns current month budget if set`() =
        runTest {
            val budget = 50000f
            repository.saveOverallBudgetForMonth(2025, 10, budget)

            repository.getOverallBudgetForMonth(2025, 10).test {
                assertEquals(budget, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(budget, repository.getOverallBudgetForMonthBlocking(2025, 10))
        }

    @Test
    fun `getOverallBudgetForMonth carries over budget from previous month`() =
        runTest {
            val budget = 40000f
            // Budget set for September 2025
            repository.saveOverallBudgetForMonth(2025, 9, budget)

            // Asking for October 2025
            repository.getOverallBudgetForMonth(2025, 10).test {
                assertEquals(budget, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(budget, repository.getOverallBudgetForMonthBlocking(2025, 10))
        }

    @Test
    fun `getOverallBudgetForMonth carries over from several months ago`() =
        runTest {
            val budget = 30000f
            // Budget set for June 2025
            repository.saveOverallBudgetForMonth(2025, 6, budget)

            // Asking for October 2025
            repository.getOverallBudgetForMonth(2025, 10).test {
                assertEquals(budget, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(budget, repository.getOverallBudgetForMonthBlocking(2025, 10))
        }

    @Test
    fun `getOverallBudgetForMonth prefers current month budget over carry-over`() =
        runTest {
            val oldBudget = 30000f
            val currentBudget = 60000f
            // Budget set for June 2025
            repository.saveOverallBudgetForMonth(2025, 6, oldBudget)
            // Budget also set for October 2025
            repository.saveOverallBudgetForMonth(2025, 10, currentBudget)

            // Asking for October 2025
            repository.getOverallBudgetForMonth(2025, 10).test {
                assertEquals(currentBudget, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(currentBudget, repository.getOverallBudgetForMonthBlocking(2025, 10))
        }

    @Test
    fun `getOverallBudgetsForYear returns list of Pair of month and budget`() =
        runTest {
            val year = 2026
            repository.saveOverallBudgetForMonth(year, 1, 1000f)
            repository.saveOverallBudgetForMonth(year, 3, 3000f)
            repository.saveOverallBudgetForMonth(year, 12, 12000f)
            repository.saveOverallBudgetForMonth(2025, 1, 500f) // Should be ignored

            val budgets = repository.getOverallBudgetsForYear(year)
            assertEquals(3, budgets.size)
            assertEquals(1000f, budgets[1])
            assertEquals(3000f, budgets[3])
            assertEquals(12000f, budgets[12])
        }

    @Test
    fun `save and get recurring transactions enabled`() =
        runTest {
            repository.getRecurringTransactionsEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.saveRecurringTransactionsEnabled(true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get goal income threshold`() =
        runTest {
            repository.getGoalIncomeThreshold().test {
                assertEquals(5000, awaitItem()) // Default
                repository.saveGoalIncomeThreshold(10000)
                assertEquals(10000, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get goal nudges enabled`() =
        runTest {
            repository.getGoalNudgesEnabled().test {
                assertEquals(true, awaitItem()) // Default
                repository.saveGoalNudgesEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isGoalNudgesEnabledBlocking works correctly`() =
        runTest {
            assertTrue(repository.isGoalNudgesEnabledBlocking()) // Default
            repository.saveGoalNudgesEnabled(false)
            assertFalse(repository.isGoalNudgesEnabledBlocking())
        }

    @Test
    fun `toggle and get excluded income months`() =
        runTest {
            repository.getExcludedIncomeMonths().test {
                assertEquals(emptySet(), awaitItem())

                repository.toggleIncomeMonthExclusion("2026_01")
                assertEquals(setOf("2026_01"), awaitItem())

                repository.toggleIncomeMonthExclusion("2026_02")
                assertEquals(setOf("2026_01", "2026_02"), awaitItem())

                repository.toggleIncomeMonthExclusion("2026_01")
                assertEquals(setOf("2026_02"), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggle and get excluded expense months`() =
        runTest {
            repository.getExcludedExpenseMonths().test {
                assertEquals(emptySet(), awaitItem())

                repository.toggleExpenseMonthExclusion("2026_03")
                assertEquals(setOf("2026_03"), awaitItem())

                repository.toggleExpenseMonthExclusion("2026_03")
                assertEquals(emptySet(), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get ignore rules checksum`() =
        runTest {
            assertEquals(0, repository.getIgnoreRulesChecksum()) // Default
            repository.saveIgnoreRulesChecksum(12345)
            assertEquals(12345, repository.getIgnoreRulesChecksum())
        }
}
