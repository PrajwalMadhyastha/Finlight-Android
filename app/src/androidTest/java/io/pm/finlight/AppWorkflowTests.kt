// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/AppWorkflowTests.kt
// REASON: FIX - Removed several unused import directives to resolve lint warnings
// and clean up the file.
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
 * NOTE: This file has been updated to include a custom TestRule to bypass the
 * onboarding screen, ensuring tests start in a consistent state.
 */
@RunWith(AndroidJUnit4::class)
class AppWorkflowTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            // --- NEW: This rule runs first to bypass the onboarding screen ---
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
     * Tests the "happy path" workflow of adding a new transaction and verifying
     * it appears on the dashboard.
     */
    @Test
    fun test_addNewTransaction_appearsOnDashboard() {
        val uniqueDescription = "Test Coffee Purchase ${UUID.randomUUID()}"

        // 1. Wait until the dashboard is fully loaded by checking for a stable element.
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Click the main FAB to add a new item.
        composeTestRule.onNodeWithContentDescription("Add").performClick()

        // 3. Verify we are on the "Add Transaction" screen and fill out the form.
        composeTestRule.onNodeWithText("Add Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description").performTextInput(uniqueDescription)
        composeTestRule.onNodeWithText("Amount").performTextInput("150.0")
        composeTestRule.onNodeWithText("Select Account").performClick()
        composeTestRule.onNodeWithText("SBI").performClick()
        composeTestRule.onNodeWithText("Select Category").performClick()
        composeTestRule.onNodeWithText("Food").performClick()

        // 4. Save the transaction.
        composeTestRule.onNodeWithText("Save Transaction").performClick()

        // --- Wait for navigation back to the dashboard to complete ---
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Verify the new transaction appears in the "Recent Transactions" list.
        val newNode = composeTestRule.onNodeWithText(uniqueDescription, useUnmergedTree = true)
        newNode.performScrollTo()
        newNode.assertIsDisplayed()
    }

    /**
     * Tests the "sad path" workflow where a user tries to save a transaction
     * with invalid input (e.g., non-numeric amount) and sees an error.
     */
    @Test
    fun test_addTransaction_failsWithInvalidAmount_showsValidationError() {
        // 1. Wait for the dashboard and navigate to the Add Transaction Screen.
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Add").performClick()

        // 2. Fill out the form, but with an invalid (non-numeric) amount.
        composeTestRule.onNodeWithText("Add Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description").performTextInput("Test Invalid Amount")
        composeTestRule.onNodeWithText("Amount").performTextInput("not-a-number")
        composeTestRule.onNodeWithText("Select Account").performClick()
        composeTestRule.onNodeWithText("SBI").performClick()
        composeTestRule.onNodeWithText("Select Category").performClick()
        composeTestRule.onNodeWithText("Food").performClick()

        // 3. Attempt to save the invalid transaction.
        composeTestRule.onNodeWithText("Save Transaction").performClick()

        // 4. Verify the validation error.
        composeTestRule.onNodeWithText("Add Transaction").assertIsDisplayed()
        val expectedError = "Please enter a valid, positive amount."
        composeTestRule.onNodeWithText(expectedError).assertIsDisplayed()
    }
}
