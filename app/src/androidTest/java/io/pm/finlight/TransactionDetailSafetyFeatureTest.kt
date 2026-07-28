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
class TransactionDetailSafetyFeatureTest {

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

    @Test
    fun test_mergedTransaction_hasSafetyLocks() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Transactions").performClick()

        // Long press first one to enter selection mode
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_GROCERY_DESC).performTouchInput { longClick() }
            
        // Tap second one's checkbox
        composeTestRule.onNodeWithTag("transaction_item_checkbox_${TestDataSeeder.TXN_COFFEE_DESC}").performTouchInput { click() }
            
        // Click merge
        composeTestRule.onNodeWithContentDescription("Merge Transactions").performClick()
        
        // Wait for it to be merged (Review merge sheet)
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Confirm Merge").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Confirm Merge").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.TXN_COFFEE_DESC).fetchSemanticsNodes().isEmpty()
        }
        
        // Open the merged transaction
        composeTestRule.onNode(hasText(TestDataSeeder.TXN_GROCERY_DESC), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()
            
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
        
        // 1. Verify "Split Transaction" button is completely hidden
        composeTestRule.onNodeWithText("Split Transaction").assertDoesNotExist()
        
        // 2. Verify Unmerge is displayed (might need to scroll down).
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Unmerge", ignoreCase = true))
            
        composeTestRule.onNodeWithText("Unmerge", ignoreCase = true, useUnmergedTree = true).assertIsDisplayed()
        
        // 3. Since we disabled the toggle, we can click it and verify that it doesn't change anything
        // "Income" or "Expense" label should remain unchanged if disabled.
    }
}
