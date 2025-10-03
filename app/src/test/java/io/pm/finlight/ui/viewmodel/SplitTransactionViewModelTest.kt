// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SplitTransactionViewModelTest.kt
// REASON: NEW FILE - Unit tests for SplitTransactionViewModel, covering logic for
// initializing the screen for both new and existing splits, handling foreign
// currency transactions, and validating UI actions like adding, removing, and
// updating split items.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SplitTransactionViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var transactionRepository: TransactionRepository
    @Mock
    private lateinit var categoryRepository: CategoryRepository
    @Mock
    private lateinit var splitTransactionRepository: SplitTransactionRepository

    private lateinit var viewModel: SplitTransactionViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init for new split populates state from parent transaction`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 100.0, isSplit = false, date = 0L, accountId = 1, categoryId = 1, notes = "Parent notes")
        `when`(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))

        // Act
        viewModel = SplitTransactionViewModel(transactionRepository, categoryRepository, splitTransactionRepository, transactionId)

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(parentTxn, state.parentTransaction)
            assertEquals(1, state.splitItems.size)
            assertEquals("100.0", state.splitItems.first().amount)
            assertEquals("Parent notes", state.splitItems.first().notes)
            assertEquals(0.0, state.remainingAmount, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init for new split with foreign currency uses originalAmount`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 830.0, originalAmount = 10.0, currencyCode = "USD", isSplit = false, date = 0L, accountId = 1, categoryId = 1, notes = "Foreign txn")
        `when`(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))

        // Act
        viewModel = SplitTransactionViewModel(transactionRepository, categoryRepository, splitTransactionRepository, transactionId)

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("10.0", state.splitItems.first().amount)
            assertEquals(0.0, state.remainingAmount, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init for existing split loads previous splits`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 100.0, isSplit = true, date = 0L, accountId = 1, categoryId = null, notes = null)
        val category = Category(1, "Food", "icon", "color")
        val existingSplits = listOf(
            SplitTransactionDetails(SplitTransaction(1, transactionId, 60.0, 1, "Lunch"), "Food", "icon", "color"),
            SplitTransactionDetails(SplitTransaction(2, transactionId, 40.0, 2, "Snacks"), "Snacks", "icon2", "color2")
        )
        `when`(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))
        `when`(splitTransactionRepository.getSplitsForParent(transactionId)).thenReturn(flowOf(existingSplits))
        `when`(categoryRepository.getCategoryById(1)).thenReturn(category)

        // Act
        viewModel = SplitTransactionViewModel(transactionRepository, categoryRepository, splitTransactionRepository, transactionId)

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.splitItems.size)
            assertEquals("60.0", state.splitItems[0].amount)
            assertEquals("40.0", state.splitItems[1].amount)
            assertEquals(category, state.splitItems[0].category)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addSplitItem adds a new item and recalculates remaining amount`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 100.0, isSplit = false, date = 0L, accountId = 1, categoryId = 1, notes = null)
        `when`(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))
        viewModel = SplitTransactionViewModel(transactionRepository, categoryRepository, splitTransactionRepository, transactionId)
        // wait for init
        viewModel.uiState.test { awaitItem() }

        // Act
        viewModel.addSplitItem()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.splitItems.size)
            assertEquals("0.0", state.splitItems.last().amount)
            // Parent: 100.0. Items: [ {100.0}, {0.0} ]. Total split: 100.0. Remaining: 100.0 - 100.0 = 0.0
            assertEquals(0.0, state.remainingAmount, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeSplitItem removes an item and recalculates remaining amount`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 100.0, isSplit = false, date = 0L, accountId = 1, categoryId = 1, notes = null)
        `when`(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))
        viewModel = SplitTransactionViewModel(transactionRepository, categoryRepository, splitTransactionRepository, transactionId)
        viewModel.uiState.test { awaitItem() } // Consume initial state
        viewModel.addSplitItem()
        val itemToRemove = viewModel.uiState.value.splitItems.first()

        // Act
        viewModel.removeSplitItem(itemToRemove)

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.splitItems.size)
            // State after remove: Parent=100, items=[{0}], totalSplit=0, remaining=100.
            assertEquals(100.0, state.remainingAmount, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `recalculateRemainingAmount works correctly`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 150.0, isSplit = false, date = 0L, accountId = 1, categoryId = 1, notes = null)
        `when`(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))
        viewModel = SplitTransactionViewModel(transactionRepository, categoryRepository, splitTransactionRepository, transactionId)
        viewModel.uiState.test { awaitItem() } // Consume initial state

        // Act
        viewModel.updateSplitAmount(viewModel.uiState.value.splitItems.first(), "80.0")
        viewModel.addSplitItem()
        viewModel.updateSplitAmount(viewModel.uiState.value.splitItems.last(), "30.0")

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            // Parent=150, splits=80+30=110, remaining=40
            assertEquals(40.0, state.remainingAmount, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
