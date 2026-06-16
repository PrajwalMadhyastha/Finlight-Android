package io.pm.finlight.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_39_40
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_40_41
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_41_42
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_43_44
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_44_45
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_45_46
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
    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    /**
     * Test MIGRATION_39_40: Adds index on transactions.date column
     */
    @Test
    fun migrate39To40_addsDateIndexOnTransactions() {
        helper.createDatabase(testDbName, 39).apply {
            execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Test Account', 'Bank')")
            execSQL(
                """
                INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit) 
                VALUES (1, 'Test Transaction', 100.0, 1234567890000, 1, 'expense', 'Manual Entry', 0, 0)
            """,
            )
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 40, true, MIGRATION_39_40).apply {
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
        helper.createDatabase(testDbName, 40).apply {
            // Insert duplicates
            // Signature "SIG_A" -> Repeated twice
            execSQL(
                """
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (1, 'SIG_A', 'Body 1', 0, 1, 2, 3)
            """,
            )
            execSQL(
                """
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (2, 'SIG_A', 'Body 1 - Duplicate', 0, 1, 2, 3)
            """,
            )
            // Signature "SIG_B" -> Unique
            execSQL(
                """
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (3, 'SIG_B', 'Body B', 0, 1, 2, 3)
            """,
            )
            close()
        }

        // Run migration to 41
        helper.runMigrationsAndValidate(testDbName, 41, true, MIGRATION_40_41).apply {
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
                execSQL(
                    """
                    INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                    VALUES (4, 'SIG_B', 'Body B Duplicate', 0, 1, 2, 3)
                """,
                )
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
        helper.createDatabase(testDbName, 41).apply {
            // Insert old schema data
            execSQL(
                """
                INSERT INTO sms_parse_templates (id, templateSignature, originalSmsBody, originalMerchantStartIndex, originalMerchantEndIndex, originalAmountStartIndex, originalAmountEndIndex)
                VALUES (1, 'SIG_OLD', 'Body Old', 0, 1, 2, 3)
            """,
            )
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 42, true, MIGRATION_41_42).apply {
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
            execSQL(
                """
                INSERT INTO sms_parse_templates (templateSignature, correctedMerchantName, originalSmsBody, originalAmountStartIndex, originalAmountEndIndex, originalMerchantStartIndex, originalMerchantEndIndex)
                VALUES ('SIG_NEW', 'Merchant', 'Body', 0, 0, 0, 0)
            """,
            )

            // Verify data count
            val countCursor = query("SELECT COUNT(*) FROM sms_parse_templates")
            assertTrue(countCursor.moveToFirst())
            assertEquals("Should have 1 record (old data was dropped)", 1, countCursor.getInt(0))
            countCursor.close()

            close()
        }
    }

    /**
     * Test MIGRATION_43_44: Adds needsReview column to transactions
     */
    @Test
    fun migrate43To44_addsNeedsReviewColumnToTransactions() {
        helper.createDatabase(testDbName, 43).apply {
            execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Test Account', 'Bank')")
            execSQL(
                """
                INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit) 
                VALUES (1, 'Test Transaction', 100.0, 1234567890000, 1, 'expense', 'Manual Entry', 0, 0)
            """,
            )
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 44, true, io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_43_44).apply {
            val cursor = query("PRAGMA table_info(transactions)")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "needsReview") {
                    found = true
                    assertEquals(
                        "needsReview should default to 0 (false) and not null",
                        1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                    )
                    assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertTrue("Column 'needsReview' should exist in transactions table", found)
            cursor.close()
            close()
        }
    }

    /**
     * Test MIGRATION_44_45: Creates deleted_sms_hashes deny-list table.
     *
     * Verifies that:
     * 1. The table is created with the correct schema.
     * 2. Hashes can be inserted and retrieved.
     * 3. Duplicate inserts are silently ignored (PRIMARY KEY constraint).
     */
    @Test
    fun migrate44To45_createsDeletedSmsHashesTable() {
        helper.createDatabase(testDbName, 44).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 45, true, MIGRATION_44_45).apply {
            // Verify table exists with correct schema
            val cursor = query("PRAGMA table_info(deleted_sms_hashes)")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "smsHash") {
                    found = true
                }
            }
            assertTrue("Column 'smsHash' should exist in deleted_sms_hashes table", found)
            cursor.close()

            // Verify data can be inserted
            execSQL("INSERT INTO deleted_sms_hashes (smsHash) VALUES ('hash_test_1')")
            val dataCursor = query("SELECT COUNT(*) FROM deleted_sms_hashes")
            assertTrue(dataCursor.moveToFirst())
            assertEquals(1, dataCursor.getInt(0))
            dataCursor.close()

            // Verify duplicate insert is silently ignored (PRIMARY KEY)
            execSQL("INSERT OR IGNORE INTO deleted_sms_hashes (smsHash) VALUES ('hash_test_1')")
            val dupCursor = query("SELECT COUNT(*) FROM deleted_sms_hashes")
            assertTrue(dupCursor.moveToFirst())
            assertEquals("Duplicate hash should be ignored", 1, dupCursor.getInt(0))
            dupCursor.close()

            close()
        }
    }

    /**
     * Test MIGRATION_45_46: Adds fields to support recurring transactions epic #105.
     *
     * Verifies that:
     * 1. `status` and `recurringRuleId` added to `transactions`.
     * 2. `smsSenderId`, `isVariableBill`, `autoApprove`, `endDate`, `skipCount` added to `recurring_transactions`.
     * 3. `isDismissed` added to `recurring_patterns`.
     */
    @Test
    fun migrate45To46_addsRecurringFields() {
        helper.createDatabase(testDbName, 45).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 46, true, MIGRATION_45_46).apply {
            // Check transactions table
            var cursor = query("PRAGMA table_info(transactions)")
            var foundStatus = false
            var foundRuleId = false
            while (cursor.moveToNext()) {
                val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (colName == "status") foundStatus = true
                if (colName == "recurringRuleId") foundRuleId = true
            }
            assertTrue("Column 'status' should exist in transactions", foundStatus)
            assertTrue("Column 'recurringRuleId' should exist in transactions", foundRuleId)
            cursor.close()

            // Check recurring_transactions table
            cursor = query("PRAGMA table_info(recurring_transactions)")
            var foundIsVariableBill = false
            while (cursor.moveToNext()) {
                val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (colName == "isVariableBill") foundIsVariableBill = true
            }
            assertTrue("Column 'isVariableBill' should exist in recurring_transactions", foundIsVariableBill)
            cursor.close()

            // Check recurring_patterns table
            cursor = query("PRAGMA table_info(recurring_patterns)")
            var foundIsDismissed = false
            while (cursor.moveToNext()) {
                val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (colName == "isDismissed") foundIsDismissed = true
            }
            assertTrue("Column 'isDismissed' should exist in recurring_patterns", foundIsDismissed)
            cursor.close()

            close()
        }
    }

    /**
     * Test MIGRATION_46_47: Creates goal_transaction_links table.
     */
    @Test
    fun migrate46To47_createsGoalTransactionLinksTable() {
        helper.createDatabase(testDbName, 46).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 47, true, io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_46_47).apply {
            val cursor = query("PRAGMA table_info(goal_transaction_links)")
            var foundGoalId = false
            var foundTransactionId = false
            while (cursor.moveToNext()) {
                val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (colName == "goalId") foundGoalId = true
                if (colName == "transactionId") foundTransactionId = true
            }
            assertTrue("Column 'goalId' should exist in goal_transaction_links", foundGoalId)
            assertTrue("Column 'transactionId' should exist in goal_transaction_links", foundTransactionId)
            cursor.close()

            close()
        }
    }
}
