package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.DashboardCardType
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.ui.theme.AppTheme
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsRepositoryTest : BaseViewModelTest() {

    private lateinit var context: Application
    private lateinit var repository: SettingsRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var internalPrefs: SharedPreferences
    private val gson = Gson()

    // Helper to get the key format used by the repository
    private fun getBudgetKey(year: Int, month: Int): String {
        return "overall_budget_${year}_${String.format("%02d", month)}"
    }

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        // Use the real SharedPreferences provided by Robolectric's context
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        internalPrefs = context.getSharedPreferences("finlight_internal_state", Context.MODE_PRIVATE)

        // Clear prefs before each test to ensure a clean state
        prefs.edit().clear().commit()
        internalPrefs.edit().clear().commit()

        repository = SettingsRepository(context)
    }

    @Test
    fun `save and get user name`() = runTest {
        val testName = "Jane Doe"
        repository.getUserName().test {
            assertEquals("User", awaitItem()) // Default value
            repository.saveUserName(testName)
            assertEquals(testName, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get profile picture uri`() = runTest {
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
    fun `save and get app lock enabled`() = runTest {
        repository.getAppLockEnabled().test {
            assertEquals(false, awaitItem()) // Default
            repository.saveAppLockEnabled(true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isAppLockEnabledBlocking works`() {
        assertEquals(false, repository.isAppLockEnabledBlocking())
        repository.saveAppLockEnabled(true)
        assertEquals(true, repository.isAppLockEnabledBlocking())
    }

    @Test
    fun `save and get home currency`() = runTest {
        val testCurrency = "USD"
        repository.getHomeCurrency().test {
            assertEquals("INR", awaitItem()) // Default
            repository.saveHomeCurrency(testCurrency)
            assertEquals(testCurrency, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get travel mode settings`() = runTest {
        val settings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 1L, 2L, "USD", 83.5f)

        repository.getTravelModeSettings().test {
            assertNull(awaitItem()) // Initial
            repository.saveTravelModeSettings(settings)
            assertEquals(settings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get selected theme`() = runTest {
        val theme = AppTheme.AURORA

        repository.getSelectedTheme().test {
            assertEquals(AppTheme.SYSTEM_DEFAULT, awaitItem()) // Initial
            repository.saveSelectedTheme(theme)
            assertEquals(theme, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getDashboardCardOrder_emitsDefaultOrder`() = runTest {
        repository.getDashboardCardOrder().test {
            // Check default
            val defaultOrder = awaitItem()
            assertTrue(defaultOrder.isNotEmpty())
            assertEquals(DashboardCardType.HERO_BUDGET, defaultOrder.first())
            // --- FIX: Correct the order to match the implementation's default ---
            assertEquals(
                listOf(
                    DashboardCardType.HERO_BUDGET,
                    DashboardCardType.QUICK_ACTIONS,
                    DashboardCardType.RECENT_TRANSACTIONS,
                    DashboardCardType.SPENDING_CONSISTENCY,
                    DashboardCardType.BUDGET_WATCH,
                    DashboardCardType.ACCOUNTS_CAROUSEL
                ), defaultOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getDashboardVisibleCards_emitsDefaultSet`() = runTest {
        repository.getDashboardVisibleCards().test {
            // Check default
            val defaultVisible = awaitItem()
            assertTrue(defaultVisible.isNotEmpty())
            assertEquals(DashboardCardType.entries.toSet(), defaultVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveDashboardLayout_updatesOrderAndVisibility`() = runTest {
        val newOrder = listOf(DashboardCardType.RECENT_TRANSACTIONS, DashboardCardType.HERO_BUDGET)
        val newVisible = setOf(DashboardCardType.RECENT_TRANSACTIONS)

        // Act
        repository.saveDashboardLayout(newOrder, newVisible)

        // Assert Order
        repository.getDashboardCardOrder().test {
            assertEquals(newOrder, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Assert Visibility
        repository.getDashboardVisibleCards().test {
            assertEquals(newVisible, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get auto backup enabled`() = runTest {
        repository.getAutoBackupEnabled().test {
            assertEquals(true, awaitItem()) // Default
            repository.saveAutoBackupEnabled(false)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get auto backup notification enabled`() = runTest {
        repository.getAutoBackupNotificationEnabled().test {
            assertEquals(false, awaitItem()) // Default
            repository.saveAutoBackupNotificationEnabled(true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get daily report enabled`() = runTest {
        repository.getDailyReportEnabled().test {
            assertEquals(true, awaitItem()) // Default
            repository.saveDailyReportEnabled(false)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get daily report time`() = runTest {
        repository.getDailyReportTime().test {
            assertEquals(Pair(23, 0), awaitItem()) // Default
            repository.saveDailyReportTime(8, 30)
            assertEquals(Pair(8, 30), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get weekly summary enabled`() = runTest {
        repository.getWeeklySummaryEnabled().test {
            assertEquals(true, awaitItem()) // Default
            repository.saveWeeklySummaryEnabled(false)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get weekly report time`() = runTest {
        repository.getWeeklyReportTime().test {
            assertEquals(Triple(Calendar.SUNDAY, 9, 0), awaitItem()) // Default
            repository.saveWeeklyReportTime(Calendar.TUESDAY, 10, 0)
            assertEquals(Triple(Calendar.TUESDAY, 10, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get monthly summary enabled`() = runTest {
        repository.getMonthlySummaryEnabled().test {
            assertEquals(true, awaitItem()) // Default
            repository.saveMonthlySummaryEnabled(false)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get monthly report time`() = runTest {
        repository.getMonthlyReportTime().test {
            assertEquals(Triple(1, 9, 0), awaitItem()) // Default
            repository.saveMonthlyReportTime(15, 12, 0)
            assertEquals(Triple(15, 12, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get popup setting`() = runTest {
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
    fun `save and get privacy mode`() = runTest {
        repository.getPrivacyModeEnabled().test {
            assertEquals(false, awaitItem()) // Default
            repository.savePrivacyModeEnabled(true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get last backup timestamp`() = runTest {
        val timestamp = System.currentTimeMillis()
        repository.getLastBackupTimestamp().test {
            assertEquals(0L, awaitItem()) // Default
            repository.saveLastBackupTimestamp(timestamp)
            assertEquals(timestamp, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first launch flag is managed correctly in internal prefs`() {
        // Initial state
        assertFalse(repository.isFirstLaunchCompleteBlocking())

        // Set the flag
        repository.setFirstLaunchComplete()

        // Verify
        assertTrue(repository.isFirstLaunchCompleteBlocking())
        assertTrue(internalPrefs.contains("is_first_launch_complete"))
        assertFalse("Main prefs should not contain the internal flag", prefs.contains("is_first_launch_complete"))
    }

    @Test
    fun `last month summary dismissal is stored and checked correctly`() {
        assertFalse("Should not be dismissed initially", repository.hasLastMonthSummaryBeenDismissed())

        repository.setLastMonthSummaryDismissed()

        assertTrue("Should be dismissed after setting", repository.hasLastMonthSummaryBeenDismissed())
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        assertTrue(prefs.contains("last_month_summary_dismissed_$monthKey"))
    }

    // --- Tests for Budget Carry-over Logic ---

    @Test
    fun `getOverallBudgetForMonth returns null when no budget is set`() = runTest {
        repository.getOverallBudgetForMonth(2025, 10).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(repository.getOverallBudgetForMonthBlocking(2025, 10))
    }

    @Test
    fun `getOverallBudgetForMonth returns current month budget if set`() = runTest {
        val budget = 50000f
        prefs.edit().putFloat(getBudgetKey(2025, 10), budget).commit()

        repository.getOverallBudgetForMonth(2025, 10).test {
            assertEquals(budget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(2025, 10))
    }

    @Test
    fun `getOverallBudgetForMonth carries over budget from previous month`() = runTest {
        val budget = 40000f
        // Budget set for September 2025
        prefs.edit().putFloat(getBudgetKey(2025, 9), budget).commit()

        // Asking for October 2025
        repository.getOverallBudgetForMonth(2025, 10).test {
            assertEquals(budget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(2025, 10))
    }

    @Test
    fun `getOverallBudgetForMonth carries over from several months ago`() = runTest {
        val budget = 30000f
        // Budget set for June 2025
        prefs.edit().putFloat(getBudgetKey(2025, 6), budget).commit()

        // Asking for October 2025
        repository.getOverallBudgetForMonth(2025, 10).test {
            assertEquals(budget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(2025, 10))
    }

    @Test
    fun `getOverallBudgetForMonth prefers current month budget over carry-over`() = runTest {
        val oldBudget = 30000f
        val currentBudget = 60000f
        // Budget set for June 2025
        prefs.edit().putFloat(getBudgetKey(2025, 6), oldBudget).commit()
        // Budget also set for October 2025
        prefs.edit().putFloat(getBudgetKey(2025, 10), currentBudget).commit()

        // Asking for October 2025
        repository.getOverallBudgetForMonth(2025, 10).test {
            assertEquals(currentBudget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(currentBudget, repository.getOverallBudgetForMonthBlocking(2025, 10))
    }
}

