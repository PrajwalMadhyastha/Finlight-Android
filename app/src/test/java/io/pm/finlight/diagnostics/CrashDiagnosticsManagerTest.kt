package io.pm.finlight.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CrashDiagnosticsManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashDiagnosticsManager.clearCrash(context)
    }

    @After
    fun tearDown() {
        CrashDiagnosticsManager.clearCrash(context)
    }

    @Test
    fun noCrashRecorded_initiallyReturnsFalseAndNull() {
        assertFalse(CrashDiagnosticsManager.hasRecordedCrash(context))
        assertNull(CrashDiagnosticsManager.getRecordedCrash(context))
    }

    @Test
    fun recordCrash_writesReportAndSanitizesPii() {
        val thread = Thread("test-worker-thread")
        val error = IllegalStateException("Corrupted account 9876543210 with balance ₹ 4,200.50")

        CrashDiagnosticsManager.recordCrash(context, thread, error)

        assertTrue(CrashDiagnosticsManager.hasRecordedCrash(context))
        val report = CrashDiagnosticsManager.getRecordedCrash(context)
        assertNotNull(report)
        assertEquals("test-worker-thread", report?.threadName)
        assertEquals("java.lang.IllegalStateException", report?.exceptionClass)
        assertEquals("Corrupted account [REDACTED_NUM] with balance [REDACTED_AMOUNT]", report?.exceptionMessage)
        assertTrue(report?.stackTrace?.contains("IllegalStateException") == true)
        assertTrue(report?.timestamp?.isNotEmpty() == true)
    }

    @Test
    fun clearCrash_removesReport() {
        val thread = Thread.currentThread()
        CrashDiagnosticsManager.recordCrash(context, thread, RuntimeException("Sample crash"))
        assertTrue(CrashDiagnosticsManager.hasRecordedCrash(context))

        val cleared = CrashDiagnosticsManager.clearCrash(context)
        assertTrue(cleared)
        assertFalse(CrashDiagnosticsManager.hasRecordedCrash(context))
        assertNull(CrashDiagnosticsManager.getRecordedCrash(context))
    }

    @Test
    fun getRecordedCrash_whenFileCorrupted_returnsNullGracefully() {
        val file = File(context.filesDir, CrashDiagnosticsManager.CRASH_REPORT_FILE_NAME)
        file.writeText("invalid-non-json-content")

        assertTrue(CrashDiagnosticsManager.hasRecordedCrash(context))
        val report = CrashDiagnosticsManager.getRecordedCrash(context)
        assertNull(report)
    }

    @Test
    fun install_registersUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            CrashDiagnosticsManager.install(context)
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull(currentHandler)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}
