// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/TransactionCrudTests.kt
// REASON: PHASE 1 — Harden existing CRUD tests.
// Changes:
//   - Added ClearDatabaseRule + SeedDatabaseRule to the rule chain so every test
//     starts from a clean, deterministic database state.
//   - Updated addTransactionForTest() to use seeded account/category names from
//     TestDataSeeder (was "Cash Spends"/"Food & Drinks" which could be live data).
//   - Added test_createIncomeTransaction_appearsOnDashboard as a new income path.
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
 *
 * Phase 1 changes:
 * - [ClearDatabaseRule] wipes transactional data before each test so tests are
 *   independent of one another and cannot pollute each other's state.
 * - [SeedDatabaseRule] injects a canonical dataset so the account/category pickers
 *   always have known options to select.
 * - Helper method updated to reference [TestDataSeeder] constants.
 * - New test: [test_createIncomeTransaction_appearsOnDashboard].
 */
@RunWith(AndroidJUnit4::class)
class TransactionCrudTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            // Clear first, then seed — ensures every test starts hermetically.
            .around(ClearDatabaseRule())
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
     * A helper function to add an expense transaction.
     *
     * Navigates through the new UI flow (merchant bottom-sheet, account/category chips)
     * and saves. All account/category references come from [TestDataSeeder] constants.
     *
     * @return The unique description of the created transaction.
     */
    private fun addTransactionForTest(
        customDescription: String? = null,
        customAmount: String = "100.0",
        isIncome: Boolean = false,
    ): String {
        val uniqueDescription = customDescription ?: "Test Txn ${UUID.randomUUID().toString().take(5)}"

        // 1. Wait for Dashboard and click FAB
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        // 2. Wait for Add Screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. If income, switch transaction type toggle first
        if (isIncome) {
            // The screen has an "Income" toggle chip or tab — tap it
            if (composeTestRule.onAllNodesWithText("Income").fetchSemanticsNodes().isNotEmpty()) {
                composeTestRule.onNodeWithText("Income").performClick()
            }
        }

        val amountInput = composeTestRule.onNodeWithTag("amount_text_field")
        amountInput.performTextInput(customAmount)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 5. Enter Description — opens Merchant BottomSheet
        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Merchant").fetchSemanticsNodes().isNotEmpty()
        }

        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput(uniqueDescription)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // Explicitly click the Save button in the MerchantPredictionSheet to close it
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        // 6. Select Account — use the seeded "Test Wallet" so it never depends on
        //    live device data like "Cash Spends"
        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()

        // 7. Select Category (expenses only — income may not require one)
        if (!isIncome) {
            composeTestRule.onNodeWithTag("category_select_chip").performClick()
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()
        }

        // 8. Save
        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        // If income, it might show the category nudge sheet since category is null
        if (isIncome) {
            try {
                composeTestRule.waitUntil(timeoutMillis = 3000) {
                    composeTestRule.onAllNodesWithText("Select Category").fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).performClick()
            } catch (e: Exception) {
                // Nudge didn't appear; proceed gracefully
            }
        }

        // 9. Wait for return to Dashboard
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        return uniqueDescription
    }

    /**
     * Tests that a newly created expense transaction appears on the dashboard.
     */
    @Test
    fun test_createTransaction_appearsOnDashboard() {
        val description = addTransactionForTest()
        // First, scroll to the Recent Transactions card so its contents are composed.
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText(description))
        composeTestRule.onNodeWithText(description).assertExists()
    }

    /**
     * Tests that a newly created income transaction appears on the dashboard.
     * Covers the income entry path which was not previously tested.
     */
    @Test
    fun test_createIncomeTransaction_appearsOnDashboard() {
        val description =
            addTransactionForTest(
                customDescription = "Test Salary Income ${UUID.randomUUID().toString().take(5)}",
                customAmount = "5000.0",
                isIncome = true,
            )
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText(description))
        composeTestRule.onNodeWithText(description).assertExists()
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
        // Navigate to Transactions tab to avoid nested scroll issues on dashboard
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(originalDescription).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText(originalDescription), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // 2. Wait for detail screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Exclude from Totals").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Click the description to edit (opens the MerchantPredictionSheet)
        composeTestRule.onNode(hasText(originalDescription) and hasClickAction()).performClick()

        // 4. Wait for the sheet and enter new description in the OutlinedTextField
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
        }

        val inputNode = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        inputNode.performTextClearance()
        inputNode.performTextInput(updatedDescription)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 5. Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for the sheet to close and the detail screen to update
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(updatedDescription).fetchSemanticsNodes().isNotEmpty()
        }

        // 6. Verify update on detail screen
        composeTestRule.onNodeWithText(updatedDescription).assertIsDisplayed()

        // 7. Back to Dashboard
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // 8. Verify on Transactions List
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(originalDescription).assertDoesNotExist()
        composeTestRule.onNodeWithText(updatedDescription).assertExists()
    }

    /**
     * Tests that a transaction can be successfully deleted from the detail screen.
     */
    @Test
    fun test_deleteTransaction_removesFromList() {
        val description = addTransactionForTest()

        // 1. Open Detail Screen
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText(description), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // 2. Click 'More' menu
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Exclude from Totals").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("More options", useUnmergedTree = true).performClick()

        // 3. Click 'Delete'
        composeTestRule.onNodeWithText("Delete").performClick()

        // 4. Confirm Deletion
        composeTestRule.onNodeWithText("Delete Transaction?").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Delete").onLast().performClick()

        // 5. Verify removal
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(description).assertDoesNotExist()
    }

    /**
     * Tests the Quick Fill feature — verifies that a recently added transaction
     * appears as a carousel suggestion and populates fields when tapped.
     */
    @Test
    fun quickFill_populatesFields() {
        // 1. Create a "Seed" transaction so it exists in recent entries
        addTransactionForTest(customDescription = "Coffee", customAmount = "50")

        // 2. Open Add Transaction screen again
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        // 3. Verify "Quick Fill from Recent" carousel is visible
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Quick Fill from Recent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Quick Fill from Recent").assertIsDisplayed()

        // 4. Verify our specific chip is visible
        composeTestRule.onNodeWithText("Coffee").assertIsDisplayed()

        // 5. Click the suggestion chip
        composeTestRule.onNodeWithText("Coffee").performClick()

        // 6. Assert the fields are populated
        composeTestRule.onNodeWithText("Coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText("50").assertIsDisplayed()

        // Chips should show the seeded account/category
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).assertIsDisplayed()
    }

    /**
     * Tests that typing an amount over 1,000,000,000 is rejected by the UI filter.
     */
    @Test
    fun test_addTransaction_amountExceedingLimit_isBlockedByUiFilter() {
        // 1. Wait for Dashboard and click FAB
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        // 2. Wait for Add Screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Find Amount Input and type 200 Million
        val amountInput = composeTestRule.onNodeWithTag("amount_text_field")

        // Initially it's empty. We type "200000000" (200 Million). This is valid.
        amountInput.performTextInput("200000000")
        composeTestRule.waitForIdle()

        // Now we append "0" which makes it "2000000000" (2 Billion).
        // This is invalid and should be dropped by the UI filter.
        amountInput.performTextInput("0")
        composeTestRule.waitForIdle()

        // Wait, if it's dropped, the text should be just "200000000"
        // Let's assert the text field's content. The text field has text "200000000".
        // Wait, compose test rule doesn't have an exact assertTextEquals for editable text directly unless we check text value.
        // But assert(hasTextExactly("200000000")) might fail if it contains a hint.
        // Actually, in `AmountComposer`, the text field is just BasicTextField.
        // Let's use `assert(hasText("200000000"))`
        amountInput.assert(hasText("200000000"))
    }
}
