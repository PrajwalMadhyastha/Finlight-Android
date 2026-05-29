package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsInteractionTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            .around(ClearDatabaseRule())
            .around(SeedDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    private fun navigateToProfile() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()
    }

    @Test
    fun test_toggleDarkMode_persistsAcrossRestart() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Theme & Appearance"))
        composeTestRule.onNodeWithText("Theme & Appearance").performClick()

        // Tap "Midnight"
        composeTestRule.onNodeWithText("Midnight").performScrollTo().performClick()

        // Restart the activity
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Verify we are still in appearance settings after recreate
        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun test_exportData_csvOptionAvailable() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Security & Data"))
        composeTestRule.onNodeWithText("Security & Data").performClick()

        composeTestRule.onNodeWithText("Export Transactions (CSV)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test_manageParseRules_showsEmptyState() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Automation"))
        composeTestRule.onNodeWithText("Automation").performClick()
        composeTestRule.onNodeWithText("Manage Custom Parse Rules").performScrollTo().performClick()

        composeTestRule.onNodeWithText("No custom parsing rules have been created yet.").assertIsDisplayed()
    }

    @Test
    fun test_manageIgnoreRules_addsAndDeletesRule() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Automation"))
        composeTestRule.onNodeWithText("Automation").performClick()
        composeTestRule.onNodeWithText("Manage Parser Ignore List").performScrollTo().performClick()

        // Switch to "Sender" rule
        composeTestRule.onNodeWithText("Sender").performClick()

        // Enter a rule
        val textInput = composeTestRule.onNodeWithText("Sender pattern to ignore")
        textInput.performTextInput("TEST_SENDER")

        // Click Add Rule button
        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()

        // Verify it was added
        composeTestRule.onNodeWithText("TEST_SENDER").assertIsDisplayed()

        // Click Delete rule button
        // In case there are multiple, click the first one
        composeTestRule.onAllNodesWithContentDescription("Delete rule", ignoreCase = true)[0].performClick()

        // Confirm deletion
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify it was deleted
        composeTestRule.onNodeWithText("TEST_SENDER").assertDoesNotExist()
    }
}
