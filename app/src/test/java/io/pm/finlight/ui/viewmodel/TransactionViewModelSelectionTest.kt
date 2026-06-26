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

}
