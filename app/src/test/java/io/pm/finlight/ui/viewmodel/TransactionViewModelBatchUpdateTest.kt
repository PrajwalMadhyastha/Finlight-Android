package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.components.ShareableField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.lang.RuntimeException
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelBatchUpdateTest : TransactionViewModelBaseSetup() {

    @Test
        fun `onAttemptToLeaveScreen prepares retro update sheet when changes are detected`() =
            runTest {
                // ARRANGE
                val initialTxn =
                    Transaction(
                        id = 1,
                        description = "Starbucks",
                        categoryId = 1,
                        amount = 10.0,
                        date = 0L,
                        accountId = 1,
                        notes = null,
                        originalDescription = "Starbucks",
                    )
                val currentTxn = initialTxn.copy(description = "Starbucks Coffee") // Description has changed
                val similarTxns =
                    listOf(
                        Transaction(id = 2, description = "Starbucks", categoryId = 1, amount = 12.0, date = 0L, accountId = 1, notes = null),
                    )

                // Mock repository calls
                whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
                whenever(transactionRepository.findSimilarTransactions("Starbucks", 1)).thenReturn(similarTxns)
                // Add mocks for the methods called inside loadTransactionForDetailScreen
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                // ACT
                viewModel.loadTransactionForDetailScreen(1)
                var hasNavigated = false
                viewModel.onAttemptToLeaveScreen { hasNavigated = true }
                advanceUntilIdle()

                // ASSERT
                val sheetState = viewModel.retroUpdateSheetState.first()
                assertNotNull("Sheet state should not be null", sheetState)
                assertEquals(false, hasNavigated) // Should not navigate yet
                assertEquals("Starbucks", sheetState?.originalDescription)
                assertEquals("Starbucks Coffee", sheetState?.newDescription)
                assertEquals(null, sheetState?.newCategoryId) // Category didn't change
                assertEquals(1, sheetState?.similarTransactions?.size)
                assertEquals(2, sheetState?.selectedIds?.first()) // The ID of the similar transaction
                // FIX: totalMatchingCount must include the current transaction (1 similar + 1 current = 2)
                assertEquals(2, sheetState?.totalMatchingCount)
            }

    // --- NEW: Retro Update Sheet Tests ---

        /**
         * Helper to set the ViewModel's retroUpdateSheetState to a valid, non-null value.
         * This simulates the sheet being opened by onAttemptToLeaveScreen.
         */

    private fun setupRetroSheet(
            initialDesc: String = "Starbucks",
            newDesc: String? = "Starbucks Coffee",
            newCatId: Int? = null,
            numSimilar: Int = 2,
        ) = runTest {
            val initialTxn =
                Transaction(
                    id = 1,
                    description = initialDesc,
                    categoryId = 1,
                    amount = 10.0,
                    date = 0L,
                    accountId = 1,
                    notes = null,
                    originalDescription = "Starbucks",
                )
            val currentTxn =
                initialTxn.copy(
                    description = newDesc ?: initialTxn.description,
                    categoryId = newCatId ?: initialTxn.categoryId,
                )
            val similarTxns =
                (2 until 2 + numSimilar).map {
                    Transaction(id = it, description = "Starbucks", categoryId = 1, amount = 12.0, date = 0L, accountId = 1, notes = null)
                }

            whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
            whenever(transactionRepository.findSimilarTransactions("Starbucks", 1)).thenReturn(similarTxns)
            whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
            whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

            viewModel.loadTransactionForDetailScreen(1)
            viewModel.onAttemptToLeaveScreen { }
            advanceUntilIdle()
        }

    @Test
        fun `dismissRetroUpdateSheet sets state to null`() =
            runTest {
                // ARRANGE
                setupRetroSheet()
                assertNotNull("Pre-condition: Sheet state should be set", viewModel.retroUpdateSheetState.value)

                // ACT
                viewModel.dismissRetroUpdateSheet()

                // ASSERT
                assertNull("Sheet state should be null after dismiss", viewModel.retroUpdateSheetState.value)
            }

    @Test
        fun `toggleRetroUpdateSelection adds and removes id`() =
            runTest {
                // ARRANGE
                setupRetroSheet() // Sets initial selected IDs to {2, 3}
                assertEquals(setOf(2, 3), viewModel.retroUpdateSheetState.value?.selectedIds)

                // ACT 1: Remove ID 2
                viewModel.toggleRetroUpdateSelection(2)
                advanceUntilIdle()
                // ASSERT 1
                assertEquals(setOf(3), viewModel.retroUpdateSheetState.value?.selectedIds)

                // ACT 2: Add ID 4
                viewModel.toggleRetroUpdateSelection(4)
                advanceUntilIdle()
                // ASSERT 2
                assertEquals(setOf(3, 4), viewModel.retroUpdateSheetState.value?.selectedIds)
            }

    @Test
        fun `toggleRetroUpdateSelectAll selects all and deselects all`() =
            runTest {
                // ARRANGE
                setupRetroSheet(numSimilar = 3) // similar = {2, 3, 4}, selected = {2, 3, 4}
                assertEquals(setOf(2, 3, 4), viewModel.retroUpdateSheetState.value?.selectedIds)

                // ACT 1: Deselect all (since all are selected)
                viewModel.toggleRetroUpdateSelectAll()
                advanceUntilIdle()
                // ASSERT 1
                assertEquals(emptySet<Int>(), viewModel.retroUpdateSheetState.value?.selectedIds)

                // ACT 2: Select all
                viewModel.toggleRetroUpdateSelectAll()
                advanceUntilIdle()
                // ASSERT 2
                assertEquals(setOf(2, 3, 4), viewModel.retroUpdateSheetState.value?.selectedIds)
            }

    @Test
        fun `performBatchUpdate updates all fields, creates rule, and dismisses when all selected`() =
            runTest {
                // ARRANGE
                setupRetroSheet(newDesc = "Starbucks Coffee", newCatId = 5, numSimilar = 2)
                val expectedIdsToUpdate = listOf(2, 3)

                // Pre-condition check
                val state = viewModel.retroUpdateSheetState.value
                assertNotNull(state)
                assertEquals(setOf(2, 3), state?.selectedIds)
                assertEquals("Starbucks Coffee", state?.newDescription)
                assertEquals(5, state?.newCategoryId)
                assertEquals(state?.similarTransactions?.size, state?.selectedIds?.size)

                // ACT & ASSERT
                viewModel.uiEvent.test {
                    viewModel.performBatchUpdate()
                    advanceUntilIdle()

                    // Verify repo calls
                    verify(merchantRenameRuleRepository).insert(MerchantRenameRule("Starbucks", "Starbucks Coffee"))
                    verify(transactionRepository).updateDescriptionForIds(expectedIdsToUpdate, "Starbucks Coffee")
                    verify(transactionRepository).updateCategoryForIds(expectedIdsToUpdate, 5)

                    // Verify UI event
                    assertEquals("Updated 2 transaction(s).", awaitItem())

                    // Verify dismiss
                    assertNull("Sheet should be dismissed", viewModel.retroUpdateSheetState.value)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `performBatchUpdate deletes rule when reverting to original name and all selected`() =
            runTest {
                // ARRANGE
                // The original description is "Starbucks".
                // We simulate that it was currently "Starbucks Coffee" (aliased) and we change it back to "Starbucks".
                setupRetroSheet(initialDesc = "Starbucks Coffee", newDesc = "Starbucks", newCatId = 5, numSimilar = 2)
                val expectedIdsToUpdate = listOf(2, 3)

                val state = viewModel.retroUpdateSheetState.value
                assertNotNull("Retro update sheet should be shown", state)
                assertEquals(setOf(2, 3), state?.selectedIds)
                assertEquals("Starbucks", state?.newDescription)
                assertEquals("Starbucks", state?.originalDescription)

                // ACT & ASSERT
                viewModel.uiEvent.test {
                    viewModel.performBatchUpdate()
                    advanceUntilIdle()

                    // Verify repo calls
                    verify(merchantRenameRuleRepository, never()).insert(any())
                    verify(merchantRenameRuleRepository).deleteByOriginalName("Starbucks")

                    verify(transactionRepository).updateDescriptionForIds(expectedIdsToUpdate, "Starbucks")
                    verify(transactionRepository).updateCategoryForIds(expectedIdsToUpdate, 5)

                    assertEquals("Updated 2 transaction(s).", awaitItem())
                    assertNull(viewModel.retroUpdateSheetState.value)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `performBatchUpdate saves global rule when updateFutureTransactions is true`() =
            runTest {
                // ARRANGE
                val initialTxn = Transaction(id = 1, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val currentTxn = initialTxn.copy(description = "Uber Rides")
                val similarTxn1 = Transaction(id = 2, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val similarTxn2 = Transaction(id = 3, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)

                whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
                whenever(transactionRepository.findSimilarTransactions("Uber", 1)).thenReturn(listOf(similarTxn1, similarTxn2))
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                advanceUntilIdle()

                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                val state = viewModel.retroUpdateSheetState.value
                assertNotNull(state)
                assertTrue(state!!.updateFutureTransactions)

                // ACT
                viewModel.performBatchUpdate()
                advanceUntilIdle()

                // ASSERT: Rule saved
                verify(merchantRenameRuleRepository).insert(MerchantRenameRule("Uber", "Uber Rides"))
            }

    @Test
        fun `performBatchUpdate deletes rule when reverting name and updateFutureTransactions is true`() =
            runTest {
                // ARRANGE
                val initialTxn = Transaction(id = 1, description = "Uber Rides", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val currentTxn = initialTxn.copy(description = "Uber")
                val similarTxn1 = Transaction(id = 2, description = "Uber Rides", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val similarTxn2 = Transaction(id = 3, description = "Uber Rides", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)

                whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
                whenever(transactionRepository.findSimilarTransactions("Uber", 1)).thenReturn(listOf(similarTxn1, similarTxn2))
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                advanceUntilIdle()

                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                // ACT
                viewModel.performBatchUpdate()
                advanceUntilIdle()

                // ASSERT: Rule deleted
                verify(merchantRenameRuleRepository).deleteByOriginalName("Uber")
            }

    @Test
        fun `performBatchUpdate skips global rule when updateFutureTransactions is false`() =
            runTest {
                // ARRANGE
                val initialTxn = Transaction(id = 1, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val currentTxn = initialTxn.copy(description = "Uber Rides")
                val similarTxn1 = Transaction(id = 2, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val similarTxn2 = Transaction(id = 3, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)

                whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
                whenever(transactionRepository.findSimilarTransactions("Uber", 1)).thenReturn(listOf(similarTxn1, similarTxn2))
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                advanceUntilIdle()

                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                // Toggle to false
                viewModel.toggleUpdateFutureTransactions()

                // ACT
                viewModel.performBatchUpdate()
                advanceUntilIdle()

                // ASSERT: Rule NOT saved, but past txns updated
                verify(merchantRenameRuleRepository, never()).insert(any())
                verify(transactionRepository).updateDescriptionForIds(any(), any())
            }

    @Test
        fun `performBatchUpdate saves category mapping when category changed and updateFutureTransactions is true`() =
            runTest {
                // ARRANGE
                val initialTxn = Transaction(id = 1, description = "Uber", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Uber", notes = null)
                val currentTxn = initialTxn.copy(categoryId = 2) // Category changed

                whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
                whenever(transactionRepository.findSimilarTransactions("Uber", 1)).thenReturn(emptyList()) // No history needed for mapping test
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                advanceUntilIdle()

                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                // ACT
                viewModel.performBatchUpdate()
                advanceUntilIdle()

                // ASSERT
                verify(merchantCategoryMappingRepository).insert(MerchantCategoryMapping("Uber", 2))
            }

    @Test
        fun `performBatchUpdate on failure sends error event and dismisses`() =
            runTest {
                // ARRANGE
                setupRetroSheet()
                assertNotNull(viewModel.retroUpdateSheetState.value)
                val expectedErrorMessage = "Batch update failed. Please try again."

                // --- MODIFICATION: Mock failure ---
                whenever(transactionRepository.updateDescriptionForIds(any(), anyString()))
                    .thenThrow(RuntimeException("Database failed!"))

                // ACT & ASSERT
                viewModel.uiEvent.test {
                    viewModel.performBatchUpdate()

                    // Note: advanceUntilIdle() is not needed here because the testDispatcher is Unconfined
                    // and the coroutine will execute eagerly.

                    // Verify error event
                    assertEquals(expectedErrorMessage, awaitItem())

                    // Verify state is still dismissed (due to finally block)
                    assertNull("Sheet should be dismissed even on failure", viewModel.retroUpdateSheetState.value)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `requestCategoryChange updates flow`() =
            runTest {
                // Arrange
                val transaction = Transaction(id = 1, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null)
                val mockDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)

                // Act & Assert
                viewModel.transactionForCategoryChange.test {
                    assertNull("Initial state should be null", awaitItem())
                    viewModel.requestCategoryChange(mockDetails)
                    assertEquals("State should be updated with details", mockDetails, awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `cancelCategoryChange resets flow to null`() =
            runTest {
                // Arrange
                val transaction = Transaction(id = 1, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null)
                val mockDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)

                // Act & Assert
                viewModel.transactionForCategoryChange.test {
                    assertNull("Initial state should be null", awaitItem())

                    viewModel.requestCategoryChange(mockDetails)
                    assertEquals("State should be updated with details", mockDetails, awaitItem())

                    viewModel.cancelCategoryChange()
                    assertNull("State should be reset to null", awaitItem())

                    cancelAndIgnoreRemainingEvents()
                }
            }

    private fun setupUniqueRenameScenario(
            initialDesc: String,
            currentDesc: String,
            originalDescription: String = initialDesc,
        ) = runTest {
            val initialTxn =
                Transaction(
                    id = 1,
                    description = initialDesc,
                    categoryId = 1,
                    amount = 10.0,
                    date = 0L,
                    accountId = 1,
                    notes = null,
                    originalDescription = originalDescription,
                )
            val currentTxn = initialTxn.copy(description = currentDesc)

            whenever(transactionRepository.getTransactionById(1))
                .thenReturn(flowOf(initialTxn), flowOf(currentTxn))
            // No similar transactions exist for this merchant
            whenever(transactionRepository.findSimilarTransactions(originalDescription, 1))
                .thenReturn(emptyList())
            whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
            whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

            viewModel.loadTransactionForDetailScreen(1)
            advanceUntilIdle()
        }

    @Test
        fun `onAttemptToLeaveScreen triggers sheet when no similar transactions exist`() =
            runTest {
                // ARRANGE: "Annual Maintenance" renamed from "Water charges", no similar txns
                setupUniqueRenameScenario(
                    initialDesc = "Water charges",
                    currentDesc = "Annual Maintenance",
                    originalDescription = "Water charges",
                )

                var hasNavigated = false

                // ACT
                viewModel.onAttemptToLeaveScreen { hasNavigated = true }
                advanceUntilIdle()

                // ASSERT: Navigation should NOT happen immediately
                assertFalse("Should not navigate when renaming even with no similar txns", hasNavigated)

                // ASSERT: Sheet should be shown with empty list
                val sheetState = viewModel.retroUpdateSheetState.value
                assertNotNull("Sheet state should not be null", sheetState)
                assertEquals(0, sheetState?.similarTransactions?.size)
                assertEquals(true, sheetState?.updateFutureTransactions)

                // Rule should NOT be saved yet
                verify(merchantRenameRuleRepository, never()).insert(any())
            }

    @Test
        fun `onAttemptToLeaveScreen filters out similar transactions that already match target`() =
            runTest {
                // ARRANGE:
                val initialTxn = Transaction(id = 1, description = "Swiggy", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Metro", notes = null)
                val currentTxn = initialTxn.copy(description = "Metro") // Reverting name

                whenever(transactionRepository.getTransactionById(1))
                    .thenReturn(flowOf(initialTxn), flowOf(currentTxn))

                // Return two similar transactions: one already "Metro", one "Swiggy"
                val similarTxn1 = Transaction(id = 2, description = "Metro", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Metro", notes = null)
                val similarTxn2 = Transaction(id = 3, description = "Swiggy", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, originalDescription = "Metro", notes = null)

                whenever(transactionRepository.findSimilarTransactions("Metro", 1))
                    .thenReturn(listOf(similarTxn1, similarTxn2))

                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                advanceUntilIdle()

                // ACT
                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                // ASSERT
                val sheetState = viewModel.retroUpdateSheetState.value
                assertNotNull(sheetState)
                // Should only include the one that DOES NOT already match "Metro"
                assertEquals(1, sheetState?.similarTransactions?.size)
                assertEquals("Swiggy", sheetState?.similarTransactions?.first()?.description)
            }

    @Test
        fun `toggleUpdateFutureTransactions flips boolean state`() =
            runTest {
                // ARRANGE: setup a state
                setupUniqueRenameScenario(
                    initialDesc = "Water charges",
                    currentDesc = "Annual Maintenance",
                    originalDescription = "Water charges",
                )
                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                val initialState = viewModel.retroUpdateSheetState.value?.updateFutureTransactions
                assertEquals(true, initialState)

                // ACT
                viewModel.toggleUpdateFutureTransactions()

                // ASSERT
                assertEquals(false, viewModel.retroUpdateSheetState.value?.updateFutureTransactions)
            }

    @Test
        fun `onAttemptToLeaveScreen allows navigation and saves no rule when description unchanged`() =
            runTest {
                // ARRANGE: No description change at all
                val txn =
                    Transaction(
                        id = 1,
                        description = "Starbucks",
                        categoryId = 1,
                        amount = 10.0,
                        date = 0L,
                        accountId = 1,
                        notes = null,
                        originalDescription = "Starbucks",
                    )
                whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(txn))
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                advanceUntilIdle()

                var hasNavigated = false

                // ACT
                viewModel.onAttemptToLeaveScreen { hasNavigated = true }
                advanceUntilIdle()

                // ASSERT
                assertTrue("Should navigate when nothing changed", hasNavigated)
                assertNull(viewModel.retroUpdateSheetState.value)
                verify(merchantRenameRuleRepository, never()).insert(any())
                verify(merchantRenameRuleRepository, never()).deleteByOriginalName(anyString())
            }

    @Test
        fun `onAttemptToLeaveScreen sets totalMatchingCount to similar size plus one`() =
            runTest {
                // ARRANGE: 3 similar transactions
                val initialTxn =
                    Transaction(
                        id = 1,
                        description = "Merchant",
                        categoryId = 1,
                        amount = 10.0,
                        date = 0L,
                        accountId = 1,
                        notes = null,
                        originalDescription = "Merchant",
                    )
                val currentTxn = initialTxn.copy(description = "Renamed Merchant")
                val similarTxns =
                    (2..4).map {
                        Transaction(id = it, description = "Merchant", categoryId = 1, amount = 5.0, date = 0L, accountId = 1, notes = null)
                    }

                whenever(transactionRepository.getTransactionById(1))
                    .thenReturn(flowOf(initialTxn), flowOf(currentTxn))
                whenever(transactionRepository.findSimilarTransactions("Merchant", 1))
                    .thenReturn(similarTxns)
                whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
                whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
                whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

                viewModel.loadTransactionForDetailScreen(1)
                viewModel.onAttemptToLeaveScreen { }
                advanceUntilIdle()

                // ASSERT
                val sheetState = viewModel.retroUpdateSheetState.value
                assertNotNull(sheetState)
                // 3 similar + 1 currently edited = 4
                assertEquals(4, sheetState?.totalMatchingCount)
                assertEquals(3, sheetState?.similarTransactions?.size)
            }

        // =========================================================================
        // --- NEW TESTS: Cross-Account Canonical Nudge (Layer B) ---
        // =========================================================================

        /**
         * Helper that wires up a performBatchUpdate scenario that will trigger
         * findCrossAccountVariants (isAllSelected=true, rule saved).
         */

    private fun setupBatchUpdateAllSelected(
            originalDesc: String = "SWIGGY INFOTECH",
            canonicalName: String = "Swiggy",
        ) = runTest {
            setupRetroSheet(newDesc = canonicalName, numSimilar = 1)
            // After setupRetroSheet: selectedIds={2}, totalMatchingCount=2 → (1+1)==2 → isAllSelected=true
            whenever(transactionRepository.getDistinctOriginalDescriptions())
                .thenReturn(emptyList()) // default: no variants
            whenever(transactionRepository.getTransactionIdsByOriginalDescription(anyString()))
                .thenReturn(emptyList())
            whenever(merchantRenameRuleRepository.getAliasesAsMap())
                .thenReturn(flowOf(mapOf(originalDesc.lowercase() to canonicalName)))
        }

    @Test
        fun `performBatchUpdate emits navigateBackEvent when no cross-account variants found`() =
            runTest {
                setupBatchUpdateAllSelected()

                viewModel.navigateBackEvent.test {
                    viewModel.performBatchUpdate()
                    advanceUntilIdle()

                    // Should receive navigation event because no variants were found
                    awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
                assertNull("Canonical nudge should remain null", viewModel.canonicalNudgeState.value)
            }

    @Test
        fun `performBatchUpdate populates canonicalNudgeState when cross-account variants exist`() =
            runTest {
                setupBatchUpdateAllSelected(originalDesc = "SWIGGY INFOTECH", canonicalName = "Swiggy")

                // Override: one cross-account variant exists that hasn't been renamed yet
                whenever(transactionRepository.getDistinctOriginalDescriptions())
                    .thenReturn(listOf("SWIGGY INFOTECH", "SWIGGY INDIA"))
                whenever(merchantRenameRuleRepository.getAliasesAsMap())
                    .thenReturn(flowOf(mapOf("swiggy infotech" to "Swiggy")))
                whenever(transactionRepository.getTransactionIdsByOriginalDescription("SWIGGY INDIA"))
                    .thenReturn(listOf(10, 11, 12))

                viewModel.performBatchUpdate()
                advanceUntilIdle()

                // Should show the nudge, NOT navigate immediately
                val nudge = viewModel.canonicalNudgeState.value
                assertNotNull("Canonical nudge should be shown", nudge)
                assertEquals("Swiggy", nudge?.canonicalName)
                assertEquals(1, nudge?.variants?.size)
                assertEquals("SWIGGY INDIA", nudge?.variants?.first()?.rawName)
                assertEquals(3, nudge?.variants?.first()?.transactionCount)
                // All variants pre-selected by default
                assertEquals(setOf("SWIGGY INDIA"), nudge?.selectedRawNames)
            }

    @Test
        fun `toggleCanonicalVariant adds and removes variant from selection`() =
            runTest {
                setupBatchUpdateAllSelected()
                whenever(transactionRepository.getDistinctOriginalDescriptions())
                    .thenReturn(listOf("SWIGGY INFOTECH", "SWIGGY INDIA"))
                whenever(merchantRenameRuleRepository.getAliasesAsMap())
                    .thenReturn(flowOf(mapOf("swiggy infotech" to "Swiggy")))
                whenever(transactionRepository.getTransactionIdsByOriginalDescription("SWIGGY INDIA"))
                    .thenReturn(listOf(10))

                viewModel.performBatchUpdate()
                advanceUntilIdle()

                // Pre-condition: SWIGGY INDIA is selected
                assertEquals(setOf("SWIGGY INDIA"), viewModel.canonicalNudgeState.value?.selectedRawNames)

                // Deselect
                viewModel.toggleCanonicalVariant("SWIGGY INDIA")
                advanceUntilIdle()
                assertEquals(emptySet<String>(), viewModel.canonicalNudgeState.value?.selectedRawNames)

                // Re-select
                viewModel.toggleCanonicalVariant("SWIGGY INDIA")
                advanceUntilIdle()
                assertEquals(setOf("SWIGGY INDIA"), viewModel.canonicalNudgeState.value?.selectedRawNames)
            }

    @Test
        fun `confirmCanonicalNudge saves rules and updates DB for selected variants`() =
            runTest {
                setupBatchUpdateAllSelected()
                whenever(transactionRepository.getDistinctOriginalDescriptions())
                    .thenReturn(listOf("SWIGGY INFOTECH", "SWIGGY INDIA"))
                whenever(merchantRenameRuleRepository.getAliasesAsMap())
                    .thenReturn(flowOf(mapOf("swiggy infotech" to "Swiggy")))
                whenever(transactionRepository.getTransactionIdsByOriginalDescription("SWIGGY INDIA"))
                    .thenReturn(listOf(10, 11))

                viewModel.performBatchUpdate()
                advanceUntilIdle()
                assertNotNull(viewModel.canonicalNudgeState.value)

                viewModel.uiEvent.test {
                    viewModel.navigateBackEvent.test {
                        viewModel.confirmCanonicalNudge()
                        advanceUntilIdle()

                        // Rule saved for cross-account variant
                        verify(merchantRenameRuleRepository).insert(
                            MerchantRenameRule(originalName = "SWIGGY INDIA", newName = "Swiggy"),
                        )
                        // Transactions updated
                        verify(transactionRepository).updateDescriptionForIds(listOf(10, 11), "Swiggy")

                        // State cleared
                        assertNull(viewModel.canonicalNudgeState.value)

                        // Navigation triggered
                        awaitItem()
                        cancelAndIgnoreRemainingEvents()
                    }
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `dismissCanonicalNudge clears state and emits navigateBackEvent`() =
            runTest {
                setupBatchUpdateAllSelected()
                whenever(transactionRepository.getDistinctOriginalDescriptions())
                    .thenReturn(listOf("SWIGGY INFOTECH", "SWIGGY INDIA"))
                whenever(merchantRenameRuleRepository.getAliasesAsMap())
                    .thenReturn(flowOf(mapOf("swiggy infotech" to "Swiggy")))
                whenever(transactionRepository.getTransactionIdsByOriginalDescription("SWIGGY INDIA"))
                    .thenReturn(listOf(10))

                viewModel.performBatchUpdate()
                advanceUntilIdle()
                assertNotNull(viewModel.canonicalNudgeState.value)

                viewModel.navigateBackEvent.test {
                    viewModel.dismissCanonicalNudge()
                    advanceUntilIdle()

                    assertNull("Nudge should be dismissed", viewModel.canonicalNudgeState.value)
                    verify(merchantRenameRuleRepository, never()).insert(
                        MerchantRenameRule(originalName = "SWIGGY INDIA", newName = "Swiggy"),
                    )
                    awaitItem() // navigateBackEvent emitted
                    cancelAndIgnoreRemainingEvents()
                }
            }

}
