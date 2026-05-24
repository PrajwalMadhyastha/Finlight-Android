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
class TagManagementTests {
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

    private fun addTransactionForTest(): String {
        val uniqueDescription = "Test Txn ${UUID.randomUUID().toString().take(5)}"
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("amount_text_field").performTextInput("100.0")
        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Merchant").fetchSemanticsNodes().isNotEmpty()
        }
        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput(uniqueDescription)
        composeTestRule.onAllNodesWithText(uniqueDescription).onFirst().performClick()
        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()
        composeTestRule.onNodeWithTag("category_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).performClick()
        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        return uniqueDescription
    }

    @Test
    fun test_addTagToTransaction_showsOnDetail() {
        val description = addTransactionForTest()
        val newTagName = "UrgentTag${UUID.randomUUID().toString().take(4)}"

        // Go to Transactions tab to avoid scroll issues
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }

        // Click the transaction
        composeTestRule.onNode(hasText(description), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Exclude from Totals").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Tags Row
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column").performScrollToNode(hasText("Tags"))
        composeTestRule.onNodeWithText("Tags").performClick()

        // Wait for Tag Picker Sheet
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Manage Tags").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a new tag
        composeTestRule.onNodeWithText("New Tag Name").performTextInput(newTagName)
        composeTestRule.onNodeWithContentDescription("Add New Tag").performClick()

        // Verify the tag chip is now present
        composeTestRule.onAllNodesWithText(newTagName).onFirst().assertExists()

        // Save the tags
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        // Verify on detail screen
        composeTestRule.onAllNodesWithText(newTagName).onFirst().assertExists()
    }

    @Test
    fun test_filterTransactionsByTag_showsOnlyTagged() {
        val description1 = addTransactionForTest()
        val description2 = addTransactionForTest()
        val newTagName = "TargetTag${UUID.randomUUID().toString().take(4)}"

        // Go to Transactions tab
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(description1).fetchSemanticsNodes().isNotEmpty()
        }

        // Click the first transaction and add a tag
        composeTestRule.onNode(hasText(description1), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        composeTestRule.onNodeWithTag("transaction_detail_lazy_column").performScrollToNode(hasText("Tags"))
        composeTestRule.onNodeWithText("Tags").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Manage Tags").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("New Tag Name").performTextInput(newTagName)
        composeTestRule.onNodeWithContentDescription("Add New Tag").performClick()

        // Save the tags
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }

        // We are now back on Transactions list. Wait, search is not on Transactions list.
        // It's on the dashboard (actually BottomNavItem.Search doesn't exist, search is an icon on bottom bar?)
        // Let's just click the "Dashboard" tab to go back
        composeTestRule.onNodeWithText("Dashboard").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Go to search screen
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Keyword (description, notes)").fetchSemanticsNodes().isNotEmpty()
        }

        // Open filters
        composeTestRule.onNodeWithText("Filters").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Tag").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap Tag filter dropdown
        composeTestRule.onNodeWithText("Tag").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(newTagName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(newTagName).performClick()

        // Collapse filters
        composeTestRule.onNodeWithText("Filters").performClick()

        // Verify only the tagged transaction shows
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(description1).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(description1).assertExists()
        composeTestRule.onNodeWithText(description2).assertDoesNotExist()
    }
}
