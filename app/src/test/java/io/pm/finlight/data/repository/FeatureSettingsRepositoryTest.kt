package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.FeatureSettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = FeatureSettingsRepository(dataStore)
    }

    @Test
    fun `save and get recurring transactions enabled`() =
        runTest {
            repository.getRecurringTransactionsEnabled().test {
                assertFalse(awaitItem()) // Default is false
                repository.saveRecurringTransactionsEnabled(true)
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get goal income threshold`() =
        runTest {
            repository.getGoalIncomeThreshold().test {
                assertEquals(5000, awaitItem()) // Default is 5000
                repository.saveGoalIncomeThreshold(25000)
                assertEquals(25000, awaitItem())
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

    @Test
    fun `constructor with context initializes properly`() {
        val repo = FeatureSettingsRepository(context)
        kotlin.test.assertNotNull(repo)
    }

    @Test
    fun `getters handle IOException by emitting defaults`() =
        runTest {
            val mockDataStore = io.mockk.mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
            io.mockk.every { mockDataStore.data } returns kotlinx.coroutines.flow.flow { throw java.io.IOException("Disk error") }
            val repo = FeatureSettingsRepository(mockDataStore)

            assertFalse(repo.getRecurringTransactionsEnabled().first())
            assertEquals(5000, repo.getGoalIncomeThreshold().first())
            assertTrue(repo.getGoalNudgesEnabled().first())
            assertEquals(emptySet<String>(), repo.getExcludedIncomeMonths().first())
            assertEquals(emptySet<String>(), repo.getExcludedExpenseMonths().first())
        }

    @Test
    fun `getters rethrow non-IOException exceptions`() =
        runTest {
            val mockDataStore = io.mockk.mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
            io.mockk.every { mockDataStore.data } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("Fatal error") }
            val repo = FeatureSettingsRepository(mockDataStore)

            kotlin.test.assertFailsWith<IllegalStateException> { repo.getRecurringTransactionsEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getGoalIncomeThreshold().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getGoalNudgesEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getExcludedIncomeMonths().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getExcludedExpenseMonths().first() }
        }
}
