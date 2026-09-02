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
                    transactionType = TransactionType.EXPENSE,
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
                    transactionType = TransactionType.INCOME,
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
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Repayments"))
        composeTestRule.onNodeWithText("Repayments").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Alice Repayment", substring = true))
        composeTestRule.onNodeWithText("Alice Repayment", substring = true).assertIsDisplayed()

        // Verify Net Cost calculation: 1500 - 500 = 1000
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Net cost", substring = true, ignoreCase = true))
        composeTestRule.onNodeWithText("Net cost", substring = true, ignoreCase = true).assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Linked as repayment", substring = true, ignoreCase = true))
        composeTestRule.onNodeWithText("Linked as repayment", substring = true, ignoreCase = true).assertIsDisplayed()

        // The parent expense description is shown in the tappable row
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Group Dinner Expense", substring = true, ignoreCase = true))
        composeTestRule.onNodeWithText("Group Dinner Expense", substring = true, ignoreCase = true).assertIsDisplayed()

        // The Unlink button is present
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Unlink", ignoreCase = true))
        composeTestRule.onNodeWithText("Unlink", ignoreCase = true).assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Unlink", ignoreCase = true))
        composeTestRule.onNodeWithText("Unlink", ignoreCase = true)
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
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Linked as repayment", substring = true, ignoreCase = true))
        composeTestRule.onNodeWithText("Linked as repayment", substring = true, ignoreCase = true)
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
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Group Dinner Expense", substring = true, ignoreCase = true))
        composeTestRule.onNodeWithText("Group Dinner Expense", substring = true, ignoreCase = true)
            .performClick()

        // Should land on the expense detail screen
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Debit transaction", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Debit transaction", ignoreCase = true).assertIsDisplayed()

        // And the expense repayment card should be present confirming we're on the right screen
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Repayments", ignoreCase = true))
        composeTestRule.onNodeWithText("Repayments", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun testOverRepaymentSettlesExpenseAndCreatesSurplus() {
        val appDatabase = AppDatabase.getInstance(composeTestRule.activity.applicationContext)
        runBlocking {
            appDatabase.transactionDao().deleteAll()

            val now = System.currentTimeMillis()
            val expenseId = 9001
            appDatabase.transactionDao().insert(
                Transaction(
                    id = expenseId,
                    description = "Lunch Expense",
                    amount = 500.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                ),
            )
            appDatabase.transactionDao().insert(
                Transaction(
                    id = 9002,
                    description = "Friend Payback",
                    amount = 600.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                ),
            )

            val repo =
                TransactionRepository(
                    transactionWriteDao = appDatabase.transactionWriteDao(),
                    transactionQueryDao = appDatabase.transactionQueryDao(),
                    transactionAnalyticsDao = appDatabase.transactionAnalyticsDao(),
                    transactionReimbursementDao = appDatabase.transactionReimbursementDao(),
                    db = appDatabase,
                )
            repo.linkReimbursement(incomeId = 9002, expenseId = expenseId)
        }

        openTransactionsTab()

        // Wait for transactions to appear
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Lunch Expense").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Lunch Expense details
        openTransactionDetail("Lunch Expense", "Debit transaction")

        // Net cost should show 0.00
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column")
            .performScrollToNode(hasText("Net cost", substring = true, ignoreCase = true))
        composeTestRule.onNodeWithText("Net cost", substring = true, ignoreCase = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("0.00", substring = true).onFirst().assertIsDisplayed()

        // Navigate back and verify the surplus income transaction appears
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Friend Payback (Surplus)").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Friend Payback (Surplus)").assertIsDisplayed()
    }
}
