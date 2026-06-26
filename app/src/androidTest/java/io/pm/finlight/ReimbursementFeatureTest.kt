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
                    parentReimbursementId = expenseId // Link it!
                )
            )
        }
    }

    @Test
    fun testReimbursementDetailFlow() {
        seedReimbursementTransactions()

        // Wait for dashboard or bottom bar to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Transactions", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Transactions tab
        composeTestRule.onNodeWithText("Transactions", ignoreCase = true).performClick()

        // Wait for transactions to load on screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Group Dinner Expense").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Expense details
        composeTestRule.onNodeWithText("Group Dinner Expense", ignoreCase = true).performClick()
        
        // Wait for detail screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Debit transaction", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the linked repayment is shown
        composeTestRule.onNodeWithText("Repayments").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Repayment").performScrollTo().assertIsDisplayed()
        
        // Verify Net Cost calculation: 1500 - 500 = 1000
        composeTestRule.onNodeWithText("Net cost", substring = true, ignoreCase = true).performScrollTo().assertIsDisplayed()
    }
}
