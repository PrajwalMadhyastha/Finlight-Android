package io.pm.finlight.data.health

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class HealthCheckResult(
    val isHealthy: Boolean,
    val integrityCheckPassed: Boolean,
    val corruptedAccountsCount: Int,
    val duplicateAccountsCount: Int,
    val orphanTransactionsCount: Int,
    val corruptedPreferencesCount: Int = 0,
    val issuesDescription: List<String>,
)

data class RepairResult(
    val success: Boolean,
    val repairedAccountsCount: Int,
    val relinkedTransactionsCount: Int,
    val repairedPreferencesCount: Int = 0,
    val message: String,
)

/**
 * Diagnostic and self-repair engine for database consistency,
 * orphan transactions re-linking, corrupted records healing, and DataStore resilience.
 */
object DatabaseHealthChecker {
    val REGISTERED_FINANCE_KEYS: List<Preferences.Key<*>> =
        listOf(
            stringPreferencesKey("user_name"),
            stringPreferencesKey("home_currency_code"),
            stringPreferencesKey("selected_app_theme"),
            stringPreferencesKey("dashboard_card_order"),
            stringPreferencesKey("travel_mode_settings"),
            longPreferencesKey("sms_scan_start_date"),
            intPreferencesKey("ignore_rules_checksum"),
            stringSetPreferencesKey("dismissed_merge_suggestions"),
            stringSetPreferencesKey("excluded_income_months"),
            stringSetPreferencesKey("excluded_expense_months"),
            booleanPreferencesKey("app_lock_enabled"),
            booleanPreferencesKey("privacy_mode_enabled"),
            booleanPreferencesKey("daily_report_enabled"),
            intPreferencesKey("daily_report_hour"),
            intPreferencesKey("daily_report_minute"),
            booleanPreferencesKey("weekly_summary_enabled"),
            intPreferencesKey("weekly_report_day"),
            intPreferencesKey("weekly_report_hour"),
            intPreferencesKey("weekly_report_minute"),
            booleanPreferencesKey("monthly_summary_enabled"),
            intPreferencesKey("monthly_report_day"),
            intPreferencesKey("monthly_report_hour"),
            intPreferencesKey("monthly_report_minute"),
            booleanPreferencesKey("autocapture_notification_enabled"),
            booleanPreferencesKey("unknown_transaction_popup_enabled"),
            stringPreferencesKey("profile_picture_uri"),
            booleanPreferencesKey("has_seen_onboarding"),
        )

    val REGISTERED_INTERNAL_KEYS: List<Preferences.Key<*>> =
        listOf(
            booleanPreferencesKey("is_first_launch_complete"),
        )

    suspend fun checkHealth(
        database: AppDatabase,
        context: Context? = null,
    ): HealthCheckResult =
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
                    db.query("SELECT COUNT(*) FROM accounts WHERE name IS NULL OR type IS NULL OR trim(name) = '' OR trim(type) = ''")
                cursor.use {
                    if (it.moveToFirst()) corruptedAccounts = it.getInt(0)
                }
            } catch (_: Exception) {
            }

            var duplicateAccounts = 0
            try {
                val cursor =
                    db.query(
                        "SELECT COUNT(*) FROM (SELECT LOWER(name) FROM accounts GROUP BY LOWER(name) HAVING COUNT(*) > 1)",
                    )
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

            var corruptedPreferences = 0
            if (context != null) {
                try {
                    val financeDataStore = context.financeSettingsDataStore
                    val financePrefs = financeDataStore.data.first()
                    for (key in REGISTERED_FINANCE_KEYS) {
                        try {
                            financePrefs[key]
                        } catch (_: Exception) {
                            corruptedPreferences++
                            issues.add("Preference key '${key.name}' is corrupted or has an incompatible type.")
                        }
                    }
                } catch (e: Exception) {
                    corruptedPreferences++
                    issues.add("Failed to read finance settings DataStore: ${e.message}")
                }

                try {
                    val internalDataStore = context.internalSettingsDataStore
                    val internalPrefs = internalDataStore.data.first()
                    for (key in REGISTERED_INTERNAL_KEYS) {
                        try {
                            internalPrefs[key]
                        } catch (_: Exception) {
                            corruptedPreferences++
                            issues.add("Preference key '${key.name}' in internal settings is corrupted.")
                        }
                    }
                } catch (e: Exception) {
                    corruptedPreferences++
                    issues.add("Failed to read internal settings DataStore: ${e.message}")
                }
            }

            val isHealthy =
                integrityPassed &&
                    corruptedAccounts == 0 &&
                    duplicateAccounts == 0 &&
                    orphanTransactions == 0 &&
                    corruptedPreferences == 0

            HealthCheckResult(
                isHealthy = isHealthy,
                integrityCheckPassed = integrityPassed,
                corruptedAccountsCount = corruptedAccounts,
                duplicateAccountsCount = duplicateAccounts,
                orphanTransactionsCount = orphanTransactions,
                corruptedPreferencesCount = corruptedPreferences,
                issuesDescription = issues,
            )
        }

    suspend fun repair(
        database: AppDatabase,
        context: Context? = null,
    ): RepairResult =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            var repairedAccounts = 0
            var relinkedTransactions = 0
            var repairedPreferences = 0

            db.beginTransaction()
            try {
                // 1. Repair Corrupted Accounts (Preserving legitimate names if only type is corrupted)
                val cursor =
                    db.query("SELECT id FROM accounts WHERE name IS NULL OR type IS NULL OR trim(name) = '' OR trim(type) = ''")
                val corruptedIds = mutableListOf<Int>()
                cursor.use {
                    while (it.moveToNext()) {
                        corruptedIds.add(it.getInt(0))
                    }
                }
                for (id in corruptedIds) {
                    db.execSQL(
                        """
                        UPDATE accounts 
                        SET name = CASE WHEN name IS NULL OR trim(name) = '' THEN 'Restored Account ' || id ELSE name END,
                            type = CASE WHEN type IS NULL OR trim(type) = '' THEN 'General' ELSE type END 
                        WHERE id = ?
                        """.trimIndent(),
                        arrayOf(id),
                    )
                    repairedAccounts++
                }

                // 2. Disambiguate Duplicate Accounts so database leaves unhealthy state
                val dupCursor = db.query("SELECT id, name FROM accounts ORDER BY id ASC")
                val seenNames = mutableSetOf<String>()
                dupCursor.use {
                    while (it.moveToNext()) {
                        val id = it.getInt(0)
                        val name = it.getString(1) ?: ""
                        val lowerName = name.lowercase().trim()
                        if (seenNames.contains(lowerName)) {
                            val newName = "$name ($id)"
                            db.execSQL("UPDATE accounts SET name = ? WHERE id = ?", arrayOf<Any>(newName, id))
                            repairedAccounts++
                            seenNames.add(newName.lowercase().trim())
                        } else {
                            seenNames.add(lowerName)
                        }
                    }
                }

                // 3. Re-link Orphan Transactions safely handling id = 0
                val orphanCursor =
                    db.query("SELECT COUNT(*) FROM transactions WHERE accountId NOT IN (SELECT id FROM accounts)")
                var orphans = 0
                orphanCursor.use {
                    if (it.moveToFirst()) orphans = it.getInt(0)
                }

                if (orphans > 0) {
                    var defaultAccountId = 1
                    val accCursor = db.query("SELECT id FROM accounts ORDER BY id ASC LIMIT 1")
                    val hasAccount =
                        accCursor.use {
                            if (it.moveToFirst()) {
                                defaultAccountId = it.getInt(0)
                                true
                            } else {
                                false
                            }
                        }
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
            } catch (e: Exception) {
                return@withContext RepairResult(
                    success = false,
                    repairedAccountsCount = 0,
                    relinkedTransactionsCount = 0,
                    repairedPreferencesCount = 0,
                    message = "Self-repair failed: ${e.message}",
                )
            } finally {
                db.endTransaction()
            }

            // 4. Reset Corrupted DataStore Keys
            if (context != null) {
                try {
                    val financeDataStore = context.financeSettingsDataStore
                    val financePrefs = financeDataStore.data.first()
                    val corruptedFinanceKeys = mutableListOf<Preferences.Key<*>>()
                    for (key in REGISTERED_FINANCE_KEYS) {
                        try {
                            financePrefs[key]
                        } catch (_: Exception) {
                            corruptedFinanceKeys.add(key)
                        }
                    }
                    if (corruptedFinanceKeys.isNotEmpty()) {
                        financeDataStore.edit { prefs ->
                            for (key in corruptedFinanceKeys) {
                                try {
                                    prefs.remove(key)
                                    repairedPreferences++
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                try {
                    val internalDataStore = context.internalSettingsDataStore
                    val internalPrefs = internalDataStore.data.first()
                    val corruptedInternalKeys = mutableListOf<Preferences.Key<*>>()
                    for (key in REGISTERED_INTERNAL_KEYS) {
                        try {
                            internalPrefs[key]
                        } catch (_: Exception) {
                            corruptedInternalKeys.add(key)
                        }
                    }
                    if (corruptedInternalKeys.isNotEmpty()) {
                        internalDataStore.edit { prefs ->
                            for (key in corruptedInternalKeys) {
                                try {
                                    prefs.remove(key)
                                    repairedPreferences++
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            RepairResult(
                success = true,
                repairedAccountsCount = repairedAccounts,
                relinkedTransactionsCount = relinkedTransactions,
                repairedPreferencesCount = repairedPreferences,
                message = "Successfully repaired $repairedAccounts account(s), relinked $relinkedTransactions transaction(s), and reset $repairedPreferences preference(s).",
            )
        }
}
