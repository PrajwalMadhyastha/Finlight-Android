package io.pm.finlight.ui.screens.recurring

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.RecurringPattern
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.viewmodel.PendingTransactionsViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recurringTransactionScreen_rendersEmptyStateWhenNoRules() {
        val navController = mockk<NavController>(relaxed = true)
        val recurringViewModel = mockk<RecurringTransactionViewModel>(relaxed = true)
        every { recurringViewModel.allRecurringTransactions } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            RecurringTransactionScreen(
                navController = navController,
                viewModel = recurringViewModel,
            )
        }

        composeTestRule.onNodeWithText("No recurring transactions set up. Tap the '+' to add one.").assertExists()
    }

    @Test
    fun upcomingPaymentsCard_rendersPendingTransactions() {
        val navController = mockk<NavController>(relaxed = true)
        val pendingViewModel = mockk<PendingTransactionsViewModel>(relaxed = true)
        val recurringViewModel = mockk<RecurringTransactionViewModel>(relaxed = true)

        val pendingTxn =
            Transaction(
                id = 1,
                description = "Netflix Subscription",
                amount = 499.0,
                date = System.currentTimeMillis(),
                accountId = 1,
                categoryId = 1,
                notes = null,
                status = TransactionStatus.PENDING,
            )

        every { pendingViewModel.pendingTransactions } returns MutableStateFlow(listOf(pendingTxn))
        every { pendingViewModel.uiEvent } returns MutableSharedFlow()
        every { recurringViewModel.allRecurringTransactions } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            UpcomingPaymentsCard(
                navController = navController,
                pendingViewModel = pendingViewModel,
                recurringViewModel = recurringViewModel,
            )
        }

        composeTestRule.onNodeWithText("Upcoming Payments").assertExists()
        composeTestRule.onNodeWithText("Netflix Subscription").assertExists()
    }

    @Test
    fun recurringSuggestionsCard_rendersSuggestionsWhenAvailable() {
        val navController = mockk<NavController>(relaxed = true)
        val recurringViewModel = mockk<RecurringTransactionViewModel>(relaxed = true)

        val pattern =
            RecurringPattern(
                smsSignature = "SIG_1",
                description = "Gym Membership",
                amount = 2500.0,
                transactionType = TransactionType.EXPENSE,
                accountId = 1,
                categoryId = 1,
                occurrences = 4,
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                isDismissed = false,
            )

        every { recurringViewModel.patternSuggestions } returns MutableStateFlow(listOf(pattern))

        composeTestRule.setContent {
            RecurringSuggestionsCard(
                navController = navController,
                recurringViewModel = recurringViewModel,
            )
        }

        composeTestRule.onNodeWithText("Recurring Suggestions").assertExists()
        composeTestRule.onNodeWithText("Gym Membership").assertExists()
        composeTestRule.onNodeWithText("Set Up Rule").assertExists()
    }
}
