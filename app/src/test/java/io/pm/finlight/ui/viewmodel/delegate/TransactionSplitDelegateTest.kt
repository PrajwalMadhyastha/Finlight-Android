package io.pm.finlight.ui.viewmodel.delegate

import io.pm.finlight.Category
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.SplitTransaction
import io.pm.finlight.SplitTransactionDetails
import io.pm.finlight.Transaction
import io.pm.finlight.ui.viewmodel.SplitItem
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSplitDelegateTest {
    @Mock
    private lateinit var splitTransactionRepository: ISplitTransactionRepository

    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var delegate: TransactionSplitDelegate

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        delegate =
            TransactionSplitDelegate(
                coroutineScope = testScope,
                splitTransactionRepository = splitTransactionRepository,
                transactionRepository = transactionRepository,
                dispatcherProvider = dispatcherProvider,
            )
    }

    @Test
    fun `getSplitDetailsForTransaction delegates to splitTransactionRepository`() =
        runTest(testDispatcher) {
            val splits =
                listOf(
                    SplitTransactionDetails(
                        splitTransaction = SplitTransaction(id = 1, parentTransactionId = 10, amount = 20.0, categoryId = 1, notes = "Note"),
                        categoryName = "Food",
                        categoryIconKey = "icon",
                        categoryColorKey = "red",
                    ),
                )
            whenever(splitTransactionRepository.getSplitsForParent(10)).thenReturn(flowOf(splits))

            val result = delegate.getSplitDetailsForTransaction(10).first()
            assertEquals(splits, result)
            verify(splitTransactionRepository).getSplitsForParent(10)
        }

    @Test
    fun `saveTransactionSplits converts split items and delegates to repository`() =
        runTest(testDispatcher) {
            val parent =
                Transaction(
                    id = 10,
                    description = "Dinner",
                    categoryId = 1,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    notes = null,
                    conversionRate = 2.0,
                    currencyCode = "USD",
                )
            whenever(transactionRepository.getTransactionById(10)).thenReturn(flowOf(parent))

            var callbackCalled = false
            val items =
                listOf(
                    SplitItem(id = 1, amount = "30.0", category = Category(id = 2, name = "Drinks", iconKey = "drink", colorKey = "blue"), notes = "Beer"),
                    SplitItem(id = 2, amount = "20.0", category = null, notes = "Tip"),
                )

            delegate.saveTransactionSplits(10, items) {
                callbackCalled = true
            }
            testScope.advanceUntilIdle()

            val captor = argumentCaptor<List<SplitTransaction>>()
            verify(splitTransactionRepository).saveSplits(org.mockito.kotlin.eq(10), captor.capture())

            val saved = captor.firstValue
            assertEquals(2, saved.size)
            assertEquals(60.0, saved[0].amount, 0.001)
            assertEquals(30.0, saved[0].originalAmount!!, 0.001)
            assertEquals(2, saved[0].categoryId)
            assertEquals("Beer", saved[0].notes)

            assertEquals(40.0, saved[1].amount, 0.001)
            assertEquals(20.0, saved[1].originalAmount!!, 0.001)
            assertEquals(null, saved[1].categoryId)
            assertEquals("Tip", saved[1].notes)

            assertEquals(true, callbackCalled)
        }

    @Test
    fun `unsplitTransaction calls splitTransactionRepository unmarkSplit`() =
        runTest(testDispatcher) {
            val txn =
                Transaction(
                    id = 15,
                    description = "Groceries",
                    categoryId = 1,
                    amount = 50.0,
                    date = 1000L,
                    accountId = 1,
                    notes = null,
                    originalDescription = "Supermarket",
                )

            delegate.unsplitTransaction(txn)
            testScope.advanceUntilIdle()

            verify(splitTransactionRepository).unmarkSplit(15, "Supermarket")
        }

    @Test
    fun `unsplitTransaction catches exception gracefully without crashing`() =
        runTest(testDispatcher) {
            val txn =
                Transaction(
                    id = 15,
                    description = "Groceries",
                    categoryId = 1,
                    amount = 50.0,
                    date = 1000L,
                    accountId = 1,
                    notes = null,
                    originalDescription = "Supermarket",
                )
            org.mockito.Mockito.doThrow(RuntimeException("DB failure")).`when`(splitTransactionRepository).unmarkSplit(15, "Supermarket")

            delegate.unsplitTransaction(txn)
            testScope.advanceUntilIdle()

            verify(splitTransactionRepository).unmarkSplit(15, "Supermarket")
        }
}
