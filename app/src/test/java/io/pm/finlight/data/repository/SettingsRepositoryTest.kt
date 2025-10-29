package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
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

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        // Use the real SharedPreferences provided by Robolectric
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        internalPrefs = context.getSharedPreferences("finlight_internal_state", Context.MODE_PRIVATE)

        // Clear prefs before each test
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
            assertNull(awaitItem()) // initial state is null
            repository.saveProfilePictureUri(testUri)
            assertEquals(testUri, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save and get overall budget for current month`() = runTest {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val budget = 50000.5f

        repository.saveOverallBudgetForCurrentMonth(budget)

        repository.getOverallBudgetForMonth(year, month).test {
            assertEquals(budget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(year, month))
    }

    // --- NEW TEST ---
    @Test
    fun `saveOverallBudgetForMonth saves to correct dynamic key`() = runTest {
        val year = 2024
        val month = 5 // May
        val budget = 12345f
        val expectedKey = "overall_budget_2024_05"

        // Act
        repository.saveOverallBudgetForMonth(year, month, budget)

        // Assert
        assertTrue(prefs.contains(expectedKey))
        assertEquals(budget, prefs.getFloat(expectedKey, 0f))
    }
    // --- END NEW TEST ---

    @Test
    fun `getOverallBudgetForMonth carries over from previous month`() = runTest {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1) // Previous month
        val prevYear = cal.get(Calendar.YEAR)
        val prevMonth = cal.get(Calendar.MONTH) + 1
        val budget = 30000f

        prefs.edit().putFloat("overall_budget_${prevYear}_${String.format("%02d", prevMonth)}", budget).commit()

        val currentCal = Calendar.getInstance()
        val currentYear = currentCal.get(Calendar.YEAR)
        val currentMonth = currentCal.get(Calendar.MONTH) + 1

        repository.getOverallBudgetForMonth(currentYear, currentMonth).test {
            assertEquals(budget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(currentYear, currentMonth))
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
    fun `ignore rules checksum is saved and retrieved correctly`() {
        val checksum = 123456789
        repository.saveIgnoreRulesChecksum(checksum)
        assertEquals(checksum, repository.getIgnoreRulesChecksum())
    }

    @Test
    fun `dismissed merge suggestions are added and retrieved correctly`() = runTest {
        val key1 = "1|2"
        val key2 = "3|4"

        repository.getDismissedMergeSuggestions().test {
            assertTrue(awaitItem().isEmpty()) // Initial state

            repository.addDismissedMergeSuggestion(key1)
            assertEquals(setOf(key1), awaitItem())

            repository.addDismissedMergeSuggestion(key2)
            assertEquals(setOf(key1, key2), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `last month summary dismissal is stored and checked correctly`() {
        assertFalse("Should not be dismissed initially", repository.hasLastMonthSummaryBeenDismissed())

        repository.setLastMonthSummaryDismissed()

        assertTrue("Should be dismissed after setting", repository.hasLastMonthSummaryBeenDismissed())
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        assertTrue(prefs.contains("last_month_summary_dismissed_$monthKey"))
    }

    @Test
    fun `save and get various notification settings`() = runTest {
        // Daily Report
        repository.getDailyReportEnabled().test {
            assertTrue(awaitItem()) // Default
            repository.saveDailyReportEnabled(false)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repository.getDailyReportTime().test {
            assertEquals(Pair(23, 0), awaitItem()) // Default
            repository.saveDailyReportTime(8, 30)
            assertEquals(Pair(8, 30), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Weekly Summary
        repository.getWeeklySummaryEnabled().test {
            assertTrue(awaitItem()) // Default
            repository.saveWeeklySummaryEnabled(false)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repository.getWeeklyReportTime().test {
            assertEquals(Triple(Calendar.SUNDAY, 9, 0), awaitItem()) // Default
            repository.saveWeeklyReportTime(Calendar.TUESDAY, 10, 0)
            assertEquals(Triple(Calendar.TUESDAY, 10, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Monthly Summary
        repository.getMonthlySummaryEnabled().test {
            assertTrue(awaitItem()) // Default
            repository.saveMonthlySummaryEnabled(false)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repository.getMonthlyReportTime().test {
            assertEquals(Triple(1, 9, 0), awaitItem()) // Default
            repository.saveMonthlyReportTime(15, 12, 0)
            assertEquals(Triple(15, 12, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
