package io.pm.finlight.ui.accessibility

import android.os.Build
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.Budget
import io.pm.finlight.BudgetWithSpending
import io.pm.finlight.LastMonthSummary
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsDebugResult
import io.pm.finlight.SmsDebugViewModel
import io.pm.finlight.SmsMessage
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.components.AuroraProgressBar
import io.pm.finlight.ui.components.DiagnosticReportPreviewSheet
import io.pm.finlight.ui.components.LastMonthSummaryCard
import io.pm.finlight.ui.components.dashboard.BudgetWatchCard
import io.pm.finlight.ui.screens.SmsDebugItem
import io.pm.finlight.ui.screens.addtransaction.AmountComposer
import io.pm.finlight.ui.screens.transactiondetail.TransactionSpotlightHeader
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import java.util.Date
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class UiAccessibilitySemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: NavController
    private lateinit var smsDebugViewModel: SmsDebugViewModel

    @Before
    fun setUp() {
        navController = mockk(relaxed = true)
        smsDebugViewModel = mockk(relaxed = true)
    }

    @Test
    fun lastMonthSummaryCard_dismissButtonMeets48dpAndHasContentDescription() {
        val summary = LastMonthSummary(totalExpenses = 4500.0, totalIncome = 12000.0)
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                LastMonthSummaryCard(
                    summary = summary,
                    navController = navController,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Dismiss")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeTestRule.onNodeWithContentDescription("Summary Info").assertIsDisplayed()
    }

    @Test
    fun amountComposer_searchButtonMeets48dpAndHasContentDescription() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AmountComposer(
                    amount = "500",
                    onAmountChange = {},
                    focusRequester = FocusRequester(),
                    description = "Coffee",
                    onDescriptionChange = {},
                    onDescriptionClick = {},
                    isTravelMode = false,
                    travelModeSettings = null,
                    highlightDescription = false,
                    isDescriptionEntered = true,
                    hasInteractedWithNumpad = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Search Predictions")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun auroraProgressBar_exposesProgressBarRangeInfo() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AuroraProgressBar(progress = 0.42f)
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("42%").assertIsDisplayed()
    }

    @Test
    fun auroraProgressBar_boundaryValues_exposeCorrectSemantics() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AuroraProgressBar(progress = 1.0f)
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1.0f, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun budgetWatchCard_exposesProgressBarRangeInfoForCategoryGauges() {
        val budgetItem =
            BudgetWithSpending(
                budget = Budget(id = 101, categoryName = "Shopping", amount = 8000.0, month = 11, year = 2026),
                spent = 4000.0,
                iconKey = "shopping",
                colorKey = "purple",
            )
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                BudgetWatchCard(
                    budgetStatus = listOf(budgetItem),
                    navController = navController,
                )
            }
        }

        val expectedProgress = (4000.0 / 8000.0).toFloat()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(expectedProgress, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Shopping").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shopping").assertIsDisplayed()
    }

    @Test
    fun decorativeIcons_inSmsDebug_preserveNullContentDescription() {
        val result =
            SmsDebugResult(
                smsMessage = SmsMessage(id = 1L, sender = "Bank", body = "Debited 100", date = 1L),
                parseResult = ParseResult.Ignored("User defined ignore rule"),
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                SmsDebugItem(
                    result = result,
                    navController = navController,
                    viewModel = smsDebugViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Create Rule For This Message").assertIsDisplayed()
        // Decorative icon beside labeled text must NOT have a standalone content description
        composeTestRule.onNodeWithContentDescription("Create Rule For This Message").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Add").assertDoesNotExist()
    }

    @Test
    fun decorativeChipIcon_inTransactionSpotlightHeader_preservesNullContentDescription() {
        val details =
            TransactionDetails(
                transaction =
                    Transaction(
                        id = 1,
                        description = "Groceries",
                        categoryId = 1,
                        amount = 120.0,
                        date = 1000L,
                        accountId = 1,
                        notes = null,
                        transactionType = TransactionType.EXPENSE,
                    ),
                images = emptyList(),
                accountName = "Primary Checking",
                categoryName = "Food",
                categoryIconKey = "restaurant",
                categoryColorKey = "orange",
                tagNames = null,
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                TransactionSpotlightHeader(
                    details = details,
                    displayDate = Date(1000L),
                    visitCount = 3,
                    isSplit = false,
                    onDescriptionClick = {},
                    onAmountClick = {},
                    onCategoryClick = {},
                    onDateTimeClick = {},
                    onSplitClick = {},
                    onVisitCountClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("3 visits").assertIsDisplayed()
        // Chip decorative leading icon must retain contentDescription = null to avoid TalkBack repetition
        composeTestRule.onNodeWithContentDescription("History").assertDoesNotExist()
    }

    @Test
    fun diagnosticReportPreviewSheet_accessibilitySemanticsAndTouchTargets() {
        val mockDb = mockk<AppDatabase>(relaxed = true)
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                DiagnosticReportPreviewSheet(
                    onDismissRequest = {},
                    database = mockDb,
                )
            }
        }

        // Close button meets 48dp minimum touch target and has content description
        composeTestRule.onNodeWithContentDescription("Close")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        // Privacy Shield informative icon has content description
        composeTestRule.onNodeWithContentDescription("Privacy Shield").assertIsDisplayed()

        // Action buttons with text labels have decorative icons suppressing contentDescription
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Copy").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Share Report").assertDoesNotExist()
    }
}
