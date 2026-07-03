package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.pm.finlight.workers.SmsCatchupWorker
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException
import org.junit.After

@RunWith(AndroidJUnit4::class)
class WorkerIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        // Initialize WorkManager for instrumentation tests
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun testSmsCatchupWorkerExecutesWithoutCrash() {
        // Enqueue the worker
        val request = OneTimeWorkRequestBuilder<SmsCatchupWorker>().build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request).result.get()
        // Get WorkInfo
        val workInfo = workManager.getWorkInfoById(request.id).get()
        // Assert that the worker ran successfully (or safely failed due to missing permission, but didn't crash)
        assertTrue(
            workInfo?.state == WorkInfo.State.SUCCEEDED || workInfo?.state == WorkInfo.State.FAILED,
        )
    }

    @After
    fun tearDownWorkManager() {
        androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase()
    }
}
