package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulatorFeatureTests {
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
    fun test_navigationToWhatIfSimulator_addsExpense() {
        // Wait for dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to and Click on Monthly What-If
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        lazyColumn.performScrollToNode(hasTestTag("btn_monthly_simulator"))
        composeTestRule.onNodeWithTag("btn_monthly_simulator").performClick()

        // Wait for screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Sandbox Mode").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Sandbox Mode").assertIsDisplayed()

        // Click Add
        composeTestRule.onNode(hasContentDescription("Add Expense")).performClick()

        // Fill dialog
        composeTestRule.onNodeWithText("Expense Name (e.g., Car EMI)").performTextReplacement("Test Expense")
        composeTestRule.onNodeWithText("Amount").performTextReplacement("500")

        // Confirm
        composeTestRule.onAllNodesWithText("Add").onLast().performClick()
        Thread.sleep(2000)

        // Verify expense is added to the list
        composeTestRule.onNodeWithTag("what_if_lazy_column").performScrollToNode(hasText("Test Expense"))
        composeTestRule.onNodeWithText("Test Expense").assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("500", substring = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun test_navigationToAnnualSimulator_addsLifeEvent() {
        // Wait for dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to and Click on Annual Sandbox
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        lazyColumn.performScrollToNode(hasTestTag("btn_annual_simulator"))
        composeTestRule.onNodeWithTag("btn_annual_simulator").performClick()

        // Wait for screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Life Events")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasText("Life Events")).onFirst().assertIsDisplayed()

        // Click Add
        composeTestRule.onNode(hasContentDescription("Add Event")).performClick()

        // Fill dialog
        composeTestRule.onNodeWithText("Event Name (e.g., Buy a Car)").performTextReplacement("Test Event")
        composeTestRule.onNodeWithText("Amount").performTextReplacement("1000")

        // Confirm
        composeTestRule.onAllNodesWithText("Add").onLast().performClick()
        Thread.sleep(2000)

        // Verify event is added to the list
        composeTestRule.onNodeWithTag("annual_lazy_column").performScrollToNode(hasText("Test Event"))
        composeTestRule.onNodeWithText("Test Event").assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("1,000", substring = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun test_navigationToWhatIfSimulator_togglesPrivacyMode() {
        // Wait for dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to and Click on Monthly What-If
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        lazyColumn.performScrollToNode(hasTestTag("btn_monthly_simulator"))
        composeTestRule.onNodeWithTag("btn_monthly_simulator").performClick()

        // Wait for screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Sandbox Mode").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially, the simulated spent amount should be visible (e.g. ₹0)
        composeTestRule.onAllNodes(hasText("₹", substring = true)).onFirst().assertExists()

        // Click Privacy Toggle
        composeTestRule.onNode(hasContentDescription("Toggle Privacy")).performClick()
        Thread.sleep(2000)

        // Verify it is obfuscated
        composeTestRule.onAllNodes(hasText("****", substring = true)).onFirst().assertExists()
    }

    @Test
    fun test_navigationToAnnualSimulator_togglesPrivacyMode() {
        // Wait for dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to and Click on Annual Sandbox
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        lazyColumn.performScrollToNode(hasTestTag("btn_annual_simulator"))
        composeTestRule.onNodeWithTag("btn_annual_simulator").performClick()

        // Wait for screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Life Events")).fetchSemanticsNodes().isNotEmpty()
        }

        // Click Privacy Toggle
        composeTestRule.onNode(hasContentDescription("Toggle Privacy")).performClick()
        Thread.sleep(2000)

        // Verify it is obfuscated
        composeTestRule.onAllNodes(hasText("****", substring = true)).onFirst().assertExists()
    }
}
