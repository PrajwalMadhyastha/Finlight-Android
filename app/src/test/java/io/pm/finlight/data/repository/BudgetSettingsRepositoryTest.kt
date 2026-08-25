package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.BudgetSettingsRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BudgetSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: BudgetSettingsRepository
    private lateinit var prefs: SharedPreferences

    private fun getBudgetKey(
        year: Int,
        month: Int,
    ): String {
        return "overall_budget_${year}_${String.format("%02d", month)}"
    }

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = BudgetSettingsRepository(context)
    }

    @Test
    fun `save and get overall budget for specific month blocking`() {
        val year = 2026
        val month = 5
        val budget = 12000f

        assertNull(repository.getOverallBudgetForMonthBlocking(year, month))

        repository.saveOverallBudgetForMonth(year, month, budget)
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(year, month))
    }

    @Test
    fun `saveOverallBudgetForCurrentMonth saves to current year and month`() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val budget = 15000f

        repository.saveOverallBudgetForCurrentMonth(budget)
        assertEquals(budget, repository.getOverallBudgetForMonthBlocking(year, month))
    }

    @Test
    fun `getOverallBudgetForMonthBlocking carries over budget from previous months within 12 months`() {
        // Set budget for Jan 2026
        repository.saveOverallBudgetForMonth(2026, 1, 10000f)

        // Query April 2026 (should carry over 10000f)
        assertEquals(10000f, repository.getOverallBudgetForMonthBlocking(2026, 4))

        // Query Dec 2026 (11 months later, should carry over)
        assertEquals(10000f, repository.getOverallBudgetForMonthBlocking(2026, 12))

        // Query Feb 2027 (13 months later, beyond 12 month lookback, returns null)
        assertNull(repository.getOverallBudgetForMonthBlocking(2027, 2))
    }

    @Test
    fun `getOverallBudgetsForYear returns only months with explicitly set budgets`() {
        repository.saveOverallBudgetForMonth(2026, 3, 20000f)
        repository.saveOverallBudgetForMonth(2026, 7, 25000f)

        val yearBudgets = repository.getOverallBudgetsForYear(2026)
        assertEquals(2, yearBudgets.size)
        assertEquals(20000f, yearBudgets[3])
        assertEquals(25000f, yearBudgets[7])
        assertNull(yearBudgets[1])
    }

    @Test
    fun `getOverallBudgetForMonth flow emits carry-over and reacts to changes`() =
        runTest {
            repository.getOverallBudgetForMonth(2026, 6).test {
                assertNull(awaitItem()) // Initially null

                // Save budget for earlier month (March 2026)
                repository.saveOverallBudgetForMonth(2026, 3, 30000f)
                assertEquals(30000f, awaitItem())

                // Save direct budget for June 2026
                repository.saveOverallBudgetForMonth(2026, 6, 35000f)
                assertEquals(35000f, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
