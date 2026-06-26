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

@RunWith(AndroidJUnit4::class)
class SplitTransactionTests {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            // For splitting, we only need to seed data so we have transactions to split
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

    private fun navigateToTestTransactionDetail() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Transactions").performClick()

        composeTestRule.onAllNodes(
            hasScrollAction(),
            useUnmergedTree = true,
        ).onLast().performScrollToNode(hasText(TestDataSeeder.TXN_GROCERY_DESC))

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.TXN_GROCERY_DESC).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNode(hasText(TestDataSeeder.TXN_GROCERY_DESC), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_splitTransaction_createsChildItems() {
        navigateToTestTransactionDetail()

        // Tap "Split Transaction"
        composeTestRule.onNodeWithText("Split Transaction").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add Item").fetchSemanticsNodes().isNotEmpty()
        }

        // Ensure we have two items
        composeTestRule.onNodeWithText("Add Item").performClick()

        // Set amounts
        val amountNodes = composeTestRule.onAllNodes(hasSetTextAction())
        amountNodes[0].performTextReplacement("400")

        amountNodes[1].performTextReplacement("400")

        // Set Categories
        // Usually, the existing transaction has a category so the first split inherits it.
        // If it says "Set", we click it. Otherwise we click the inherited category name.
        // The grocery txn has "Food & Drinks".

        // Let's just click the nodes above the text fields to set categories.
        // It's easier to find the category node by looking for texts.
        // If the second one is "Set", click it.
        while (composeTestRule.onAllNodesWithText("Set").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onAllNodesWithText("Set").onFirst().performClick()
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                composeTestRule.onAllNodesWithText("Select Category for Split").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(TestDataSeeder.CATEGORY_SHOPPING_NAME))
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_SHOPPING_NAME).onLast().performClick()
        }

        // Save Splits
        // Save Splits
        composeTestRule.onNodeWithText("Save Splits").performClick()

        // Wait to return to details screen (Header changes to "Split Transaction")
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Split Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll down to the child items
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column").performScrollToNode(hasText("Split Details"))

        // Verify detail screen shows split line items
        composeTestRule.onNodeWithText("Split Details").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_SHOPPING_NAME).onFirst().assertIsDisplayed()
    }

    @Test
    fun test_splitTransaction_validation_amountMismatch() {
        navigateToTestTransactionDetail()

        composeTestRule.onNodeWithText("Split Transaction").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add Item").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Add Item").performClick()

        // Enter mismatched amounts (800 total required)
        val amountNodes = composeTestRule.onAllNodes(hasSetTextAction())
        amountNodes[0].performTextReplacement("400")

        amountNodes[1].performTextReplacement("300")

        while (composeTestRule.onAllNodesWithText("Set").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onAllNodesWithText("Set").onFirst().performClick()
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                composeTestRule.onAllNodesWithText("Select Category for Split").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(TestDataSeeder.CATEGORY_SHOPPING_NAME))
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_SHOPPING_NAME).onLast().performClick()
        }

        // Save Splits should be disabled
        composeTestRule.onNodeWithText("Save Splits").assertIsNotEnabled()
    }

    @Test
    fun test_splitTransaction_childInheritsCategory() {
        navigateToTestTransactionDetail()

        composeTestRule.onNodeWithText("Split Transaction").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add Item").fetchSemanticsNodes().isNotEmpty()
        }

        // Add 2 new items
        composeTestRule.onNodeWithText("Add Item").performClick()

        val amountNodes = composeTestRule.onAllNodes(hasSetTextAction())
        amountNodes[0].performTextReplacement("500")
        amountNodes[1].performTextReplacement("300")

        // Let's set category for the second one
        // Set category for first item to Food & Drinks
        composeTestRule.onAllNodesWithText("Set").onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule.onAllNodesWithText("Select Category for Split").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(TestDataSeeder.CATEGORY_FOOD_NAME))
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()

        // Set category for second item to Shopping
        composeTestRule.onAllNodesWithText("Set").onFirst().performClick()
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule.onAllNodesWithText("Select Category for Split").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(TestDataSeeder.CATEGORY_SHOPPING_NAME))
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_SHOPPING_NAME).onLast().performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            val nodes = composeTestRule.onAllNodesWithText("Save Splits").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        }
        composeTestRule.onNodeWithText("Save Splits").performClick()

        // Wait to return to details screen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Split Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("transaction_detail_lazy_column").performScrollToNode(hasText(TestDataSeeder.CATEGORY_SHOPPING_NAME))

        // Assert multiple categories are displayed
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_SHOPPING_NAME).onFirst().assertIsDisplayed()
    }
}
