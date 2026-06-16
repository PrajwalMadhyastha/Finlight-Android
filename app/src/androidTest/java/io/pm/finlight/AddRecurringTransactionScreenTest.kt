package io.pm.finlight

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@org.junit.Ignore("Temporarily disabled (Issue #105)")
@RunWith(AndroidJUnit4::class)
class AddRecurringTransactionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testVariableBillToggleHidesAmountAndShowsSmsSender() {
        composeTestRule.setContent {
            io.pm.finlight.ui.theme.PersonalFinanceAppTheme {
                io.pm.finlight.ui.screens.AddRecurringTransactionScreen(
                    navController = androidx.navigation.compose.rememberNavController(),
                    ruleId = null
                )
            }
        }

        // Wait for idle after setting content
        composeTestRule.waitForIdle()

        // 3. Verify initial state (Fixed Bill by default)
        composeTestRule.onNodeWithText("Variable Bill").assertIsOff()
        composeTestRule.onNodeWithText("Amount").assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Linked SMS Sender ID", substring = true)).assertDoesNotExist()

        // 4. Toggle Variable Bill on
        composeTestRule.onNodeWithText("Variable Bill").performClick()
        composeTestRule.onNodeWithText("Variable Bill").assertIsOn()

        // 5. Verify UI changes
        composeTestRule.onNodeWithText("Expected Amount").assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Linked SMS Sender ID", substring = true)).assertExists()
    }

    @Test
    fun testAutoApproveToggle() {
        composeTestRule.setContent {
            io.pm.finlight.ui.theme.PersonalFinanceAppTheme {
                io.pm.finlight.ui.screens.AddRecurringTransactionScreen(
                    navController = androidx.navigation.compose.rememberNavController(),
                    ruleId = null
                )
            }
        }

        composeTestRule.waitForIdle()

        // 3. Verify initial state (Auto-approve is off)
        composeTestRule.onNodeWithText("Auto-Approve Payments").assertIsOff()

        // Toggle Auto Approve on
        composeTestRule.onNodeWithText("Auto-Approve Payments").performClick()
        composeTestRule.onNodeWithText("Auto-Approve Payments").assertIsOn()
    }
}
