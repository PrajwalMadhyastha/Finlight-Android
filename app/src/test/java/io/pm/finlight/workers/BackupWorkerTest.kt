package io.pm.finlight.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.pm.finlight.BackupWorker
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BackupWorkerTest : BaseViewModelTest() {

    private lateinit var context: Context

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        mockkConstructor(SettingsRepository::class)
        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)

        // Clear any previous interactions from other tests
        clearAllMocks()
    }

    @After
    override fun tearDown() {
        unmockkConstructor(SettingsRepository::class)
        unmockkObject(NotificationHelper)
        unmockkObject(ReminderManager)
        super.tearDown()
    }

    @Test
    fun `doWork succeeds and shows notification when enabled`() = runTest {
        // Arrange
        every { anyConstructed<SettingsRepository>().getAutoBackupNotificationEnabled() } returns flowOf(true)
        coEvery { ReminderManager.scheduleAutoBackup(any()) } returns Unit
        every { NotificationHelper.showAutoBackupNotification(any()) } just runs

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { ReminderManager.scheduleAutoBackup(context) }
        verify(exactly = 1) { NotificationHelper.showAutoBackupNotification(context) }
    }

    @Test
    fun `doWork succeeds and does not show notification when disabled`() = runTest {
        // Arrange
        every { anyConstructed<SettingsRepository>().getAutoBackupNotificationEnabled() } returns flowOf(false)
        coEvery { ReminderManager.scheduleAutoBackup(any()) } returns Unit
        every { NotificationHelper.showAutoBackupNotification(any()) } just runs

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { ReminderManager.scheduleAutoBackup(context) }
        verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(any()) }
    }

    @Test
    fun `doWork returns retry on failure`() = runTest {
        // Arrange
        every { anyConstructed<SettingsRepository>().getAutoBackupNotificationEnabled() } throws RuntimeException("Test exception")
        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { ReminderManager.scheduleAutoBackup(any()) }
        verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(any()) }
    }
}