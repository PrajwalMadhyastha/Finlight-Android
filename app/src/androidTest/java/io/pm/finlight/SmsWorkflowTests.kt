// =================================================================================
// FILE: ./app/src/androidTest/java/io/pm/finlight/SmsWorkflowTests.kt
// REASON: PHASE 10 - UI tests for the SMS Approval workflow.
// Verifies that ApproveTransactionScreen works correctly when provided with a
// PotentialTransaction. Uses isolated composable testing to avoid deep link flakiness.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.screens.ApproveTransactionScreen
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModelFactory
import io.pm.finlight.ui.viewmodel.TransactionViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsWorkflowTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var transactionViewModel: TransactionViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        db = AppDatabase.getInstance(context)

        runBlocking {
            db.transactionDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().insert(Category(name = "Food", iconKey = "fastfood", colorKey = "orange"))
        }

        // Initialize ViewModels manually for the isolated composable test
        transactionViewModel = TransactionViewModelFactory(context).create(TransactionViewModel::class.java)
        settingsViewModel = SettingsViewModelFactory(context, transactionViewModel).create(SettingsViewModel::class.java)
    }

    @Test
    fun test_approveSmsTransaction_createsTransaction() =
        runBlocking {
            // 1. Create a PotentialTransaction (in-memory)
            val pt =
                PotentialTransaction(
                    sourceSmsId = 998L,
                    smsSender = "AD-HDFCBK",
                    amount = 450.0,
                    transactionType = "expense",
                    merchantName = "Swiggy",
                    originalMessage = "Spent Rs.450.00 at Swiggy on HDFC Bank Card.",
                    date = System.currentTimeMillis(),
                )

            // 2. Set the content directly to the ApproveTransactionScreen
            composeTestRule.setContent {
                ApproveTransactionScreen(
                    potentialTxn = pt,
                    navController = rememberNavController(),
                    transactionViewModel = transactionViewModel,
                    settingsViewModel = settingsViewModel,
                )
            }

            // Verify the UI loads the transaction data
            composeTestRule.onNodeWithText("Swiggy").assertExists()
            composeTestRule.onNodeWithText("₹450.00").assertExists() // Assuming default currency format

            // 3. In Approve Screen, select category
            composeTestRule.onNodeWithText("Select category").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Food").performClick()
            composeTestRule.waitForIdle()

            // 4. Save the transaction
            composeTestRule.onNodeWithText("Save Transaction").performClick()
            composeTestRule.waitForIdle()

            // 5. Verify it was added to transactions in the database
            val txns = db.transactionDao().getAllTransactionsSimple().first()
            assert(txns.size == 1) { "One transaction should have been created" }
            assert(txns[0].description == "Swiggy")
            assert(txns[0].amount == 450.0)
            assert(txns[0].sourceSmsId == 998L)
        }

    @Test
    fun test_cancelSmsTransaction_doesNotCreateTransaction() =
        runBlocking {
            // 1. Create a PotentialTransaction
            val pt =
                PotentialTransaction(
                    sourceSmsId = 997L,
                    smsSender = "TM-ZOMATO",
                    amount = 250.0,
                    transactionType = "expense",
                    merchantName = "Zomato",
                    originalMessage = "Spent Rs.250.00 at Zomato.",
                    date = System.currentTimeMillis(),
                )

            // 2. Set the content directly
            composeTestRule.setContent {
                ApproveTransactionScreen(
                    potentialTxn = pt,
                    navController = rememberNavController(),
                    transactionViewModel = transactionViewModel,
                    settingsViewModel = settingsViewModel,
                )
            }

            composeTestRule.onNodeWithText("Zomato").assertExists()

            // 3. Cancel the flow
            composeTestRule.onNodeWithText("Cancel").performClick()
            composeTestRule.waitForIdle()

            // 4. Verify it was not added to DB
            val txns = db.transactionDao().getAllTransactionsSimple().first()
            assert(txns.isEmpty()) { "No transaction should have been created on cancel" }
        }
}
