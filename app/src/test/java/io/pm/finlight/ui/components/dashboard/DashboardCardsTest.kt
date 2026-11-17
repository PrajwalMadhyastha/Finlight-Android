package io.pm.finlight.ui.components.dashboard

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.Account
import io.pm.finlight.AccountWithBalance
import io.pm.finlight.Budget
import io.pm.finlight.BudgetWithSpending
import io.pm.finlight.CreditCardMetrics
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DashboardCardsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: NavController

    @Before
    fun setUp() {
        navController = mockk(relaxed = true)
    }

    @Test
    fun dashboardHeroCard_rendersMetricsAndDispatchesClicks() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                DashboardHeroCard(
                    totalBudget = 50000L,
                    amountSpent = 20000L,
                    amountRemaining = 30000L,
                    income = 80000L,
                    safeToSpend = 1000L,
                    navController = navController,
                    monthYear = "November 2026",
                    budgetHealthSummary = "Budget Health: Great",
                    isPrivacyModeEnabled = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Budget Health: Great").assertIsDisplayed()
        composeTestRule.onNodeWithText("Income").assertIsDisplayed()
        composeTestRule.onNodeWithText("Budget").assertIsDisplayed()
        composeTestRule.onNodeWithText("Safe to Spend").assertIsDisplayed()

        composeTestRule.onNodeWithText("Income").performClick()
        verify { navController.navigate("income_screen") }

        composeTestRule.onNodeWithText("Budget").performClick()
        verify { navController.navigate("budget_screen") }
    }

    @Test
    fun accountsCarouselCard_emptyState_dispatchesViewAll() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AccountsCarouselCard(
                    accounts = emptyList(),
                    navController = navController,
                )
            }
        }

        composeTestRule.onNodeWithText("Accounts").assertIsDisplayed()
        composeTestRule.onNodeWithText("View All").assertIsDisplayed()
        composeTestRule.onNodeWithText("View All").performClick()
        verify { navController.navigate("account_list") }
    }

    @Test
    fun accountsCarouselCard_populatedList_rendersAndDispatchesDetailClick() {
        val accounts =
            listOf(
                fakeAccount(id = 1, name = "HDFC Bank", balance = 50000.0),
                fakeAccount(id = 2, name = "Axis Credit Card", type = "Credit Card", limit = 100000.0, balance = -15000.0),
            )
        val metrics =
            mapOf(
                2 to
                    CreditCardMetrics(
                        creditLimit = 100000.0,
                        billingCycleDay = 15,
                        currentCycleSpend = 15000.0,
                        availableLimit = 85000.0,
                        utilizationPercentage = 15.0f,
                        cycleStartDate = 1000L,
                        cycleEndDate = 2000L,
                        daysRemainingInCycle = 10,
                    ),
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AccountsCarouselCard(
                    accounts = accounts,
                    creditCardMetrics = metrics,
                    navController = navController,
                )
            }
        }

        composeTestRule.onNodeWithText("HDFC Bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("Axis Credit Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("15%").assertIsDisplayed()

        composeTestRule.onNodeWithText("HDFC Bank").performClick()
        verify { navController.navigate("account_detail/1") }
    }

    @Test
    fun budgetWatchCard_emptyState_rendersMessageAndDispatchesClick() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                BudgetWatchCard(
                    budgetStatus = emptyList(),
                    navController = navController,
                )
            }
        }

        composeTestRule.onNodeWithText("Budget Watch").assertIsDisplayed()
        composeTestRule.onNodeWithText("No category-specific budgets set for this month.").assertIsDisplayed()

        composeTestRule.onNodeWithText("Budget Watch").performClick()
        verify { navController.navigate("budget_screen") }
    }

    @Test
    fun budgetWatchCard_populatedList_rendersCategoryGauge() {
        val budgets =
            listOf(
                fakeBudget(id = 10, category = "Groceries", amount = 10000.0, spent = 3500.0),
            )

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                BudgetWatchCard(
                    budgetStatus = budgets,
                    navController = navController,
                )
            }
        }

        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹6,500 left").assertIsDisplayed()

        composeTestRule.onNodeWithText("Groceries").performClick()
        verify { navController.navigate("budget_screen") }
    }

    @Test
    fun auroraRecentTransactionsCard_emptyState_dispatchesAddAndViewAll() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AuroraRecentTransactionsCard(
                    transactions = emptyList(),
                    navController = navController,
                    onCategoryClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Recent Transactions").assertIsDisplayed()
        composeTestRule.onNodeWithText("No transactions yet.").assertIsDisplayed()

        composeTestRule.onNodeWithText("Add").performClick()
        verify { navController.navigate("add_transaction") }

        composeTestRule.onNodeWithText("View All").performClick()
        verify { navController.navigate(BottomNavItem.Transactions.route, any<androidx.navigation.NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun auroraRecentTransactionsCard_populatedList_rendersItemsAndDispatchesClicks() {
        val txn = fakeTransaction(id = 42, description = "Starbucks Coffee", category = "Dining")
        var categoryClicked: TransactionDetails? = null

        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AuroraRecentTransactionsCard(
                    transactions = listOf(txn),
                    navController = navController,
                    onCategoryClick = { categoryClicked = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Starbucks Coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText("Starbucks Coffee").performClick()
        verify { navController.navigate("transaction_detail/42") }

        composeTestRule.onNodeWithContentDescription("Dining").performClick()
        assertEquals(42, categoryClicked?.transaction?.id)
    }

    @Test
    fun auroraQuickActionsCard_dispatchesNavigation() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AuroraQuickActionsCard(navController = navController)
            }
        }

        composeTestRule.onNodeWithText("View Trends").assertIsDisplayed()
        composeTestRule.onNodeWithText("View Categories").assertIsDisplayed()

        composeTestRule.onNodeWithText("View Trends").performClick()
        verify { navController.navigate(BottomNavItem.Reports.route, any<androidx.navigation.NavOptionsBuilder.() -> Unit>()) }

        composeTestRule.onNodeWithText("View Categories").performClick()
        verify { navController.navigate("transaction_list?initialTab=1", any<androidx.navigation.NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun auroraSimulatorsCard_dispatchesNavigation() {
        composeTestRule.setContent {
            PersonalFinanceAppTheme {
                AuroraSimulatorsCard(navController = navController)
            }
        }

        composeTestRule.onNodeWithText("Financial Simulators").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_monthly_simulator").performClick()
        verify { navController.navigate("what_if_simulator") }

        composeTestRule.onNodeWithTag("btn_annual_simulator").performClick()
        verify { navController.navigate("annual_simulator") }
    }

    private fun fakeAccount(
        id: Int,
        name: String,
        type: String = "Savings",
        limit: Double? = null,
        balance: Double = 0.0,
    ) = AccountWithBalance(
        account = Account(id = id, name = name, type = type, creditLimit = limit),
        balance = balance,
    )

    private fun fakeBudget(
        id: Int,
        category: String,
        amount: Double,
        spent: Double,
    ) = BudgetWithSpending(
        budget = Budget(id = id, categoryName = category, amount = amount, month = 11, year = 2026),
        spent = spent,
        iconKey = "groceries",
        colorKey = "green",
    )

    private fun fakeTransaction(
        id: Int,
        description: String,
        category: String,
    ) = TransactionDetails(
        transaction =
            Transaction(
                id = id,
                description = description,
                categoryId = 1,
                amount = 350.0,
                date = System.currentTimeMillis(),
                accountId = 1,
                notes = null,
                transactionType = TransactionType.EXPENSE,
            ),
        images = emptyList(),
        accountName = "HDFC Bank",
        categoryName = category,
        categoryIconKey = "restaurant",
        categoryColorKey = "orange",
        tagNames = null,
    )
}
