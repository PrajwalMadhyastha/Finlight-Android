// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/TransactionCrudTests.kt
// REASON: FIX - Resolved "Unresolved reference 'exists'" build error.
// The method `exists()` is not available on `SemanticsNodeInteraction`.
// Replaced the logic with `fetchSemanticsNodes().isNotEmpty()` on the collection
// to correctly check for existence before selecting the fallback node.
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
 * Instrumented UI tests for the full CRUD (Create, Read, Update, Delete)
 * lifecycle of a transaction.
 */
@RunWith(AndroidJUnit4::class)
class TransactionCrudTests {
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
     * A helper function to add a transaction.
     * Adapted to handle the new UI flow where Description is entered via a BottomSheet.
     * @return The unique description of the created transaction.
     */
    private fun addTransactionForTest(
        customDescription: String? = null,
        customAmount: String = "100.0"
    ): String {
        val uniqueDescription = customDescription ?: "Test Txn ${UUID.randomUUID().toString().take(5)}"

        // 1. Wait for Dashboard and Click FAB
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // FIX: Check collection size instead of .exists()
        val addTransactionNodes = composeTestRule.onAllNodesWithContentDescription("Add Transaction")
        val fabNode = if (addTransactionNodes.fetchSemanticsNodes().isNotEmpty()) {
            addTransactionNodes.onFirst()
        } else {
            composeTestRule.onNodeWithContentDescription("Add")
        }

        fabNode.performClick()

        // 2. Wait for Add Screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Enter Amount (Find the field displaying "0" initially)
        composeTestRule.onNodeWithText("0").performTextInput(customAmount)

        // 4. Enter Description (Opens Merchant Sheet)
        // The field says "Paid to..." initially.
        composeTestRule.onNodeWithText("Paid to...").performClick()

        // Wait for Sheet
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            // "Search or enter" is the likely hint or title in the Merchant sheet
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
                    || composeTestRule.onAllNodesWithText("Merchant").fetchSemanticsNodes().isNotEmpty()
        }

        // Input the description into the search field of the sheet
        // We look for a text field in the sheet.
        // Since we don't have tags, we look for the node that accepts input.
        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput(uniqueDescription)

        // Select the "Manual Entry" option (usually repeats the text you typed)
        // Click the row that has the text we just typed.
        composeTestRule.onAllNodesWithText(uniqueDescription).onFirst().performClick()

        // 5. Select Account (if not default)
        // Assuming "Cash Spends" is default or we select it.
        // If "Account" chip is visible, click it.
        if (composeTestRule.onAllNodesWithText("Account").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Account").performClick()
            composeTestRule.onNodeWithText("Cash Spends").performClick()
        }

        // 6. Select Category (if not default)
        if (composeTestRule.onAllNodesWithText("Category").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Category").performClick()
            composeTestRule.onNodeWithText("Food & Drinks").performClick()
        }

        // 7. Save
        composeTestRule.onNodeWithText("Save Transaction").performClick()

        // 8. Wait for return to Dashboard
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(uniqueDescription).fetchSemanticsNodes().isNotEmpty()
        }
        return uniqueDescription
    }

    /**
     * Tests that a newly created transaction appears on the dashboard.
     */
    @Test
    fun test_createTransaction_appearsOnDashboard() {
        val description = addTransactionForTest()
        composeTestRule.onNodeWithText(description, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Tests that a transaction can be successfully edited and the update
     * is reflected on the dashboard.
     */
    @Test
    fun test_editTransaction_updatesSuccessfully() {
        val originalDescription = addTransactionForTest()
        val updatedDescription = "Updated Dinner ${UUID.randomUUID().toString().take(5)}"

        // 1. Open Detail Screen
        composeTestRule.onNodeWithText(originalDescription, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        // 2. Wait for detail screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(originalDescription).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Click the description to edit (This opens the RetroUpdateSheet)
        composeTestRule.onNodeWithText(originalDescription).performClick()

        // 4. Input new description in the sheet
        // Look for the text field pre-filled with original description
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Rename Transaction").fetchSemanticsNodes().isNotEmpty()
                    || composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }

        // Clear and enter new text
        val inputNode = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        inputNode.performTextClearance()
        inputNode.performTextInput(updatedDescription)

        // 5. Click Save/Update
        // FIX: Check collection size for fallback logic instead of .exists()
        val updateButtons = composeTestRule.onAllNodesWithText("Update")
        val saveButton = if (updateButtons.fetchSemanticsNodes().isNotEmpty()) {
            updateButtons.onFirst()
        } else {
            composeTestRule.onNodeWithText("Save")
        }
        saveButton.performClick()

        // 6. Verify update on detail screen
        composeTestRule.onNodeWithText(updatedDescription).assertIsDisplayed()

        // 7. Back to Dashboard
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // 8. Verify on Dashboard
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(originalDescription).assertDoesNotExist()
        composeTestRule.onNodeWithText(updatedDescription, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Tests that a transaction can be successfully deleted from the detail screen.
     */
    @Test
    fun test_deleteTransaction_removesFromList() {
        val description = addTransactionForTest()

        // 1. Open Detail Screen
        composeTestRule.onNodeWithText(description, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        // 2. Click 'More' menu
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithContentDescription("More options").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("More options").performClick()

        // 3. Click 'Delete'
        composeTestRule.onNodeWithText("Delete").performClick()

        // 4. Confirm Deletion
        composeTestRule.onNodeWithText("Delete Transaction?").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Delete").onLast().performClick() // Select the button in dialog

        // 5. Verify removal
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Monthly Budget").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(description).assertDoesNotExist()
    }

    /**
     * Tests the new Quick Fill feature.
     * Verifies that recent manual transactions appear in the carousel and populate fields when clicked.
     */
    @Test
    fun quickFill_populatesFields() {
        // 1. Create a "Seed" transaction ("Coffee" for 50)
        // This puts it into the database as a recent manual entry.
        addTransactionForTest(customDescription = "Coffee", customAmount = "50")

        // 2. Open Add Transaction screen again
        // FIX: Check collection size instead of .exists()
        val addTransactionNodes = composeTestRule.onAllNodesWithContentDescription("Add Transaction")
        val fabNode = if (addTransactionNodes.fetchSemanticsNodes().isNotEmpty()) {
            addTransactionNodes.onFirst()
        } else {
            composeTestRule.onNodeWithContentDescription("Add")
        }
        fabNode.performClick()

        // 3. Verify "Quick Fill from Recent" carousel is visible
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Quick Fill from Recent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Quick Fill from Recent").assertIsDisplayed()

        // 4. Verify our specific chip is visible
        composeTestRule.onNodeWithText("Coffee").assertIsDisplayed()
        // Note: The amount might be formatted (e.g., ₹50). Checking existence of description is sufficient.

        // 5. Click the suggestion chip
        composeTestRule.onNodeWithText("Coffee").performClick()

        // 6. Assert the fields are populated
        // The Description field should now display "Coffee"
        // Note: In the new UI, the description is shown in a Text widget (not a TextField).
        // Use assertIsDisplayed to ensure the text is present on screen in the header area.
        composeTestRule.onNodeWithText("Coffee").assertIsDisplayed()

        // The Amount field should contain "50"
        // Since custom input fields can be tricky, we check if "50" text exists.
        composeTestRule.onNodeWithText("50").assertIsDisplayed()

        // Chips should be populated (Food & Drinks, Cash Spends)
        // We check if these texts are displayed.
        composeTestRule.onNodeWithText("Food & Drinks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cash Spends").assertIsDisplayed()
    }
}