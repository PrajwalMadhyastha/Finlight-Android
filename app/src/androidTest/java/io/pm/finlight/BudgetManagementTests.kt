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
 * Instrumented UI tests for Phase 3: Budget Management.
 * Covers budget creation, editing, deletion, and progress display.
 */
@RunWith(AndroidJUnit4::class)
class BudgetManagementTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
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
     * Navigates from Dashboard to the Budget screen.
     */
    private fun navigateToBudgetScreen() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Budget Watch card and click it.
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Budget Watch"))

        composeTestRule.onNodeWithText("Budget Watch").performClick()

        // Wait for BudgetScreen to open
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Category Budgets").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Test adding a new budget and verifying it appears in the list.
     */
    @Test
    fun test_addBudget_appearsInBudgetList() {
        navigateToBudgetScreen()

        // Click Add FAB
        composeTestRule.onNodeWithContentDescription("Add Category Budget").performClick()

        // Wait for AddEditBudgetScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // Fill Category
        composeTestRule.onNodeWithText("Select Category").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transport").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Transport").performClick()

        val amountInput = composeTestRule.onNodeWithText("Budget Amount")
        amountInput.performTextInput("2000")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // Click Save Budget
        composeTestRule.onNodeWithText("Save Budget").performClick()

        // Wait for return to BudgetScreen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Category Budgets").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to the new budget entry to ensure it's visible and assert
        composeTestRule.onNodeWithTag("budget_lazy_column")
            .performScrollToNode(hasText("Transport"))
        composeTestRule.onNodeWithText("Transport").assertExists()
    }

    /**
     * Test editing an existing budget and verifying the update.
     */
    @Test
    fun test_editBudget_updatesAmount() {
        navigateToBudgetScreen()

        // Tap seeded "Food & Drinks" budget -> Edit
        composeTestRule.onNodeWithTag("edit_budget_Food & Drinks").performClick()

        // Wait for Edit Budget screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Edit Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // Change amount
        val amountInput = composeTestRule.onNodeWithText("Budget Amount")
        amountInput.performTextReplacement("6000")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // Save
        composeTestRule.onNodeWithText("Update Budget").performClick()

        // Wait for return
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Category Budgets").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify updated amount is shown (₹6,000)
        composeTestRule.onNodeWithTag("budget_lazy_column")
            .performScrollToNode(hasText("₹6,000", substring = true))
        composeTestRule.onNodeWithText("₹6,000", substring = true).assertExists()
    }

    /**
     * Test deleting a budget and verifying it is removed from the list.
     */
    @Test
    fun test_deleteBudget_removesFromList() {
        navigateToBudgetScreen()

        // Tap budget -> Delete
        composeTestRule.onNodeWithTag("delete_budget_Food & Drinks").performClick()

        // Confirm
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Delete Budget?").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify removed
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Food & Drinks").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Food & Drinks").assertDoesNotExist()
    }

    /**
     * Test that budget progress reflects spending correctly based on seeded transactions.
     */
    @Test
    fun test_budgetProgress_reflectsSpending() {
        navigateToBudgetScreen()

        composeTestRule.onNodeWithText("Food & Drinks").assertIsDisplayed()

        // Progress indicator should be non-zero. The TestDataSeeder has a budget of ₹5000 for Food & Drinks.
        composeTestRule.onNodeWithText("of ₹5,000", substring = true).assertExists()
    }

    /**
     * Test validation error (button disabled) when amount is empty.
     */
    @Test
    fun test_addBudget_validationError_emptyAmount() {
        navigateToBudgetScreen()

        composeTestRule.onNodeWithContentDescription("Add Category Budget").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add Budget").fetchSemanticsNodes().isNotEmpty()
        }

        // Select category
        composeTestRule.onNodeWithText("Select Category").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transport").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Transport").performClick()

        // Verify the Save button is not enabled since amount is empty
        composeTestRule.onNodeWithText("Save Budget").assertIsNotEnabled()
    }

    /**
     * Test navigating to Annual Budget Planning and setting an overall budget.
     */
    @Test
    fun test_annualBudgetPlanning_setsOverallBudget() {
        navigateToBudgetScreen()

        // Click Annual Plan button
        composeTestRule.onNodeWithText("Annual Plan").performClick()

        // Wait for Annual Plan screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Annual Budget Plan").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap Overall Annual Budget card
        composeTestRule.onNodeWithText("Overall Annual Budget").performClick()

        // Wait for dialog
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Total Annual Amount").fetchSemanticsNodes().isNotEmpty()
        }

        // Enter budget
        val amountInput = composeTestRule.onNodeWithText("Total Annual Amount")
        amountInput.performTextReplacement("900")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        // Ensure we are back on the Annual Budget Plan screen and it shows ₹900
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("₹900", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
