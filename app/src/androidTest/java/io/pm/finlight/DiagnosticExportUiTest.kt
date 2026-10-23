package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Instrumented UI test for the privacy-redacted diagnostic export workflow.
 * Verifies navigation to Settings -> Security & Data -> Diagnostic Report & Logs,
 * transparency callout notice display, filter chip toggles, and copy action.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticExportUiTest {
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

    private fun navigateToDataSettings() {
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("profile_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasText("Security & Data"))
        composeTestRule.onNodeWithText("Security & Data").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Diagnostic Report & Logs").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_diagnosticReportPreviewSheet_opensAndDisplaysRedactedReport() {
        navigateToDataSettings()

        // Scroll to and click Diagnostic Report preview
        composeTestRule.onNodeWithTag("data_settings_lazy_column")
            .performScrollToNode(hasText("Diagnostic Report & Logs"))
        composeTestRule.onNodeWithText("Diagnostic Report & Logs").performClick()

        // Verify bottom sheet or section chip appears
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Device OS").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify transparency callout
        composeTestRule.onNodeWithText(
            "Zero-Cloud & Redacted: No account numbers, balances, or SMS texts are exposed. All data stays local until shared.",
        ).assertIsDisplayed()

        // Verify section toggles are visible
        composeTestRule.onNodeWithText("Device OS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Health Metrics").assertIsDisplayed()

        // Verify Copy and Share buttons are displayed and clickable
        composeTestRule.onNodeWithText("Share")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Copy")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }
}
