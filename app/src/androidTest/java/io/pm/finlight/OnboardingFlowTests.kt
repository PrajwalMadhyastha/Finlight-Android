package io.pm.finlight

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

class EnableOnboardingRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                kotlinx.coroutines.runBlocking {
                    val settingsRepository = SettingsRepository(context)
                    settingsRepository.setHasSeenOnboarding(false)
                }
                base.evaluate()
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTests {
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(EnableOnboardingRule())
            .around(DisableAppLockRule())
            .around(ClearDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ),
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
            composeTestRule.onAllNodesWithText("Not Now").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Not Now").onFirst().performScrollTo().performClick() // advances to Notification page

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Stay Updated").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Not Now").onLast().performScrollTo().performClick() // advances to Completion

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
    fun test_onboarding_invalidName_showsErrorAndDisablesNext() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").performClick() // to User Name

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Your Name").fetchSemanticsNodes().isNotEmpty()
        }

        // Enter a name with special characters
        composeTestRule.onNodeWithText("Your Name").performTextInput("John!")
        composeTestRule.waitForIdle()

        // Verify the error message is displayed and Next is disabled
        composeTestRule.onNodeWithText("Name can only contain letters and spaces").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()

        // Clear and enter a valid name
        composeTestRule.onNodeWithContentDescription("Clear Name").performClick()
        composeTestRule.onNodeWithText("Your Name").performTextInput("John")
        composeTestRule.waitForIdle()

        // Verify the error message is gone and Next is enabled
        composeTestRule.onNodeWithText("Name can only contain letters and spaces").assertDoesNotExist()
        composeTestRule.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun test_onboarding_clearButton_worksCorrectly() {
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").performClick() // to User Name

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Your Name").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially no clear button
        composeTestRule.onNodeWithContentDescription("Clear Name").assertDoesNotExist()

        // Type text
        composeTestRule.onNodeWithText("Your Name").performTextInput("John Doe")
        composeTestRule.waitForIdle()

        // Clear button should appear and we click it
        composeTestRule.onNodeWithContentDescription("Clear Name").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Value should be empty (Next is disabled)
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()

        // Type a name again to proceed
        composeTestRule.onNodeWithText("Your Name").performTextInput("Jane")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").performClick() // to Budget

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Set a Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // Test budget clear
        composeTestRule.onNodeWithContentDescription("Clear Budget").assertDoesNotExist()
        composeTestRule.onNodeWithText("Total Monthly Budget").performTextInput("5000")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Clear Budget").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Ensure that there is no clear button after clearing
        composeTestRule.onNodeWithContentDescription("Clear Budget").assertDoesNotExist()
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
