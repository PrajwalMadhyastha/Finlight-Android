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
class SmartUpdateFeatureTest {
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
        customDescription: String? = null,
        customAmount: String = "100.0",
        isIncome: Boolean = false,
    ): String {
        val uniqueDescription = customDescription ?: "Test Txn"

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

        // 6. Select Account
        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()

        // 7. Select Category
        if (!isIncome) {
            composeTestRule.onNodeWithTag("category_select_chip").performClick()
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()
        }

        // 8. Save
        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        // 9. Wait for return to Dashboard
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        return uniqueDescription
    }

    @Test
    fun test_smartUpdateSheet_appears_and_updates() {
        // 1. Create a "Starbucks" transaction via UI
        val originalDesc = "Starbucks"
        addTransactionForTest(originalDesc)

        // Navigate to Transactions tab to avoid nested scroll issues on dashboard
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(originalDesc).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Click on the transaction "Starbucks"
        composeTestRule.onNode(hasText(originalDesc), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // 3. Wait for Detail Screen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }

        // 4. Click the description to edit (opens the MerchantPredictionSheet)
        composeTestRule.onNode(hasText(originalDesc) and hasClickAction()).performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty()
        }

        val searchInput = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        searchInput.performTextClearance()
        searchInput.performTextInput("Starbucks Coffee")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // 5. Hit Save to close Merchant Bottom Sheet
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for the sheet to close and the detail screen to update
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Starbucks Coffee").fetchSemanticsNodes().isNotEmpty()
        }

        // 6. Press Back to leave TransactionDetailScreen and trigger SmartUpdate
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // 7. Verify Smart Update Sheet appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Apply Changes").fetchSemanticsNodes().isNotEmpty()
        }

        // 8. Verify elements on the sheet (empty history expected)
        composeTestRule.onNodeWithText("Update future transactions").assertExists()
        composeTestRule.onNodeWithText("Past transactions to update").assertDoesNotExist()

        // 9. Click "Apply Changes"
        composeTestRule.onNodeWithText("Apply Changes").performClick()

        // 10. Verify we are back on Transactions List
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
