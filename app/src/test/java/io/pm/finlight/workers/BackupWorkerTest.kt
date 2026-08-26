package io.pm.finlight.workers

import android.app.backup.BackupManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.*
import io.pm.finlight.BackupWorker
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.data.DataExportService
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    application = TestApplication::class,
)
class BackupWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Mock constructors
        mockkConstructor(BackupManager::class)

        // Mock singleton objects
        mockkObject(DataExportService)
        mockkObject(NotificationHelper)
        mockkObject(ReminderManager)

        // Clear any previous interactions from other tests
        clearAllMocks()

        coEvery { ReminderManager.scheduleAutoBackup(any()) } returns Unit
        every { NotificationHelper.showAutoBackupNotification(any(), any()) } just runs
        coEvery { DataExportService.createBackupSnapshot(context) } returns true
        every { anyConstructed<BackupManager>().dataChanged() } just runs
    }

    @After
    override fun tearDown() {
        unmockkConstructor(BackupManager::class)
        unmockkObject(DataExportService)
        unmockkObject(NotificationHelper)
        unmockkObject(ReminderManager)
        super.tearDown()
    }

    @Test
    fun `doWork success with notification`() =
        runTest {
            val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
            verify(exactly = 1) { anyConstructed<BackupManager>().dataChanged() }
            verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(context, any()) }
            coVerify(exactly = 0) { ReminderManager.scheduleAutoBackup(context) }
        }

    @Test
    fun `doWork success without notification`() =
        runTest {
            val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
            verify(exactly = 1) { anyConstructed<BackupManager>().dataChanged() }
            verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(any(), any()) }
            coVerify(exactly = 0) { ReminderManager.scheduleAutoBackup(context) }
        }

    @Test
    fun `doWork succeeds but does not notify manager if snapshot fails`() =
        runTest {
            coEvery { DataExportService.createBackupSnapshot(context) } returns false // Snapshot fails

            val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
            verify(exactly = 0) { anyConstructed<BackupManager>().dataChanged() }
            coVerify(exactly = 0) { ReminderManager.scheduleAutoBackup(context) }
        }

    @Test
    fun `doWork returns retry on exception`() =
        runTest {
            coEvery { DataExportService.createBackupSnapshot(context) } throws RuntimeException("Test exception")

            val worker = TestListenableWorkerBuilder<BackupWorker>(context).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(exactly = 0) { anyConstructed<BackupManager>().dataChanged() }
            coVerify(exactly = 0) { ReminderManager.scheduleAutoBackup(any()) }
            verify(exactly = 0) { NotificationHelper.showAutoBackupNotification(any(), any()) }
        }
}

