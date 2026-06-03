package io.pm.finlight

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileTests {
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            .around(ClearDatabaseRule())
            .around(SeedDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    @Test
    fun test_profile_displaysAccountSummary() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()
        // Ensure user profile elements are displayed
        composeTestRule.onNodeWithText("Edit Profile").assertIsDisplayed()
    }

    @Test
    fun test_editProfile_updatesDisplayName() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()
        composeTestRule.onNodeWithText("Edit Profile").performClick()

        composeTestRule.onNodeWithText("Your Name").performTextClearance()
        composeTestRule.onNodeWithText("Your Name").performTextInput("Jane Doe")
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for pop back stack
        composeTestRule.onNodeWithText("Jane Doe").assertIsDisplayed()
    }

    @Test
    fun test_profile_navigatesToSettings() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()
        // Scroll to "Theme & Appearance"
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Theme & Appearance"))
        composeTestRule.onNodeWithText("Theme & Appearance").performClick()

        // Assuming "Theme" or "Appearance" related text is shown in the settings screen
        // Wait for screen to navigate
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Theme").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }
}
