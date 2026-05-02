// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/DashboardAndReportsWorkflowTests.kt
// REASON: FIX - Removed unused imports for SimpleDateFormat and java.util.* to
// resolve lint warnings.
// =================================================================================
package io.pm.finlight

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the "Project Aurora" dashboard and the new
 * time-period based reporting screens.
 */
@RunWith(AndroidJUnit4::class)
class DashboardAndReportsWorkflowTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            .around(
                GrantPermissionRule.grant(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    /**
     * Verifies that the main "Project Aurora" dashboard cards are displayed on launch.
     */
    @Test
    fun test_auroraDashboard_displaysAllDefaultCards() {
        // Wait for the dashboard to load by checking for the hero card's title.
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // --- FIX: Create a list of expected content and scroll to each one individually ---
        // This is more robust than a single swipe, as it ensures each item is found
        // before the test proceeds.
        val expectedCardContent =
            listOf(
                "Monthly Budget",
                "View Trends", // Content from Quick Actions card
                "Net Worth",
                "Recent Transactions",
                "Accounts",
                "Budget Watch",
            )

        // Find the scrollable container using its test tag.
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")

        // Iterate through the expected content, scrolling to and verifying each one.
        expectedCardContent.forEach { contentText ->
            lazyColumn.performScrollToNode(hasText(contentText))
            composeTestRule.onNodeWithText(contentText).assertIsDisplayed()
        }
    }

    /**
     * Tests navigation from the main reports screen to the Daily Report screen
     * and verifies the header content.
     */
    @Test
    fun test_navigationToDailyReport_showsCorrectHeader() {
        // 1. Navigate from the dashboard to the Reports screen via the bottom nav.
        composeTestRule.onNodeWithText("Reports").performClick()

        // 2. On the reports screen, click the "Daily Report" card.
        composeTestRule.onNodeWithText("Daily Report").performClick()

        // 3. Verify we are on the "Daily Report" screen.
        composeTestRule.onNodeWithText("Daily Report").assertIsDisplayed()

        // 4. Verify the "Hero" card and "Insights" card are displayed.
        // We check for "Total Spent" which is in the hero card.
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        // We check for "Change" which is in the insights card.
        composeTestRule.onNodeWithText("Change").assertIsDisplayed()
    }

    /**
     * Tests the swipe gestures on the TimePeriodReportScreen to navigate
     * between different days.
     */
    @Test
    fun test_swipeGestures_onReportScreen_changeDate() {
        // 1. Navigate to the Daily Report screen.
        composeTestRule.onNodeWithText("Reports").performClick()
        composeTestRule.onNodeWithText("Daily Report").performClick()
        composeTestRule.onNodeWithText("Daily Report").assertIsDisplayed()

        // 2. Get the initial date text from the subtitle.
        val initialSubtitleNode = composeTestRule.onNodeWithText("Since", substring = true)
        val initialSubtitleText = initialSubtitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        // 3. Perform a swipe left gesture to move to the next day.
        composeTestRule.onRoot().performTouchInput { swipeLeft() }

        // 4. Verify the date in the subtitle has changed.
        val nextSubtitleNode = composeTestRule.onNodeWithText("Since", substring = true)
        val nextSubtitleText = nextSubtitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialSubtitleText != nextSubtitleText) { "Date should have changed after swiping left." }

        // 5. Perform a swipe right gesture to move back to the previous day.
        composeTestRule.onRoot().performTouchInput { swipeRight() }

        // 6. Verify the date has returned to the initial date.
        val finalSubtitleNode = composeTestRule.onNodeWithText("Since", substring = true)
        val finalSubtitleText = finalSubtitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialSubtitleText == finalSubtitleText) { "Date should have returned to the original after swiping right." }
    }
}
