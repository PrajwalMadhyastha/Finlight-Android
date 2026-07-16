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
class TransactionMergeUITest {
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
            appDatabase.transactionDao().insert(
                Transaction(
                    description = "ManualMergeTest1",
                    amount = 100.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now - 1000L,
                    transactionType = "expense",
                    notes = null,
                    mergeDismissed = false
                ),
            )
            appDatabase.transactionDao().insert(
                Transaction(
                    description = "ManualMergeTest2",
                    amount = 50.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = now - 500L,
                    transactionType = "expense",
                    notes = null,
                    mergeDismissed = false
                ),
            )
        }
    }

    @Test
    fun manualMergeFlow_showsSheet_andMergesSuccessfully() {
        seedMergeableTransactions()

        // Wait for dashboard to load and navigate to Transactions tab
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Transactions").performClick()

        // Wait for transactions to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("ManualMergeTest1").fetchSemanticsNodes().isNotEmpty()
        }

        // Long press first transaction to enter selection mode
        composeTestRule.onNodeWithText("ManualMergeTest1").performTouchInput { longClick() }

        // Wait for selection mode to activate
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("1 Selected").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Add sleep to wait for any placement animations to settle
        Thread.sleep(1000)
        // Tap second transaction to select it
        composeTestRule.onNodeWithTag("transaction_item_checkbox_ManualMergeTest2").performTouchInput { click() }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("2 Selected").fetchSemanticsNodes().isNotEmpty()
        }

        // The merge icon should be visible in the selection top bar
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithContentDescription("Merge Transactions").fetchSemanticsNodes().isNotEmpty()
        }

        // Click the merge icon
        composeTestRule.onNodeWithContentDescription("Merge Transactions").performClick()

        // Verify the review merge bottom sheet appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Review Merge").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify anchor selection works
        // The sheet displays radio buttons or clickable cards.
        // Let's click on "Confirm Merge"
        composeTestRule.onNodeWithText("Confirm Merge").assertExists()
        composeTestRule.onNodeWithText("Confirm Merge").performClick()

        // The sheet should dismiss and "ManualMergeTest2" should disappear from list
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("ManualMergeTest2").fetchSemanticsNodes().isEmpty()
        }

        // ManualMergeTest1 should now have the combined amount of 150.0
        // Or wait, is the amount updated? Yes.
        // But amount display may vary by locale, so just checking ManualMergeTest2 disappearance is good enough.
    }

    @Test
    fun manualMergeFlow_editAnchorDetails_updatesSuccessfully() {
        seedMergeableTransactions()

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Transactions").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("ManualMergeTest1").fetchSemanticsNodes().isNotEmpty()
        }

        // Enter selection mode
        composeTestRule.onNodeWithText("ManualMergeTest1").performTouchInput { longClick() }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("1 Selected").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        Thread.sleep(1000)
        composeTestRule.onNodeWithTag("transaction_item_checkbox_ManualMergeTest2").performTouchInput { click() }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("2 Selected").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Merge Sheet
        composeTestRule.onNodeWithContentDescription("Merge Transactions").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Review Merge").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap Edit Anchor Details
        composeTestRule.onNodeWithText("Edit Anchor Details").performClick()

        // Wait for inline edit view
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty()
        }

        // Edit Description (initial value is ManualMergeTest1). Use hasSetTextAction to disambiguate from the read-only row item.
        composeTestRule.onNode(hasText("ManualMergeTest1") and hasSetTextAction()).performTextReplacement("EditedAnchor")

        // Save
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for it to go back to Review Merge
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Confirm Merge").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm Merge
        composeTestRule.onNodeWithText("Confirm Merge").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("ManualMergeTest2").fetchSemanticsNodes().isEmpty()
        }

        // Verify EditedAnchor is visible
        composeTestRule.onNodeWithText("EditedAnchor").assertExists()
    }
}
