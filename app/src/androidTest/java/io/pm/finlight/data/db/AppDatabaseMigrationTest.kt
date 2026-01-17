package io.pm.finlight.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_39_40
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_40_41
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_41_42
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_42_43
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Database migration tests for AppDatabase.
 * 
 * These tests use Room's MigrationTestHelper to verify database migrations work correctly.
 * They create temporary test databases and DO NOT affect the actual app database.
 * 
 * Tested Migrations:
 * - 39→40: Add index on transactions.date
 * - 40→41: Deduplicate sms_parse_templates and add UNIQUE index
 * - 41→42: Recreate sms_parse_templates table with new schema
 * - 42→43: Add transactionType column to custom_sms_rules
 * 
 * NOTE: These are instrumented tests and must run on a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB_NAME = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /**
     * Test MIGRATION_39_40: Adds index on transactions.date column
     */
    @Test
    fun migrate39To40_addsDateIndexOnTransactions() {
        helper.createDatabase(TEST_DB_NAME, 39).apply {
            execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Test Account', 'Bank')")
            execSQL("""
                INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit) 
                VALUES (1, 'Test Transaction', 100.0, 1234567890000, 1, 'expense', 'Manual Entry', 0, 0)
            """)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_NAME, 40, true, MIGRATION_39_40).apply {
            val cursor = query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_transactions_date'")
            assertTrue("Index 'index_transactions_date' should exist", cursor.moveToFirst())
            cursor.close()
            close()
        }
    }

    /**
     * Test MIGRATION_40_41: Deduplicates sms_parse_templates and adds UNIQUE index
     * 
     * Verifies that:
     * 1. Duplicate records are removed (keeping the one with MIN id)
     * 2. A UNIQUE index is created on templateSignature
     * 3. New duplicates cannot be inserted
     */
    @Test
    fun migrate40To41_deduplicatesTemplates() {
        // Create DB at version 40 (Schema has NO unique constraint on templateSignature)
        helper.createDatabase(TEST_DB_NAME, 40).apply {
            // Insert duplicates
            // Signature "SIG_A" -> Repeated twice
            execSQL("""
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (1, 'SIG_A', 'Body 1', 0, 1, 2, 3)
            """)
            execSQL("""
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (2, 'SIG_A', 'Body 1 - Duplicate', 0, 1, 2, 3)
            """)
            // Signature "SIG_B" -> Unique
            execSQL("""
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (3, 'SIG_B', 'Body B', 0, 1, 2, 3)
            """)
            close()
        }

        // Run migration to 41
        helper.runMigrationsAndValidate(TEST_DB_NAME, 41, true, MIGRATION_40_41).apply {
            // Check count - should be 2 (SIG_A + SIG_B)
            val countCursor = query("SELECT COUNT(*) FROM sms_parse_templates")
            assertTrue(countCursor.moveToFirst())
            assertEquals("Should have 2 records after deduplication", 2, countCursor.getInt(0))
            countCursor.close()

            // Verify ID 1 (MIN id) was kept for SIG_A
            val dataCursor = query("SELECT id FROM sms_parse_templates WHERE templateSignature = 'SIG_A'")
            assertTrue(dataCursor.moveToFirst())
            assertEquals("Should keep record with MIN(id) = 1", 1, dataCursor.getInt(0))
            dataCursor.close()

            // Verify Unique Constraint works now
            try {
                execSQL("""
                    INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                    VALUES (4, 'SIG_B', 'Body B Duplicate', 0, 1, 2, 3)
                """)
                fail("Should fail to insert duplicate templateSignature due to UNIQUE constraint")
            } catch (e: SQLiteConstraintException) {
                // Expected failure
            }

            close()
        }
    }

    /**
     * Test MIGRATION_41_42: Recreates sms_parse_templates table with new schema
     * 
     * Verifies that the table structure changes (e.g., new columns, new primary key).
     * Note: This migration is destructive (drops old table).
     */
    @Test
    fun migrate41To42_recreatesTable() {
        helper.createDatabase(TEST_DB_NAME, 41).apply {
            // Insert old schema data
            execSQL("""
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (1, 'SIG_OLD', 'Body Old', 0, 1, 2, 3)
            """)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_NAME, 42, true, MIGRATION_41_42).apply {
            // Verify new columns exist (e.g. correctedMerchantName) by checking table info
            val cursor = query("PRAGMA table_info(sms_parse_templates)")
            var hasCorrectedMerchantName = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "correctedMerchantName") {
                    hasCorrectedMerchantName = true
                }
            }
            assertTrue("Column 'correctedMerchantName' should exist", hasCorrectedMerchantName)
            cursor.close()

            // Verify we can insert using NEW schema
            // New schema PK: (templateSignature, correctedMerchantName)
            execSQL("""
                INSERT INTO sms_parse_templates (templateSignature, correctedMerchantName, originalSmsBody, originalAmountStartIndex, originalAmountEndIndex, originalMerchantStartIndex, originalMerchantEndIndex)
                VALUES ('SIG_NEW', 'Merchant', 'Body', 0, 0, 0, 0)
            """)
            
            // Verify data count
            val countCursor = query("SELECT COUNT(*) FROM sms_parse_templates")
            assertTrue(countCursor.moveToFirst())
            assertEquals("Should have 1 record (old data was dropped)", 1, countCursor.getInt(0))
            countCursor.close()

            close()
        }
    }

    /**
     * Test MIGRATION_42_43: Adds transactionType column to custom_sms_rules
     */
    @Test
    fun migrate42To43_addsTransactionTypeColumnToCustomSmsRules() {
        helper.createDatabase(TEST_DB_NAME, 42).apply {
            execSQL("INSERT INTO custom_sms_rules (id, triggerPhrase, priority, sourceSmsBody) VALUES (1, 'debited', 1, 'Test SMS body')")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_NAME, 43, true, MIGRATION_42_43).apply {
            val cursor = query("PRAGMA table_info(custom_sms_rules)")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "transactionType") {
                    found = true
                    assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                }
            }
            assertTrue("Column 'transactionType' should exist", found)
            cursor.close()
            close()
        }
    }
}
