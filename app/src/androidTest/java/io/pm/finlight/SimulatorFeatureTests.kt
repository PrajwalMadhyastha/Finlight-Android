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
import java.util.Calendar

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
        composeTestRule.waitForIdle()

        // Verify expense is added to the list
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Test Expense").fetchSemanticsNodes().isNotEmpty()
        }
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
        composeTestRule.waitForIdle()

        // Verify event is added to the list
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Test Event").fetchSemanticsNodes().isNotEmpty()
        }
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
        composeTestRule.waitForIdle()

        // Verify it is obfuscated
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("****", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
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
        composeTestRule.waitForIdle()

        // Verify it is obfuscated
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("****", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasText("****", substring = true)).onFirst().assertExists()
    }

    @Test
    fun test_navigationToAnnualSimulator_yearNavigation() {
        // Wait for dashboard
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to and Click on Annual Sandbox
        val lazyColumn = composeTestRule.onNodeWithTag("dashboard_lazy_column")
        lazyColumn.performScrollToNode(hasTestTag("btn_annual_simulator"))
        composeTestRule.onNodeWithTag("btn_annual_simulator").performClick()

        // Wait for screen
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Projected Annual Balance ($currentYear)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Projected Annual Balance ($currentYear)").assertIsDisplayed()

        // Click previous year button
        composeTestRule.onNodeWithTag("btn_prev_year").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Projected Annual Balance (${currentYear - 1})")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Projected Annual Balance (${currentYear - 1})").assertIsDisplayed()

        // Click next year button to return to current year
        composeTestRule.onNodeWithTag("btn_next_year").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Projected Annual Balance ($currentYear)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Projected Annual Balance ($currentYear)").assertIsDisplayed()
    }

    @Test
    fun test_navigationToWhatIfSimulator_deficitSpendingFormatting() {
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

        // Click Add
        composeTestRule.onNode(hasContentDescription("Add Expense")).performClick()

        // Fill dialog with massive amount to trigger deficit
        composeTestRule.onNodeWithText("Expense Name (e.g., Car EMI)").performTextReplacement("Luxury Vacation")
        composeTestRule.onNodeWithText("Amount").performTextReplacement("9999999")

        // Confirm
        composeTestRule.onAllNodesWithText("Add").onLast().performClick()
        composeTestRule.waitForIdle()

        // Verify deficit display without double negatives
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Deficit:", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("Deficit:", substring = true)).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("Deficit: -", substring = true)).assertCountEquals(0)
        composeTestRule.onNode(hasText("Simulated Deficit per Day", substring = true)).assertIsDisplayed()
    }
}
