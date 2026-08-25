package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.FeatureSettingsRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class FeatureSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: FeatureSettingsRepository
    private lateinit var prefs: SharedPreferences

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = FeatureSettingsRepository(context)
    }

    @Test
    fun `save and get recurring transactions enabled`() =
        runTest {
            repository.getRecurringTransactionsEnabled().test {
                assertFalse(awaitItem()) // Default
                repository.saveRecurringTransactionsEnabled(true)
                assertTrue(awaitItem())
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
                assertTrue(awaitItem()) // Default
                repository.saveGoalNudgesEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isGoalNudgesEnabledBlocking works`() {
        assertTrue(repository.isGoalNudgesEnabledBlocking())
        repository.saveGoalNudgesEnabled(false)
        assertFalse(repository.isGoalNudgesEnabledBlocking())
    }

    @Test
    fun `toggle and get excluded income months`() =
        runTest {
            repository.getExcludedIncomeMonths().test {
                assertEquals(emptySet(), awaitItem()) // Default
                repository.toggleIncomeMonthExclusion("2026-03")
                assertEquals(setOf("2026-03"), awaitItem())
                repository.toggleIncomeMonthExclusion("2026-03")
                assertEquals(emptySet(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggle and get excluded expense months`() =
        runTest {
            repository.getExcludedExpenseMonths().test {
                assertEquals(emptySet(), awaitItem()) // Default
                repository.toggleExpenseMonthExclusion("2026-04")
                assertEquals(setOf("2026-04"), awaitItem())
                repository.toggleExpenseMonthExclusion("2026-04")
                assertEquals(emptySet(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
