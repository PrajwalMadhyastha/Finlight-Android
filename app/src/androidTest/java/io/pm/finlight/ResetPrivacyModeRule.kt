package io.pm.finlight

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ResetPrivacyModeRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                runBlocking {
                    val settingsRepository = SettingsRepository(context)
                    settingsRepository.saveSimulatorPrivacyModeEnabled(false)
                }
                base.evaluate()
            }
        }
    }
}
