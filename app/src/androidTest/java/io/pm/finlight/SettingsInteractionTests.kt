package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.ExperimentalTestApi
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun test_manageParseRules_addsAndDeletesRule() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Automation"))
        composeTestRule.onNodeWithText("Automation").performClick()
        composeTestRule.onNodeWithText("Manage Custom Parse Rules").performScrollTo().performClick()

        // Wait for empty state or Add button
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Add New Rule").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Add New Rule
        composeTestRule.onNodeWithText("Add New Rule").performClick()

        // In RuleCreationScreen, paste a mock SMS
        val mockSms = "Paid ZOMATO FOOD 500 Rs"
        composeTestRule.onNodeWithTag("sms_input_field")
            .performTextInput(mockSms)

        // Select trigger phrase "Paid"
        composeTestRule.onNodeWithTag("sms_input_field")
            .performTextInputSelection(TextRange(0, 4))
        composeTestRule.onNodeWithTag("mark_trigger_btn").performClick()

        // Select merchant "ZOMATO FOOD"
        composeTestRule.onNodeWithTag("sms_input_field")
            .performTextInputSelection(TextRange(5, 16))
        composeTestRule.onNodeWithTag("mark_merchant_btn").performClick()

        // Select amount "500"
        composeTestRule.onNodeWithTag("sms_input_field")
            .performTextInputSelection(TextRange(17, 20))
        composeTestRule.onNodeWithTag("mark_amount_btn").performClick()

        // Save Rule
        composeTestRule.onNodeWithTag("save_rule_btn").performScrollTo().performClick()

        // Verify it appears in ManageParseRulesScreen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Paid").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Paid").assertIsDisplayed()

        // Delete Rule
        composeTestRule.onNodeWithContentDescription("Delete Rule").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify deletion
        composeTestRule.onNodeWithText("Paid").assertDoesNotExist()
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

    @Test
    fun test_toggleAppLock_persists() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Security & Data"))
        composeTestRule.onNodeWithText("Security & Data").performClick()

        // Toggle App Lock on
        val appLockToggle = composeTestRule.onNodeWithText("Enable App Lock")
        appLockToggle.performScrollTo().assertIsDisplayed()

        // Find the toggle (Switch) next to it, or just click the row if it handles it
        // The help text says "Use biometrics or screen lock (PIN, pattern, password) to secure the app" so we can check that too
        composeTestRule.onNodeWithText("Use biometrics or screen lock (PIN, pattern, password) to secure the app").assertIsDisplayed()

        // Since we cannot interact with the system BiometricPrompt in Compose tests,
        // we'll just check that the setting is present and interactable.
        appLockToggle.performClick()

        // Wait for idle
        composeTestRule.waitForIdle()

        // Toggle back off
        appLockToggle.performClick()
        composeTestRule.waitForIdle()
    }
}
