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

@RunWith(AndroidJUnit4::class)
class ReportsNavigationTests {
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

    private fun navigateToReports() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Reports").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Spending Consistency").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_navigateToWeeklyReport_showsHeader() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Weekly Report"))
        composeTestRule.onNodeWithText("Weekly Report").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Income").assertIsDisplayed()
    }

    @Test
    fun test_navigateToMonthlyReport_showsHeader() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Monthly Report"))
        composeTestRule.onNodeWithText("Monthly Report").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Income").assertIsDisplayed()
    }

    @Test
    fun test_navigateToYearlyReport_showsHeader() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Yearly Report"))
        composeTestRule.onNodeWithText("Yearly Report").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total Spent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Income").assertIsDisplayed()
    }

    @Test
    fun test_reportScreen_swipeLeft_movesToNextPeriod() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Daily Report"))
        composeTestRule.onNodeWithText("Daily Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }

        val initialTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val initialTitleText = initialTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        
        // Wait for change
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val currentText = composeTestRule.onNodeWithTag("report_period_title").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
            currentText != initialTitleText
        }

        val nextTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val nextTitleText = nextTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialTitleText != nextTitleText) { "Date should have changed after swiping left." }
    }

    @Test
    fun test_reportScreen_swipeRight_movesToPreviousPeriod() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Daily Report"))
        composeTestRule.onNodeWithText("Daily Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }

        val initialTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val initialTitleText = initialTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        composeTestRule.onRoot().performTouchInput { swipeRight() }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val currentText = composeTestRule.onNodeWithTag("report_period_title").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
            currentText != initialTitleText
        }
        
        val nextTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val nextTitleText = nextTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialTitleText != nextTitleText) { "Date should have changed after swiping right." }
    }

    @Test
    fun test_reportScreen_swipeLeftThenRight_returnsToOriginal() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Daily Report"))
        composeTestRule.onNodeWithText("Daily Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }

        val initialTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val initialTitleText = initialTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val currentText = composeTestRule.onNodeWithTag("report_period_title").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
            currentText != initialTitleText
        }

        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val currentText = composeTestRule.onNodeWithTag("report_period_title").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
            currentText == initialTitleText
        }
        
        val finalTitleNode = composeTestRule.onNodeWithTag("report_period_title")
        val finalTitleText = finalTitleNode.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assert(initialTitleText == finalTitleText) { "Date should have returned to original after swiping right." }
    }

    @Test
    fun test_reportScreen_showsInsightCard() {
        navigateToReports()
        // Wait for reports to load
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Monthly Report"))
        composeTestRule.onNodeWithText("Monthly Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("time_period_report_lazy_column").performScrollToNode(hasText("Top Spend"))
        composeTestRule.onNodeWithText("Top Spend").assertExists()
    }

    @Test
    fun test_reportScreen_showsTransactionList() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Monthly Report"))
        composeTestRule.onNodeWithText("Monthly Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("time_period_report_lazy_column").performScrollToNode(hasText("Transactions in this Period"))
        composeTestRule.onNodeWithText("Transactions in this Period").assertIsDisplayed()
    }

    @Test
    fun test_reportScreen_emptyState_noTransactions() {
        navigateToReports()
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Daily Report"))
        composeTestRule.onNodeWithText("Daily Report").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
        }
        
        // Use a date far in the future by swiping a few times if necessary
        // Or simply wait, since the current day likely has no transactions.
        composeTestRule.onNodeWithTag("time_period_report_lazy_column").performScrollToNode(hasText("No transactions recorded for this period."))
        composeTestRule.onNodeWithText("No transactions recorded for this period.").assertIsDisplayed()
    }
}
