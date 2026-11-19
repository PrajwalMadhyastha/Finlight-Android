package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.Account
import io.pm.finlight.Budget
import io.pm.finlight.Category
import io.pm.finlight.Goal
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.common.AccountSaver
import io.pm.finlight.ui.common.CategorySaver
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.BudgetViewModel
import io.pm.finlight.ui.viewmodel.GoalViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class FormStateRestorationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val navController: NavController = mockk(relaxed = true)

    private val testCategories =
        listOf(
            Category(id = 1, name = "Groceries", iconKey = "cart", colorKey = "green"),
            Category(id = 2, name = "Entertainment", iconKey = "movie", colorKey = "purple"),
        )

    private val testAccounts =
        listOf(
            Account(id = 10, name = "Salary Account", type = "Bank"),
            Account(id = 20, name = "Credit Card", type = "Credit Card", creditLimit = 50000.0, billingCycleDay = 15),
        )

    private val testSaverScope =
        object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }

    private fun <T> saveWith(
        saver: androidx.compose.runtime.saveable.Saver<T, Any>,
        value: T
    ): Any? =
        with(saver) { testSaverScope.save(value) }

    @Test
    fun categorySaver_savesAndRestoresStateCorrectly() {
        val original = Category(id = 42, name = "Dining", iconKey = "food", colorKey = "orange")
        val saved = saveWith(CategorySaver, original)
        val restored = CategorySaver.restore(saved!!)

        assertEquals(original, restored)

        val savedNull = saveWith<Category?>(CategorySaver, null)
        assertNull(savedNull)
        assertNull(CategorySaver.restore(emptyList<Any>()))
    }

    @Test
    fun accountSaver_savesAndRestoresStateCorrectly() {
        val original =
            Account(
                id = 99,
                name = "Axis Bank",
                type = "Credit Card",
                creditLimit = 150000.0,
                billingCycleDay = 25,
            )
        val saved = saveWith(AccountSaver, original)
        val restored = AccountSaver.restore(saved!!)

        assertEquals(original, restored)

        val accountWithNulls = Account(id = 100, name = "Cash Wallet", type = "Cash")
        val savedWithNulls = saveWith(AccountSaver, accountWithNulls)
        val restoredWithNulls = AccountSaver.restore(savedWithNulls!!)
        assertEquals(accountWithNulls, restoredWithNulls)

        val savedNull = saveWith<Account?>(AccountSaver, null)
        assertNull(savedNull)
        assertNull(AccountSaver.restore(emptyList<Any>()))
    }

    @Test
    fun addEditBudgetScreen_restoresInputStateAcrossConfigurationChange() {
        val viewModel = createBudgetViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddEditBudgetScreen(
                    navController = navController,
                    viewModel = viewModel,
                    budgetId = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Budget Amount").performTextInput("7500")
        composeTestRule.onNodeWithText("Select Category").performClick()
        composeTestRule.onNodeWithText("Groceries").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("7500").assertExists()
        composeTestRule.onNodeWithText("Groceries").assertExists()
    }

    @Test
    fun addEditBudgetScreen_editMode_doesNotOverwriteUserEditsOnFlowReEmission() {
        val initialBudget = Budget(id = 5, categoryName = "Groceries", amount = 3000.0, month = 11, year = 2026)
        val budgetFlow = MutableStateFlow<Budget?>(initialBudget)
        val viewModel = createBudgetViewModel(budgetFlow)
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddEditBudgetScreen(
                    navController = navController,
                    viewModel = viewModel,
                    budgetId = 5,
                )
            }
        }

        composeTestRule.onNodeWithText("3000").assertExists()
        composeTestRule.onNodeWithText("3000").performTextClearance()
        composeTestRule.onNodeWithText("Budget Amount").performTextInput("4500")

        restorationTester.emulateSavedInstanceStateRestore()

        // Re-emit existing DB budget to simulate Room re-emission
        budgetFlow.value = initialBudget

        composeTestRule.onNodeWithText("4500").assertExists()
        composeTestRule.onNodeWithText("Groceries").assertExists()
    }

    @Test
    fun addEditAccountScreen_restoresInputStateAcrossConfigurationChange() {
        val viewModel = createAccountViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddEditAccountScreen(
                    navController = navController,
                    viewModel = viewModel,
                    accountId = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Account Name (e.g., Savings, Credit Card)").performTextInput("Amex Platinum")
        composeTestRule.onNodeWithText("Account Type (e.g., Bank, Wallet)").performTextInput("Credit Card")
        composeTestRule.onNodeWithText("Credit Limit (e.g., 100000)").performTextInput("500000")
        composeTestRule.onNodeWithText("Billing Day of Month (1 - 31)").performTextInput("12")

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Amex Platinum").assertExists()
        composeTestRule.onNodeWithText("Credit Card").assertExists()
        composeTestRule.onNodeWithText("500000").assertExists()
        composeTestRule.onNodeWithText("12").assertExists()
    }

    @Test
    fun addEditAccountScreen_editMode_doesNotOverwriteUserEditsOnFlowReEmission() {
        val initialAccount =
            Account(id = 8, name = "Old Name", type = "Savings", creditLimit = null, billingCycleDay = null)
        val accountFlow = MutableStateFlow<Account?>(initialAccount)
        val viewModel = createAccountViewModel(accountFlow)
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddEditAccountScreen(
                    navController = navController,
                    viewModel = viewModel,
                    accountId = 8,
                )
            }
        }

        composeTestRule.onNodeWithText("Old Name").assertExists()
        composeTestRule.onNodeWithText("Old Name").performTextClearance()
        composeTestRule.onNodeWithText("Account Name (e.g., Savings, Credit Card)").performTextInput("New Name")

        restorationTester.emulateSavedInstanceStateRestore()

        // Re-emit original DB value to simulate Room re-emission
        accountFlow.value = initialAccount

        composeTestRule.onNodeWithText("New Name").assertExists()
    }

    @Test
    fun addEditGoalScreen_restoresInputStateAcrossConfigurationChange() {
        val (goalVm, txnVm) = createGoalViewModels()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddEditGoalScreen(
                    navController = navController,
                    goalId = null,
                    goalViewModel = goalVm,
                    txnViewModel = txnVm,
                )
            }
        }

        composeTestRule.onNodeWithText("Goal Name").performTextInput("Vacation Fund")
        composeTestRule.onNodeWithText("Target Amount").performTextInput("120000")
        composeTestRule.onNodeWithText("Notes (optional)").performTextInput("Trip to Japan")
        composeTestRule.onNodeWithText("Select Account").performClick()
        composeTestRule.onNodeWithText("Salary Account").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Vacation Fund").assertExists()
        composeTestRule.onNodeWithText("120000").assertExists()
        composeTestRule.onNodeWithText("Trip to Japan").assertExists()
        composeTestRule.onNodeWithText("Salary Account").assertExists()
    }

    @Test
    fun addEditGoalScreen_editMode_doesNotOverwriteUserEditsOnFlowReEmission() {
        val initialGoal =
            Goal(id = 3, name = "Car Downpayment", targetAmount = 200000.0, targetDate = null, accountId = 10, notes = "Initial Note")
        val goalFlow = MutableStateFlow<Goal?>(initialGoal)
        val (goalVm, txnVm) = createGoalViewModels(goalFlow)
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddEditGoalScreen(
                    navController = navController,
                    goalId = 3,
                    goalViewModel = goalVm,
                    txnViewModel = txnVm,
                )
            }
        }

        composeTestRule.onNodeWithText("Car Downpayment").assertExists()
        composeTestRule.onNodeWithText("Car Downpayment").performTextClearance()
        composeTestRule.onNodeWithText("Goal Name").performTextInput("House Downpayment")

        restorationTester.emulateSavedInstanceStateRestore()

        // Re-emit original DB goal
        goalFlow.value = initialGoal

        composeTestRule.onNodeWithText("House Downpayment").assertExists()
    }

    @Test
    fun addRecurringTransactionScreen_restoresInputStateAcrossConfigurationChange() {
        val (recVm, txnVm) = createRecurringViewModels()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddRecurringTransactionScreen(
                    navController = navController,
                    ruleId = null,
                    recurringViewModel = recVm,
                    transactionViewModel = txnVm,
                )
            }
        }

        composeTestRule.onNodeWithText("Description").performTextInput("Electricity Bill")
        composeTestRule.onNodeWithText("Amount").performTextInput("2200")
        composeTestRule.onNodeWithText("Variable Bill").performClick()
        composeTestRule.onNodeWithText("Variable Bill").assertIsOn()
        composeTestRule.onNodeWithText("Linked SMS Sender ID (e.g. AM-BESCOM)").performTextInput("BESCOM")

        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        composeTestRule.onNodeWithText("Select Account").performClick()
        composeTestRule.onNodeWithText("Salary Account").performClick()
        composeTestRule.onNodeWithText("Select Category").performClick()
        composeTestRule.onNodeWithText("Groceries").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithText("Electricity Bill").assertExists()
        composeTestRule.onNodeWithText("2200").assertExists()
        composeTestRule.onNodeWithText("Variable Bill").assertIsOn()
        composeTestRule.onNodeWithText("BESCOM").assertExists()
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        composeTestRule.onNodeWithText("Salary Account").assertExists()
        composeTestRule.onNodeWithText("Groceries").assertExists()
    }

    @Test
    fun addRecurringTransactionScreen_editMode_doesNotOverwriteUserEditsOnFlowReEmission() {
        val initialRule =
            RecurringTransaction(
                id = 7,
                description = "Gym Membership",
                amount = 1500.0,
                transactionType = TransactionType.EXPENSE,
                recurrenceInterval = "Monthly",
                startDate = 1000L,
                accountId = 10,
                categoryId = 1,
            )
        val ruleFlow = MutableStateFlow<RecurringTransaction?>(initialRule)
        val (recVm, txnVm) = createRecurringViewModels(ruleFlow)
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            PersonalFinanceAppTheme {
                AddRecurringTransactionScreen(
                    navController = navController,
                    ruleId = 7,
                    recurringViewModel = recVm,
                    transactionViewModel = txnVm,
                )
            }
        }

        composeTestRule.onNodeWithText("Gym Membership").assertExists()
        composeTestRule.onNodeWithText("Gym Membership").performTextClearance()
        composeTestRule.onNodeWithText("Description").performTextInput("Gold's Gym Membership")

        restorationTester.emulateSavedInstanceStateRestore()

        // Re-emit original DB rule
        ruleFlow.value = initialRule

        composeTestRule.onNodeWithText("Gold's Gym Membership").assertExists()
    }

    // --- Common Fixture Helpers to prevent duplication (CPD < 3%) ---

    private fun createBudgetViewModel(budgetFlow: MutableStateFlow<Budget?> = MutableStateFlow(null)): BudgetViewModel {
        val vm: BudgetViewModel = mockk(relaxed = true)
        every { vm.availableCategoriesForNewBudget } returns MutableStateFlow(testCategories)
        every { vm.allCategories } returns MutableStateFlow(testCategories)
        every { vm.selectedMonth } returns MutableStateFlow(Calendar.getInstance())
        every { vm.getBudgetById(any()) } returns budgetFlow
        return vm
    }

    private fun createAccountViewModel(accountFlow: MutableStateFlow<Account?> = MutableStateFlow(null)): AccountViewModel {
        val vm: AccountViewModel = mockk(relaxed = true)
        every { vm.getAccountById(any()) } returns accountFlow
        return vm
    }

    private fun createGoalViewModels(goalFlow: MutableStateFlow<Goal?> = MutableStateFlow(null)): Pair<GoalViewModel, TransactionViewModel> {
        val goalVm: GoalViewModel = mockk(relaxed = true)
        val txnVm: TransactionViewModel = mockk(relaxed = true)
        every { goalVm.getGoalById(any()) } returns goalFlow
        every { txnVm.allAccounts } returns MutableStateFlow(testAccounts)
        return Pair(goalVm, txnVm)
    }

    private fun createRecurringViewModels(
        ruleFlow: MutableStateFlow<RecurringTransaction?> = MutableStateFlow(null),
    ): Pair<RecurringTransactionViewModel, TransactionViewModel> {
        val recVm: RecurringTransactionViewModel = mockk(relaxed = true)
        val txnVm: TransactionViewModel = mockk(relaxed = true)
        every { recVm.getRuleById(any()) } returns ruleFlow
        every { txnVm.allAccounts } returns MutableStateFlow(testAccounts)
        every { txnVm.allCategories } returns MutableStateFlow(testCategories)
        return Pair(recVm, txnVm)
    }
}
