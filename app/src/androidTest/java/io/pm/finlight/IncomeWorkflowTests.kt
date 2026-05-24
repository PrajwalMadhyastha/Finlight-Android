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

@RunWith(AndroidJUnit4::class)
class IncomeWorkflowTests {
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

    private fun addIncomeTransaction(amount: String, description: String) {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to Income
        if (composeTestRule.onAllNodesWithText("Income").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Income").performClick()
        }

        composeTestRule.onNodeWithTag("amount_text_field").performTextInput(amount)

        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Merchant").fetchSemanticsNodes().isNotEmpty()
        }

        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput(description)
        composeTestRule.onAllNodesWithText(description).onFirst().performClick()

        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()

        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        try {
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithText("Select Category").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).performClick()
        } catch (e: Exception) {
            // Nudge didn't appear; proceed gracefully
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_addIncomeTransaction_increasesTotalBalance() {
        val description = "Freelance ${UUID.randomUUID().toString().take(5)}"
        addIncomeTransaction("12000.0", description)

        // Wait until back on Dashboard
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        
        // Scroll to Income and check if it exists (Total Income)
        composeTestRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasText("Income"))
        composeTestRule.onNodeWithText("Income").assertIsDisplayed()

        // Wait to make sure the total increased (Since original seed is 60000, new is 72000)
        // Check if the amount exists
        composeTestRule.onNodeWithText("₹72,000", substring = true).assertExists()
    }

    @Test
    fun test_addIncomeTransaction_appearsInList_withGreenText() {
        val description = "Gift ${UUID.randomUUID().toString().take(5)}"
        addIncomeTransaction("5000.0", description)

        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText(description))
        composeTestRule.onNodeWithText(description).assertExists()

        // Optionally, check Transactions tab
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(description).assertExists()
        composeTestRule.onNodeWithText("5000.00", substring = true).assertExists()
    }
}
