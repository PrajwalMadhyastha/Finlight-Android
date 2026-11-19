package io.pm.finlight.ui.screens.recurring

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.viewmodel.PendingTransactionsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ConfirmPendingBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun confirmPendingBottomSheet_rendersDetailsAndConfirmsPayment() {
        val pendingViewModel = mockk<PendingTransactionsViewModel>(relaxed = true)
        val transaction =
            Transaction(
                id = 42,
                description = "Electricity Bill",
                amount = 1200.0,
                date = System.currentTimeMillis(),
                accountId = 1,
                categoryId = 1,
                notes = null,
                status = TransactionStatus.PENDING,
                recurringRuleId = 7,
            )
        val rule =
            RecurringTransaction(
                id = 7,
                description = "Electricity Bill",
                amount = 1200.0,
                transactionType = TransactionType.EXPENSE,
                recurrenceInterval = "Monthly",
                startDate = System.currentTimeMillis(),
                accountId = 1,
                categoryId = 1,
                isVariableBill = false,
            )

        composeTestRule.setContent {
            ConfirmPendingBottomSheet(
                transaction = transaction,
                rule = rule,
                onDismiss = {},
                pendingViewModel = pendingViewModel,
            )
        }

        composeTestRule.onNodeWithText("Electricity Bill").assertExists()
        composeTestRule.onNode(hasText("Confirm Payment") and hasClickAction()).assertExists().performClick()

        verify { pendingViewModel.confirmPending(42, 7, null) }
    }
}
