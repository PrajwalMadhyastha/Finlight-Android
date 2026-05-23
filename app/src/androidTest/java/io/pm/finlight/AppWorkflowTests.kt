// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/AppWorkflowTests.kt
// REASON: PHASE 1 — Harden existing tests by replacing hardcoded device-data
// references ("SBI", "Food") with deterministic seeded data via SeedDatabaseRule.
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
import java.util.UUID

/**
 * Instrumented UI test for common user workflows in the application.
 *
 * Phase 1 changes:
 * - Added [SeedDatabaseRule] to rule chain — guarantees "Test Bank" and
 *   "Food & Drinks" exist, replacing brittle references to live-device data
 *   ("SBI", "Food") that caused intermittent failures.
 */
@RunWith(AndroidJUnit4::class)
class AppWorkflowTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            // Seed known data BEFORE the activity is launched so account/category
            // dropdowns have deterministic entries.
            .around(SeedDatabaseRule())
            .around(
                GrantPermissionRule.grant(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    /**
     * Tests the "happy path" workflow of adding a new transaction and verifying
     * it appears on the dashboard.
     */
    @Test
    fun test_addNewTransaction_appearsOnDashboard() {
        val uniqueDescription = "Test Coffee Purchase ${UUID.randomUUID()}"

        // 1. Wait until the dashboard is fully loaded by checking for a stable element.
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Scroll to "Recent Transactions" and click the Add button.
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        // 3. Verify we are on the "Compose Transaction" screen and fill out the form.
        composeTestRule.onNodeWithText("Compose Transaction").assertIsDisplayed()

        // Click search icon to open Merchant BottomSheet
        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
        }
        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput(uniqueDescription)
        composeTestRule.onAllNodesWithText(uniqueDescription).onFirst().performClick()

        // Enter amount
        composeTestRule.onNodeWithTag("amount_text_field").performTextInput("150.0")

        // Select Account and Category names using robust testTags and onLast
        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onAllNodesWithText(TestDataSeeder.ACCOUNT_BANK_NAME).onLast().performClick()

        composeTestRule.onNodeWithTag("category_select_chip").performClick()
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()

        // 4. Save the transaction.
        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        // --- Wait for navigation back to the dashboard to complete ---
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Verify the new transaction appears in the "Recent Transactions" list.
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText(uniqueDescription))
        composeTestRule.onNodeWithText(uniqueDescription).assertExists()
    }

    /**
     * Tests the "sad path" workflow where a user tries to save a transaction
     * with invalid input (e.g., non-numeric amount) and sees an error.
     */
    @Test
    fun test_addTransaction_failsWithInvalidAmount_showsValidationError() {
        // 1. Wait for the dashboard and navigate to the Add Transaction Screen.
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        // 2. Fill out the form, but with an invalid (non-numeric) amount.
        composeTestRule.onNodeWithText("Compose Transaction").assertIsDisplayed()

        // Click search icon to open Merchant BottomSheet
        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
        }
        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput("Test Invalid Amount")
        composeTestRule.onAllNodesWithText("Test Invalid Amount").onFirst().performClick()

        // Enter amount
        composeTestRule.onNodeWithTag("amount_text_field").performTextInput("not-a-number")

        // Select Account and Category names using robust testTags and onLast
        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onAllNodesWithText(TestDataSeeder.ACCOUNT_BANK_NAME).onLast().performClick()

        composeTestRule.onNodeWithTag("category_select_chip").performClick()
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()

        // 3. Attempt to save the invalid transaction.
        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        // 4. Verify that the save button is disabled (amount is invalid) and we remain on the same screen.
        composeTestRule.onNodeWithText("Compose Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Transaction").assertIsNotEnabled()
    }
}

