package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.pm.finlight.ParseResult
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.SmsDebugResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsDebugScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `force save button is displayed on success and clicking it saves transaction`() {
        val mockNavController: NavController = mockk(relaxed = true)
        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)

        val potentialTxn =
            PotentialTransaction(
                sourceSmsId = 1L,
                smsSender = "Sender",
                amount = 100.0,
                transactionType = "expense",
                merchantName = "Test Merchant",
                originalMessage = "Test Message",
            )

        val successResult =
            SmsDebugResult(
                smsMessage = SmsMessage(1L, "Sender", "Test Message", 1L),
                parseResult = ParseResult.Success(potentialTxn),
            )

        coEvery { mockTransactionViewModel.autoSaveSmsTransaction(any(), any()) } returns true

        composeTestRule.setContent {
            SmsDebugItem(
                result = successResult,
                navController = mockNavController,
                transactionViewModel = mockTransactionViewModel,
            )
        }

        // Verify button exists
        composeTestRule.onNodeWithText("Force Save Transaction").assertIsDisplayed()

        // Perform click
        composeTestRule.onNodeWithText("Force Save Transaction").performClick()

        // Verify view model was called
        coVerify(exactly = 1) { mockTransactionViewModel.autoSaveSmsTransaction(potentialTxn, "Manual Import") }
    }

    @Test
    fun `force save button is not displayed on failure`() {
        val mockNavController: NavController = mockk(relaxed = true)
        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)

        val failureResult =
            SmsDebugResult(
                smsMessage = SmsMessage(1L, "Sender", "Test Message", 1L),
                parseResult = ParseResult.NotParsed("Failed"),
            )

        composeTestRule.setContent {
            SmsDebugItem(
                result = failureResult,
                navController = mockNavController,
                transactionViewModel = mockTransactionViewModel,
            )
        }

        // Verify button doesn't exist
        composeTestRule.onNodeWithText("Force Save Transaction").assertDoesNotExist()
    }
}
