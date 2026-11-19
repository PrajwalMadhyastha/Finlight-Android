package io.pm.finlight.ui.components

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionTypeToggleTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun transactionTypeToggle_selectsIncomeAndExpense() {
        var selected = TransactionType.EXPENSE
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionTypeToggle(
                    selectedType = selected,
                    onTypeSelected = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Expense").assertIsDisplayed()
        composeTestRule.onNodeWithText("Income").assertIsDisplayed()

        composeTestRule.onNodeWithText("Income").performClick()
        assertEquals(TransactionType.INCOME, selected)

        composeTestRule.onNodeWithText("Expense").performClick()
        assertEquals(TransactionType.EXPENSE, selected)
    }

    @Test
    fun transactionTypeToggle_stringOverload_selectsProperString() {
        var selected = "expense"
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionTypeToggle(
                    selectedType = selected,
                    onTypeSelected = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Income").performClick()
        assertEquals("income", selected)

        composeTestRule.onNodeWithText("Expense").performClick()
        assertEquals("expense", selected)
    }

    @Test
    fun transactionTypeToggle_stringOverload_handlesInvalidStringGracefully() {
        var selected = "unknown_type"
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionTypeToggle(
                    selectedType = selected,
                    onTypeSelected = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Expense").assertIsDisplayed()
        composeTestRule.onNodeWithText("Income").performClick()
        assertEquals("income", selected)
    }

    @Test
    fun transactionTypeToggle_disabledState_disablesButtons() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionTypeToggle(
                    selectedType = TransactionType.EXPENSE,
                    onTypeSelected = {},
                    enabled = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Expense").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Income").assertIsNotEnabled()
    }
}
