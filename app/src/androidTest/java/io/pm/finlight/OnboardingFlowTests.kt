package io.pm.finlight

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import androidx.test.rule.GrantPermissionRule

class EnableOnboardingRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_seen_onboarding", false).commit()
                base.evaluate()
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTests {
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(EnableOnboardingRule())
        .around(DisableAppLockRule())
        .around(ClearDatabaseRule())
        .around(
            GrantPermissionRule.grant(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        )
        .around(composeTestRule)

    @Test
    fun test_onboarding_showsOnFreshInstall() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Welcome to Finlight").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Welcome to Finlight").assertIsDisplayed()
    }

    @Test
    fun test_onboarding_nextStepsProgress() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").assertIsEnabled().performClick()
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("What should we call you?").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("What should we call you?").assertIsDisplayed()
        
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Your Name").performTextInput("John Doe")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").assertIsEnabled().performClick()
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Set a Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Set a Monthly Budget").assertIsDisplayed()
    }

    @Test
    fun test_onboarding_completesAndNavigatesToDashboard() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").performClick() // to User Name
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Your Name").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Your Name").performTextInput("John Doe")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").performClick() // to Budget
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Set a Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").performClick() // to SMS permission
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Enable SMS Scanning").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Enable SMS Scanning").performClick() // grants permission via rule and advances
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").performClick() // to Completion
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Finish Setup").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Finish Setup").performClick() // Finishes and goes to dashboard
        
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithText("Recent Transactions").assertIsDisplayed()
    }

    @Test
    fun test_onboarding_backNavigation_worksCorrectly() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").performClick() // to User Name
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("What should we call you?").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("What should we call you?").assertIsDisplayed()
        
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Welcome to Finlight").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Welcome to Finlight").assertIsDisplayed()
    }
}
