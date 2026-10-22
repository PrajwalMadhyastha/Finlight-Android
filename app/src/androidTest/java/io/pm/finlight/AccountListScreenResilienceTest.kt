package io.pm.finlight

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Instrumented resilience test for AccountListScreen.
 * Validates smooth rendering and resilience when accounts use OEM gradient vectors
 * (Bandhan, NatWest), standard logos (SBI, HDFC), and boundary-case account names.
 */
@RunWith(AndroidJUnit4::class)
class AccountListScreenResilienceTest {
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

    @Before
    fun setupEdgeCaseAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getInstance(context)
        runBlocking {
            db.accountDao().insertAll(
                listOf(
                    Account(name = "Bandhan Bank Account", type = "Bank"),
                    Account(name = "NatWest Current Account", type = "Bank"),
                    Account(name = "HDFC Salary Account", type = "Bank"),
                    Account(name = "Very Long Account Name That Exceeds Normal Length Boundary 12345", type = "General"),
                ),
            )
        }
    }

    private fun navigateToAccountList() {
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Accounts"))
        composeTestRule.onNodeWithText("Accounts").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_accountList_rendersWithEdgeCaseNamesAndLogosWithoutCrashing() {
        navigateToAccountList()

        // Verify accounts with previously crash-prone vectors render successfully
        composeTestRule.onNodeWithText("Bandhan Bank Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("NatWest Current Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("HDFC Salary Account").assertIsDisplayed()
    }
}
