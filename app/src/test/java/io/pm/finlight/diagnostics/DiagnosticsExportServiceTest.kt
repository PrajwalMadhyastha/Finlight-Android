package io.pm.finlight.diagnostics

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DiagnosticsExportServiceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashDiagnosticsManager.clearCrash(context)
    }

    @After
    fun tearDown() {
        CrashDiagnosticsManager.clearCrash(context)
        val dir = File(context.cacheDir, DiagnosticsExportService.DIAGNOSTICS_DIR)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        try {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        } catch (_: Exception) {
        }
    }

    @Test
    fun generateReport_allSectionsIncluded_containsExpectedHeaders() {
        val report = DiagnosticsExportService.generateReport(context, null)
        assertTrue(report.contains("FINLIGHT LOCAL DIAGNOSTIC REPORT"))
        assertTrue(report.contains("[1. DEVICE & OS ENVIRONMENT]"))
        assertTrue(report.contains("Database Schema Ver:   58"))
        assertTrue(report.contains("[2. SYSTEM HEALTH & AGGREGATE METRICS]"))
        assertTrue(report.contains("[3. RECENT CRASH DIAGNOSTICS]"))
        assertTrue(report.contains("No recent unhandled crashes recorded."))
    }

    @Test
    fun generateReport_withCrashRecorded_includesSanitizedCrash() {
        val thread = Thread("worker-test")
        val error = RuntimeException("Sensitive card 4111222233334444 balance ₹ 900")
        CrashDiagnosticsManager.recordCrash(context, thread, error)

        val report = DiagnosticsExportService.generateReport(context, null)
        assertTrue(report.contains("worker-test"))
        assertTrue(report.contains("[REDACTED_NUM]"))
        assertTrue(report.contains("[REDACTED_AMOUNT]"))
    }

    @Test
    fun generateReport_sectionsToggledOff_omitsSections() {
        val report =
            DiagnosticsExportService.generateReport(
                context = context,
                database = null,
                includeDevice = false,
                includeHealth = false,
                includeCrash = false,
            )
        assertTrue(!report.contains("[1. DEVICE & OS ENVIRONMENT]"))
        assertTrue(!report.contains("[2. SYSTEM HEALTH & AGGREGATE METRICS]"))
        assertTrue(!report.contains("[3. RECENT CRASH DIAGNOSTICS]"))
    }

    @Test
    fun exportReportToFile_writesToCacheAndReturnsFile() {
        val sampleText = "Report Content 123"
        val file = DiagnosticsExportService.exportReportToFile(context, sampleText)
        assertTrue(file.exists())
        assertEquals(sampleText, file.readText())
    }

    @Test
    fun createShareIntent_returnsValidActionSendIntent() {
        val file = DiagnosticsExportService.exportReportToFile(context, "Share Test")
        val intent = DiagnosticsExportService.createShareIntent(context, file)
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertNotNull(intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM))
    }

    @Test
    fun copyToClipboard_succeedsWithoutException() {
        DiagnosticsExportService.copyToClipboard(context, "Test clipboard content")
    }
}
