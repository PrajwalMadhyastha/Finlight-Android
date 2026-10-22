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
        CrashDiagnosticsManager.resetInstalledStateForTesting()
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

    @Test
    fun install_isIdempotent_doesNotChainMultipleTimes() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            CrashDiagnosticsManager.install(context)
            val firstHandler = Thread.getDefaultUncaughtExceptionHandler()

            CrashDiagnosticsManager.install(context)
            val secondHandler = Thread.getDefaultUncaughtExceptionHandler()

            // Handler should be identical because subsequent install() calls are no-ops
            assertEquals(firstHandler, secondHandler)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun install_whenUncaughtExceptionOccurs_recordsCrashAndDelegatesToPreviousHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        var delegatedThread: Thread? = null
        var delegatedThrowable: Throwable? = null
        val mockHandler =
            Thread.UncaughtExceptionHandler { thread, throwable ->
                delegatedThread = thread
                delegatedThrowable = throwable
            }
        Thread.setDefaultUncaughtExceptionHandler(mockHandler)
        try {
            CrashDiagnosticsManager.install(context)
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull(currentHandler)

            val testThread = Thread("bg-test-thread")
            val testException = IllegalStateException("Uncaught test crash")
            currentHandler?.uncaughtException(testThread, testException)

            assertTrue(CrashDiagnosticsManager.hasRecordedCrash(context))
            val report = CrashDiagnosticsManager.getRecordedCrash(context)
            assertNotNull(report)
            assertEquals("bg-test-thread", report?.threadName)
            assertEquals("Uncaught test crash", report?.exceptionMessage)

            assertEquals(testThread, delegatedThread)
            assertEquals(testException, delegatedThrowable)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun install_whenUncaughtExceptionOccurs_andNoPreviousHandler_recordsCrashCleanly() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(null)
        try {
            CrashDiagnosticsManager.install(context)
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull(currentHandler)

            val testThread = Thread("null-handler-thread")
            val testException = RuntimeException("Crash with no default handler")
            currentHandler?.uncaughtException(testThread, testException)

            assertTrue(CrashDiagnosticsManager.hasRecordedCrash(context))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun install_whenRecordingCrashFails_doesNotThrowAndStillDelegates() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = false
        val mockHandler =
            Thread.UncaughtExceptionHandler { _, _ ->
                delegated = true
            }
        Thread.setDefaultUncaughtExceptionHandler(mockHandler)
        try {
            val throwingContext =
                object : android.content.ContextWrapper(context) {
                    override fun getFilesDir(): File {
                        throw RuntimeException("Simulated I/O failure")
                    }
                }
            CrashDiagnosticsManager.install(throwingContext)
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()

            val testThread = Thread("fail-safe-thread")
            val testException = RuntimeException("Crash during failure")
            currentHandler?.uncaughtException(testThread, testException)

            assertTrue(delegated)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun recordCrash_withNullMessage_recordsEmptyStringMessage() {
        val thread = Thread.currentThread()
        val error = RuntimeException()

        CrashDiagnosticsManager.recordCrash(context, thread, error)

        val report = CrashDiagnosticsManager.getRecordedCrash(context)
        assertNotNull(report)
        assertEquals("", report?.exceptionMessage)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun recordCrash_onPreTiramisuSdk_recordsSuccessfully() {
        val thread = Thread.currentThread()
        CrashDiagnosticsManager.recordCrash(context, thread, RuntimeException("Pre-Tiramisu crash"))
        val report = CrashDiagnosticsManager.getRecordedCrash(context)
        assertNotNull(report)
        assertEquals(Build.VERSION_CODES.S, report?.androidApi)
    }

    @Test
    fun hasRecordedCrash_whenFileExistsButZeroLength_returnsFalse() {
        val file = File(context.filesDir, CrashDiagnosticsManager.CRASH_REPORT_FILE_NAME)
        file.createNewFile()
        assertTrue(file.exists())
        assertEquals(0L, file.length())

        assertFalse(CrashDiagnosticsManager.hasRecordedCrash(context))
        assertNull(CrashDiagnosticsManager.getRecordedCrash(context))
    }

    @Test
    fun clearCrash_whenFileDoesNotExist_returnsTrue() {
        val file = File(context.filesDir, CrashDiagnosticsManager.CRASH_REPORT_FILE_NAME)
        if (file.exists()) file.delete()
        assertTrue(CrashDiagnosticsManager.clearCrash(context))
    }

    @Test
    fun recordCrash_whenPackageInfoFails_fallsBackToUnknownVersion() {
        val badPackageContext =
            object : android.content.ContextWrapper(context) {
                override fun getPackageName(): String = "non.existent.package.finlight"
            }
        val thread = Thread.currentThread()
        CrashDiagnosticsManager.recordCrash(badPackageContext, thread, RuntimeException("Test crash"))
        val report = CrashDiagnosticsManager.getRecordedCrash(badPackageContext)
        assertNotNull(report)
        assertEquals("unknown", report?.appVersionName)
        assertEquals(0L, report?.appVersionCode)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun recordCrash_whenPackageInfoFails_onPreTiramisuSdk_fallsBackToUnknownVersion() {
        val badPackageContext =
            object : android.content.ContextWrapper(context) {
                override fun getPackageName(): String = "non.existent.package.finlight"
            }
        val thread = Thread.currentThread()
        CrashDiagnosticsManager.recordCrash(badPackageContext, thread, RuntimeException("Test crash"))
        val report = CrashDiagnosticsManager.getRecordedCrash(badPackageContext)
        assertNotNull(report)
        assertEquals("unknown", report?.appVersionName)
        assertEquals(0L, report?.appVersionCode)
    }

    @Test
    fun recordCrash_withNullMessageAndNonNullLocalizedMessage_recordsLocalizedMessage() {
        val thread = Thread.currentThread()
        val customError =
            object : Throwable() {
                override val message: String? = null

                override fun getLocalizedMessage(): String = "Custom localized error message"
            }
        CrashDiagnosticsManager.recordCrash(context, thread, customError)
        val report = CrashDiagnosticsManager.getRecordedCrash(context)
        assertNotNull(report)
        assertEquals("Custom localized error message", report?.exceptionMessage)
    }
}
