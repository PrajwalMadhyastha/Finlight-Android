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

class TransactionViewModelMiscTest : TransactionViewModelBaseSetup() {

    // --- NEW: Simple Update Function Tests (Success Path) ---

    @Test
        fun `updateTransactionAmount calls repository successfully`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newAmountStr = "123.45"
                val newAmountDouble = 123.45

                // Act
                viewModel.updateTransactionAmount(transactionId, newAmountStr)
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateAmount(transactionId, newAmountDouble)
            }

    @Test
        fun `updateTransactionAmount shows validation error for amount exceeding limit`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newAmountStr = "2000000000.0"

                // Act
                viewModel.updateTransactionAmount(transactionId, newAmountStr)
                advanceUntilIdle()

                // Assert
                viewModel.validationError.test {
                    assertEquals("Maximum limit of 1 Billion (1,000,000,000) reached.", awaitItem())
                }
                verify(transactionRepository, never()).updateAmount(any(), any())
            }

    @Test
        fun `updateTransactionNotes calls repository successfully`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newNotes = "This is a test note"

                // Act
                viewModel.updateTransactionNotes(transactionId, newNotes)
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateNotes(transactionId, newNotes)
            }

    @Test
        fun `updateTransactionCategory calls repository and mapping logic`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newCategoryId = 5
                val originalDescription = "Original Merchant"
                val transaction =
                    Transaction(
                        id = transactionId,
                        description = "Test",
                        amount = 1.0,
                        date = 0,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                        originalDescription = originalDescription,
                    )

                whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(transaction))

                // Act
                viewModel.updateTransactionCategory(transactionId, newCategoryId)
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateCategoryId(transactionId, newCategoryId)
                // Verify learning logic is also called
                verify(merchantCategoryMappingRepository).insert(
                    MerchantCategoryMapping(
                        parsedName = originalDescription,
                        categoryId = newCategoryId,
                    ),
                )
            }

    @Test
        fun `updateTransactionAccount calls repository successfully`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newAccountId = 2

                // Act
                viewModel.updateTransactionAccount(transactionId, newAccountId)
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateAccountId(transactionId, newAccountId)
            }

    @Test
        fun `updateTransactionDate calls repository successfully`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newDate = 123456789L

                // Act
                viewModel.updateTransactionDate(transactionId, newDate)
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateDate(transactionId, newDate)
            }

    @Test
        fun `updateTransactionExclusion calls repository successfully`() =
            runTest {
                // Arrange
                val transactionId = 1
                val newExclusionStatus = true

                // Act
                viewModel.updateTransactionExclusion(transactionId, newExclusionStatus)
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateExclusionStatus(transactionId, newExclusionStatus)
            }

        // --- NEW TESTS FOR ERROR HANDLING ---

    // --- NEW: Simple State Update Tests ---

    // --- NEW: Tests for Merchant Search ---

    // --- NEW: Tests for Category Change Request ---

    // --- NEW: Tests for functions requested in the last prompt ---

    // --- NEW: Tests for Quick Fill feature (new code coverage) ---

    @Test
        fun `updateTransactionAmount does nothing when amountStr is not a valid number`() =
            runTest {
                // Arrange - non-parseable string
                val transactionId = 1

                // Act
                viewModel.updateTransactionAmount(transactionId, "not_a_number")
                advanceUntilIdle()

                // Assert — repository must NOT be called since toDoubleOrNull() returns null
                verify(transactionRepository, never()).updateAmount(any(), any())
            }

    @Test
        fun `updateTransactionAmount does nothing when amount is zero or negative`() =
            runTest {
                // Arrange - zero amount (not > 0)
                val transactionId = 1

                // Act
                viewModel.updateTransactionAmount(transactionId, "0.0")
                advanceUntilIdle()

                // Assert — repository must NOT be called since 0.0 is not > 0
                verify(transactionRepository, never()).updateAmount(any(), any())
            }

}
