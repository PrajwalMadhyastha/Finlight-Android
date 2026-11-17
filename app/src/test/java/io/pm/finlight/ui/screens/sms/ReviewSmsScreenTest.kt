package io.pm.finlight.ui.screens.sms

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ReviewSmsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun reviewSmsScreen_displaysTransactionsAndHandlesDismiss() {
        val navController = mockk<NavController>(relaxed = true)
        every { navController.currentBackStackEntry } returns null

        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)

        val pt =
            PotentialTransaction(
                sourceSmsId = 555L,
                smsSender = "VM-AMZN",
                amount = 1299.0,
                transactionType = "expense",
                merchantName = "Amazon India",
                originalMessage = "Spent Rs.1299 on Amazon",
            )

        every { settingsViewModel.potentialTransactions } returns MutableStateFlow(listOf(pt))
        every { settingsViewModel.isScanning } returns MutableStateFlow(false)

        composeTestRule.setContent {
            ReviewSmsScreen(
                navController = navController,
                viewModel = settingsViewModel,
            )
        }

        composeTestRule.onNodeWithText("1 potential transactions found.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amazon India").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dismiss").assertIsDisplayed().performClick()

        verify { settingsViewModel.dismissPotentialTransaction(pt) }
    }
}
