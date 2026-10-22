package io.pm.finlight.data.health

import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DatabaseHealthCheckerTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    @Before
    fun setup() =
        runTest {
            dbRule.db.accountDao().insertAll(
                listOf(
                    Account(id = 1, name = "Cash Spends", type = "Cash"),
                    Account(id = 2, name = "HDFC Bank", type = "Bank"),
                ),
            )
        }

    @Test
    fun checkHealth_onHealthyDatabase_returnsHealthy() =
        runTest {
            val result = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertTrue(result.isHealthy)
            assertTrue(result.integrityCheckPassed)
            assertEquals(0, result.corruptedAccountsCount)
            assertEquals(0, result.duplicateAccountsCount)
            assertEquals(0, result.orphanTransactionsCount)
            assertTrue(result.issuesDescription.isEmpty())
        }

    @Test
    fun checkHealth_andRepair_recoversCorruptedDataAndOrphans() =
        runTest {
            val db = dbRule.db.openHelper.writableDatabase

            // Inject corrupted account with blank name
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (10, '', 'General')")

            // Disable foreign keys temporarily to simulate corrupted/unconstrained orphan transactions
            db.execSQL("PRAGMA foreign_keys = OFF")

            // Inject orphan transaction pointing to non-existent account 999
            db.execSQL(
                """
                INSERT INTO transactions (id, description, amount, date, accountId, transactionType, source, isExcluded, isSplit, needsReview, status, mergeDismissed)
                VALUES (100, 'Supermarket', 150.0, 1234567, 999, 'EXPENSE', 'Manual Entry', 0, 0, 0, 'CONFIRMED', 0)
                """.trimIndent(),
            )

            // Health check before repair
            val checkBefore = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertFalse(checkBefore.isHealthy)
            assertEquals(1, checkBefore.corruptedAccountsCount)
            assertEquals(1, checkBefore.orphanTransactionsCount)

            // Execute Self-Repair
            val repairResult = DatabaseHealthChecker.repair(dbRule.db)
            assertTrue(repairResult.success)
            assertEquals(1, repairResult.repairedAccountsCount)
            assertEquals(1, repairResult.relinkedTransactionsCount)

            // Verify account 10 was healed
            val restoredAccount = dbRule.db.accountDao().getAccountByIdSync(10)
            assertEquals("Restored Account 10", restoredAccount?.name)
            assertEquals("General", restoredAccount?.type)

            // Verify transaction 100 was relinked to an existing account (account 1)
            val cursor = db.query("SELECT accountId FROM transactions WHERE id = 100")
            cursor.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }

            // Health check after repair
            val checkAfter = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertTrue(checkAfter.isHealthy)
            assertEquals(0, checkAfter.corruptedAccountsCount)
            assertEquals(0, checkAfter.orphanTransactionsCount)
        }

    @Test
    fun repair_preservesLegitimateAccountName_whenOnlyTypeIsCorrupted() =
        runTest {
            val db = dbRule.db.openHelper.writableDatabase
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (20, 'HDFC Salary', '')")

            val checkBefore = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertFalse(checkBefore.isHealthy)
            assertEquals(1, checkBefore.corruptedAccountsCount)

            val repairResult = DatabaseHealthChecker.repair(dbRule.db)
            assertTrue(repairResult.success)

            val account = dbRule.db.accountDao().getAccountByIdSync(20)
            assertEquals("HDFC Salary", account?.name)
            assertEquals("General", account?.type)

            val checkAfter = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertTrue(checkAfter.isHealthy)
            assertEquals(0, checkAfter.corruptedAccountsCount)
        }

    @Test
    fun repair_disambiguatesDuplicateAccounts_andLeavesDatabaseHealthy() =
        runTest {
            val db = dbRule.db.openHelper.writableDatabase
            // Temporarily disable unique index to simulate legacy database corruption with duplicates
            db.execSQL("DROP INDEX IF EXISTS index_accounts_name_nocase")
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (30, 'Duplicate Bank', 'Bank')")
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (31, 'duplicate bank', 'Bank')")

            val checkBefore = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertFalse(checkBefore.isHealthy)
            assertTrue(checkBefore.duplicateAccountsCount >= 1)

            val repairResult = DatabaseHealthChecker.repair(dbRule.db)
            assertTrue(repairResult.success)

            val account31 = dbRule.db.accountDao().getAccountByIdSync(31)
            assertEquals("duplicate bank (31)", account31?.name)

            val checkAfter = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertEquals(0, checkAfter.duplicateAccountsCount)
            assertTrue(checkAfter.isHealthy)
        }

    @Test
    fun repair_disambiguatesDuplicateAccounts_avoidsCollisionWhenDisambiguatedNameAlreadyExists() =
        runTest {
            val db = dbRule.db.openHelper.writableDatabase
            db.execSQL("DROP INDEX IF EXISTS index_accounts_name_nocase")
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (30, 'Collision Bank', 'Bank')")
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (31, 'Collision Bank', 'Bank')")
            // Pre-existing account that has the exact name "Collision Bank (31)"
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (32, 'Collision Bank (31)', 'Bank')")

            val repairResult = DatabaseHealthChecker.repair(dbRule.db)
            assertTrue(repairResult.success)

            val account31 = dbRule.db.accountDao().getAccountByIdSync(31)
            assertEquals("Collision Bank (31-1)", account31?.name)

            val checkAfter = DatabaseHealthChecker.checkHealth(dbRule.db)
            assertEquals(0, checkAfter.duplicateAccountsCount)
            assertTrue(checkAfter.isHealthy)
        }

    @Test
    fun checkHealth_withValidDataStoreContext_reportsHealthyPreferences() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val result = DatabaseHealthChecker.checkHealth(dbRule.db, context)
            assertTrue(result.isHealthy)
            assertEquals(0, result.corruptedPreferencesCount)

            val repairResult = DatabaseHealthChecker.repair(dbRule.db, context)
            assertTrue(repairResult.success)
            assertEquals(0, repairResult.repairedPreferencesCount)
        }

    @Test
    fun checkHealth_and_repair_withCorruptedPreferences_detectsAndRepairsCorruptedKeys() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()

            // Inject corrupted (type mismatch) preferences
            context.financeSettingsDataStore.edit { prefs ->
                // Inject string where boolean is expected
                prefs[stringPreferencesKey("app_lock_enabled")] = "not_a_boolean"
                // Inject string where float is expected for dynamic budget key
                prefs[stringPreferencesKey("overall_budget_2026_10")] = "not_a_float"
            }
            context.internalSettingsDataStore.edit { prefs ->
                // Inject string where boolean is expected
                prefs[stringPreferencesKey("is_first_launch_complete")] = "not_a_boolean"
            }

            val checkBefore = DatabaseHealthChecker.checkHealth(dbRule.db, context)
            assertFalse(checkBefore.isHealthy)
            assertTrue(checkBefore.corruptedPreferencesCount >= 3)
            assertTrue(checkBefore.issuesDescription.any { it.contains("app_lock_enabled") })
            assertTrue(checkBefore.issuesDescription.any { it.contains("overall_budget_2026_10") })
            assertTrue(checkBefore.issuesDescription.any { it.contains("is_first_launch_complete") })

            val repairResult = DatabaseHealthChecker.repair(dbRule.db, context)
            assertTrue(repairResult.success)
            assertTrue(repairResult.repairedPreferencesCount >= 3)

            val checkAfter = DatabaseHealthChecker.checkHealth(dbRule.db, context)
            assertEquals(0, checkAfter.corruptedPreferencesCount)
            assertTrue(checkAfter.isHealthy)
        }
}
