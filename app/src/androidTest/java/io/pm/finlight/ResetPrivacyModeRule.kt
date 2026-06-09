package io.pm.finlight

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ResetPrivacyModeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("simulator_privacy_mode_enabled", false)
                    .commit()
                base.evaluate()
            }
        }
    }
}
