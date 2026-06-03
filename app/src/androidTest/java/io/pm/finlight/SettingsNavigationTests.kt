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
class SettingsNavigationTests {
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

    private fun navigateToProfile() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()
    }

    @Test
    fun test_navigateToAppearanceSettings() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Theme & Appearance"))
        composeTestRule.onNodeWithText("Theme & Appearance").performClick()

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun test_navigateToAutomationSettings() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Automation"))
        composeTestRule.onNodeWithText("Automation").performClick()

        composeTestRule.onNodeWithText("Scan Full Inbox").assertIsDisplayed()
    }

    @Test
    fun test_navigateToNotificationSettings() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Notifications"))
        composeTestRule.onNodeWithText("Notifications").performClick()

        composeTestRule.onNodeWithText("Auto-Captured Transactions").assertIsDisplayed()
    }

    @Test
    fun test_navigateToDataSettings() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Security & Data"))
        composeTestRule.onNodeWithText("Security & Data").performClick()

        composeTestRule.onNodeWithText("Enable App Lock").assertIsDisplayed()
    }

    @Test
    fun test_navigateToCurrencyTravelSettings() {
        navigateToProfile()
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Currency & Travel"))
        composeTestRule.onNodeWithText("Currency & Travel").performClick()

        composeTestRule.onNodeWithText("Default Currency").assertIsDisplayed()
    }
}
