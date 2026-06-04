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
 * Instrumented UI tests for Phase 2: Account Management.
 * Covers the full CRUD lifecycle of an account and detail views.
 */
@RunWith(AndroidJUnit4::class)
class AccountManagementTests {
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

    /**
     * Navigates from Dashboard to the Accounts list screen.
     */
    private fun navigateToAccountList() {
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_column").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasText("Accounts"))
        composeTestRule.onNodeWithText("Accounts").performClick()

        // Wait for AccountListScreen to open (by checking for "Add" FAB)
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Test adding a new account and verifying it appears in the list.
     */
    @Test
    fun test_addAccount_appearsInAccountList() {
        navigateToAccountList()

        val uniqueAccountName = "Savings Account ${UUID.randomUUID().toString().take(5)}"

        // Click Add FAB
        composeTestRule.onNodeWithContentDescription("Add").performClick()

        // Wait for AddEditAccountScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add New Account").fetchSemanticsNodes().isNotEmpty()
        }

        // Fill Name
        composeTestRule.onNodeWithText("Account Name (e.g., Savings, Credit Card)").performTextInput(uniqueAccountName)

        // Fill Type
        composeTestRule.onNodeWithText("Account Type (e.g., Bank, Wallet)").performTextInput("Bank")

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for return to AccountListScreen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify new account appears in the list
        composeTestRule.onNodeWithText(uniqueAccountName).assertExists()
    }

    /**
     * Test editing an existing account's name and verifying the update.
     */
    @Test
    fun test_editAccount_updatesName() {
        navigateToAccountList()

        val updatedAccountName = "Updated Bank ${UUID.randomUUID().toString().take(5)}"

        // Click Edit Account button for the seeded "Test Bank" account
        // We do NOT use useUnmergedTree = true here because in the default merged tree,
        // the parent GlassPanel merges the child text node's text "Test Bank",
        // so the IconButton matches hasAnyAncestor(hasText("Test Bank")).
        composeTestRule.onNode(
            hasContentDescription("Edit Account") and hasAnyAncestor(hasText(TestDataSeeder.ACCOUNT_BANK_NAME)),
        ).performClick()

        // Wait for AddEditAccountScreen to load in edit mode
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Edit Account").fetchSemanticsNodes().isNotEmpty()
        }

        // Clear existing name and enter the updated name
        val nameInput = composeTestRule.onNodeWithText("Account Name (e.g., Savings, Credit Card)")
        nameInput.performTextClearance()
        nameInput.performTextInput(updatedAccountName)

        // Click Update
        composeTestRule.onNodeWithText("Update").performClick()

        // Wait for return to AccountListScreen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify updated account exists and original does not
        composeTestRule.onNodeWithText(updatedAccountName).assertExists()
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_BANK_NAME).assertDoesNotExist()
    }

    /**
     * Test deleting an account and verifying it is removed from the list.
     */
    @Test
    fun test_deleteAccount_removesFromList() {
        navigateToAccountList()

        // Click Edit Account button for the seeded "Test Wallet" account
        composeTestRule.onNode(
            hasContentDescription("Edit Account") and hasAnyAncestor(hasText(TestDataSeeder.ACCOUNT_WALLET_NAME)),
        ).performClick()

        // Wait for AddEditAccountScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Edit Account").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Delete Account
        composeTestRule.onNodeWithText("Delete Account").performClick()

        // Wait for confirmation dialog and click Delete
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Confirm Deletion").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete").performClick()

        // Wait for return to AccountListScreen
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify account is removed from list
        composeTestRule.onNodeWithText(TestDataSeeder.ACCOUNT_WALLET_NAME).assertDoesNotExist()
    }

    /**
     * Test that the AccountDetailScreen header shows the correct transaction count badge
     * for an account. Resolves GitHub Issue #7.
     *
     * Test Bank has 4 seeded transactions: Grocery Run, Shirt Purchase, Salary, Bonus.
     */
    @Test
    fun test_accountDetail_showsTransactionCountBadge_forBankAccount() {
        navigateToAccountList()

        // Click on the "Test Bank" card item to open detail screen
        composeTestRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText(TestDataSeeder.ACCOUNT_BANK_NAME)),
            useUnmergedTree = true,
        ).performClick()

        // Wait for AccountDetailScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Current Balance").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the transaction count badge is displayed with the correct count
        // Test Bank has 4 transactions: Grocery Run, Shirt Purchase, Salary, Bonus
        composeTestRule.onNodeWithText("4 Txns").assertIsDisplayed()
    }

    /**
     * Test that the AccountDetailScreen header shows the correct transaction count badge
     * for the Wallet account.
     *
     * Test Wallet has 3 seeded transactions: Coffee, Bus Fare, Taxi.
     */
    @Test
    fun test_accountDetail_showsTransactionCountBadge_forWalletAccount() {
        navigateToAccountList()

        // Click on the "Test Wallet" card item to open detail screen
        composeTestRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText(TestDataSeeder.ACCOUNT_WALLET_NAME)),
            useUnmergedTree = true,
        ).performClick()

        // Wait for AccountDetailScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Current Balance").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the transaction count badge is displayed with the correct count
        // Test Wallet has 3 transactions: Coffee, Bus Fare, Taxi
        composeTestRule.onNodeWithText("3 Txns").assertIsDisplayed()
    }

    /**
     * Test that account detail view shows transactions seeded for that account.
     */
    @Test
    fun test_accountDetail_showsSeededTransactions() {
        navigateToAccountList()

        // Click on the "Test Bank" card item to open detail screen
        composeTestRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText(TestDataSeeder.ACCOUNT_BANK_NAME)),
            useUnmergedTree = true,
        ).performClick()

        // Wait for AccountDetailScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Current Balance").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify seeded transaction is visible (TXN_GROCERY_DESC: "Test Grocery Run")
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_GROCERY_DESC).assertIsDisplayed()

        // Verify seeded salary income is visible (TXN_SALARY_DESC: "Test Salary")
        composeTestRule.onNodeWithText(TestDataSeeder.TXN_SALARY_DESC).assertIsDisplayed()
    }

    /**
     * Test that account detail view shows the correct calculated balance.
     */
    @Test
    fun test_accountDetail_showsCorrectBalance() {
        navigateToAccountList()

        // Click on the "Test Wallet" card item to open detail screen
        composeTestRule.onNode(
            hasClickAction() and hasAnyDescendant(hasText(TestDataSeeder.ACCOUNT_WALLET_NAME)),
            useUnmergedTree = true,
        ).performClick()

        // Wait for AccountDetailScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Current Balance").fetchSemanticsNodes().isNotEmpty()
        }

        // Wallet transactions: -150 (coffee), -50 (bus), -250 (taxi) = -450 total balance.
        val expectedBalance =
            java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))
                .apply { maximumFractionDigits = 0 }
                .format(-450.0)
        composeTestRule.onNodeWithText(expectedBalance).assertExists()
    }

    /**
     * Test adding an account with an existing name shows the duplicate match dialog.
     */
    @Test
    fun test_addAccount_withDuplicateName_showsMergeDialog() {
        navigateToAccountList()

        // Click Add FAB
        composeTestRule.onNodeWithContentDescription("Add").performClick()

        // Wait for AddEditAccountScreen to load
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText("Add New Account").fetchSemanticsNodes().isNotEmpty()
        }

        // Fill Name with an existing seeded account name
        val nameInput = composeTestRule.onNodeWithText("Account Name (e.g., Savings, Credit Card)")
        nameInput.performTextClearance()
        nameInput.performTextInput(TestDataSeeder.ACCOUNT_BANK_NAME)

        val typeInput = composeTestRule.onNodeWithText("Account Type (e.g., Bank, Wallet)")
        typeInput.performTextClearance()
        typeInput.performTextInput("Bank")

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        // Wait for duplicate dialog to appear
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            try {
                composeTestRule.onNodeWithText("Account Already Exists", useUnmergedTree = true).assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // Verify the dialog elements
        composeTestRule.onNodeWithText("Choose another name", useUnmergedTree = true).assertIsDisplayed()
        
        // Dismiss the dialog
        composeTestRule.onNodeWithText("Choose another name", useUnmergedTree = true).performClick()
        
        // Dialog goes away, we are still on the Add New Account screen
        composeTestRule.onNodeWithText("Account Already Exists", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Add New Account").onFirst().assertIsDisplayed()
    }
}
