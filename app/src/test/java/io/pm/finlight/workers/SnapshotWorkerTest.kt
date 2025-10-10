package io.pm.finlight.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.data.DataExportService
import io.pm.finlight.utils.ReminderManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SnapshotWorkerTest : BaseViewModelTest() {

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

        // Mock static objects for each test
        mockkObject(DataExportService)
        mockkObject(ReminderManager)
    }

    @After
    override fun tearDown() {
        unmockkObject(DataExportService)
        unmockkObject(ReminderManager)
        super.tearDown()
    }

    @Test
    fun `doWork succeeds and calls service and reschedules`() = runTest {
        // Arrange
        coEvery { DataExportService.createBackupSnapshot(context) } returns true
        coEvery { ReminderManager.scheduleSnapshotWorker(context) } returns Unit

        val worker = TestListenableWorkerBuilder<SnapshotWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
        coVerify(exactly = 1) { ReminderManager.scheduleSnapshotWorker(context) }
    }

    @Test
    fun `doWork returns retry if DataExportService throws exception`() = runTest {
        // Arrange
        val exception = RuntimeException("Failed to create snapshot")
        coEvery { DataExportService.createBackupSnapshot(context) } throws exception
        coEvery { ReminderManager.scheduleSnapshotWorker(context) } returns Unit

        val worker = TestListenableWorkerBuilder<SnapshotWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        // Verify rescheduling is NOT called on failure, as WorkManager handles retries for this specific job.
        coVerify(exactly = 0) { ReminderManager.scheduleSnapshotWorker(context) }
    }

    @Test
    fun `doWork returns success even if snapshot creation fails`() = runTest {
        // Arrange
        // The service returns false, but doesn't throw an exception.
        // This is not a critical failure, so the worker should succeed and reschedule.
        coEvery { DataExportService.createBackupSnapshot(context) } returns false
        coEvery { ReminderManager.scheduleSnapshotWorker(context) } returns Unit

        val worker = TestListenableWorkerBuilder<SnapshotWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { DataExportService.createBackupSnapshot(context) }
        coVerify(exactly = 1) { ReminderManager.scheduleSnapshotWorker(context) }
    }
}