package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SplitTransactionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SplitHeader displays negative remaining amount with leading minus and currency symbol`() {
        val parentTxn =
            Transaction(
                id = 1,
                description = "Dinner",
                amount = 240.0,
                isSplit = false,
                date = 1000L,
                accountId = 1,
                categoryId = 1,
                notes = null,
            )

        composeTestRule.setContent {
            SplitHeader(
                parentTransaction = parentTxn,
                remainingAmount = -14.0,
            )
        }

        composeTestRule.onNodeWithText("Total Amount").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹240.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remaining: -₹14.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remaining: ₹₹14.00").assertDoesNotExist()
    }

    @Test
    fun `SplitHeader displays positive remaining amount correctly`() {
        val parentTxn =
            Transaction(
                id = 1,
                description = "Dinner",
                amount = 240.0,
                isSplit = false,
                date = 1000L,
                accountId = 1,
                categoryId = 1,
                notes = null,
            )

        composeTestRule.setContent {
            SplitHeader(
                parentTransaction = parentTxn,
                remainingAmount = 14.0,
            )
        }

        composeTestRule.onNodeWithText("Remaining: ₹14.00").assertIsDisplayed()
    }

    @Test
    fun `SplitHeader displays zero remaining amount correctly`() {
        val parentTxn =
            Transaction(
                id = 1,
                description = "Dinner",
                amount = 240.0,
                isSplit = false,
                date = 1000L,
                accountId = 1,
                categoryId = 1,
                notes = null,
            )

        composeTestRule.setContent {
            SplitHeader(
                parentTransaction = parentTxn,
                remainingAmount = 0.0,
            )
        }

        composeTestRule.onNodeWithText("Remaining: ₹0.00").assertIsDisplayed()
    }

    @Test
    fun `SplitHeader displays foreign currency with negative remaining amount correctly`() {
        val parentTxn =
            Transaction(
                id = 1,
                description = "Dinner",
                amount = 8300.0,
                originalAmount = 100.0,
                currencyCode = "USD",
                isSplit = false,
                date = 1000L,
                accountId = 1,
                categoryId = 1,
                notes = null,
            )

        composeTestRule.setContent {
            SplitHeader(
                parentTransaction = parentTxn,
                remainingAmount = -15.0,
            )
        }

        composeTestRule.onNodeWithText("$100.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remaining: -$15.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remaining: $$15.00").assertDoesNotExist()
    }
}
