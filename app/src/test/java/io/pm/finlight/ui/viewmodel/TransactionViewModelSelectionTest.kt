package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import io.pm.finlight.ui.components.ShareableField
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.lang.RuntimeException
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelSelectionTest : TransactionViewModelBaseSetup() {
    @Test
    fun `deleteTransaction failure sends uiEvent`() =
        runTest {
            // ARRANGE
            val transactionToDelete =
                Transaction(id = 1, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null)
            val errorMessage = "Failed to delete transaction. Please try again."
            whenever(transactionRepository.delete(any())).thenThrow(RuntimeException("DB delete failed"))

            // ACT & ASSERT
            viewModel.uiEvent.test {
                viewModel.deleteTransaction(transactionToDelete)
                advanceUntilIdle()
                assertEquals(errorMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onConfirmDeleteSelection failure sends uiEvent`() =
        runTest {
            // ARRANGE
            val errorMessage = "Failed to delete transactions. Please try again."
            whenever(transactionRepository.deleteByIds(any())).thenThrow(RuntimeException("DB batch delete failed"))

            viewModel.enterSelectionMode(1) // Set up the state for deletion
            advanceUntilIdle()

            // ACT & ASSERT
            viewModel.uiEvent.test {
                viewModel.onConfirmDeleteSelection()
                advanceUntilIdle()
                assertEquals(errorMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- NEW: Selection Mode Tests ---

    @Test
    fun `enterSelectionMode activates selection mode and selects initial transaction`() =
        runTest {
            // Assert initial state
            assertFalse("Selection mode should be inactive initially", viewModel.isSelectionModeActive.value)
            assertTrue("Selected IDs should be empty initially", viewModel.selectedTransactionIds.value.isEmpty())

            // Act
            viewModel.enterSelectionMode(initialTransactionId = 1)
            advanceUntilIdle() // Ensure coroutine in VM completes

            // Assert final state
            assertTrue("Selection mode should be active", viewModel.isSelectionModeActive.value)
            assertEquals(setOf(1), viewModel.selectedTransactionIds.value)
        }

    @Test
    fun `toggleTransactionSelection adds and removes ids correctly`() =
        runTest {
            viewModel.selectedTransactionIds.test {
                // Initial state
                assertTrue("Selected IDs should be empty initially", awaitItem().isEmpty())

                // Act: Add first item
                viewModel.toggleTransactionSelection(1)
                assertEquals(setOf(1), awaitItem())

                // Act: Add second item
                viewModel.toggleTransactionSelection(2)
                assertEquals(setOf(1, 2), awaitItem())

                // Act: Remove first item
                viewModel.toggleTransactionSelection(1)
                assertEquals(setOf(2), awaitItem())

                // Act: Remove second item
                viewModel.toggleTransactionSelection(2)
                assertTrue("Should be empty again", awaitItem().isEmpty())
            }
        }

    @Test
    fun `clearSelectionMode deactivates mode and clears selection`() =
        runTest {
            // Arrange: Start in selection mode
            viewModel.enterSelectionMode(1)
            viewModel.toggleTransactionSelection(2)
            advanceUntilIdle()
            assertTrue("Pre-condition: Selection mode should be active", viewModel.isSelectionModeActive.value)
            assertEquals("Pre-condition: 2 items selected", setOf(1, 2), viewModel.selectedTransactionIds.value)

            // Act
            viewModel.clearSelectionMode()
            advanceUntilIdle()

            // Assert
            assertFalse("Selection mode should be inactive", viewModel.isSelectionModeActive.value)
            assertTrue("Selected IDs should be empty", viewModel.selectedTransactionIds.value.isEmpty())
        }

    @Test
    fun `onDeleteSelectionClick shows delete confirmation dialog`() =
        runTest {
            viewModel.showDeleteConfirmation.test {
                // Initial state
                assertFalse("Dialog should be hidden initially", awaitItem())

                // Act
                viewModel.onDeleteSelectionClick()
                advanceUntilIdle()

                // Assert
                assertTrue("Dialog should be visible", awaitItem())
            }
        }

    @Test
    fun `onConfirmDeleteSelection calls repository and clears selection`() =
        runTest {
            // Arrange
            val idsToDelete = setOf(1, 2)
            viewModel.enterSelectionMode(1)
            viewModel.toggleTransactionSelection(2)
            viewModel.onDeleteSelectionClick() // Show dialog
            advanceUntilIdle()

            // Pre-conditions
            assertTrue(viewModel.isSelectionModeActive.value)
            assertTrue(viewModel.showDeleteConfirmation.value)
            assertEquals(idsToDelete, viewModel.selectedTransactionIds.value)

            // Act
            viewModel.onConfirmDeleteSelection()
            advanceUntilIdle() // Let the coroutine finish

            // Assert
            // Verify the repository was called with the correct list
            verify(transactionRepository).deleteByIds(eq(idsToDelete.toList()))

            // Verify state is reset
            assertFalse("Selection mode should be inactive", viewModel.isSelectionModeActive.value)
            assertFalse("Dialog should be hidden", viewModel.showDeleteConfirmation.value)
            assertTrue("Selected IDs should be empty", viewModel.selectedTransactionIds.value.isEmpty())
        }

    @Test
    fun `onCancelDeleteSelection hides delete confirmation dialog`() =
        runTest {
            // Arrange
            viewModel.onDeleteSelectionClick() // Show dialog
            advanceUntilIdle()
            assertTrue("Pre-condition: Dialog should be visible", viewModel.showDeleteConfirmation.value)

            // Act
            viewModel.onCancelDeleteSelection()
            advanceUntilIdle()

            // Assert
            assertFalse("Dialog should be hidden", viewModel.showDeleteConfirmation.value)
        }

    // --- NEW: Share Logic Tests ---

    @Test
    fun `onShareClick sets showShareSheet to true`() =
        runTest {
            viewModel.showShareSheet.test {
                assertFalse("Sheet should be hidden initially", awaitItem())
                viewModel.onShareClick()
                assertTrue("Sheet should be visible after click", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onShareSheetDismiss sets showShareSheet to false`() =
        runTest {
            viewModel.showShareSheet.test {
                assertFalse("Sheet hidden initially", awaitItem())
                viewModel.onShareClick()
                assertTrue("Sheet visible after click", awaitItem())
                viewModel.onShareSheetDismiss()
                assertFalse("Sheet hidden after dismiss", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onShareableFieldToggled adds and removes fields from state`() =
        runTest {
            viewModel.shareableFields.test {
                val defaultFields = awaitItem()
                assertTrue("Default fields should contain Amount", defaultFields.contains(ShareableField.Amount))

                // Act 1: Remove Amount
                viewModel.onShareableFieldToggled(ShareableField.Amount)
                val fieldsAfterRemove = awaitItem()
                assertFalse("Fields should not contain Amount after remove", fieldsAfterRemove.contains(ShareableField.Amount))
                assertEquals(defaultFields.size - 1, fieldsAfterRemove.size)

                // Act 2: Add Amount back
                viewModel.onShareableFieldToggled(ShareableField.Amount)
                val fieldsAfterAdd = awaitItem()
                assertTrue("Fields should contain Amount after add", fieldsAfterAdd.contains(ShareableField.Amount))
                assertEquals(defaultFields.size, fieldsAfterAdd.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canManualMerge returns true for same-account selection`() =
        runTest {
            val t1 = Transaction(id = 1, amount = 100.0, date = 0L, accountId = 1, categoryId = 1, description = "Anchor", transactionType = "expense", notes = null)
            val t2 = Transaction(id = 2, amount = 50.0, date = 0L, accountId = 1, categoryId = 1, description = "Child", transactionType = "expense", notes = null)
            // Mock getTransactionDetailsForRange flow
            whenever(transactionRepository.getTransactionDetailsForRange(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull())).thenReturn(
                kotlinx.coroutines.flow.flowOf(
                    listOf(
                        io.pm.finlight.TransactionDetails(transaction = t1, images = emptyList(), accountName = "HDFC", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null),
                        io.pm.finlight.TransactionDetails(transaction = t2, images = emptyList(), accountName = "HDFC", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null)
                    )
                )
            )

            // Re-initialize ViewModel so it picks up the mocked flow
            initializeViewModel()

            viewModel.canManualMerge.test {
                // Initial state
                assertFalse(awaitItem())

                // Select t1 and t2
                viewModel.toggleTransactionSelection(1)
                viewModel.toggleTransactionSelection(2)

                assertTrue(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canManualMerge returns true for cross-account selection`() =
        runTest {
            // t1 and t2 are on DIFFERENT accounts — previously this was blocked.
            val t1 = Transaction(id = 1, amount = 500.0, date = 0L, accountId = 1, categoryId = 1, description = "Anchor", transactionType = "expense", notes = null)
            val t2 = Transaction(id = 2, amount = 300.0, date = 0L, accountId = 2, categoryId = 1, description = "Child", transactionType = "expense", notes = null)
            whenever(transactionRepository.getTransactionDetailsForRange(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull())).thenReturn(
                kotlinx.coroutines.flow.flowOf(
                    listOf(
                        io.pm.finlight.TransactionDetails(transaction = t1, images = emptyList(), accountName = "HDFC Savings", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null),
                        io.pm.finlight.TransactionDetails(transaction = t2, images = emptyList(), accountName = "SBI Current", categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null)
                    )
                )
            )

            initializeViewModel()

            viewModel.canManualMerge.test {
                assertFalse("Initial: nothing selected", awaitItem())

                viewModel.toggleTransactionSelection(1)
                viewModel.toggleTransactionSelection(2)

                assertTrue("Cross-account merge should now be permitted", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `confirmManualMerge delegates to repository and clears selection`() =
        runTest {
            val t1 = Transaction(id = 1, amount = 100.0, date = 0L, accountId = 1, categoryId = 1, description = "Anchor", transactionType = "expense", notes = null)
            val t2 = Transaction(id = 2, amount = 50.0, date = 0L, accountId = 1, categoryId = 1, description = "Child", transactionType = "expense", notes = null)

            whenever(transactionRepository.getTransactionDetailsForRange(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull())).thenReturn(
                kotlinx.coroutines.flow.flowOf(
                    listOf(
                        io.pm.finlight.TransactionDetails(transaction = t1, images = emptyList(), accountName = null, categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null),
                        io.pm.finlight.TransactionDetails(transaction = t2, images = emptyList(), accountName = null, categoryName = null, categoryIconKey = null, categoryColorKey = null, tagNames = null)
                    )
                )
            )
            initializeViewModel()

            // Setup selections
            viewModel.toggleTransactionSelection(1)
            viewModel.toggleTransactionSelection(2)
            viewModel.setAnchorTransaction(1)

            // Mock repository call
            whenever(transactionRepository.manualMergeTransactions(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(Unit)

            viewModel.confirmManualMerge()

            org.mockito.kotlin.verify(transactionRepository).manualMergeTransactions(1, listOf(2))

            viewModel.selectedTransactionIds.test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateAnchorDetailsInPlace delegates to repository correctly`() =
        runTest {
            val id = 1
            val desc = "Updated Desc"
            val catId = 2
            val notes = "Updated Notes"

            viewModel.updateAnchorDetailsInPlace(id, desc, catId, notes)
            advanceUntilIdle()

            verify(transactionRepository).updateDescription(eq(id), eq(desc))
            verify(transactionRepository).updateCategoryId(eq(id), eq(catId))
            verify(transactionRepository).updateNotes(eq(id), eq(notes))
        }
}
