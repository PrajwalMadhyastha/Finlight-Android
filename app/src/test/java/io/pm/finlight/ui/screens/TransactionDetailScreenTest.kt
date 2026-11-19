package io.pm.finlight.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.CanonicalNudgeSheetState
import io.pm.finlight.CanonicalVariant
import io.pm.finlight.RetroUpdateSheetState
import io.pm.finlight.SmsMessage
import io.pm.finlight.SplitTransaction
import io.pm.finlight.SplitTransactionDetails
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.model.MergedTransactionItem
import io.pm.finlight.ui.screens.transactiondetail.AccountCard
import io.pm.finlight.ui.screens.transactiondetail.CanonicalNudgeSheetContent
import io.pm.finlight.ui.screens.transactiondetail.CurrencyConversionInfoCard
import io.pm.finlight.ui.screens.transactiondetail.EditTextFieldSheet
import io.pm.finlight.ui.screens.transactiondetail.MultiAccountBreakdownCard
import io.pm.finlight.ui.screens.transactiondetail.OriginalSmsCard
import io.pm.finlight.ui.screens.transactiondetail.RetrospectiveUpdateSheetContent
import io.pm.finlight.ui.screens.transactiondetail.ReviewBannerCard
import io.pm.finlight.ui.screens.transactiondetail.SplitSummaryCard
import io.pm.finlight.ui.screens.transactiondetail.TransactionActionsCard
import io.pm.finlight.ui.screens.transactiondetail.TransactionPropertiesCard
import io.pm.finlight.ui.screens.transactiondetail.TransactionSpotlightHeader
import io.pm.finlight.ui.screens.transactiondetail.navigateToRuleCreation
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.Date

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createSampleTransaction(
        id: Int = 101,
        amount: Double = 1450.50,
        desc: String = "Starbucks Coffee",
        type: TransactionType = TransactionType.EXPENSE,
        isSplit: Boolean = false,
        source: String = "SMS",
    ) = Transaction(
        id = id,
        amount = amount,
        description = desc,
        date = 1700000000000L,
        transactionType = type,
        isSplit = isSplit,
        source = source,
        sourceSmsId = 999L,
        categoryId = 1,
        accountId = 10,
        notes = "Coffee with friends",
    )

    private fun createSampleDetails(
        txn: Transaction = createSampleTransaction(),
        accountName: String = "HDFC Bank",
        categoryName: String = "Dining",
    ) = TransactionDetails(
        transaction = txn,
        images = emptyList(),
        accountName = accountName,
        categoryName = categoryName,
        categoryIconKey = "restaurant",
        categoryColorKey = "orange_light",
        tagNames = "food, coffee",
    )

    @Test
    fun spotlightHeader_rendersMerchantAndDispatchesClicks() {
        val details = createSampleDetails()
        var descClicked = false
        var amountClicked = false
        var categoryClicked = false
        var visitClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionSpotlightHeader(
                    details = details,
                    displayDate = Date(details.transaction.date),
                    visitCount = 3,
                    isSplit = false,
                    onDescriptionClick = { descClicked = true },
                    onAmountClick = { amountClicked = true },
                    onCategoryClick = { categoryClicked = true },
                    onDateTimeClick = {},
                    onSplitClick = {},
                    onVisitCountClick = { visitClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Starbucks Coffee").assertIsDisplayed().performClick()
        assertTrue(descClicked)

        composeTestRule.onNodeWithText("₹1,450.50").assertIsDisplayed().performClick()
        assertTrue(amountClicked)

        composeTestRule.onNodeWithText("Dining").assertIsDisplayed().performClick()
        assertTrue(categoryClicked)

        composeTestRule.onNodeWithText("3 visits").assertIsDisplayed().performClick()
        assertTrue(visitClicked)
    }

    @Test
    fun spotlightHeader_splitMode_rendersSplitIndicators() {
        val splitTxn = createSampleTransaction(isSplit = true)
        val details = createSampleDetails(txn = splitTxn)
        var splitClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionSpotlightHeader(
                    details = details,
                    displayDate = Date(details.transaction.date),
                    visitCount = 1,
                    isSplit = true,
                    onDescriptionClick = {},
                    onAmountClick = {},
                    onCategoryClick = {},
                    onDateTimeClick = {},
                    onSplitClick = { splitClicked = true },
                    onVisitCountClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Split Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit Splits").assertIsDisplayed().performClick()
        assertTrue(splitClicked)
    }

    @Test
    fun propertiesCard_rendersAndTriggersCallbacks() {
        val details = createSampleDetails()
        var selectedType: TransactionType? = null
        var excludedToggled = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionPropertiesCard(
                    details = details,
                    onTypeSelected = { selectedType = it },
                    onExcludeToggled = { excludedToggled = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Income").assertIsDisplayed().performClick()
        assertEquals(TransactionType.INCOME, selectedType)

        composeTestRule.onNodeWithText("Exclude from Totals").assertIsDisplayed().performClick()
        assertTrue(excludedToggled)
    }

    @Test
    fun multiAccountBreakdownCard_rendersMergedEntriesAndRepayments() {
        val merged =
            listOf(
                MergedTransactionItem(
                    accountId = 10,
                    accountName = "ICICI Bank",
                    amount = 1200.0,
                    transactionType = TransactionType.EXPENSE,
                    isAnchor = true,
                    description = "Merged Txn 1",
                    date = 1700000000000L,
                ),
                MergedTransactionItem(
                    accountId = 20,
                    accountName = "Axis Bank",
                    amount = 800.0,
                    transactionType = TransactionType.EXPENSE,
                    isAnchor = false,
                    description = "Merged Txn 2",
                    date = 1700000000000L,
                ),
            )
        val repayment =
            listOf(
                createSampleDetails(
                    txn = createSampleTransaction(id = 3, amount = 500.0, type = TransactionType.INCOME),
                    accountName = "SBI Bank",
                ),
            )
        var cardClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                MultiAccountBreakdownCard(
                    entries = merged,
                    reimbursements = repayment,
                    onCardClick = { cardClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("ICICI Bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("anchor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Axis Bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("merged").assertIsDisplayed()
        composeTestRule.onNodeWithText("SBI Bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("repayment").assertIsDisplayed()

        composeTestRule.onNodeWithText("Accounts").performClick()
        assertTrue(cardClicked)
    }

    @Test
    fun accountCard_rendersAccountNameAndDispatchesClick() {
        val details = createSampleDetails(accountName = "Kotak Mahindra")
        var clicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AccountCard(
                    details = details,
                    readOnly = false,
                    onAccountClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Kotak Mahindra").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kotak Mahindra").performClick()
        assertTrue(clicked)
    }

    @Test
    fun splitSummaryCard_rendersSplitRows() {
        val splits =
            listOf(
                SplitTransactionDetails(
                    splitTransaction =
                        SplitTransaction(
                            id = 1,
                            parentTransactionId = 101,
                            categoryId = 1,
                            amount = 600.0,
                            notes = null,
                        ),
                    categoryName = "Groceries",
                    categoryIconKey = "shopping_cart",
                    categoryColorKey = "green",
                ),
                SplitTransactionDetails(
                    splitTransaction =
                        SplitTransaction(
                            id = 2,
                            parentTransactionId = 101,
                            categoryId = 2,
                            amount = 850.50,
                            notes = null,
                        ),
                    categoryName = "Entertainment",
                    categoryIconKey = "movie",
                    categoryColorKey = "purple",
                ),
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                SplitSummaryCard(splits = splits)
            }
        }

        composeTestRule.onNodeWithText("Split Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹600.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Entertainment").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹850.50").assertIsDisplayed()
    }

    @Test
    fun currencyConversionInfoCard_rendersRatesAndAmounts() {
        val txn =
            createSampleTransaction(amount = 8300.0).copy(
                originalAmount = 100.0,
                currencyCode = "USD",
                conversionRate = 83.0,
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                CurrencyConversionInfoCard(transaction = txn)
            }
        }

        composeTestRule.onNodeWithText("Currency Conversion").assertIsDisplayed()
        composeTestRule.onNodeWithText("$100").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 USD = ₹83").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹8,300").assertIsDisplayed()
    }

    @Test
    fun retrospectiveUpdateSheet_togglesFutureAndSelection() {
        val txns =
            listOf(
                createSampleTransaction(id = 201, desc = "Uber Trip 1", amount = 350.0),
                createSampleTransaction(id = 202, desc = "Uber Trip 2", amount = 420.0),
            )
        val state =
            RetroUpdateSheetState(
                originalDescription = "UBER*INDIA",
                newDescription = "Uber Ride",
                similarTransactions = txns,
                selectedIds = setOf(201),
                updateFutureTransactions = true,
                isLoading = false,
            )

        var toggledFuture = false
        var toggledId: Int? = null
        var selectAllToggled = false
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                Box(modifier = Modifier.size(400.dp, 800.dp)) {
                    RetrospectiveUpdateSheetContent(
                        state = state,
                        onToggleSelection = { toggledId = it },
                        onToggleSelectAll = { selectAllToggled = true },
                        onToggleUpdateFuture = { toggledFuture = true },
                        onConfirm = { confirmed = true },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Smart Update").assertIsDisplayed()
        composeTestRule.onNodeWithText("Update future transactions").performClick()
        assertTrue(toggledFuture)

        composeTestRule.onNodeWithText("Also update 2 similar past transaction(s):").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select All").performClick()
        assertTrue(selectAllToggled)

        composeTestRule.onNodeWithText("Uber Trip 1").performClick()
        assertEquals(201, toggledId)

        composeTestRule.onNodeWithText("Apply Changes").performClick()
        assertTrue(confirmed)

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun canonicalNudgeSheet_togglesVariantsAndDispatches() {
        val variants =
            listOf(
                CanonicalVariant(rawName = "AMZN MKTP", transactionCount = 4, transactionIds = listOf(1, 2, 3, 4)),
                CanonicalVariant(rawName = "AMAZON PAY", transactionCount = 2, transactionIds = listOf(5, 6)),
            )
        val state =
            CanonicalNudgeSheetState(
                canonicalName = "Amazon",
                variants = variants,
                selectedRawNames = setOf("AMZN MKTP"),
            )

        var toggledVariant: String? = null
        var applied = false
        var skipped = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                CanonicalNudgeSheetContent(
                    state = state,
                    onToggleVariant = { toggledVariant = it },
                    onConfirm = { applied = true },
                    onDismiss = { skipped = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Similar merchants found").assertIsDisplayed()
        composeTestRule.onNodeWithText("AMAZON PAY").performClick()
        assertEquals("AMAZON PAY", toggledVariant)

        composeTestRule.onNodeWithText("Apply to 1 variant").performClick()
        assertTrue(applied)

        composeTestRule.onNodeWithText("Skip").performClick()
        assertTrue(skipped)
    }

    @Test
    fun reviewBannerCard_rendersWarningAndTriggersReview() {
        var reviewed = false
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                ReviewBannerCard(
                    reason = "Anomalous debit amount",
                    onReviewClick = { reviewed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Needs Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("Anomalous debit amount").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mark as Reviewed").performClick()
        assertTrue(reviewed)
    }

    @Test
    fun originalSmsCard_rendersSmsBody() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                OriginalSmsCard(originalSms = "Rs 1500 debited from A/C XX1234 at POS")
            }
        }

        composeTestRule.onNodeWithText("Original SMS Message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rs 1500 debited from A/C XX1234 at POS").assertIsDisplayed()
    }

    @Test
    fun transactionActionsCard_rendersAttachAndFixParsingWhenSmsPresent() {
        var addClicked = false
        var fixParsingClicked = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionActionsCard(
                    images = emptyList(),
                    onAddClick = { addClicked = true },
                    onViewClick = {},
                    onDeleteClick = {},
                    sourceSmsId = 123L,
                    onFixParsingClick = { fixParsingClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Attach").assertIsDisplayed().performClick()
        assertTrue(addClicked)

        composeTestRule.onNodeWithText("Fix Parsing").assertIsDisplayed().performClick()
        assertTrue(fixParsingClicked)
    }

    @Test
    fun transactionActionsCard_hidesFixParsingWhenSmsAbsent() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionActionsCard(
                    images = emptyList(),
                    onAddClick = {},
                    onViewClick = {},
                    onDeleteClick = {},
                    sourceSmsId = null,
                    onFixParsingClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Attach").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fix Parsing").assertDoesNotExist()
    }

    @Test
    fun editTextFieldSheet_editsAndConfirms() {
        var savedValue = ""
        var dismissed = false

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                EditTextFieldSheet(
                    title = "Edit Notes",
                    initialValue = "Dinner with friends",
                    onConfirm = { savedValue = it },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Edit Notes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dinner with friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").performClick()
        assertEquals("Dinner with friends", savedValue)

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun navigateToRuleCreation_handlesNullSmsIdGracefully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val navController = mock<NavController>()
        val viewModel = mock<TransactionViewModel>()
        val details = createSampleDetails(txn = createSampleTransaction().copy(sourceSmsId = null))
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        navigateToRuleCreation(context, navController, viewModel, testScope, details)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Original SMS not found.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun navigateToRuleCreation_withMissingSms_showsToast() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val navController = mock<NavController>()
            val viewModel = mock<TransactionViewModel>()
            val details = createSampleDetails(txn = createSampleTransaction().copy(sourceSmsId = 888L))

            whenever(viewModel.getOriginalSmsMessage(888L)).thenReturn(null)

            navigateToRuleCreation(context, navController, viewModel, this, details)
            testScheduler.advanceUntilIdle()

            assertEquals("Original SMS not found.", ShadowToast.getTextOfLatestToast())
        }

    @Test
    fun navigateToRuleCreation_withValidSms_navigatesToRuleCreation() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val navController = mock<NavController>()
            val viewModel = mock<TransactionViewModel>()
            val details = createSampleDetails(txn = createSampleTransaction().copy(sourceSmsId = 555L))
            val sampleSms =
                SmsMessage(
                    id = 555L,
                    sender = "HDFCBK",
                    body = "Paid Rs 1450.50 to Starbucks",
                    date = 1700000000000L,
                )

            whenever(viewModel.getOriginalSmsMessage(555L)).thenReturn(sampleSms)

            navigateToRuleCreation(context, navController, viewModel, this, details)
            testScheduler.advanceUntilIdle()

            val captor = argumentCaptor<String>()
            verify(navController).navigate(captor.capture(), anyOrNull(), anyOrNull())
            assertTrue(captor.firstValue.startsWith("rule_creation_screen?potentialTransactionJson="))
        }
}
