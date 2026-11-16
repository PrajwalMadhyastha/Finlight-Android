package io.pm.finlight.ui.components

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class PrivacyAwareTextAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun maskedCurrencyAmount_setsHiddenBalanceContentDescription() {
        composeTestRule.setContent {
            PrivacyAwareText(
                amount = 2500,
                isPrivacyMode = true,
                isCurrency = true,
            )
        }

        composeTestRule.onNodeWithContentDescription("Hidden balance").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹ ****").assertIsDisplayed()
    }

    @Test
    fun maskedNonCurrencyAmount_setsHiddenBalanceContentDescription() {
        composeTestRule.setContent {
            PrivacyAwareText(
                amount = 42,
                isPrivacyMode = true,
                isCurrency = false,
            )
        }

        composeTestRule.onNodeWithContentDescription("Hidden balance").assertIsDisplayed()
        composeTestRule.onNodeWithText("****").assertIsDisplayed()
    }

    @Test
    fun maskedAmountWithPrefix_setsHiddenBalanceContentDescription() {
        composeTestRule.setContent {
            PrivacyAwareText(
                amount = 1000,
                isPrivacyMode = true,
                prefix = "Balance: ",
            )
        }

        composeTestRule.onNodeWithContentDescription("Hidden balance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balance: ₹ ****").assertIsDisplayed()
    }

    @Test
    fun unmaskedAmount_doesNotSetHiddenBalanceContentDescription() {
        composeTestRule.setContent {
            PrivacyAwareText(
                amount = 500,
                isPrivacyMode = false,
                isCurrency = true,
            )
        }

        composeTestRule.onNodeWithContentDescription("Hidden balance").assertDoesNotExist()
    }
}
