package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.NotificationSettingsRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var prefs: SharedPreferences

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = NotificationSettingsRepository(context)
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
    fun `isAutoCaptureNotificationEnabledBlocking works`() {
        assertTrue(repository.isAutoCaptureNotificationEnabledBlocking())
        repository.saveAutoCaptureNotificationEnabled(false)
        assertFalse(repository.isAutoCaptureNotificationEnabledBlocking())
    }

    @Test
    fun `save and get unknown transaction popup enabled`() =
        runTest {
            repository.getUnknownTransactionPopupEnabled().test {
                assertTrue(awaitItem()) // Default
                repository.saveUnknownTransactionPopupEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isUnknownTransactionPopupEnabledBlocking works`() {
        assertTrue(repository.isUnknownTransactionPopupEnabledBlocking())
        repository.saveUnknownTransactionPopupEnabled(false)
        assertFalse(repository.isUnknownTransactionPopupEnabledBlocking())
    }

    @Test
    fun `dismiss and check last month summary status`() {
        assertFalse(repository.hasLastMonthSummaryBeenDismissed())
        repository.setLastMonthSummaryDismissed()
        assertTrue(repository.hasLastMonthSummaryBeenDismissed())
    }
}
