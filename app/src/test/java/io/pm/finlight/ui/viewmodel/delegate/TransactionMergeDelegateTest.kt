package io.pm.finlight.ui.viewmodel.delegate

import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.data.model.MergedTransactionItem
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionMergeDelegateTest {
    @Mock
    private lateinit var mergeTransactionsUseCase: MergeTransactionsUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var delegate: TransactionMergeDelegate

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        delegate =
            TransactionMergeDelegate(
                coroutineScope = testScope,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
                dispatcherProvider = dispatcherProvider,
            )
    }

    @Test
    fun `openReviewMergeSheet selects largest amount transaction as anchor`() =
        runTest(testDispatcher) {
            val t1 =
                TransactionDetails(
                    transaction = Transaction(id = 1, description = "A", categoryId = null, amount = 50.0, date = 0L, accountId = 1, notes = null),
                    images = emptyList(), accountName = "Bank", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null,
                )
            val t2 =
                TransactionDetails(
                    transaction = Transaction(id = 2, description = "B", categoryId = null, amount = 150.0, date = 0L, accountId = 1, notes = null),
                    images = emptyList(), accountName = "Bank", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null,
                )

            delegate.openReviewMergeSheet(setOf(1, 2), listOf(t1, t2))
            testScope.advanceUntilIdle()

            assertTrue(delegate.showReviewMergeSheet.value)
            assertEquals(2, delegate.anchorTransactionId.value)
        }

    @Test
    fun `dismissReviewMergeSheet resets sheet state and error`() =
        runTest(testDispatcher) {
            val t1 =
                TransactionDetails(
                    transaction = Transaction(id = 1, description = "A", categoryId = null, amount = 50.0, date = 0L, accountId = 1, notes = null),
                    images = emptyList(), accountName = "Bank", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null,
                )
            val t2 =
                TransactionDetails(
                    transaction = Transaction(id = 2, description = "B", categoryId = null, amount = 150.0, date = 0L, accountId = 1, notes = null),
                    images = emptyList(), accountName = "Bank", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null,
                )
            delegate.openReviewMergeSheet(setOf(1, 2), listOf(t1, t2))
            testScope.advanceUntilIdle()

            delegate.dismissReviewMergeSheet()
            assertFalse(delegate.showReviewMergeSheet.value)
            assertNull(delegate.anchorTransactionId.value)
            assertNull(delegate.mergeValidationError.value)
        }

    @Test
    fun `setAnchorTransaction updates anchor ID`() {
        delegate.setAnchorTransaction(5, setOf(1, 5))
        assertEquals(5, delegate.anchorTransactionId.value)
    }

    @Test
    fun `confirmManualMerge executes merge and invokes callbacks on success`() =
        runTest(testDispatcher) {
            val t1 =
                TransactionDetails(
                    transaction = Transaction(id = 1, description = "A", categoryId = null, amount = 50.0, date = 0L, accountId = 1, notes = null),
                    images = emptyList(), accountName = "Bank", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null,
                )
            val t2 =
                TransactionDetails(
                    transaction = Transaction(id = 2, description = "B", categoryId = null, amount = 150.0, date = 0L, accountId = 1, notes = null),
                    images = emptyList(), accountName = "Bank", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null,
                )
            delegate.openReviewMergeSheet(setOf(1, 2), listOf(t1, t2))
            testScope.advanceUntilIdle()

            var eventMsg = ""
            var cleared = false
            delegate.confirmManualMerge(
                selectedIds = setOf(1, 2),
                onEvent = { eventMsg = it },
                onClearSelection = { cleared = true },
            )
            testScope.advanceUntilIdle()

            verify(mergeTransactionsUseCase).manualMerge(2, listOf(1))
            assertEquals("Successfully merged 2 transactions.", eventMsg)
            assertTrue(cleared)
            assertFalse(delegate.showReviewMergeSheet.value)
        }

    @Test
    fun `loadMergedTransactionBreakdown updates state`() =
        runTest(testDispatcher) {
            val breakdown =
                listOf(
                    MergedTransactionItem(
                        accountId = 1,
                        accountName = "Bank",
                        amount = 25.0,
                        transactionType = TransactionType.EXPENSE,
                        isAnchor = false,
                        description = "Split Item",
                        date = 0L,
                    ),
                )
            whenever(mergeTransactionsUseCase.getMergedTransactionBreakdown(10)).thenReturn(breakdown)

            delegate.loadMergedTransactionBreakdown(10)
            testScope.advanceUntilIdle()

            assertEquals(breakdown, delegate.mergedTransactionBreakdown.value)
        }

    @Test
    fun `unmergeTransaction delegates to mergeTransactionsUseCase`() =
        runTest(testDispatcher) {
            delegate.unmergeTransaction(10)
            testScope.advanceUntilIdle()

            verify(mergeTransactionsUseCase).unmerge(10)
        }
}
