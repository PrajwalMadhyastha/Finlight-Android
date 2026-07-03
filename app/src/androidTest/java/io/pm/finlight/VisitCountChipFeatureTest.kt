// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/VisitCountChipFeatureTest.kt
// REASON: FEATURE TEST — Verifies the visit-count chip behaviour on
// TransactionDetailScreen after the fix:
//   - Expense transactions show "N visits" chip when count > 1
//   - Income transactions show "N credits" chip when count > 1
//   - Single-transaction merchants show no chip (threshold > 1)
// =================================================================================
package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Feature tests for the visit-count chip on [TransactionDetailScreen].
 *
 * These tests exercise the full stack: UI → ViewModel → Repository → Room DB,
 * ensuring that the chip label is correct for expense and income types, and
 * absent when there is only a single transaction for that merchant/source.
 */
@RunWith(AndroidJUnit4::class)
class VisitCountChipFeatureTest {
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

    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    /** Waits for the dashboard and then opens the Add Transaction screen. */
    private fun openAddTransactionScreen() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Recent Transactions"))
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithText("Save Transaction").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Enters merchant name via the prediction sheet and saves it. */
    private fun enterMerchantAndSave(merchantName: String) {
        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithText("Search or enter new merchant").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Merchant").fetchSemanticsNodes().isNotEmpty()
        }
        val searchInput =
            composeTestRule
                .onAllNodes(hasSetTextAction())
                .onFirst()
        searchInput.performTextInput(merchantName)
        closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Adds one expense transaction via the UI.
     * Account and category are taken from [TestDataSeeder] constants.
     *
     * @return the description used so callers can locate it in the list.
     */
    private fun addExpense(
        description: String,
        amount: String = "100.0",
    ): String {
        openAddTransactionScreen()

        composeTestRule.onNodeWithTag("amount_text_field").performTextInput(amount)
        closeSoftKeyboard()
        composeTestRule.waitForIdle()

        enterMerchantAndSave(description)

        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()

        composeTestRule.onNodeWithTag("category_select_chip").performClick()
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onLast().performClick()

        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        // Wait for return to dashboard
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        return description
    }

    /**
     * Adds one income transaction via the UI.
     * Uses [TestDataSeeder.ACCOUNT_BANK_NAME] as the account.
     */
    private fun addIncome(
        description: String,
        amount: String = "5000.0",
    ): String {
        openAddTransactionScreen()

        // Switch to Income tab
        composeTestRule.waitUntil(timeoutMillis = 4_000) {
            composeTestRule.onAllNodesWithText("Income").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Income").performClick()

        composeTestRule.onNodeWithTag("amount_text_field").performTextInput(amount)
        closeSoftKeyboard()
        composeTestRule.waitForIdle()

        enterMerchantAndSave(description)

        composeTestRule.onNodeWithTag("account_select_chip").performClick()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_BANK_NAME).performClick()

        composeTestRule.onNodeWithText("Save Transaction").performScrollTo().performClick()

        // Handle optional category-nudge sheet for income
        try {
            composeTestRule.waitUntil(timeoutMillis = 3_000) {
                composeTestRule.onAllNodesWithText("Select Category").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).performClick()
        } catch (_: Exception) {
            // Sheet didn't appear — that's fine
        }

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        return description
    }

    /** Opens the detail screen for the most recently visible transaction with [description]. */
    private fun openDetailScreen(description: String) {
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onAllNodes(hasText(description), useUnmergedTree = true)
            .onFirst()
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // Wait for detail screen
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---------------------------------------------------------------------------
    // FT-1: Expense — "N visits" chip appears when same merchant is used twice
    // ---------------------------------------------------------------------------

    @Test
    fun test_expenseDetailScreen_showsVisitsChip_forRepeatedMerchant() {
        val merchant = "ChipTest Bakery"

        // Add the same expense merchant twice
        addExpense(merchant, "120.0")
        addExpense(merchant, "95.0")

        // Open detail for the latest transaction
        openDetailScreen(merchant)

        // The chip must say "2 visits"
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("2 visits").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("2 visits").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // FT-2: Income — "N credits" chip appears when same source is used twice
    // ---------------------------------------------------------------------------

    @Test
    fun test_incomeDetailScreen_showsCreditsChip_forRepeatedSource() {
        val source = "ChipTest Salary Source"

        // Add two income transactions from the same source
        addIncome(source, "50000.0")
        addIncome(source, "50000.0")

        // Open detail for the latest transaction
        openDetailScreen(source)

        // The chip must say "2 credits" (not "2 visits")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("2 credits").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("2 credits").assertIsDisplayed()
        // "visits" label must not be present on an income transaction
        composeTestRule.onNodeWithText("2 visits").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------
    // FT-3: Single expense — chip is absent (threshold is visitCount > 1)
    // ---------------------------------------------------------------------------

    @Test
    fun test_singleExpenseDetailScreen_doesNotShowVisitsChip() {
        val merchant = "ChipTest OnceOnly"

        // Add only ONE expense for this merchant
        addExpense(merchant, "200.0")

        openDetailScreen(merchant)

        // Neither chip variant should be visible
        composeTestRule.onNodeWithText("1 visits").assertDoesNotExist()
        composeTestRule.onNodeWithText("1 credits").assertDoesNotExist()
    }
}
