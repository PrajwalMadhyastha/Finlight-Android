package io.pm.finlight.data.health

import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HealthCheckResult(
    val isHealthy: Boolean,
    val integrityCheckPassed: Boolean,
    val corruptedAccountsCount: Int,
    val duplicateAccountsCount: Int,
    val orphanTransactionsCount: Int,
    val issuesDescription: List<String>,
)

data class RepairResult(
    val success: Boolean,
    val repairedAccountsCount: Int,
    val relinkedTransactionsCount: Int,
    val message: String,
)

/**
 * Diagnostic and self-repair engine for database consistency,
 * orphan transactions re-linking, and corrupted records healing.
 */
object DatabaseHealthChecker {
    suspend fun checkHealth(database: AppDatabase): HealthCheckResult =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase

            var integrityPassed = true
            try {
                val cursor = db.query("PRAGMA integrity_check")
                cursor.use {
                    if (it.moveToFirst()) {
                        val result = it.getString(0) ?: ""
                        if (!result.equals("ok", ignoreCase = true)) {
                            integrityPassed = false
                        }
                    }
                }
            } catch (_: Exception) {
                integrityPassed = false
            }

            var corruptedAccounts = 0
            try {
                val cursor =
                    db.query("SELECT COUNT(*) FROM accounts WHERE name IS NULL OR type IS NULL OR trim(name) = ''")
                cursor.use {
                    if (it.moveToFirst()) corruptedAccounts = it.getInt(0)
                }
            } catch (_: Exception) {
            }

            var duplicateAccounts = 0
            try {
                val cursor =
                    db.query("SELECT COUNT(*) FROM (SELECT LOWER(name) FROM accounts GROUP BY LOWER(name) HAVING COUNT(*) > 1)")
                cursor.use {
                    if (it.moveToFirst()) duplicateAccounts = it.getInt(0)
                }
            } catch (_: Exception) {
            }

            var orphanTransactions = 0
            try {
                val cursor =
                    db.query("SELECT COUNT(*) FROM transactions WHERE accountId NOT IN (SELECT id FROM accounts)")
                cursor.use {
                    if (it.moveToFirst()) orphanTransactions = it.getInt(0)
                }
            } catch (_: Exception) {
            }

            val issues = mutableListOf<String>()
            if (!integrityPassed) issues.add("SQLite PRAGMA integrity check reported errors.")
            if (corruptedAccounts > 0) issues.add("Found $corruptedAccounts account(s) with missing or empty names.")
            if (duplicateAccounts > 0) issues.add("Found $duplicateAccounts group(s) of accounts with duplicate names.")
            if (orphanTransactions > 0) {
                issues.add("Found $orphanTransactions orphan transaction(s) referencing non-existent accounts.")
            }

            val isHealthy = integrityPassed && corruptedAccounts == 0 && duplicateAccounts == 0 && orphanTransactions == 0

            HealthCheckResult(
                isHealthy = isHealthy,
                integrityCheckPassed = integrityPassed,
                corruptedAccountsCount = corruptedAccounts,
                duplicateAccountsCount = duplicateAccounts,
                orphanTransactionsCount = orphanTransactions,
                issuesDescription = issues,
            )
        }

    suspend fun repair(database: AppDatabase): RepairResult =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            var repairedAccounts = 0
            var relinkedTransactions = 0

            db.beginTransaction()
            try {
                // 1. Repair Corrupted Accounts
                val cursor = db.query("SELECT id FROM accounts WHERE name IS NULL OR type IS NULL OR trim(name) = ''")
                val corruptedIds = mutableListOf<Int>()
                cursor.use {
                    while (it.moveToNext()) {
                        corruptedIds.add(it.getInt(0))
                    }
                }
                for (id in corruptedIds) {
                    db.execSQL(
                        "UPDATE accounts SET name = 'Restored Account ' || id, type = CASE WHEN type IS NULL OR trim(type) = '' THEN 'General' ELSE type END WHERE id = ?",
                        arrayOf(id),
                    )
                    repairedAccounts++
                }

                // 2. Re-link Orphan Transactions
                val orphanCursor =
                    db.query("SELECT COUNT(*) FROM transactions WHERE accountId NOT IN (SELECT id FROM accounts)")
                var orphans = 0
                orphanCursor.use {
                    if (it.moveToFirst()) orphans = it.getInt(0)
                }

                if (orphans > 0) {
                    var defaultAccountId = 1
                    val accCursor = db.query("SELECT id FROM accounts ORDER BY id ASC LIMIT 1")
                    val hasAccount = accCursor.use { it.moveToFirst() && it.getInt(0).also { id -> defaultAccountId = id } > 0 }
                    if (!hasAccount) {
                        db.execSQL("INSERT INTO accounts (id, name, type) VALUES (1, 'Cash Spends', 'Cash')")
                        defaultAccountId = 1
                    }
                    db.execSQL(
                        "UPDATE transactions SET accountId = ? WHERE accountId NOT IN (SELECT id FROM accounts)",
                        arrayOf(defaultAccountId),
                    )
                    relinkedTransactions = orphans
                }

                db.setTransactionSuccessful()
                RepairResult(
                    success = true,
                    repairedAccountsCount = repairedAccounts,
                    relinkedTransactionsCount = relinkedTransactions,
                    message = "Successfully repaired $repairedAccounts account(s) and relinked $relinkedTransactions transaction(s).",
                )
            } catch (e: Exception) {
                RepairResult(
                    success = false,
                    repairedAccountsCount = 0,
                    relinkedTransactionsCount = 0,
                    message = "Self-repair failed: ${e.message}",
                )
            } finally {
                db.endTransaction()
            }
        }
}
