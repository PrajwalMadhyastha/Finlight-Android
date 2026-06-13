package io.pm.finlight

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A custom JUnit Rule to disable the onboarding screen before a test runs.
 * This rule accesses the app's SharedPreferences and sets the flag to true,
 * ensuring the onboarding flow does not interfere with UI tests.
 */
class DisableOnboardingRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("has_seen_onboarding", true).commit()
                    base.evaluate()
                } finally {
                    // Cleanup not needed
                }
            }
        }
    }
}

/**
 * A custom JUnit Rule to disable the app lock feature before a test runs.
 * This rule accesses the app's SharedPreferences and sets the app lock flag to false,
 * ensuring the lock screen does not interfere with UI tests.
 */
class DisableAppLockRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("app_lock_enabled", false).commit()
                    base.evaluate()
                } finally {
                    // Cleanup not needed
                }
            }
        }
    }
}

/**
 * A custom JUnit Rule that clears user-owned transactional data from the live
 * application database before each test runs.
 *
 * ## What is cleared
 * - transactions (cascades to split_transactions, transaction_tag_cross_ref, transaction_images)
 * - budgets
 * - tags (cascades to transaction_tag_cross_ref)
 * - goals
 * - accounts (cascades to transactions)
 *
 * ## What is NOT cleared
 * - categories — the app's DatabaseCallback repopulates them if empty, so wiping
 *   them would cause a race condition on the first open. Tests rely on default
 *   categories already being present, plus any extras seeded by [SeedDatabaseRule].
 * - SMS rules, recurring rules, parse templates — not relevant to UI workflow tests.
 *
 * ## Usage
 * Add to a [org.junit.rules.RuleChain] *before* [SeedDatabaseRule] so the DB is
 * clean before fresh seed data is inserted:
 * ```kotlin
 * val ruleChain = RuleChain
 *     .outerRule(DisableOnboardingRule())
 *     .around(DisableAppLockRule())
 *     .around(ClearDatabaseRule())   // clears first
 *     .around(SeedDatabaseRule())    // then seeds
 *     .around(composeTestRule)
 * ```
 *
 * Only apply this rule to tests where prior data would be noise. Tests that
 * verify "account detail shows transactions" still need the [SeedDatabaseRule]
 * but do NOT need this clear rule.
 */
class ClearDatabaseRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val db = AppDatabase.getInstance(context)

                // Clear any stored budgets from SharedPreferences to avoid cross-test contamination
                val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                prefs.all.keys.forEach { key ->
                    if (key.startsWith("budget_")) {
                        editor.remove(key)
                    }
                }
                editor.commit()

                runBlocking {
                    // Use a raw transaction to clear all tables in correct dependency order,
                    // guaranteeing no foreign key constraint failures or orphan rows.
                    val writableDb = db.openHelper.writableDatabase
                    writableDb.beginTransaction()
                    try {
                        writableDb.execSQL("DELETE FROM transaction_images")
                        writableDb.execSQL("DELETE FROM transaction_tag_cross_ref")
                        writableDb.execSQL("DELETE FROM split_transactions")
                        writableDb.execSQL("DELETE FROM transactions")
                        writableDb.execSQL("DELETE FROM trips")
                        writableDb.execSQL("DELETE FROM tags")
                        writableDb.execSQL("DELETE FROM goals")
                        writableDb.execSQL("DELETE FROM recurring_transactions")
                        writableDb.execSQL("DELETE FROM accounts")
                        writableDb.execSQL("DELETE FROM budgets")
                        writableDb.setTransactionSuccessful()
                    } finally {
                        writableDb.endTransaction()
                    }
                }
                base.evaluate()
            }
        }
    }
}

/**
 * A custom JUnit Rule that seeds a canonical, deterministic dataset into the
 * live application database *before* each test runs.
 *
 * This rule depends on [TestDataSeeder] which is the single source of truth for
 * all test data. All new UI tests should reference constants from [TestDataSeeder]
 * (e.g., [TestDataSeeder.ACCOUNT_BANK_NAME]) instead of using magic strings.
 *
 * ## Usage
 * Chain after [ClearDatabaseRule] when you need a clean slate, or use alone
 * when the test merely needs data to exist (not a fresh DB):
 * ```kotlin
 * val ruleChain = RuleChain
 *     .outerRule(DisableOnboardingRule())
 *     .around(DisableAppLockRule())
 *     .around(ClearDatabaseRule())   // optional — only if prior data is noise
 *     .around(SeedDatabaseRule())    // always seed known data
 *     .around(composeTestRule)
 * ```
 */
class SeedDatabaseRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val db = AppDatabase.getInstance(context)
                TestDataSeeder.seed(db)
                base.evaluate()
            }
        }
    }
}
