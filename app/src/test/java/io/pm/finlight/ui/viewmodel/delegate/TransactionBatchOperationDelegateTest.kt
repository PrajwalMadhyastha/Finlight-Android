package io.pm.finlight.ui.viewmodel.delegate

import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.ISmsParseTemplateRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionBatchOperationDelegateTest {
    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    @Mock
    private lateinit var merchantRenameRuleRepository: IMerchantRenameRuleRepository

    @Mock
    private lateinit var merchantCategoryMappingRepository: IMerchantCategoryMappingRepository

    @Mock
    private lateinit var smsRepository: ISmsRepository

    @Mock
    private lateinit var smsParseTemplateRepository: ISmsParseTemplateRepository

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var delegate: TransactionBatchOperationDelegate

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        delegate =
            TransactionBatchOperationDelegate(
                coroutineScope = testScope,
                transactionRepository = transactionRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                smsRepository = smsRepository,
                smsParseTemplateRepository = smsParseTemplateRepository,
                dispatcherProvider = dispatcherProvider,
            )
    }

    @Test
    fun `selection mode toggling and clearing`() {
        assertFalse(delegate.isSelectionModeActive.value)
        assertEquals(emptySet<Int>(), delegate.selectedTransactionIds.value)

        delegate.enterSelectionMode(1)
        assertTrue(delegate.isSelectionModeActive.value)
        assertEquals(setOf(1), delegate.selectedTransactionIds.value)

        delegate.toggleTransactionSelection(2)
        assertEquals(setOf(1, 2), delegate.selectedTransactionIds.value)

        delegate.toggleTransactionSelection(1)
        assertEquals(setOf(2), delegate.selectedTransactionIds.value)

        delegate.clearSelectionMode()
        assertFalse(delegate.isSelectionModeActive.value)
        assertEquals(emptySet<Int>(), delegate.selectedTransactionIds.value)
    }

    @Test
    fun `delete confirmation flow and confirm delete`() =
        runTest(testDispatcher) {
            delegate.enterSelectionMode(10)
            delegate.onDeleteSelectionClick()
            assertTrue(delegate.showDeleteConfirmation.value)

            delegate.onCancelDeleteSelection()
            assertFalse(delegate.showDeleteConfirmation.value)

            delegate.onDeleteSelectionClick()
            whenever(transactionRepository.getSmsHashesByIds(listOf(10))).thenReturn(listOf("hash10"))

            var eventMsg = ""
            delegate.onConfirmDeleteSelection { eventMsg = it }
            testScope.advanceUntilIdle()

            verify(transactionRepository).insertDeletedSmsHash("hash10")
            verify(transactionRepository).deleteByIds(listOf(10))
            assertFalse(delegate.showDeleteConfirmation.value)
            assertFalse(delegate.isSelectionModeActive.value)
        }

    @Test
    fun `share sheet toggles and fields`() {
        assertFalse(delegate.showShareSheet.value)
        delegate.onShareClick()
        assertTrue(delegate.showShareSheet.value)

        delegate.onShareableFieldToggled(ShareableField.Date)
        assertFalse(ShareableField.Date in delegate.shareableFields.value)

        delegate.onShareableFieldToggled(ShareableField.Date)
        assertTrue(ShareableField.Date in delegate.shareableFields.value)

        delegate.onShareSheetDismiss()
        assertFalse(delegate.showShareSheet.value)
    }

    @Test
    fun `onAttemptToLeaveScreen triggers retro sheet when description changed and similar found`() =
        runTest(testDispatcher) {
            val initial = Transaction(id = 1, description = "Old Name", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, notes = null, originalDescription = "Raw Name")
            val current = initial.copy(description = "New Name")
            val similar = listOf(Transaction(id = 2, description = "Old Name", categoryId = 1, amount = 20.0, date = 0L, accountId = 1, notes = null, originalDescription = "Raw Name"))

            whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(current))
            whenever(transactionRepository.findSimilarTransactions("Raw Name", 1)).thenReturn(similar)

            var navAllowed = false
            delegate.onAttemptToLeaveScreen(initial) { navAllowed = true }
            testScope.advanceUntilIdle()

            assertFalse(navAllowed)
            val sheetState = delegate.retroUpdateSheetState.value
            assertNotNull(sheetState)
            assertEquals("Raw Name", sheetState?.originalDescription)
            assertEquals("New Name", sheetState?.newDescription)
            assertEquals(listOf(similar[0]), sheetState?.similarTransactions)
            assertEquals(setOf(2), sheetState?.selectedIds)
        }

    @Test
    fun `dismissRetroUpdateSheet clears state`() =
        runTest(testDispatcher) {
            val initial = Transaction(id = 1, description = "Old Name", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, notes = null, originalDescription = "Raw Name")
            val current = initial.copy(description = "New Name")
            whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(current))
            whenever(transactionRepository.findSimilarTransactions("Raw Name", 1)).thenReturn(emptyList())

            delegate.onAttemptToLeaveScreen(initial) {}
            testScope.advanceUntilIdle()
            assertNotNull(delegate.retroUpdateSheetState.value)

            delegate.dismissRetroUpdateSheet()
            assertNull(delegate.retroUpdateSheetState.value)
        }

    @Test
    fun `canonical nudge dismiss`() =
        runTest(testDispatcher) {
            delegate.dismissCanonicalNudge()
            assertNull(delegate.canonicalNudgeState.value)
        }
}
