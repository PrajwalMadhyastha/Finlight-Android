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
class MergeTransactionFeatureTest {
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

    private fun seedMergeableTransactions() {
        val appDatabase = AppDatabase.getInstance(composeTestRule.activity.applicationContext)
        runBlocking {
            val now = System.currentTimeMillis()
            // Parent transaction
            appDatabase.transactionDao().insert(
                Transaction(
                    description = "MergeTestMerchant",
                    amount = 100.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now - 1000L,
                    transactionType = "expense",
                    notes = null,
                    mergeDismissed = false
                ),
            )
            // Child transaction
            appDatabase.transactionDao().insert(
                Transaction(
                    description = "MergeTestMerchant",
                    amount = 50.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now,
                    transactionType = "expense",
                    notes = null,
                    mergeDismissed = false
                ),
            )
        }
    }

    @Test
    fun test_dashboardShowsMergeSuggestion_andDismissWorks() {
        seedMergeableTransactions()

        // Wait for dashboard to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Merge Suggestion").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the suggestion card is displayed
        composeTestRule.onNodeWithText("Merge Suggestion").assertIsDisplayed()

        // Click dismiss
        composeTestRule.onNodeWithContentDescription("Dismiss").performClick()

        // Verify card disappears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Merge Suggestion").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("Merge Suggestion").assertDoesNotExist()
    }

    @Test
    fun test_dashboardShowsMergeSuggestion_andMergeWorks() {
        seedMergeableTransactions()

        // Wait for dashboard to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Merge Suggestion").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the suggestion card is displayed
        composeTestRule.onNodeWithText("Merge Suggestion").assertIsDisplayed()

        // Click merge
        composeTestRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(androidx.compose.ui.test.hasTestTag("merge_button"))
        composeTestRule.onNodeWithTag("merge_button").performClick()

        // Verify card disappears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Merge Suggestion").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("Merge Suggestion").assertDoesNotExist()
    }

    @Test
    fun test_unmergeTransaction_fromDetailScreen() {
        seedMergeableTransactions()

        // Wait for dashboard to load and show merge suggestion
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Merge Suggestion").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Merge them
        composeTestRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(androidx.compose.ui.test.hasTestTag("merge_button"))
        composeTestRule.onNodeWithTag("merge_button").performClick()

        // Verify card disappears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Merge Suggestion").fetchSemanticsNodes().isEmpty()
        }

        // 2. Open the merged transaction from the transactions tab
        composeTestRule.onNodeWithText("Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("MergeTestMerchant").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNode(hasText("MergeTestMerchant"), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // 3. Verify the unmerge card is visible and click "Unmerge"
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("transaction_detail_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column").performScrollToNode(hasText("Merged Transaction"))
        composeTestRule.onNodeWithText("Unmerge").performClick()

        // 4. Verify confirmation dialog appears and confirm
        composeTestRule.onNodeWithText("Unmerge Transactions?").assertIsDisplayed()
        composeTestRule.onNode(hasText("Unmerge").and(hasAnyAncestor(isDialog()))).performClick()

        // 5. Should navigate back to dashboard
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Dashboard").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
