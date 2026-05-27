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
import java.util.UUID

/**
 * Instrumented UI tests for Phase 4: Category Management.
 * Covers category management CRUD and detail views.
 */
@RunWith(AndroidJUnit4::class)
class CategoryManagementTests {
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

    private fun navigateToCategoryList() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Profile").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Manage Categories").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("Manage Categories").performScrollTo().performClick()
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Add New Category").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_addCategory_appearsInList() {
        navigateToCategoryList()

        val uniqueCategoryName = "New Cat ${UUID.randomUUID().toString().take(5)}"

        composeTestRule.onNodeWithText("Add New Category").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Category Name").fetchSemanticsNodes().isNotEmpty()
        }

        val nameInput = composeTestRule.onNodeWithText("Category Name")
        nameInput.performTextInput(uniqueCategoryName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        
        composeTestRule.onNodeWithText("Add").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(uniqueCategoryName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(uniqueCategoryName).assertExists()
    }

    @Test
    fun test_editCategory_updatesName() {
        navigateToCategoryList()

        val updatedCategoryName = "Updated Cat ${UUID.randomUUID().toString().take(5)}"

        composeTestRule.onNodeWithTag("edit_category_${TestDataSeeder.CATEGORY_FOOD_NAME}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Edit Category").fetchSemanticsNodes().isNotEmpty()
        }

        val nameInput = composeTestRule.onNodeWithText("Category Name")
        nameInput.performTextClearance()
        nameInput.performTextInput(updatedCategoryName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        composeTestRule.onNodeWithText("Update").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(updatedCategoryName).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(updatedCategoryName).assertExists()
        composeTestRule.onNodeWithText(TestDataSeeder.CATEGORY_FOOD_NAME).assertDoesNotExist()
    }

    @Test
    fun test_deleteCategory_removesFromList() {
        navigateToCategoryList()

        // 1. Add a temporary category
        val tempCategoryName = "Temp Delete Cat"
        composeTestRule.onNodeWithText("Add New Category").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Category Name").fetchSemanticsNodes().isNotEmpty()
        }
        val nameInputTemp = composeTestRule.onNodeWithText("Category Name")
        nameInputTemp.performTextInput(tempCategoryName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.onNodeWithText("Add").performClick()

        // 2. Wait for it to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(tempCategoryName).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Delete it
        composeTestRule.onNodeWithTag("delete_category_$tempCategoryName").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Delete Category").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete").performClick()

        // 4. Verify it's gone
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(tempCategoryName).fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText(tempCategoryName).assertDoesNotExist()
    }

    @Test
    fun test_categoryDetail_showsSpendingBreakdown() {
        // Navigate to Reports -> Spending Analysis screen.
        // The Reports screen card is labeled "Spending Analysis" (not just "Analysis").
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_item_Reports").performClick()

        // Wait for the reports list to appear (same anchor used by ReportsNavigationTests)
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Spending Consistency").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll down to the "Spending Analysis" card and tap it
        composeTestRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasText("Spending Analysis"))
        composeTestRule.onNodeWithText("Spending Analysis").performClick()

        // Wait for the AnalysisScreen to fully load (search field is a reliable anchor)
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("analysis_item_${TestDataSeeder.CATEGORY_FOOD_ID}").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("No spending data for this selection.").fetchSemanticsNodes().isNotEmpty()
        }

        // The default period is MONTH. Seeded transactions are in the current month so they
        // should appear without switching to ALL TIME. Only switch if not visible yet.
        if (composeTestRule.onAllNodesWithTag("analysis_item_${TestDataSeeder.CATEGORY_FOOD_ID}").fetchSemanticsNodes().isEmpty()) {
            // Switch to "ALL TIME" period chip.
            // Chip label: period.name.replaceFirstChar { it.titlecase() }.replace("_", " ")
            // ALL_TIME -> 'A' is already uppercase -> "ALL_TIME" -> replace _ -> "ALL TIME"
            composeTestRule.onNodeWithText("ALL TIME").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("analysis_item_${TestDataSeeder.CATEGORY_FOOD_ID}").fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Scroll to the Food & Drinks analysis item and tap it
        composeTestRule.onNodeWithTag("analysis_item_${TestDataSeeder.CATEGORY_FOOD_ID}")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("analysis_item_${TestDataSeeder.CATEGORY_FOOD_ID}").performClick()
        composeTestRule.waitForIdle()

        // Verify the AnalysisDetailScreen shows the seeded grocery transaction
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("transaction_item_${TestDataSeeder.TXN_GROCERY_DESC}").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("transaction_item_${TestDataSeeder.TXN_GROCERY_DESC}")
            .performScrollTo().assertIsDisplayed()
    }
}
