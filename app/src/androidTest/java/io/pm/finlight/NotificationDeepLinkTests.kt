package io.pm.finlight

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeepLinkTests {
    private val baseIntent =
        Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java
        ).apply {
            action = Intent.ACTION_VIEW
        }

    // Use createEmptyComposeRule so we can launch the Activity with a custom Intent
    // inside the test methods.
    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createEmptyComposeRule()

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
                )
            )

    @Test
    fun test_deepLinkToReportsScreen() {
        val intent =
            Intent(baseIntent).apply {
                data = Uri.parse("app://finlight.pm.io/reports")
            }

        val scenario = ActivityScenario.launch<MainActivity>(intent)

        try {
            // Wait for splash screen to clear and the destination to load
            composeTestRule.waitUntil(timeoutMillis = 15000) {
                composeTestRule.onAllNodesWithText("Reports").fetchSemanticsNodes().isNotEmpty() ||
                    composeTestRule.onAllNodesWithText("Spending Consistency").fetchSemanticsNodes().isNotEmpty()
            }

            // Check that we are on the Reports screen (Spending Consistency is a key card on Reports)
            composeTestRule.onNodeWithText("Spending Consistency", substring = true).assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun test_deepLinkToReportScreen() {
        // Deep link to a specific report (e.g. monthly for a specific date)
        // Uses the registered deep link: app://finlight.pm.io/report/{timePeriod}?date={date}&showPreviousMonth={showPreviousMonth}
        val intent =
            Intent(baseIntent).apply {
                data = Uri.parse("app://finlight.pm.io/report/MONTHLY?date=1704067200000&showPreviousMonth=false")
            }

        val scenario = ActivityScenario.launch<MainActivity>(intent)

        try {
            // Wait for the TimePeriodReportScreen to load (it will have the Spending Summary card)
            composeTestRule.waitUntil(timeoutMillis = 15000) {
                composeTestRule.onAllNodesWithText("Total Spent").fetchSemanticsNodes().isNotEmpty()
            }

            // Verify the screen actually loaded
            composeTestRule.onNodeWithText("Total Spent", substring = true).assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun test_deepLinkToTransactionDetailScreen() {
        // Deep link to transaction detail (transaction ID 1 is seeded by SeedDatabaseRule)
        val intent =
            Intent(baseIntent).apply {
                data = Uri.parse("app://finlight.pm.io/transaction_detail/1")
            }

        val scenario = ActivityScenario.launch<MainActivity>(intent)

        try {
            // Wait for the TransactionDetailScreen to load
            composeTestRule.waitUntil(timeoutMillis = 15000) {
                composeTestRule.onAllNodesWithTag("transaction_detail_lazy_column").fetchSemanticsNodes().isNotEmpty()
            }

            // Verify the screen actually loaded
            composeTestRule.onNodeWithTag("transaction_detail_lazy_column").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
