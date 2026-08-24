package io.pm.finlight.ui.viewmodel

import androidx.room.withTransaction
import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelSplitTest : TransactionViewModelBaseSetup() {
    @Test
    fun `unsplitTransaction correctly calls DAO methods`() =
        runTest {
            // ARRANGE
            mockkStatic("androidx.room.RoomDatabaseKt")

            // Use a more robust mock for withTransaction that doesn't rely on complex arg matching if possible
            coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
                val block = secondArg<suspend () -> Any?>()
                block()
            }

            val transaction =
                Transaction(id = 1, description = "Split", isSplit = true, originalDescription = "Original Desc", amount = 100.0, date = 0L, accountId = 1, categoryId = null, notes = null)
            val splits = listOf(SplitTransactionDetails(SplitTransaction(1, 1, 50.0, 1, null), "Cat1", "", ""))

            whenever(splitTransactionDao.getSplitsForParentSimple(1)).thenReturn(splits)

            // ACT
            viewModel.unsplitTransaction(transaction)
            advanceUntilIdle()

            // ASSERT
            verify(splitTransactionDao).deleteSplitsForParent(1)
            verify(transactionWriteDao).unmarkAsSplit(1, "Original Desc", 1)

            unmockkStatic("androidx.room.RoomDatabaseKt")
        }

    // --- NEW: Test for getSplitDetailsForTransaction ---

    @Test
    fun `getSplitDetailsForTransaction calls repository`() =
        runTest {
            // Arrange
            val transactionId = 1
            val mockSplits =
                listOf(
                    SplitTransactionDetails(SplitTransaction(1, 1, 50.0, 1, null), "Cat1", "", ""),
                )
            whenever(splitTransactionRepository.getSplitsForParent(transactionId)).thenReturn(flowOf(mockSplits))

            // Act
            val resultFlow = viewModel.getSplitDetailsForTransaction(transactionId)

            // Assert
            resultFlow.test {
                assertEquals("Flow should emit repository data", mockSplits, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(splitTransactionRepository).getSplitsForParent(transactionId)
        }
}
