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
            booleanPreferencesKey("simulator_privacy_mode_enabled"),
            booleanPreferencesKey("google_drive_backup_enabled"),
            booleanPreferencesKey("auto_backup_enabled"),
            booleanPreferencesKey("auto_backup_notification_enabled"),
            longPreferencesKey("last_backup_timestamp"),
            booleanPreferencesKey("recurring_transactions_enabled"),
            intPreferencesKey("goal_income_threshold"),
            booleanPreferencesKey("goal_nudges_enabled"),
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

    private val FINANCE_KEY_EXPECTED_TYPES: Map<String, Class<*>> =
        mapOf(
            "user_name" to String::class.java,
            "home_currency_code" to String::class.java,
            "selected_app_theme" to String::class.java,
            "dashboard_card_order" to String::class.java,
            "travel_mode_settings" to String::class.java,
            "sms_scan_start_date" to java.lang.Long::class.java,
            "ignore_rules_checksum" to java.lang.Integer::class.java,
            "dismissed_merge_suggestions" to Set::class.java,
            "excluded_income_months" to Set::class.java,
            "excluded_expense_months" to Set::class.java,
            "app_lock_enabled" to java.lang.Boolean::class.java,
            "privacy_mode_enabled" to java.lang.Boolean::class.java,
            "simulator_privacy_mode_enabled" to java.lang.Boolean::class.java,
            "google_drive_backup_enabled" to java.lang.Boolean::class.java,
            "auto_backup_enabled" to java.lang.Boolean::class.java,
            "auto_backup_notification_enabled" to java.lang.Boolean::class.java,
            "last_backup_timestamp" to java.lang.Long::class.java,
            "recurring_transactions_enabled" to java.lang.Boolean::class.java,
            "goal_income_threshold" to java.lang.Integer::class.java,
            "goal_nudges_enabled" to java.lang.Boolean::class.java,
            "daily_report_enabled" to java.lang.Boolean::class.java,
            "daily_report_hour" to java.lang.Integer::class.java,
            "daily_report_minute" to java.lang.Integer::class.java,
            "weekly_summary_enabled" to java.lang.Boolean::class.java,
            "weekly_report_day" to java.lang.Integer::class.java,
            "weekly_report_hour" to java.lang.Integer::class.java,
            "weekly_report_minute" to java.lang.Integer::class.java,
            "monthly_summary_enabled" to java.lang.Boolean::class.java,
            "monthly_report_day" to java.lang.Integer::class.java,
            "monthly_report_hour" to java.lang.Integer::class.java,
            "monthly_report_minute" to java.lang.Integer::class.java,
            "autocapture_notification_enabled" to java.lang.Boolean::class.java,
            "unknown_transaction_popup_enabled" to java.lang.Boolean::class.java,
            "profile_picture_uri" to String::class.java,
            "has_seen_onboarding" to java.lang.Boolean::class.java,
        )

    private val INTERNAL_KEY_EXPECTED_TYPES: Map<String, Class<*>> =
        mapOf(
            "is_first_launch_complete" to java.lang.Boolean::class.java,
        )

    suspend fun checkHealth(
        context: Context,
    ): HealthCheckResult = checkHealth(AppDatabase.getInstance(context), context)

    suspend fun checkHealth(
        database: AppDatabase,
        context: Context? = null,
    ): HealthCheckResult =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.readableDatabase

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
                        "SELECT COUNT(*) FROM (SELECT LOWER(TRIM(name)) FROM accounts GROUP BY LOWER(TRIM(name)) HAVING COUNT(*) > 1)",
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
                    val prefsMap = financePrefs.asMap()
                    for ((keyName, expectedType) in FINANCE_KEY_EXPECTED_TYPES) {
                        val entry = prefsMap.entries.find { it.key.name == keyName }
                        if (entry != null && !expectedType.isInstance(entry.value)) {
                            corruptedPreferences++
                            issues.add("Preference key '$keyName' is corrupted or has an incompatible type.")
                        }
                    }
                    for ((k, value) in prefsMap) {
                        if (k.name.startsWith("overall_budget_")) {
                            if (!java.lang.Float::class.java.isInstance(value)) {
                                corruptedPreferences++
                                issues.add("Preference key '${k.name}' is corrupted or has an incompatible type.")
                            }
                        } else if (k.name.startsWith("last_month_summary_dismissed_")) {
                            if (!java.lang.Boolean::class.java.isInstance(value)) {
                                corruptedPreferences++
                                issues.add("Preference key '${k.name}' is corrupted or has an incompatible type.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    corruptedPreferences++
                    issues.add("Failed to read finance settings DataStore: ${e.message}")
                }

                try {
                    val internalDataStore = context.internalSettingsDataStore
                    val internalPrefs = internalDataStore.data.first()
                    val internalMap = internalPrefs.asMap()
                    for ((keyName, expectedType) in INTERNAL_KEY_EXPECTED_TYPES) {
                        val entry = internalMap.entries.find { it.key.name == keyName }
                        if (entry != null && !expectedType.isInstance(entry.value)) {
                            corruptedPreferences++
                            issues.add("Preference key '$keyName' in internal settings is corrupted.")
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
        context: Context,
    ): RepairResult = repair(AppDatabase.getInstance(context), context)

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
                val allNamesCursor = db.query("SELECT name FROM accounts WHERE name IS NOT NULL AND trim(name) != ''")
                val existingNames = mutableSetOf<String>()
                allNamesCursor.use {
                    while (it.moveToNext()) {
                        existingNames.add(it.getString(0).lowercase().trim())
                    }
                }

                val cursor =
                    db.query("SELECT id, name, type FROM accounts WHERE name IS NULL OR type IS NULL OR trim(name) = '' OR trim(type) = ''")
                val corruptedAccountsList = mutableListOf<Triple<Int, String?, String?>>()
                cursor.use {
                    while (it.moveToNext()) {
                        corruptedAccountsList.add(
                            Triple(
                                it.getInt(0),
                                it.getString(1),
                                it.getString(2),
                            ),
                        )
                    }
                }
                for ((id, currentName, currentType) in corruptedAccountsList) {
                    val finalName =
                        if (currentName == null || currentName.trim().isEmpty()) {
                            var candidate = "Restored Account $id"
                            var counter = 1
                            while (existingNames.contains(candidate.lowercase().trim())) {
                                candidate = "Restored Account $id ($counter)"
                                counter++
                            }
                            existingNames.add(candidate.lowercase().trim())
                            candidate
                        } else {
                            currentName
                        }
                    val finalType =
                        if (currentType == null || currentType.trim().isEmpty()) {
                            "General"
                        } else {
                            currentType
                        }

                    db.execSQL(
                        "UPDATE accounts SET name = ?, type = ? WHERE id = ?",
                        arrayOf<Any>(finalName, finalType, id),
                    )
                    repairedAccounts++
                }

                // 2. Disambiguate Duplicate Accounts so database leaves unhealthy state
                val dupCursor = db.query("SELECT id, name FROM accounts ORDER BY id ASC")
                val allAccounts = mutableListOf<Pair<Int, String>>()
                dupCursor.use {
                    while (it.moveToNext()) {
                        allAccounts.add(it.getInt(0) to (it.getString(1) ?: ""))
                    }
                }
                existingNames.clear()
                existingNames.addAll(allAccounts.map { it.second.lowercase().trim() })
                val seenNames = mutableSetOf<String>()
                for ((id, name) in allAccounts) {
                    val lowerName = name.lowercase().trim()
                    if (seenNames.contains(lowerName)) {
                        val baseName = name.trim()
                        var candidate = "$baseName ($id)"
                        var counter = 1
                        while (existingNames.contains(candidate.lowercase().trim())) {
                            candidate = "$baseName ($id-$counter)"
                            counter++
                        }
                        db.execSQL("UPDATE accounts SET name = ? WHERE id = ?", arrayOf<Any>(candidate, id))
                        repairedAccounts++
                        existingNames.add(candidate.lowercase().trim())
                        seenNames.add(candidate.lowercase().trim())
                    } else {
                        seenNames.add(lowerName)
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
                    var found = false
                    val cashCursor = db.query("SELECT id FROM accounts WHERE name = 'Cash Spends' COLLATE NOCASE LIMIT 1")
                    cashCursor.use {
                        if (it.moveToFirst()) {
                            defaultAccountId = it.getInt(0)
                            found = true
                        }
                    }
                    if (!found) {
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

            database.invalidationTracker.refreshVersionsAsync()

            // 4. Reset Corrupted DataStore Keys
            if (context != null) {
                try {
                    val financeDataStore = context.financeSettingsDataStore
                    val financePrefs = financeDataStore.data.first()
                    val prefsMap = financePrefs.asMap()
                    val corruptedFinanceKeys = mutableListOf<Preferences.Key<*>>()
                    for ((keyName, expectedType) in FINANCE_KEY_EXPECTED_TYPES) {
                        val entry = prefsMap.entries.find { it.key.name == keyName }
                        if (entry != null && !expectedType.isInstance(entry.value)) {
                            corruptedFinanceKeys.add(entry.key)
                        }
                    }
                    for ((k, value) in prefsMap) {
                        if (k.name.startsWith("overall_budget_")) {
                            if (!java.lang.Float::class.java.isInstance(value)) {
                                corruptedFinanceKeys.add(k)
                            }
                        } else if (k.name.startsWith("last_month_summary_dismissed_")) {
                            if (!java.lang.Boolean::class.java.isInstance(value)) {
                                corruptedFinanceKeys.add(k)
                            }
                        }
                    }
                    val uniqueFinanceKeysToRepair = corruptedFinanceKeys.distinctBy { it.name }
                    if (uniqueFinanceKeysToRepair.isNotEmpty()) {
                        financeDataStore.edit { prefs ->
                            for (key in uniqueFinanceKeysToRepair) {
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
                    val internalMap = internalPrefs.asMap()
                    val corruptedInternalKeys = mutableListOf<Preferences.Key<*>>()
                    for ((keyName, expectedType) in INTERNAL_KEY_EXPECTED_TYPES) {
                        val entry = internalMap.entries.find { it.key.name == keyName }
                        if (entry != null && !expectedType.isInstance(entry.value)) {
                            corruptedInternalKeys.add(entry.key)
                        }
                    }
                    val uniqueInternalKeysToRepair = corruptedInternalKeys.distinctBy { it.name }
                    if (uniqueInternalKeysToRepair.isNotEmpty()) {
                        internalDataStore.edit { prefs ->
                            for (key in uniqueInternalKeysToRepair) {
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
