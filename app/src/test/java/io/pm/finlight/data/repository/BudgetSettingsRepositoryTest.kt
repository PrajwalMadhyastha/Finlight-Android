package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.BudgetSettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = BudgetSettingsRepository(dataStore)
    }

    @Test
    fun `save and get overall budget for specific month`() =
        runTest {
            val year = 2026
            val month = 5
            val budget = 12000f

            assertNull(repository.getOverallBudgetForMonth(year, month).first())

            repository.saveOverallBudgetForMonth(year, month, budget)
            assertEquals(budget, repository.getOverallBudgetForMonth(year, month).first())
        }

    @Test
    fun `saveOverallBudgetForCurrentMonth saves to current year and month`() =
        runTest {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val budget = 15000f

            repository.saveOverallBudgetForCurrentMonth(budget)
            assertEquals(budget, repository.getOverallBudgetForMonth(year, month).first())
        }

    @Test
    fun `getOverallBudgetForMonth carries over budget from previous months within 12 months`() =
        runTest {
            // Set budget for Jan 2026
            repository.saveOverallBudgetForMonth(2026, 1, 10000f)

            // Query April 2026 (should carry over 10000f)
            assertEquals(10000f, repository.getOverallBudgetForMonth(2026, 4).first())

            // Query Dec 2026 (11 months later, should carry over)
            assertEquals(10000f, repository.getOverallBudgetForMonth(2026, 12).first())

            // Query Feb 2027 (13 months later, beyond 12 month lookback, returns null)
            assertNull(repository.getOverallBudgetForMonth(2027, 2).first())
        }

    @Test
    fun `getOverallBudgetsForYear returns only months with explicitly set budgets`() =
        runTest {
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
