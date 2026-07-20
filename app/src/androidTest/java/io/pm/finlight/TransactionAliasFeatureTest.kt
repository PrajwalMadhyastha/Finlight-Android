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
class TransactionAliasFeatureTest {
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

    private fun addTransactionForTest(
        customDescription: String = "Test Txn"
    ): String {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }

        val amountInput = composeTestRule.onNodeWithTag("amount_text_field")
        amountInput.performTextInput("100.0")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Merchant").fetchSemanticsNodes().isNotEmpty()
        }

        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextInput(customDescription)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()

        composeTestRule.onNodeWithTag("category_select_chip").performClick()
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()

        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        return customDescription
    }

    @Test
    fun test_merchantAlias_isAppliedCorrectlyAfterManualRename() {
        val originalDescription1 = "RAM"
        val originalDescription2 = "VIJAYALAKSH"

        // 1. Create RAM transaction
        addTransactionForTest(originalDescription1)

        // 2. Create VIJAYALAKSH transaction
        addTransactionForTest(originalDescription2)

        // 3. Rename RAM to Badminton
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(originalDescription1).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText(originalDescription1), useUnmergedTree = true)
            .onAncestors().filterToOne(hasClickAction()).performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText(originalDescription1) and hasClickAction()).performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
        }
        val searchInput1 = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput1.performTextClearance()
        searchInput1.performTextInput("Badminton")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for sheet to close and detail screen to update
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Badminton").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Smart update
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Apply Changes").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Apply Changes").performClick()

        // 4. Rename VIJAYALAKSH to Food in Office
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText(originalDescription2), useUnmergedTree = true)
            .onAncestors().filterToOne(hasClickAction()).performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText(originalDescription2) and hasClickAction()).performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
        }
        val searchInput2 = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput2.performTextClearance()
        searchInput2.performTextInput("Food in Office")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for sheet to close and detail screen to update
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Food in Office").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Smart update
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Apply Changes").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Apply Changes").performClick()

        // 5. Verify the VIJAYALAKSH transaction details
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }

        // In the list, we should see "Food in Office" (since we renamed it)
        composeTestRule.onNode(hasText("Food in Office"), useUnmergedTree = true)
            .onAncestors().filterToOne(hasClickAction()).performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }

        // Detail screen MUST show "Food in Office", and not mistakenly show "Badminton"
        composeTestRule.onNodeWithText("Food in Office").assertIsDisplayed()
        composeTestRule.onNodeWithText("Badminton").assertDoesNotExist()
    }
}
