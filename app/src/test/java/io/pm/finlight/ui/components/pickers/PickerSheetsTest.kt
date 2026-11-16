package io.pm.finlight.ui.components.pickers

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.components.CategoryIconDisplay
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class PickerSheetsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleAccounts =
        listOf(
            Account(id = 1, name = "HDFC Savings", type = "SAVINGS"),
            Account(id = 2, name = "SBI Credit", type = "CREDIT_CARD", creditLimit = 75000.0),
        )

    private val sampleCategories =
        listOf(
            Category(id = 1, name = "Groceries", iconKey = "shopping_cart", colorKey = "green"),
            Category(id = 2, name = "Uncategorized", iconKey = "help_outline", colorKey = "red_light"),
            Category(id = 3, name = "PersonalCare", iconKey = "letter_default", colorKey = "purple"),
        )

    private val sampleTags =
        listOf(
            Tag(id = 1, name = "Groceries"),
            Tag(id = 2, name = "Vacation"),
        )

    @Test
    fun accountPickerBottomSheet_displaysAccountsAndSelectsItem() {
        var selectedAccount: Account? = null
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AccountPickerBottomSheet(
                    accounts = sampleAccounts,
                    selectedAccountId = 1,
                    onAccountSelected = { selectedAccount = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Select Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("HDFC Savings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Currently Selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("SBI Credit").assertIsDisplayed()

        composeTestRule.onNodeWithText("SBI Credit").performClick()
        assertEquals(2, selectedAccount?.id)
    }

    @Test
    fun accountPickerBottomSheet_handlesAddNewCallback() {
        var addNewTriggered = false
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AccountPickerBottomSheet(
                    accounts = sampleAccounts,
                    onAccountSelected = {},
                    onAddNew = { addNewTriggered = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Create New Account").performClick()
        assertTrue(addNewTriggered)
    }

    @Test
    fun accountPickerBottomSheet_handlesInlineRenameCallback() {
        var renamedId = -1
        var renamedName = ""
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AccountPickerBottomSheet(
                    accounts = sampleAccounts,
                    selectedAccountId = 1,
                    onAccountSelected = {},
                    onRenameAccount = { id, newName ->
                        renamedId = id
                        renamedName = newName
                    },
                )
            }
        }

        composeTestRule.onAllNodesWithContentDescription("Edit Account Name")[0].performClick()
        composeTestRule.onNodeWithText("Account Name").performTextInput(" Updated")
        composeTestRule.onNodeWithContentDescription("Save Name").performClick()

        assertEquals(1, renamedId)
        assertTrue(renamedName.contains("HDFC Savings"))
    }

    @Test
    fun categoryPickerBottomSheet_displaysCategoriesAndSelectsItem() {
        var selectedCategory: Category? = null
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                CategoryPickerBottomSheet(
                    categories = sampleCategories,
                    title = "Choose Category",
                    onCategorySelected = { selectedCategory = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Choose Category").assertIsDisplayed()
        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeTestRule.onNodeWithText("PersonalCare").assertIsDisplayed()

        composeTestRule.onNodeWithText("Groceries").performClick()
        assertEquals(1, selectedCategory?.id)
    }

    @Test
    fun categoryPickerBottomSheet_handlesAddNewCallback() {
        var addNewTriggered = false
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                CategoryPickerBottomSheet(
                    categories = sampleCategories,
                    onCategorySelected = {},
                    onAddNew = { addNewTriggered = true },
                )
            }
        }

        composeTestRule.onNodeWithText("New").performClick()
        assertTrue(addNewTriggered)
    }

    @Test
    fun tagPickerBottomSheet_displaysTagsAndTogglesSelection() {
        var selectedTag: Tag? = null
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TagPickerBottomSheet(
                    allTags = sampleTags,
                    selectedTags = setOf(sampleTags.first()),
                    onTagSelected = { selectedTag = it },
                    onAddNewTag = {},
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Manage Tags").assertIsDisplayed()
        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vacation").assertIsDisplayed()

        composeTestRule.onNodeWithText("Vacation").performClick()
        assertEquals(2, selectedTag?.id)
    }

    @Test
    fun tagPickerBottomSheet_addsNewTagAndConfirms() {
        var addedTagName = ""
        var confirmTriggered = false
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TagPickerBottomSheet(
                    allTags = sampleTags,
                    selectedTags = emptySet(),
                    onTagSelected = {},
                    onAddNewTag = { addedTagName = it },
                    onConfirm = { confirmTriggered = true },
                )
            }
        }

        composeTestRule.onNodeWithText("New Tag Name").performTextInput("Work")
        composeTestRule.onNodeWithContentDescription("Add New Tag").performClick()
        assertEquals("Work", addedTagName)

        composeTestRule.onNodeWithText("Save").performClick()
        assertTrue(confirmTriggered)
    }

    @Test
    fun tagPickerBottomSheet_handlesCancelDismiss() {
        var dismissTriggered = false
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TagPickerBottomSheet(
                    allTags = sampleTags,
                    selectedTags = emptySet(),
                    onTagSelected = {},
                    onAddNewTag = {},
                    onConfirm = {},
                    onDismiss = { dismissTriggered = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissTriggered)
    }

    @Test
    fun categoryIconDisplay_rendersAllVariantsCleanly() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                CategoryIconDisplay(sampleCategories[0])
                CategoryIconDisplay(sampleCategories[1], modifier = Modifier.size(24.dp))
                CategoryIconDisplay(sampleCategories[2])
            }
        }

        composeTestRule.onNodeWithContentDescription("Uncategorized").assertIsDisplayed()
        composeTestRule.onNodeWithText("P").assertIsDisplayed()
    }
}
