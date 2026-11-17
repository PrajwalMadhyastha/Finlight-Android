package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.screens.addtransaction.AmountComposer
import io.pm.finlight.ui.screens.addtransaction.OrbitalChipsSection
import io.pm.finlight.ui.screens.addtransaction.PredictionCarousel
import io.pm.finlight.ui.screens.addtransaction.QuickFillCarousel
import io.pm.finlight.ui.screens.addtransaction.TransactionHistorySheet
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AddTransactionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultDate = Date(1700000000000L)
    private val sampleAccount = Account(id = 10, name = "HDFC Salary", type = "SAVINGS")
    private val sampleCategory = Category(id = 20, name = "Groceries", iconKey = "shopping_cart", colorKey = "green")
    private val sampleTag = Tag(id = 1, name = "Weekly")

    private fun sampleTransaction(
        id: Int = 101,
        description: String = "Whole Foods",
        amount: Double = 1250.0,
    ) = Transaction(
        id = id,
        description = description,
        amount = amount,
        isSplit = false,
        date = defaultDate.time,
        accountId = 10,
        categoryId = 20,
        notes = null,
        transactionType = TransactionType.EXPENSE,
    )

    private fun sampleDetails(
        id: Int = 101,
        description: String = "Whole Foods",
        amount: Double = 1250.0,
    ) = TransactionDetails(
        transaction = sampleTransaction(id, description, amount),
        images = emptyList(),
        accountName = "HDFC Salary",
        categoryName = "Groceries",
        categoryIconKey = "shopping_cart",
        categoryColorKey = "green",
        tagNames = "Weekly",
    )

    private fun samplePrediction(
        description: String = "Starbucks",
        categoryId: Int = 20,
        accountId: Int = 10,
    ) = MerchantPrediction(
        description = description,
        categoryId = categoryId,
        categoryName = "Dining",
        categoryIconKey = "restaurant",
        categoryColorKey = "orange",
        accountId = accountId,
        accountName = "HDFC Salary",
    )

    @Test
    fun amountComposer_updatesAmountAndDescriptionOnInput() {
        var updatedAmount = ""
        var updatedDescription = ""
        var searchClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AmountComposer(
                    amount = "",
                    onAmountChange = { updatedAmount = it },
                    focusRequester = FocusRequester(),
                    description = "",
                    onDescriptionChange = { updatedDescription = it },
                    onDescriptionClick = { searchClicked = true },
                    isTravelMode = false,
                    travelModeSettings = null,
                    highlightDescription = false,
                    isDescriptionEntered = false,
                    hasInteractedWithNumpad = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("description_text_field").performTextInput("Coffee")
        assertEquals("Coffee", updatedDescription)

        composeTestRule.onNodeWithTag("amount_text_field").performTextInput("250")
        assertEquals("250", updatedAmount)

        composeTestRule.onNodeWithContentDescription("Search Predictions").performClick()
        assertTrue(searchClicked)
    }

    @Test
    fun amountComposer_displaysInternationalTravelConversion() {
        val travelSettings =
            TravelModeSettings(
                isEnabled = true,
                tripName = "US Vacation",
                tripType = TripType.INTERNATIONAL,
                currencyCode = "USD",
                conversionRate = 83.5f,
                startDate = 0L,
                endDate = Long.MAX_VALUE,
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AmountComposer(
                    amount = "10",
                    onAmountChange = {},
                    focusRequester = FocusRequester(),
                    description = "Subway",
                    onDescriptionChange = {},
                    onDescriptionClick = {},
                    isTravelMode = true,
                    travelModeSettings = travelSettings,
                    highlightDescription = false,
                    isDescriptionEntered = true,
                    hasInteractedWithNumpad = true,
                )
            }
        }

        composeTestRule.onNodeWithText("$").assertIsDisplayed()
        composeTestRule.onNodeWithText("≈ ₹835").assertIsDisplayed()
    }

    @Test
    fun orbitalChipsSection_triggersCallbacksOnChipClicks() {
        var categoryClicked = false
        var accountClicked = false
        var dateClicked = false
        var notesClicked = false
        var tagsClicked = false
        var attachClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                OrbitalChipsSection(
                    selectedCategory = sampleCategory,
                    selectedAccount = sampleAccount,
                    selectedDateTime = defaultDate,
                    notes = "Lunch with team",
                    tags = setOf(sampleTag),
                    imageCount = 2,
                    onCategoryClick = { categoryClicked = true },
                    onAccountClick = { accountClicked = true },
                    onDateClick = { dateClicked = true },
                    onNotesClick = { notesClicked = true },
                    onTagsClick = { tagsClicked = true },
                    onAttachmentClick = { attachClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("category_select_chip").assertIsDisplayed().performClick()
        assertTrue(categoryClicked)

        composeTestRule.onNodeWithTag("account_select_chip").assertIsDisplayed().performClick()
        assertTrue(accountClicked)

        composeTestRule.onNodeWithTag("date_select_chip").assertIsDisplayed().performClick()
        assertTrue(dateClicked)

        composeTestRule.onNodeWithText("Notes").assertIsDisplayed().performClick()
        assertTrue(notesClicked)

        composeTestRule.onNodeWithText("Tags").assertIsDisplayed().performClick()
        assertTrue(tagsClicked)

        composeTestRule.onNodeWithText("Attach").assertIsDisplayed().performClick()
        assertTrue(attachClicked)
    }

    @Test
    fun quickFillCarousel_rendersItemsAndHandlesSelection() {
        val details = sampleDetails(id = 201, description = "Weekly Grocery", amount = 1450.0)
        var selectedItem: TransactionDetails? = null
        var viewAllClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                QuickFillCarousel(
                    recentTransactions = listOf(details),
                    onQuickFillSelected = { selectedItem = it },
                    onViewAllClick = { viewAllClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Quick Fill from Recent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weekly Grocery").assertIsDisplayed().performClick()
        assertNotNull(selectedItem)
        assertEquals(201, selectedItem?.transaction?.id)

        composeTestRule.onNodeWithText("View All").assertIsDisplayed().performClick()
        assertTrue(viewAllClicked)
    }

    @Test
    fun predictionCarousel_rendersItemsAndHandlesSelection() {
        val prediction = samplePrediction(description = "Blue Tokai Coffee")
        var selectedPrediction: MerchantPrediction? = null

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                PredictionCarousel(
                    predictions = listOf(prediction),
                    onPredictionSelected = { selectedPrediction = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Past Merchants").assertIsDisplayed()
        composeTestRule.onNodeWithText("Blue Tokai Coffee").assertIsDisplayed().performClick()
        assertNotNull(selectedPrediction)
        assertEquals("Blue Tokai Coffee", selectedPrediction?.description)
    }

    @Test
    fun transactionHistorySheet_filtersTransactionsAndSelectsItem() {
        val txn1 = sampleDetails(id = 301, description = "Trader Joes", amount = 42.0)
        val txn2 = sampleDetails(id = 302, description = "Pharmacy Express", amount = 15.0)
        var selectedDetails: TransactionDetails? = null

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionHistorySheet(
                    transactions = listOf(txn1, txn2),
                    onTransactionSelected = { selectedDetails = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Transaction History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trader Joes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pharmacy Express").assertIsDisplayed()

        composeTestRule.onNodeWithText("Search History").performTextInput("Pharm")
        composeTestRule.onNodeWithText("Pharmacy Express").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Trader Joes").assertDoesNotExist()

        assertNotNull(selectedDetails)
        assertEquals(302, selectedDetails?.transaction?.id)
    }
}
