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

/**
 * Instrumented UI tests for Phase 4: Search Workflow.
 */
@RunWith(AndroidJUnit4::class)
class SearchWorkflowTests {
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

    private fun navigateToSearch() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Keyword (description, notes)").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_search_byDescription_returnsResults() {
        navigateToSearch()

        val searchInput = composeTestRule.onNodeWithText("Keyword (description, notes)")
        searchInput.performTextInput(TestDataSeeder.TXN_GROCERY_DESC)

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.TXN_GROCERY_DESC).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_GROCERY_DESC).assertExists()
    }

    @Test
    fun test_search_noResults_showsEmptyState() {
        navigateToSearch()

        val searchInput = composeTestRule.onNodeWithText("Keyword (description, notes)")
        searchInput.performTextInput("xyzzy_notreal_abc")

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("No transactions match your criteria.").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("No transactions match your criteria.").assertExists()
    }

    @Test
    fun test_search_result_tapNavigatesToDetail() {
        navigateToSearch()

        val searchInput = composeTestRule.onNodeWithText("Keyword (description, notes)")
        searchInput.performTextInput(TestDataSeeder.TXN_SALARY_DESC)

        // Wait for the transaction item test tag instead of text to avoid matching the text field
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("transaction_item_${TestDataSeeder.TXN_SALARY_DESC}").fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss keyboard completely using Espresso to ensure the bottom of the screen is clickable
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        // Tap the transaction result using its test tag to avoid clicking the text field
        composeTestRule.onNodeWithTag("transaction_item_${TestDataSeeder.TXN_SALARY_DESC}").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify we are on the transaction detail screen.
        // Income transactions show "Credit transaction" as the TopAppBar title
        // (see TransactionDetailScreen.kt: when (transactionType) { "income" -> "Credit transaction" })
        // We verify via the detail screen's unique lazy column tag + the transaction description.
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("transaction_detail_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("transaction_detail_lazy_column").assertExists()
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_SALARY_DESC).assertIsDisplayed()
        // Confirm title: income = "Credit transaction"
        composeTestRule.onNodeWithText("Credit transaction").assertIsDisplayed()
    }

    @Test
    fun test_search_filter_byAccount() {
        navigateToSearch()

        // Open filters
        composeTestRule.onNodeWithText("Filters").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Account").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap Account filter dropdown
        composeTestRule.onNodeWithText("Account").performClick()
        
        // Select Cash Account
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).performClick()

        // Collapse filters to give space for the list
        composeTestRule.onNodeWithText("Filters").performClick()

        // Wait for results. TXN_TAXI_DESC is near the top of the list, avoiding scroll issues
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("transaction_item_${TestDataSeeder.TXN_TAXI_DESC}").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(TestDataSeeder.TXN_TAXI_DESC).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_SALARY_DESC).assertDoesNotExist() // Salary is in Checking
    }
}
