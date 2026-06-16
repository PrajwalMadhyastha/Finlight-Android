package io.pm.finlight

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@org.junit.Ignore("Temporarily disabled (Issue #105)")
@RunWith(AndroidJUnit4::class)
class RecurringTransactionScreenTest {
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
    fun testUpcomingPaymentsCardRendersAndOpensBottomSheet() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = AppDatabase.getInstance(context)

            val ruleId =
                db.recurringTransactionDao().insert(
                    RecurringTransaction(
                        description = "Test Pending Rule",
                        amount = 150.0,
                        transactionType = "expense",
                        recurrenceInterval = "Monthly",
                        startDate = System.currentTimeMillis(),
                        accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                        categoryId = TestDataSeeder.CATEGORY_FOOD_ID
                    )
                ).toInt()

            db.transactionDao().insert(
                Transaction(
                    description = "Test Pending Rule",
                    amount = 150.0,
                    date = System.currentTimeMillis(),
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    notes = null,
                    transactionType = "expense",
                    status = "PENDING",
                    recurringRuleId = ruleId
                )
            )

            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 10000) {
                composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNode(hasTestTag("dashboard_lazy_column"))
                .performScrollToNode(hasText("Upcoming Payments", substring = true))

            composeTestRule.onNodeWithText("Upcoming Payments", substring = true).assertExists()
            composeTestRule.onNodeWithText("Test Pending Rule").assertExists()

            composeTestRule.onNode(hasText("150.00", substring = true)).performClick()

            composeTestRule.onAllNodesWithText("Confirm Payment", substring = true).onFirst().assertExists()
            composeTestRule.onNode(hasText("Expected:", substring = true)).assertExists()
        }
    }

    @Test
    fun testRecurringSuggestionsCardRenders() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = AppDatabase.getInstance(context)

            db.recurringPatternDao().insert(
                RecurringPattern(
                    smsSignature = "TEST_PATTERN",
                    description = "Test Suggestion",
                    amount = 199.0,
                    transactionType = "expense",
                    accountId = TestDataSeeder.ACCOUNT_BANK_ID,
                    categoryId = TestDataSeeder.CATEGORY_FOOD_ID,
                    occurrences = 3,
                    firstSeen = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis(),
                    isDismissed = false
                )
            )

            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 10000) {
                composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNode(hasTestTag("dashboard_lazy_column"))
                .performScrollToNode(hasText("Recurring Suggestions", substring = true))

            composeTestRule.onNodeWithText("Recurring Suggestions", substring = true).assertExists()
            composeTestRule.onNodeWithText("Test Suggestion").assertExists()
            composeTestRule.onNode(hasText("3 occurrences", substring = true)).assertExists()
            composeTestRule.onNodeWithText("Set Up Rule").assertExists()
        }
    }
}
