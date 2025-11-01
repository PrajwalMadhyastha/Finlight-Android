// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/BackupWorkerTest.kt
// REASON: REFACTOR - This test suite is updated to reflect the new,
// consolidated logic of `BackupWorker`.
// - It now mocks `DataExportService` and `BackupManager`.
// - It verifies that `doWork` correctly calls `createBackupSnapshot`
//   and notifies the `BackupManager` on success.
// - It verifies that `dataChanged` is NOT called if snapshot creation returns
//   false, but the worker still succeeds and reschedules.
// =================================================================================
package io.pm.finlight.workers

import android.app.backup.BackupManager
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
import io.pm.finlight.data.DataExportService
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

        // Mock static objects and constructors
        mockkConstructor(SettingsRepository::class)
        mockkConstructor(BackupManager::class) // <-- NEW
        mockkObject(DataExportService)        // <-- NEW
        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)

        // Clear any previous interactions from other tests
        clearAllMocks()
    }

    @After
    override fun tearDown() {
        unmockkConstructor(SettingsRepository::class)
        unmockkConstructor(BackupManager::class) // <-- NEW
        unmockkObject(DataExportService)        // <-- NEW
        unmockkObject(NotificationHelper)
        unmockkObject(ReminderManager)
        super.tearDown()
    }

    @Test
    fun `doWork success with notification`() = runTest {
        // Arrange
        every { anyConstructed<SettingsRepository>().getAutoBackupNotificationEnabled() } returns flowOf(true)
        coEvery { ReminderManager.scheduleAutoBackup(any()) } returns Unit
        every { NotificationHelper.showAutoBackupNotification(any()) } just runs
        coEvery { DataExportService.createBackupSnapshot(context) } returns true
        every { anyConstructed<BackupManager>().dataChanged() } just runs

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
        verify(exactly = 1) { anyConstructed<BackupManager>().dataChanged() }
        verify(exactly = 1) { NotificationHelper.showAutoBackupNotification(context) }
        coVerify(exactly = 1) { ReminderManager.scheduleAutoBackup(context) }
    }

    @Test
    fun `doWork success without notification`() = runTest {
        // Arrange
        every { anyConstructed<SettingsRepository>().getAutoBackupNotificationEnabled() } returns flowOf(false)
        coEvery { ReminderManager.scheduleAutoBackup(any()) } returns Unit
        every { NotificationHelper.showAutoBackupNotification(any()) } just runs
        coEvery { DataExportService.createBackupSnapshot(context) } returns true
        every { anyConstructed<BackupManager>().dataChanged() } just runs

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
        verify(exactly = 1) { anyConstructed<BackupManager>().dataChanged() }
        verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(any()) }
        coVerify(exactly = 1) { ReminderManager.scheduleAutoBackup(context) }
    }

    @Test
    fun `doWork succeeds but does not notify manager if snapshot fails`() = runTest {
        // Arrange
        // This covers the case where snapshot creation returns false (but doesn't throw)
        every { anyConstructed<SettingsRepository>().getAutoBackupNotificationEnabled() } returns flowOf(false)
        coEvery { ReminderManager.scheduleAutoBackup(any()) } returns Unit
        coEvery { DataExportService.createBackupSnapshot(context) } returns false // <-- Snapshot fails

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result) // Worker itself succeeds
        coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
        verify(exactly = 0) { anyConstructed<BackupManager>().dataChanged() } // <-- Not called
        coVerify(exactly = 1) { ReminderManager.scheduleAutoBackup(context) } // Reschedule still happens
    }

    @Test
    fun `doWork returns retry on exception`() = runTest {
        // Arrange
        // Simulate a crash during snapshot creation
        coEvery { DataExportService.createBackupSnapshot(context) } throws RuntimeException("Test exception")

        val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        // Verify dependent services are NOT called on failure
        verify(exactly = 0) { anyConstructed<BackupManager>().dataChanged() }
        coVerify(exactly = 0) { ReminderManager.scheduleAutoBackup(any()) }
        verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(any()) }
    }
}
