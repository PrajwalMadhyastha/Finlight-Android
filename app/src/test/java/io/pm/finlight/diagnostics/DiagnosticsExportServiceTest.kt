package io.pm.finlight.diagnostics

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.util.DatabaseTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DiagnosticsExportServiceTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashDiagnosticsManager.clearCrash(context)
    }

    @After
    fun tearDown() {
        CrashDiagnosticsManager.clearCrash(context)
        val dbFile = context.getDatabasePath("finance_database")
        if (dbFile.exists()) {
            dbFile.delete()
        }
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

    @Test
    fun fetchAggregates_withActiveDatabase_returnsAccurateCounts() {
        val db = dbRule.db.openHelper.writableDatabase
        db.execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Cash Account', 'Cash')")
        db.execSQL(
            """
            INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, status, mergeDismissed)
            VALUES (10, 'Groceries', 50.0, 1000, 1, 'EXPENSE', 'Manual', 0, 0, 0, 'CONFIRMED', 0)
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO categories (id, name, iconKey, colorKey) VALUES (1, 'Food', 'ic_food', 'red')")
        db.execSQL(
            """
            INSERT INTO recurring_patterns (smsSignature, description, amount, transactionType, accountId, categoryId, occurrences, firstSeen, lastSeen, isDismissed)
            VALUES ('sig1', 'Netflix', 15.0, 'EXPENSE', 1, NULL, 1, 1000, 2000, 0)
            """.trimIndent(),
        )

        val dbFile = context.getDatabasePath("finance_database")
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(ByteArray(4096))

        val aggregates = DiagnosticsExportService.fetchAggregates(context, dbRule.db)
        assertEquals(1L, aggregates.accountsCount)
        assertEquals(1L, aggregates.transactionsCount)
        assertEquals(1L, aggregates.categoriesCount)
        assertEquals(1L, aggregates.recurringPatternsCount)
        assertTrue(aggregates.databaseSizeKb > 0)
        assertTrue(aggregates.sqliteVersion.isNotEmpty())
        assertTrue(aggregates.sqliteVersion != "Unknown")

        val report = DiagnosticsExportService.generateReport(context, dbRule.db)
        assertTrue(report.contains("Total Accounts:        1"))
        assertTrue(report.contains("Total Transactions:    1"))
        assertTrue(report.contains("Total Categories:      1"))
        assertTrue(report.contains("Total Recurring Rules: 1"))
        assertTrue(report.contains("Database Size:         ${aggregates.databaseSizeKb} KB"))
        assertTrue(report.contains("Database Schema Ver:   ${dbRule.db.openHelper.readableDatabase.version}"))
    }

    @Test
    fun fetchAggregates_whenDbFileDoesNotExist_returnsZeroSize() {
        val dbFile = context.getDatabasePath("finance_database")
        if (dbFile.exists()) {
            dbFile.delete()
        }
        val aggregates = DiagnosticsExportService.fetchAggregates(context, dbRule.db)
        assertEquals(0L, aggregates.databaseSizeKb)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun generateReport_onPreTiramisuSdk_generatesSuccessfully() {
        val report = DiagnosticsExportService.generateReport(context, null)
        assertTrue(report.contains("Android API Level:     31"))
    }

    @Test
    fun generateReport_whenPackageInfoFails_fallsBackToDefaultVersion() {
        val badPackageContext =
            object : android.content.ContextWrapper(context) {
                override fun getPackageName(): String = "non.existent.package.finlight"
            }
        val report = DiagnosticsExportService.generateReport(badPackageContext, null)
        assertTrue(report.contains("App Version Name:      1.12.0"))
        assertTrue(report.contains("App Version Code:      0"))
    }

    @Test
    fun exportReportToFile_whenDirAlreadyExists_overwritesCleanly() {
        val file1 = DiagnosticsExportService.exportReportToFile(context, "First")
        assertTrue(file1.exists())
        val file2 = DiagnosticsExportService.exportReportToFile(context, "Second")
        assertTrue(file2.exists())
        assertEquals("Second", file2.readText())
    }

    @Test
    fun generateReport_whenDatabaseReadableThrows_fallsBackToDefaultSchemaVersion() {
        val mockDb = io.mockk.mockk<AppDatabase>()
        io.mockk.every { mockDb.openHelper } throws RuntimeException("DB access error")
        val report = DiagnosticsExportService.generateReport(context, mockDb)
        assertTrue(report.contains("Database Schema Ver:   58"))
    }
}
