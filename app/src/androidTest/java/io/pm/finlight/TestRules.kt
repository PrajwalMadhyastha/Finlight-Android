package io.pm.finlight

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
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
