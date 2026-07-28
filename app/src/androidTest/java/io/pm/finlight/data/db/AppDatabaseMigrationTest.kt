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
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_47_48
import io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_50_51
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

    /**
     * Test MIGRATION_47_48: Creates goal_contributions table.
     */
    @Test
    fun migrate47To48_createsGoalContributionsTable() {
        helper.createDatabase(testDbName, 47).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 48, true, MIGRATION_47_48).apply {
            val cursor = query("PRAGMA table_info(goal_contributions)")
            var foundGoalId = false
            var foundAmount = false
            while (cursor.moveToNext()) {
                val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (colName == "goalId") foundGoalId = true
                if (colName == "amount") foundAmount = true
            }
            assertTrue("Column 'goalId' should exist in goal_contributions", foundGoalId)
            assertTrue("Column 'amount' should exist in goal_contributions", foundAmount)
            cursor.close()

            close()
        }
    }

    /**
     * Test MIGRATION_48_49: Adds mergeDismissed column to transactions
     */
    @Test
    fun migrate48To49_addsMergeDismissedColumnToTransactions() {
        helper.createDatabase(testDbName, 48).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 49, true, io.pm.finlight.data.db.AppDatabase.Companion.MIGRATION_48_49).apply {
            val cursor = query("PRAGMA table_info(transactions)")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "mergeDismissed") {
                    found = true
                    assertEquals(
                        "mergeDismissed should default to 0 (false) and not null",
                        1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                    )
                    assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertTrue("Column 'mergeDismissed' should exist in transactions table", found)
            cursor.close()
            close()
        }
    }

    @Test
    fun migrate49To50_addsParentReimbursementIdColumnToTransactions() {
        helper.createDatabase(testDbName, 49).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 50, true, AppDatabase.MIGRATION_49_50).apply {
            val cursor = query("PRAGMA table_info(transactions)")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "parentReimbursementId") {
                    found = true
                    assertEquals(
                        "parentReimbursementId should be nullable (notnull=0)",
                        0,
                        cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                    )
                    val dfltValue = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                    assertTrue(
                        "Default value should be NULL or string 'NULL'",
                        dfltValue == null || dfltValue.equals("NULL", ignoreCase = true)
                    )
                }
            }
            assertTrue("Column 'parentReimbursementId' should exist in transactions table", found)
            cursor.close()
            close()
        }
    }

    /**
     * Test MIGRATION_50_51: Creates the merge_records table for the Unmerge feature.
     *
     * Verifies that:
     * 1. The merge_records table is created with the expected columns and correct nullability.
     * 2. The index on parentTxnId is created.
     */
    @Test
    fun migrate50To51_createsMergeRecordsTable() {
        helper.createDatabase(testDbName, 50).apply {
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 51, true, MIGRATION_50_51).apply {
            // Verify table exists with correct columns
            val cursor = query("PRAGMA table_info(merge_records)")
            val columnNames = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                columnNames.add(name)
                when (name) {
                    // NOT NULL columns
                    "id", "parentTxnId", "mergedAt",
                    "originalParentAmount", "originalParentDate",
                    "childDescription", "childAmount", "childDate",
                    "childAccountId", "childTransactionType", "childSource" ->
                        assertEquals("Column '$name' should be NOT NULL", 1, notNull)
                    // Nullable columns
                    "originalParentNotes", "childCategoryId", "childNotes",
                    "childSourceSmsId", "childSourceSmsHash", "childSmsSignature",
                    "childOriginalDescription", "childOriginalAmount",
                    "childCurrencyCode", "childConversionRate" ->
                        assertEquals("Column '$name' should be nullable", 0, notNull)
                }
            }
            cursor.close()

            // Verify all expected columns are present
            val expectedColumns =
                setOf(
                    "id", "parentTxnId", "mergedAt",
                    "originalParentAmount", "originalParentDate", "originalParentNotes",
                    "childDescription", "childAmount", "childDate", "childAccountId",
                    "childCategoryId", "childTransactionType", "childSource", "childNotes",
                    "childSourceSmsId", "childSourceSmsHash", "childSmsSignature",
                    "childOriginalDescription", "childOriginalAmount",
                    "childCurrencyCode", "childConversionRate",
                )
            assertEquals(
                "merge_records table should have exactly the expected columns",
                expectedColumns,
                columnNames,
            )

            // Verify the index on parentTxnId was created
            val indexCursor =
                query(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='index_merge_records_parentTxnId'"
                )
            assertTrue(
                "Index 'index_merge_records_parentTxnId' should exist",
                indexCursor.moveToFirst(),
            )
            indexCursor.close()
            close()
        }
    }

    /**
     * Test MIGRATION_51_52: Adds mergeGroupId and mergeType columns to merge_records table.
     */
    @Test
    fun migrate51To52_addsMergeGroupColumnsToMergeRecords() {
        helper.createDatabase(testDbName, 51).apply {
            // Insert dummy data into merge_records (requires transactions to exist due to foreign key)
            execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Test Account', 'Bank')")
            execSQL(
                "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                    "VALUES (1, 'Parent', 100.0, 1000, 1, 'expense', 'AUTO', 0, 0, 0, 0, 'CONFIRMED')"
            )
            execSQL(
                "INSERT INTO merge_records (parentTxnId, mergedAt, originalParentAmount, originalParentDate, " +
                    "childDescription, childAmount, childDate, childAccountId, childTransactionType, childSource) " +
                    "VALUES (1, 1000, 100.0, 1000, 'Child', 50.0, 1000, 1, 'expense', 'AUTO')"
            )
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 52, true, AppDatabase.Companion.MIGRATION_51_52).apply {
            val cursor = query("PRAGMA table_info(merge_records)")
            val columnNames = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                columnNames.add(name)
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                val dfltValue = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))

                when (name) {
                    "mergeGroupId" -> {
                        assertEquals("Column 'mergeGroupId' should be NOT NULL", 1, notNull)
                        assertEquals("''", dfltValue)
                    }
                    "mergeType" -> {
                        assertEquals("Column 'mergeType' should be NOT NULL", 1, notNull)
                        assertEquals("'AUTO'", dfltValue)
                    }
                }
            }
            cursor.close()

            assertTrue("Column 'mergeGroupId' should exist", columnNames.contains("mergeGroupId"))
            assertTrue("Column 'mergeType' should exist", columnNames.contains("mergeType"))

            val indexCursor = query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_merge_records_mergeGroupId'")
            assertTrue("Index 'index_merge_records_mergeGroupId' should exist", indexCursor.moveToFirst())
            indexCursor.close()

            close()
        }
    }

    @Test
    fun migrate52To53_addsLinkedTransferId() {
        helper.createDatabase(testDbName, 52).apply {
            execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Test Account', 'Bank')")
            execSQL(
                "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                    "VALUES (1, 'Parent', 100.0, 1000, 1, 'expense', 'AUTO', 0, 0, 0, 0, 'CONFIRMED')"
            )
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 53, true, AppDatabase.Companion.MIGRATION_52_53).apply {
            val cursor = query("PRAGMA table_info(transactions)")
            var hasColumn = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name == "linkedTransferId") {
                    hasColumn = true
                    val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                    assertEquals("Column 'linkedTransferId' should be nullable", 0, notNull)
                }
            }
            cursor.close()
            assertTrue("Column 'linkedTransferId' should exist", hasColumn)
            close()
        }
    }

    @Test
    fun migrate53To54_healsCorruptedTransactionAmounts() {
        val db = helper.createDatabase(testDbName, 53)

        // Insert a dummy account
        db.execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Cash', 'Cash')")

        // Scenario A: A transaction clamped to 0.0 with a reimbursement
        // True original amount = 100, Reimbursement = 150. True math = -50.
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, originalAmount, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (1, 'Test Expense', 0.0, 1000, 1, 'expense', 100.0, 'manual', 0, 0, 0, 0, 'cleared')"
        )
        // Insert reimbursement
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, parentReimbursementId, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (2, 'Test Income', 150.0, 1000, 1, 'income', 1, 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // Scenario B: An unmerged transaction (amount reset to base, but forgot reimbursements)
        // Base = 200. Reimbursement = 100. Unmerge reset it to 200. True math = 200 - 100 = 100.
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, originalAmount, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (3, 'Test Unmerged', 200.0, 1000, 1, 'expense', 200.0, 'manual', 0, 0, 0, 0, 'cleared')"
        )
        // Reimbursement
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, parentReimbursementId, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (4, 'Test Income 2', 100.0, 1000, 1, 'income', 3, 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // Scenario C: A transaction that is perfectly fine (should not be touched)
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, originalAmount, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (5, 'Perfect', 50.0, 1000, 1, 'expense', 50.0, 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // Scenario D: A transaction that was manually edited
        // Original = 100, User edited to 150. Reimbursement = 20. Current Amount = 130.
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, originalAmount, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (6, 'Test Edited', 130.0, 1000, 1, 'expense', 100.0, 'manual', 0, 0, 0, 0, 'cleared')"
        )
        // Reimbursement
        db.execSQL(
            "INSERT INTO transactions (id, description, amount, date, accountId, transactionType, parentReimbursementId, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (7, 'Test Income 3', 20.0, 1000, 1, 'income', 6, 'manual', 0, 0, 0, 0, 'cleared')"
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 54, true, AppDatabase.MIGRATION_53_54)

        // Verify Scenario A
        val cursorA = migratedDb.query("SELECT amount, transactionType FROM transactions WHERE id = 1")
        org.junit.Assert.assertTrue(cursorA.moveToFirst())
        org.junit.Assert.assertEquals(-50.0, cursorA.getDouble(0), 0.001)
        org.junit.Assert.assertEquals("expense", cursorA.getString(1))
        cursorA.close()

        // Verify Scenario B
        val cursorB = migratedDb.query("SELECT amount, transactionType FROM transactions WHERE id = 3")
        org.junit.Assert.assertTrue(cursorB.moveToFirst())
        org.junit.Assert.assertEquals(100.0, cursorB.getDouble(0), 0.001)
        org.junit.Assert.assertEquals("expense", cursorB.getString(1))
        cursorB.close()

        // Verify Scenario C
        val cursorC = migratedDb.query("SELECT amount, transactionType FROM transactions WHERE id = 5")
        org.junit.Assert.assertTrue(cursorC.moveToFirst())
        org.junit.Assert.assertEquals(50.0, cursorC.getDouble(0), 0.001)
        org.junit.Assert.assertEquals("expense", cursorC.getString(1))
        cursorC.close()

        // Verify Scenario D
        val cursorD = migratedDb.query("SELECT amount, transactionType FROM transactions WHERE id = 6")
        org.junit.Assert.assertTrue(cursorD.moveToFirst())
        org.junit.Assert.assertEquals(130.0, cursorD.getDouble(0), 0.001)
        org.junit.Assert.assertEquals("expense", cursorD.getString(1))
        cursorD.close()
    }

    @Test
    fun migrate54To55_heals_original_description() {
        val db = helper.createDatabase(testDbName, 54)

        // Setup initial state for v54
        db.execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Test Account', 'Bank Account')")

        // Insert a rename rule: STARBUCKS -> Coffee
        db.execSQL("INSERT INTO merchant_rename_rules (originalName, newName) VALUES ('STARBUCKS', 'Coffee')")

        // 1. Corrupted transaction: originalDescription is saved as the newName ('Coffee')
        db.execSQL(
            "INSERT INTO transactions (id, description, originalDescription, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (1, 'Coffee', 'Coffee', 50.0, 1000, 1, 'expense', 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // 2. Correct transaction: originalDescription is correct ('STARBUCKS')
        db.execSQL(
            "INSERT INTO transactions (id, description, originalDescription, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (2, 'Coffee', 'STARBUCKS', 60.0, 1000, 1, 'expense', 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // 3. Unrelated transaction
        db.execSQL(
            "INSERT INTO transactions (id, description, originalDescription, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (3, 'Food', 'Food', 20.0, 1000, 1, 'expense', 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // 4. Null originalDescription transaction (e.g. pre-dating the column)
        db.execSQL(
            "INSERT INTO transactions (id, description, originalDescription, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (4, 'Old Txn', NULL, 10.0, 1000, 1, 'expense', 'manual', 0, 0, 0, 0, 'cleared')"
        )

        // 5. Case-insensitive corrupted transaction
        db.execSQL(
            "INSERT INTO transactions (id, description, originalDescription, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, mergeDismissed, status) " +
                "VALUES (5, 'COFFEE', 'COFFEE', 15.0, 1000, 1, 'expense', 'manual', 0, 0, 0, 0, 'cleared')"
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 55, true, AppDatabase.MIGRATION_54_55)

        // Verify transaction 1: 'Coffee' should be healed to 'STARBUCKS'
        val cursor1 = migratedDb.query("SELECT originalDescription FROM transactions WHERE id = 1")
        org.junit.Assert.assertTrue(cursor1.moveToFirst())
        org.junit.Assert.assertEquals("STARBUCKS", cursor1.getString(0))
        cursor1.close()

        // Verify transaction 2: 'STARBUCKS' should remain untouched
        val cursor2 = migratedDb.query("SELECT originalDescription FROM transactions WHERE id = 2")
        org.junit.Assert.assertTrue(cursor2.moveToFirst())
        org.junit.Assert.assertEquals("STARBUCKS", cursor2.getString(0))
        cursor2.close()

        // Verify transaction 3: 'Food' should remain untouched
        val cursor3 = migratedDb.query("SELECT originalDescription FROM transactions WHERE id = 3")
        org.junit.Assert.assertTrue(cursor3.moveToFirst())
        org.junit.Assert.assertEquals("Food", cursor3.getString(0))
        cursor3.close()

        // Verify transaction 4: NULL should remain untouched
        val cursor4 = migratedDb.query("SELECT originalDescription FROM transactions WHERE id = 4")
        org.junit.Assert.assertTrue(cursor4.moveToFirst())
        org.junit.Assert.assertTrue(cursor4.isNull(0))
        cursor4.close()

        // Verify transaction 5: 'COFFEE' should be healed to 'STARBUCKS' (case-insensitive)
        val cursor5 = migratedDb.query("SELECT originalDescription FROM transactions WHERE id = 5")
        org.junit.Assert.assertTrue(cursor5.moveToFirst())
        org.junit.Assert.assertEquals("STARBUCKS", cursor5.getString(0))
        cursor5.close()
    }
}
