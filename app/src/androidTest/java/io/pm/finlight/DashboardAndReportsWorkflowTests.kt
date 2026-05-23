// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/DashboardAndReportsWorkflowTests.kt
// REASON: PHASE 1 — Harden existing dashboard tests and add new report navigation
// tests for Weekly, Monthly and Yearly report screens.
// Changes:
//   - Added SeedDatabaseRule so "Recent Transactions" card and budget watch have
//     deterministic data to display.
//   - Added test_navigationToWeeklyReport_showsCorrectHeader
//   - Added test_navigationToMonthlyReport_showsCorrectHeader
//   - Added test_navigationToYearlyReport_showsCorrectHeader
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
 * Instrumented UI tests for the "Project Aurora" dashboard and the
 * time-period based reporting screens.
 *
 * Phase 1 changes:
 * - Added [SeedDatabaseRule] so dashboard cards that rely on data (Budget Watch,
 *   Recent Transactions) are populated deterministically.
 * - Added three new navigation tests for Weekly, Monthly, and Yearly reports.
 */
@RunWith(AndroidJUnit4::class)
class DashboardAndReportsWorkflowTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            // Seed known data so report/budget cards render predictably.
            .around(SeedDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    // -------------------------------------------------------------------------
    // Dashboard tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that the main "Project Aurora" dashboard cards are displayed on launch.
     */
    @Test
    fun test_auroraDashboard_displaysAllDefaultCards() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        val expectedCardContent =
            listOf(
                "View Trends", // Content from Quick Actions card
                "Recent Transactions",
                "Accounts",
                "Budget Watch",
                "Yearly Spending Consistency",
            )

        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        expectedCardContent.forEach { contentText ->
            lazyColumn.performScrollToNode(hasText(contentText))
            composeTestRule.onNodeWithText(contentText).assertIsDisplayed()
        }
    }

    // -------------------------------------------------------------------------
    // Reports navigation tests — Daily
    // -------------------------------------------------------------------------

    private fun navigateToReports() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Reports").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Spending Consistency").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Tests navigation from the main Reports screen to the Daily Report screen
     * and verifies the header content.
     */
    @Test
    fun test_navigationToDailyReport_showsCorrectHeader() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Daily Report"))
        composeTestRule.onNodeWithText("Daily Report").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Reports navigation tests — Weekly  (NEW in Phase 1)
    // -------------------------------------------------------------------------

    /**
     * Tests navigation from the Reports screen to the Weekly Report screen
     * and verifies that the core summary card elements are rendered.
     */
    @Test
    fun test_navigationToWeeklyReport_showsCorrectHeader() {
        // 1. Navigate to the Reports screen
        navigateToReports()

        // 2. Scroll to and click the "Weekly Report" navigation card
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Weekly Report"))
        composeTestRule.onNodeWithText("Weekly Report").performClick()

        // 3. The SpendingSummaryCard is always present on the TimePeriodReportScreen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Income").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Reports navigation tests — Monthly  (NEW in Phase 1)
    // -------------------------------------------------------------------------

    /**
     * Tests navigation from the Reports screen to the Monthly Report screen
     * and verifies that the core summary card elements are rendered.
     */
    @Test
    fun test_navigationToMonthlyReport_showsCorrectHeader() {
        navigateToReports()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Monthly Report"))
        composeTestRule.onNodeWithText("Monthly Report").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Income").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Reports navigation tests — Yearly  (NEW in Phase 1)
    // -------------------------------------------------------------------------

    /**
     * Tests navigation from the Reports screen to the Yearly Report screen
     * and verifies that the core summary card elements are rendered.
     */
    @Test
    fun test_navigationToYearlyReport_showsCorrectHeader() {
        navigateToReports()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Yearly Report"))
        composeTestRule.onNodeWithText("Yearly Report").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Income").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Swipe gesture tests
    // -------------------------------------------------------------------------

    /**
     * Tests the swipe gestures on the TimePeriodReportScreen to navigate
     * between different days — verifies that a left swipe changes the date
     * and a right swipe restores the original date.
     */
    @Test
    fun test_swipeGestures_onReportScreen_changeDate() {
        // 1. Navigate to the Daily Report screen.
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Daily Report"))
        composeTestRule.onNodeWithText("Daily Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Get the initial date text from the title using its test tag.
        val initialTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val initialTitleText = initialTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        // 3. Perform a swipe left gesture to move to the next day.
        composeTestRule.onRoot().performTouchInput { swipeLeft() }

        // 4. Verify the date in the title has changed.
        val nextTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val nextTitleText = nextTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialTitleText != nextTitleText) { "Date should have changed after swiping left." }

        // 5. Perform a swipe right gesture to move back to the previous day.
        composeTestRule.onRoot().performTouchInput { swipeRight() }

        // 6. Verify the date has returned to the initial date.
        val finalTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val finalTitleText = finalTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialTitleText == finalTitleText) { "Date should have returned to the original after swiping right." }
    }
}
