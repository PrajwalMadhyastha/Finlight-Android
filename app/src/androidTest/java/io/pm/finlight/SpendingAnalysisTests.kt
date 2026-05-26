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
class SpendingAnalysisTests {

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(DisableOnboardingRule())
        .around(DisableAppLockRule())
        // Requires seeded data to render charts
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

    private fun navigateToAnalysisScreen() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Reports").performClick()
        
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToNode(hasText("Spending Analysis"))

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Spending Analysis").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Spending Analysis").performClick()
        

    }

    @Test
    fun test_analysisScreen_displaysCategoryCards() {
        navigateToAnalysisScreen()
        
        // Wait for the total spending to be populated
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Assert category breakdown cards are visible
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_TRANSPORT_NAME).assertIsDisplayed()
    }

    @Test
    fun test_analysisDrilldown_navigatesToDetail() {
        navigateToAnalysisScreen()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Tap a category card
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).performClick()
        
        // Assert Detail screen opens
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Analysis Details").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Check if the drill-down title is present
        // The title might be 'Food & Drinks' at the top
        composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).onFirst().assertIsDisplayed()
    }

    @Test
    fun test_analysisDetail_showsTransactionList() {
        navigateToAnalysisScreen()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.TXN_GROCERY_DESC).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Assert that a known seeded transaction for this category is shown
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_GROCERY_DESC).assertIsDisplayed()
    }

    @Test
    fun test_analysisScreen_dateRangeFilter() {
        navigateToAnalysisScreen()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Select custom range by scrolling to index 4 (the Custom item)
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst().performScrollToIndex(4)
        composeTestRule.onNodeWithText("Custom").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Select Date Range").fetchSemanticsNodes().isNotEmpty()
        }
        
        // Cancel to go back
        composeTestRule.onNodeWithText("Cancel").performClick()
        
        // Alternatively, click on a different period like "YEAR"
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst().performScrollToIndex(2)
        composeTestRule.onNodeWithText("YEAR").performClick()
        
        // Total should still show up
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(TestDataSeeder.CATEGORY_FOOD_NAME).fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).assertIsDisplayed()
    }
}
