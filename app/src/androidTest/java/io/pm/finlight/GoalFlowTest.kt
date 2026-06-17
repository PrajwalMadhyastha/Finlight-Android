package io.pm.finlight

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalFlowTest {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(DisableOnboardingRule())
            .around(DisableAppLockRule())
            .around(ClearDatabaseRule())
            .around(SeedDatabaseRule())
            .around(
                androidx.test.rule.GrantPermissionRule.grant(
                    android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
            .around(composeTestRule)

    @Test
    fun testCreateGoalAndLinkTransaction() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = AppDatabase.getInstance(context)

            // Insert a dummy goal so the "Savings Goals" card appears on the Dashboard
            db.goalDao().insert(
                Goal(
                    name = "Test Goal #0",
                    targetAmount = 1000.0,
                    targetDate = null,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID
                )
            )
        }

        composeTestRule.waitForIdle()

        // Wait for dashboard to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Savings Goals and click it
        composeTestRule.onNode(hasTestTag("dashboard_lazy_column"))
            .performScrollToNode(hasText("Savings Goals", substring = true))
        composeTestRule.onNodeWithText("Savings Goals", substring = true).performClick()

        // Now on GoalScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add Goal").assertExists().performClick()

        // Now on AddEditGoalScreen
        composeTestRule.waitForIdle()
        // Wait for screen to be ready
        composeTestRule.onNodeWithText("Goal Name", substring = true).performTextInput("Dream Car")
        composeTestRule.onNodeWithText("Target Amount", substring = true).performTextInput("50000")

        // Select Account (ExposedDropdownMenuBox)
        composeTestRule.onNodeWithText("Allocate To Account", substring = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_BANK_NAME, substring = true).performClick()

        // Click Save
        composeTestRule.onNodeWithText("Save", substring = true).performClick()

        // Should return to GoalScreen and display "Dream Car"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dream Car", substring = true).assertExists().performClick()

        // Now on GoalDetailScreen
        composeTestRule.waitForIdle()
        // Assert we are on the detail screen for "Dream Car"
        composeTestRule.onNodeWithText("Dream Car", substring = true).assertExists()

        // Click "Link a transaction"
        composeTestRule.onNodeWithText("Link a transaction", substring = true, useUnmergedTree = true)
            .assertExists()
            .performClick()

        // Now TransactionPickerSheet is shown
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Select Transaction", substring = true)
            .assertExists()

        // Click the first transaction (e.g. Test Salary, etc. from SeedDatabaseRule)
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_GROCERY_DESC, substring = true).performScrollTo().performClick()

        // Sheet dismisses, back on GoalDetailScreen
        composeTestRule.waitForIdle()

        // Assert transaction description appears in Linked Transactions
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_GROCERY_DESC, substring = true).performScrollTo().assertExists()
    }

    @Test
    fun testCreateGoalWithOfflineContribution() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = AppDatabase.getInstance(context)

            db.goalDao().insert(
                Goal(
                    name = "Test Goal #1",
                    targetAmount = 1000.0,
                    targetDate = null,
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID
                )
            )
        }

        composeTestRule.waitForIdle()

        // Wait for dashboard to load
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Savings Goals and click it
        composeTestRule.onNode(hasTestTag("dashboard_lazy_column"))
            .performScrollToNode(hasText("Savings Goals", substring = true))
        composeTestRule.onNodeWithText("Savings Goals", substring = true).performClick()

        // Now on GoalScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add Goal").assertExists().performClick()

        // Now on AddEditGoalScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Goal Name", substring = true).performTextInput("Vacation")
        composeTestRule.onNodeWithText("Target Amount", substring = true).performTextInput("5000")

        // Select Account (ExposedDropdownMenuBox)
        composeTestRule.onNodeWithText("Allocate To Account", substring = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_BANK_NAME, substring = true).performClick()

        // Click Save
        composeTestRule.onNodeWithText("Save", substring = true).performClick()

        // Should return to GoalScreen and display "Vacation"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Vacation", substring = true).assertExists().performClick()

        // Now on GoalDetailScreen
        composeTestRule.waitForIdle()
        // Assert we are on the detail screen for "Vacation"
        composeTestRule.onNodeWithText("Vacation", substring = true).assertExists()

        // Add an offline contribution via GoalDetailScreen
        composeTestRule.onNodeWithText("Add", substring = true, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Inside Add Contribution Dialog
        composeTestRule.onNodeWithText("Amount", substring = true).performTextInput("1000")
        composeTestRule.onNodeWithText("Description (Optional)", substring = true).performTextInput("Cash savings")

        // Save the contribution
        composeTestRule.onNodeWithText("Save", substring = true, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Assert that the offline contribution is displayed. (1,000 offline)
        composeTestRule.onAllNodesWithText("1,000", substring = true).onFirst().assertExists()
        composeTestRule.onNodeWithText("Cash savings", substring = true).assertExists()
    }
}
