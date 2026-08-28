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

@RunWith(AndroidJUnit4::class)
class MerchantRenameRuleFeatureTest {
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

    private fun navigateToManageMerchantRules() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("nav_item_Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_item_Profile").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Automation").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasText("Automation"))
        composeTestRule.onNodeWithText("Automation").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Merchant Rename Rules").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Merchant Rename Rules").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Add Rule").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_addMerchantRenameRule_appearsInList() {
        navigateToManageMerchantRules()

        val rawName = "RAW_MCH_${UUID.randomUUID().toString().take(4)}"
        val displayName = "Renamed Merchant"

        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("add_rule_raw_input").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("add_rule_raw_input").performTextInput(rawName)
        composeTestRule.onNodeWithTag("add_rule_display_input").performTextInput(displayName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("add_rule_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText(displayName))
        composeTestRule.onNodeWithText(displayName).assertExists()
        composeTestRule.onNodeWithText(rawName).assertExists()
        composeTestRule.onNodeWithTag("tx_count_$rawName").assertExists()
    }

    @Test
    fun test_editMerchantRenameRule_updatesDisplayName() {
        navigateToManageMerchantRules()

        val rawName = "RAW_EDIT_${UUID.randomUUID().toString().take(4)}"
        val initialName = "Initial Store"
        val updatedName = "Updated Store"

        // 1. Add rule first
        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("add_rule_raw_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_rule_raw_input").performTextInput(rawName)
        composeTestRule.onNodeWithTag("add_rule_display_input").performTextInput(initialName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_rule_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText(initialName))
        composeTestRule.onNodeWithText(initialName).assertExists()

        // 2. Click Edit
        composeTestRule.onNodeWithTag("edit_rule_$rawName").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("edit_rule_display_input").fetchSemanticsNodes().isNotEmpty()
        }

        val nameInput = composeTestRule.onNodeWithTag("edit_rule_display_input")
        nameInput.performTextClearance()
        nameInput.performTextInput(updatedName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("edit_rule_save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText(updatedName))
        composeTestRule.onNodeWithText(updatedName).assertExists()
    }

    @Test
    fun test_deleteMerchantRenameRule_removesFromList() {
        navigateToManageMerchantRules()

        val rawName = "RAW_DEL_${UUID.randomUUID().toString().take(4)}"
        val displayName = "To Be Deleted"

        // 1. Add rule
        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("add_rule_raw_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_rule_raw_input").performTextInput(rawName)
        composeTestRule.onNodeWithTag("add_rule_display_input").performTextInput(displayName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_rule_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText(displayName))
        composeTestRule.onNodeWithText(displayName).assertExists()

        // 2. Click Delete
        composeTestRule.onNodeWithTag("delete_rule_$rawName").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Delete Rename Rule?").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete").performClick()

        // 3. Verify removed
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(displayName).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText(displayName).assertDoesNotExist()
    }

    @Test
    fun test_collisionWarning_showsAmbiguityDialog() {
        navigateToManageMerchantRules()

        val uniqueSuffix = UUID.randomUUID().toString().take(4)
        val raw1 = "CONFLICT_ROOT_$uniqueSuffix PAY"
        val raw2 = "CONFLICT_ROOT_$uniqueSuffix INDIA"

        // 1. Add Rule 1
        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("add_rule_raw_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_rule_raw_input").performTextInput(raw1)
        composeTestRule.onNodeWithTag("add_rule_display_input").performTextInput("Target One")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_rule_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText("Target One"))
        composeTestRule.onNodeWithText("Target One").assertExists()

        // 2. Add Rule 2 with same root but different target
        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("add_rule_raw_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_rule_raw_input").performTextInput(raw2)
        composeTestRule.onNodeWithTag("add_rule_display_input").performTextInput("Target Two")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_rule_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText("Target Two"))
        composeTestRule.onNodeWithText("Target Two").assertExists()

        // 3. Collision warning icon should appear
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasTestTag("warning_rule_$raw1"))
        composeTestRule.onNodeWithTag("warning_rule_$raw1").performClick()

        // 4. Verify Rule Ambiguity Dialog
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Rule Ambiguity Warning").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Rule Ambiguity Warning").assertExists()
        composeTestRule.onNodeWithText("Got It").performClick()
    }

    @Test
    fun test_impactedTransactions_opensBottomSheetAndShowsTransactions() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val db = io.pm.finlight.data.db.AppDatabase.getInstance(context)
        val rawName = "UBER_TRIP_${UUID.randomUUID().toString().take(4)}"
        val displayName = "Uber"

        kotlinx.coroutines.runBlocking {
            db.transactionDao().insert(
                Transaction(
                    description = "Uber Trip",
                    originalDescription = rawName,
                    amount = 320.0,
                    categoryId = TestDataSeeder.CATEGORY_TRANSPORT_ID,
                    accountId = TestDataSeeder.ACCOUNT_WALLET_ID,
                    date = System.currentTimeMillis() - 10000L,
                    transactionType = TransactionType.EXPENSE,
                    notes = "Ride home",
                ),
            )
        }

        navigateToManageMerchantRules()

        // 1. Add rule for this rawName
        composeTestRule.onNodeWithContentDescription("Add Rule").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("add_rule_raw_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_rule_raw_input").performTextInput(rawName)
        composeTestRule.onNodeWithTag("add_rule_display_input").performTextInput(displayName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_rule_confirm_button").performClick()

        // 2. Locate rule and verify transaction count badge
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("rules_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rules_lazy_column").performScrollToNode(hasText(displayName))
        composeTestRule.onNodeWithTag("tx_count_$rawName").assertExists()

        // 3. Click the transaction impact badge
        composeTestRule.onNodeWithTag("tx_count_$rawName").performClick()

        // 4. Bottom Sheet should appear
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Impacted Transactions").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Impacted Transactions").assertExists()
        composeTestRule.onNodeWithTag("impacted_transactions_list").assertExists()
    }
}
