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
class HelpSystemIntegrationTest {
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
            .around(ResetPrivacyModeRule())
            .around(composeTestRule)

    @Test
    fun test_helpIconOpensHelpBottomSheet_onWhatIfSimulatorScreen() {
        // Wait for dashboard to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to and Click on Monthly What-If
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        lazyColumn.performScrollToNode(hasTestTag("btn_monthly_simulator"))
        composeTestRule.onNodeWithTag("btn_monthly_simulator").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Sandbox Mode").fetchSemanticsNodes().isNotEmpty()
        }

        // Find and click the help icon
        composeTestRule.onNodeWithContentDescription("Help").performClick()

        // Assert that the HelpBottomSheet is displayed with the correct content
        composeTestRule.onNodeWithText("Explore hypothetical scenarios", substring = true).assertIsDisplayed()
    }
}
