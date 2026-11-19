package io.pm.finlight.ui.screens.sms

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.Category
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ApproveTransactionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun approveTransactionScreen_displaysDetailsAndCategories() {
        val navController = mockk<NavController>(relaxed = true)
        val transactionViewModel = mockk<TransactionViewModel>(relaxed = true)
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)

        val pt =
            PotentialTransaction(
                sourceSmsId = 888L,
                smsSender = "AD-SWIGGY",
                amount = 450.0,
                transactionType = "expense",
                merchantName = "Swiggy Delivery",
                originalMessage = "Spent Rs.450 at Swiggy",
            )

        val fakeCategories = listOf(Category(id = 1, name = "Food & Dining", iconKey = "food", colorKey = "orange"))

        every { transactionViewModel.allCategories } returns MutableStateFlow(fakeCategories)
        every { transactionViewModel.allTags } returns MutableStateFlow(emptyList<Tag>())
        every { transactionViewModel.selectedTags } returns MutableStateFlow(emptySet<Tag>())
        every { transactionViewModel.travelModeSettings } returns MutableStateFlow(null)

        composeTestRule.setContent {
            ApproveTransactionScreen(
                navController = navController,
                transactionViewModel = transactionViewModel,
                settingsViewModel = settingsViewModel,
                potentialTxn = pt,
            )
        }

        composeTestRule.onNodeWithText("Approve Transaction").assertExists()
        composeTestRule.onNodeWithText("Swiggy Delivery").assertExists()
        composeTestRule.onNodeWithText("Select category").assertExists()
    }
}
