package io.pm.finlight

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            .around(SeedDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    @Test
    fun test_bottomNav_allFourTabsLoad() {
        // Wait for dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Dashboard").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap Transactions
        composeTestRule.onNodeWithTag("nav_item_Transactions").performClick()
        composeTestRule.onAllNodesWithText("Transactions").onFirst().assertIsDisplayed()

        // Tap Reports
        composeTestRule.onNodeWithTag("nav_item_Reports").performClick()
        composeTestRule.onAllNodesWithText("Reports").onFirst().assertIsDisplayed()

        // Tap Profile
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()
        composeTestRule.onAllNodesWithText("Profile").onFirst().assertIsDisplayed()

        // Back to Dashboard
        composeTestRule.onNodeWithTag("nav_item_Dashboard").performClick()
        composeTestRule.onAllNodesWithText("Dashboard").onFirst().assertIsDisplayed()
    }

    @Test
    fun test_backStack_fromDetailToList() {
        // Go to Transactions
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Transactions").performClick()

        // Find any seeded transaction visible on screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodes(
                hasText("Test", substring = true, ignoreCase = true),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasText("Test", substring = true, ignoreCase = true), useUnmergedTree = true).onFirst()
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // Verify we are on detail screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Exclude from Totals").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Exclude from Totals").onFirst().assertExists()

        // Go back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Verify we are back on the list
        composeTestRule.onAllNodesWithText("Transactions").onFirst().assertIsDisplayed()
    }

    @Test
    fun test_backStack_fromSettingsToProfile() {
        // Go to Profile
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()

        // Go to Settings -> Theme & Appearance
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Theme & Appearance"))
        composeTestRule.onNodeWithText("Theme & Appearance").performClick()

        // Verify inside Theme settings
        composeTestRule.onAllNodesWithText("Theme").onFirst().assertIsDisplayed()

        // Go back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Verify back on Profile
        composeTestRule.onAllNodesWithText("Profile").onFirst().assertIsDisplayed()
    }

    @Test
    fun test_fabPresent_onAllMainScreens() {
        // Dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Dashboard").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction", useUnmergedTree = true).assertIsDisplayed()

        // Transactions
        composeTestRule.onNodeWithTag("nav_item_Transactions").performClick()
        composeTestRule.onNodeWithContentDescription("Add Transaction", useUnmergedTree = true).assertIsDisplayed()
    }

    @org.junit.Ignore("App is locked to portrait mode in AndroidManifest.xml. Forcing landscape in tests causes flaky lifecycle thrashing.")
    @Test
    fun test_noUnhandledCrash_onScreenRotation() {
        // Wait for dashboard to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Dashboard").fetchSemanticsNodes().isNotEmpty()
        }

        // Rotate to landscape
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // Wait for composition to settle
        composeTestRule.waitForIdle()

        // Verify dashboard is still displayed (meaning no crash)
        composeTestRule.onAllNodesWithText("Dashboard").onFirst().assertIsDisplayed()

        // Navigate to another screen (Transactions)
        composeTestRule.onNodeWithTag("nav_item_Transactions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Transactions").onFirst().assertIsDisplayed()

        // Rotate back to portrait
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        composeTestRule.waitForIdle()

        // Still on Transactions
        composeTestRule.onAllNodesWithText("Transactions").onFirst().assertIsDisplayed()
    }
}
