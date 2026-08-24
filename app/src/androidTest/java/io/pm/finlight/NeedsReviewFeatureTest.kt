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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NeedsReviewFeatureTest {
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

    private fun seedNeedsReviewTransaction(description: String) {
        val appDatabase = AppDatabase.getInstance(composeTestRule.activity.applicationContext)
        runBlocking {
            appDatabase.transactionDao().insert(
                Transaction(
                    description = description,
                    // High amount typical for needsReview
                    amount = 1000000.0,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    date = System.currentTimeMillis() - 60_000L,
                    transactionType = TransactionType.EXPENSE,
                    needsReview = true,
                    notes = null,
                ),
            )
        }
    }

    @Test
    fun test_markAsReviewed_removesReviewBanner() {
        val uniqueDescription = "Suspicious Transaction ${UUID.randomUUID().toString().take(5)}"

        // 1. Seed the database with a needsReview transaction
        seedNeedsReviewTransaction(uniqueDescription)

        // 2. Wait for Dashboard and click the Transactions tab
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Transactions").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(uniqueDescription).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Click on the transaction to go to details screen
        composeTestRule.onNode(hasText(uniqueDescription), useUnmergedTree = true)
            .onAncestors()
            .filterToOne(hasClickAction())
            .performClick()

        // 4. Wait for detail screen to appear and check for the review banner
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Mark as Reviewed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify banner exists
        composeTestRule.onNodeWithText("Mark as Reviewed", substring = true).assertIsDisplayed()

        // 5. Click Mark as Reviewed
        composeTestRule.onNodeWithText("Mark as Reviewed", substring = true).performClick()

        // 6. Verify banner is gone
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Mark as Reviewed", substring = true).fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("Mark as Reviewed", substring = true).assertDoesNotExist()
    }
}
