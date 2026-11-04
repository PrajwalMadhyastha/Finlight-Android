package io.pm.finlight.ui.components

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CreationDialogsValidationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `CreateAccountDialog validates credit limit and rejects zero or negative amounts`() {
        composeTestRule.setContent {
            CreateAccountDialog(
                onDismiss = {},
                onConfirm = { _, _, _, _ -> },
            )
        }

        // Fill required fields
        composeTestRule.onNodeWithText("Account Name (e.g., Chase Sapphire)").performTextInput("Test Card")
        composeTestRule.onNodeWithText("Account Type (e.g., Credit Card)").performTextInput("Credit Card")

        // Without limit, Create is enabled (limit is optional)
        composeTestRule.onNodeWithText("Create").assertIsEnabled()

        // Enter zero limit - must be rejected
        composeTestRule.onNodeWithText("Credit Limit (e.g., 100000)").performTextInput("0")
        composeTestRule.onNodeWithText("Please enter a valid credit limit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create").assertIsNotEnabled()

        // Enter zero with decimals
        composeTestRule.onNodeWithText("Credit Limit (e.g., 100000)").performTextReplacement("0.0")
        composeTestRule.onNodeWithText("Please enter a valid credit limit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create").assertIsNotEnabled()

        // Enter positive limit - must be accepted
        composeTestRule.onNodeWithText("Credit Limit (e.g., 100000)").performTextReplacement("50000")
        composeTestRule.onNodeWithText("Create").assertIsEnabled()
    }
}
