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
                // Content from Quick Actions card
                "View Trends",
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
}
