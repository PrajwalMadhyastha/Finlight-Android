package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.NotificationSettingsRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class NotificationSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: NotificationSettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = NotificationSettingsRepository(dataStore)
    }

    @Test
    fun `save and get daily report enabled`() =
        runTest {
            repository.getDailyReportEnabled().test {
                assertTrue(awaitItem()) // Default
                repository.saveDailyReportEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get daily report time`() =
        runTest {
            repository.getDailyReportTime().test {
                assertEquals(Pair(23, 0), awaitItem()) // Default 23:00
                repository.saveDailyReportTime(20, 30)
                assertEquals(Pair(20, 30), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get weekly summary enabled`() =
        runTest {
            repository.getWeeklySummaryEnabled().test {
                assertTrue(awaitItem()) // Default
                repository.saveWeeklySummaryEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get weekly report time`() =
        runTest {
            repository.getWeeklyReportTime().test {
                assertEquals(Triple(Calendar.SUNDAY, 9, 0), awaitItem()) // Default
                repository.saveWeeklyReportTime(Calendar.MONDAY, 10, 15)
                assertEquals(Triple(Calendar.MONDAY, 10, 15), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get monthly summary enabled`() =
        runTest {
            repository.getMonthlySummaryEnabled().test {
                assertTrue(awaitItem()) // Default
                repository.saveMonthlySummaryEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get monthly report time`() =
        runTest {
            repository.getMonthlyReportTime().test {
                assertEquals(Triple(1, 9, 0), awaitItem()) // Default 1st of month at 9:00
                repository.saveMonthlyReportTime(5, 8, 45)
                assertEquals(Triple(5, 8, 45), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get auto capture notification enabled`() =
        runTest {
            repository.getAutoCaptureNotificationEnabled().test {
                assertTrue(awaitItem()) // Default
                repository.saveAutoCaptureNotificationEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismiss and check last month summary status`() =
        runTest {
            repository.hasLastMonthSummaryBeenDismissed().test {
                assertFalse(awaitItem())
                repository.setLastMonthSummaryDismissed()
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `constructor with context initializes properly`() {
        val repo = NotificationSettingsRepository(context)
        kotlin.test.assertNotNull(repo)
    }

    @Test
    fun `getters handle IOException by emitting defaults`() =
        runTest {
            val mockDataStore = io.mockk.mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
            io.mockk.every { mockDataStore.data } returns kotlinx.coroutines.flow.flow { throw java.io.IOException("Disk error") }
            val repo = NotificationSettingsRepository(mockDataStore)

            assertTrue(repo.getDailyReportEnabled().first())
            assertEquals(Pair(23, 0), repo.getDailyReportTime().first())
            assertTrue(repo.getWeeklySummaryEnabled().first())
            assertEquals(Triple(java.util.Calendar.SUNDAY, 9, 0), repo.getWeeklyReportTime().first())
            assertTrue(repo.getMonthlySummaryEnabled().first())
            assertEquals(Triple(1, 9, 0), repo.getMonthlyReportTime().first())
            assertTrue(repo.getAutoCaptureNotificationEnabled().first())
            assertTrue(repo.getUnknownTransactionPopupEnabled().first())
            assertFalse(repo.hasLastMonthSummaryBeenDismissed().first())
        }

    @Test
    fun `getters rethrow non-IOException exceptions`() =
        runTest {
            val mockDataStore = io.mockk.mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
            io.mockk.every { mockDataStore.data } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("Fatal error") }
            val repo = NotificationSettingsRepository(mockDataStore)

            kotlin.test.assertFailsWith<IllegalStateException> { repo.getDailyReportEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getDailyReportTime().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getWeeklySummaryEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getWeeklyReportTime().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getMonthlySummaryEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getMonthlyReportTime().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getAutoCaptureNotificationEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getUnknownTransactionPopupEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.hasLastMonthSummaryBeenDismissed().first() }
        }
}
