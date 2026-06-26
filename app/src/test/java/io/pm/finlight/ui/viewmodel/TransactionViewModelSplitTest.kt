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
                verify(transactionDao).unmarkAsSplit(1, "Original Desc", 1)

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
