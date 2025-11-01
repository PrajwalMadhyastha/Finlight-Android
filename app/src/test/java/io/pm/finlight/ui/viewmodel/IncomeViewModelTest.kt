// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/IncomeViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class IncomeViewModelTest : BaseViewModelTest() {

    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository

    private lateinit var viewModel: IncomeViewModel

    @Before
    override fun setup() {
        super.setup()

        // Setup default mocks for initialization
        `when`(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getIncomeByCategoryForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
    }

    private fun initializeViewModel() {
        viewModel = IncomeViewModel(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
    }

    @Test
    fun `totalIncomeForSelectedMonth calculates sum correctly`() = runTest {
        // Arrange
        val transactions = listOf(
            TransactionDetails(Transaction(id = 1, description = "Salary", amount = 5000.0, transactionType = "income", date = 0L, accountId = 1, categoryId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 2, description = "Freelance", amount = 1500.50, transactionType = "income", date = 0L, accountId = 1, categoryId = 1, notes = null), emptyList(), null, null, null, null, null)
        )
        `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))
        initializeViewModel()

        // Assert
        viewModel.totalIncomeForSelectedMonth.test {
            assertEquals(6501L, awaitItem()) // 5000.0 + 1500.50 = 6500.50, rounded to 6501
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSelectedMonth triggers refetch of data`() = runTest {
        // Arrange
        val initialTransactions = listOf(
            TransactionDetails(Transaction(id = 1, description = "Initial Tx", amount = 100.0, transactionType = "income", date = 0L, accountId = 1, categoryId = 1, notes = null), emptyList(), null, null, null, null, null)
        )
        val newTransactions = listOf(
            TransactionDetails(Transaction(id = 2, description = "New Month Tx", amount = 200.0, transactionType = "income", date = 0L, accountId = 1, categoryId = 1, notes = null), emptyList(), null, null, null, null, null)
        )
        // This chained 'thenReturn' is crucial. The first time the mock is called (in init), it returns the initial list.
        // The second time (after changing the month), it returns the new list.
        `when`(transactionRepository.getIncomeTransactionsForRange(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(flowOf(initialTransactions))
            .thenReturn(flowOf(newTransactions))

        initializeViewModel()
        val newMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

        // Act & Assert
        viewModel.incomeTransactionsForSelectedMonth.test {
            // Await the emission from the first repository call. This is different from the initial `emptyList()`
            assertEquals(initialTransactions, awaitItem())

            // Act: Change the month, which triggers the second repository call
            viewModel.setSelectedMonth(newMonth)

            // Await the emission from the second repository call. Since the list is different, StateFlow emits.
            assertEquals(newTransactions, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `updateFilterKeyword updates filter state`() = runTest {
        // Arrange
        initializeViewModel()
        val keyword = "test"

        // Assert
        viewModel.filterState.test {
            // 1. Await the initial default state
            assertEquals(TransactionFilterState(), awaitItem())

            // 2. Perform the action
            viewModel.updateFilterKeyword(keyword)

            // 3. Await the updated state
            val updatedState = awaitItem()
            assertEquals(keyword, updatedState.keyword)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateFilterAccount updates filter state`() = runTest {
        // Arrange
        initializeViewModel()
        val account = Account(1, "Test Account", "Bank")

        // Assert
        viewModel.filterState.test {
            // 1. Await the initial default state
            assertEquals(TransactionFilterState(), awaitItem())

            // 2. Perform the action
            viewModel.updateFilterAccount(account)

            // 3. Await the updated state
            val updatedState = awaitItem()
            assertEquals(account, updatedState.account)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateFilterCategory updates filter state`() = runTest {
        // Arrange
        initializeViewModel()
        val category = Category(1, "Test Category", "", "")

        // Assert
        viewModel.filterState.test {
            // 1. Await the initial default state
            assertEquals(TransactionFilterState(), awaitItem())

            // 2. Perform the action
            viewModel.updateFilterCategory(category)

            // 3. Await the updated state
            val updatedState = awaitItem()
            assertEquals(category, updatedState.category)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearFilters resets filter state to default`() = runTest {
        // Arrange
        initializeViewModel()

        // Assert
        viewModel.filterState.test {
            // 1. Await initial state
            assertEquals(TransactionFilterState(), awaitItem())

            // 2. Set some filters to create a non-default state
            viewModel.updateFilterKeyword("test")
            viewModel.updateFilterAccount(Account(1, "Test Account", "Bank"))

            // 3. Consume the intermediate states to ensure we are at the non-default state
            awaitItem() // After keyword update
            awaitItem() // After account update

            // 4. Perform the clear action
            viewModel.clearFilters()

            // 5. Await the final, reset state
            val clearedState = awaitItem()
            assertEquals("", clearedState.keyword)
            assertNull(clearedState.account)
            assertNull(clearedState.category)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
