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
            composeTestRule.onAllNodesWithText("Add Rename Rule").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Raw / SMS Merchant Name").performTextInput(rawName)
        composeTestRule.onNodeWithText("Display Name").performTextInput(displayName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(displayName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(displayName).assertExists()
        composeTestRule.onNodeWithText(rawName).assertExists()
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
            composeTestRule.onAllNodesWithText("Add Rename Rule").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Raw / SMS Merchant Name").performTextInput(rawName)
        composeTestRule.onNodeWithText("Display Name").performTextInput(initialName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(initialName).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Click Edit
        composeTestRule.onNodeWithTag("edit_rule_$rawName").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Edit Rename Rule").fetchSemanticsNodes().isNotEmpty()
        }

        val nameInput = composeTestRule.onNodeWithText("Display Name")
        nameInput.performTextClearance()
        nameInput.performTextInput(updatedName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(updatedName).fetchSemanticsNodes().isNotEmpty()
        }
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
            composeTestRule.onAllNodesWithText("Add Rename Rule").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Raw / SMS Merchant Name").performTextInput(rawName)
        composeTestRule.onNodeWithText("Display Name").performTextInput(displayName)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add").performClick()

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(displayName).fetchSemanticsNodes().isNotEmpty()
        }

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
}
