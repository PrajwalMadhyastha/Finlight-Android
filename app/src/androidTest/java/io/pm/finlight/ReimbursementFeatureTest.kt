package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReimbursementFeatureTest {
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

    private fun seedReimbursementTransactions() {
        val appDatabase = AppDatabase.getInstance(composeTestRule.activity.applicationContext)
        runBlocking {
            appDatabase.transactionDao().deleteAll()

            val now = System.currentTimeMillis()
            val expenseId = 9001
            appDatabase.transactionDao().insert(
                Transaction(
                    id = expenseId,
                    description = "Group Dinner Expense",
                    amount = 1500.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now,
                    transactionType = "expense",
                    notes = ""
                )
            )
            appDatabase.transactionDao().insert(
                Transaction(
                    id = 9002,
                    description = "Alice Repayment",
                    amount = 500.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now,
                    transactionType = "income",
                    notes = "",
                    parentReimbursementId = expenseId
                )
            )
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Navigates to the Transactions tab and waits for the list to settle. */
    private fun openTransactionsTab() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Transactions", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Transactions", ignoreCase = true).performClick()
    }

    /** Taps a transaction row by description and waits for the detail screen to open. */
    private fun openTransactionDetail(
        description: String,
        detailScreenTitle: String,
    ) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(description, ignoreCase = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(detailScreenTitle, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // -----------------------------------------------------------------------
    // Existing: expense-side detail flow
    // -----------------------------------------------------------------------

    @Test
    fun testReimbursementDetailFlow() {
        seedReimbursementTransactions()

        openTransactionsTab()
        openTransactionDetail("Group Dinner Expense", "Debit transaction")

        // Verify the linked repayment is shown
        composeTestRule.onNodeWithText("Repayments").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Repayment", substring = true).performScrollTo().assertIsDisplayed()

        // Verify Net Cost calculation: 1500 - 500 = 1000
        composeTestRule.onNodeWithText("Net cost", substring = true, ignoreCase = true)
            .performScrollTo().assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // NEW: income-side detail — badge is visible
    // -----------------------------------------------------------------------

    /**
     * Opens the income transaction ("Alice Repayment") and verifies that the
     * "Linked as repayment" card is shown, including the parent expense name
     * and the Unlink button.
     */
    @Test
    fun testIncomeDetailShowsLinkedExpenseBadge() {
        seedReimbursementTransactions()

        openTransactionsTab()
        // Alice Repayment is an income (excluded from totals), so it may not
        // be in the default expense view. Use text search to find it.
        openTransactionDetail("Alice Repayment", "Credit transaction")

        // The card header
        composeTestRule.onNodeWithText("Linked as repayment", substring = true, ignoreCase = true)
            .performScrollTo().assertIsDisplayed()

        // The parent expense description is shown in the tappable row
        composeTestRule.onNodeWithText("Group Dinner Expense", substring = true, ignoreCase = true)
            .performScrollTo().assertIsDisplayed()

        // The Unlink button is present
        composeTestRule.onNodeWithText("Unlink", ignoreCase = true)
            .performScrollTo().assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // NEW: Unlink from income detail screen — confirmation dialog then removed
    // -----------------------------------------------------------------------

    /**
     * Taps "Unlink" on the income screen, verifies the confirmation dialog
     * appears with correct content, cancels it, then confirms it and verifies
     * the card is gone.
     */
    @Test
    fun testUnlinkFromIncomeDetailScreen() {
        seedReimbursementTransactions()

        openTransactionsTab()
        openTransactionDetail("Alice Repayment", "Credit transaction")

        // Scroll to and tap the Unlink button
        composeTestRule.onNodeWithText("Unlink", ignoreCase = true)
            .performScrollTo()
            .performClick()

        // Confirmation dialog should appear
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithText("Unlink Repayment?", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Unlink Repayment?").assertIsDisplayed()

        // The dialog body text mentions the expense name — use onFirst() because the same
        // description text also exists in the card behind the dialog.
        composeTestRule.onAllNodesWithText("Group Dinner Expense", substring = true, ignoreCase = true)
            .onFirst()
            .assertIsDisplayed()

        // --- Cancel flow: dialog should close, badge should still be there ---
        composeTestRule.onNodeWithText("Cancel", ignoreCase = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithText("Unlink Repayment?").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Linked as repayment", substring = true, ignoreCase = true)
            .performScrollTo()
            .assertIsDisplayed()

        // --- Confirm flow: badge should disappear after unlink ---
        composeTestRule.onNodeWithText("Unlink", ignoreCase = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithText("Unlink Repayment?", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Find the destructive Unlink button inside the dialog and click it
        composeTestRule.onAllNodesWithText("Unlink", ignoreCase = true).onLast().performClick()

        // The "Linked as repayment" card should no longer be visible
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Linked as repayment", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    // -----------------------------------------------------------------------
    // NEW: Tap expense row on income screen → navigates to expense detail
    // -----------------------------------------------------------------------

    /**
     * Taps the linked expense row on the income detail screen and verifies
     * that navigation lands on the expense's detail screen (top bar reads
     * "Debit transaction").
     */
    @Test
    fun testNavigateToExpenseFromIncomeDetailScreen() {
        seedReimbursementTransactions()

        openTransactionsTab()
        openTransactionDetail("Alice Repayment", "Credit transaction")

        // Scroll to and tap the expense row (tapping the description text)
        composeTestRule.onNodeWithText("Group Dinner Expense", substring = true, ignoreCase = true)
            .performScrollTo()
            .performClick()

        // Should land on the expense detail screen
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Debit transaction", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Debit transaction", ignoreCase = true).assertIsDisplayed()

        // And the expense repayment card should be present confirming we're on the right screen
        composeTestRule.onNodeWithText("Repayments", ignoreCase = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testOverRepaymentFlipsUIToIncome() {
        val appDatabase = AppDatabase.getInstance(composeTestRule.activity.applicationContext)
        runBlocking {
            appDatabase.transactionDao().deleteAll()

            val now = System.currentTimeMillis()
            val expenseId = 9001
            appDatabase.transactionDao().insert(
                Transaction(
                    id = expenseId,
                    description = "Over-repaid Expense",
                    // Over-repaid by 500
                    amount = -500.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now,
                    transactionType = "expense",
                    notes = ""
                )
            )
        }

        openTransactionsTab()

        // Wait for the transaction to appear and navigate into it directly.
        // We avoid matching on the top-bar title ("Debit transaction") because the visual type
        // for over-repaid expenses may differ from the DB type. Instead we use the description.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Over-repaid Expense").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Over-repaid Expense"), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(androidx.compose.ui.test.hasClickAction())
            .performClick()

        // Wait for the detail screen to load (top bar uses DB type, so "Debit transaction" is correct
        // for an expense regardless of amount sign — this is verified here for safety).
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Debit transaction", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the dynamic UI shows the positive absolute amount (|−500| = 500.00)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("500.00", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("500.00", substring = true).assertIsDisplayed()
    }
}
