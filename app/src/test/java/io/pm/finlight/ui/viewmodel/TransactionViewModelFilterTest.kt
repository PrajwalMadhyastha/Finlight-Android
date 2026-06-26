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

class TransactionViewModelFilterTest : TransactionViewModelBaseSetup() {

    @Test
        fun `updateFilterKeyword updates filterState correctly`() =
            runTest {
                // ARRANGE
                val newKeyword = "Coffee"

                // ACT
                viewModel.updateFilterKeyword(newKeyword)

                // ASSERT
                val updatedState = viewModel.filterState.first()
                assertEquals(newKeyword, updatedState.keyword)
            }

    // --- NEW: Filter Logic Tests ---

    @Test
        fun `updateFilterAccount updates filterState correctly`() =
            runTest {
                // ARRANGE
                val newAccount = Account(1, "Test Account", "Bank")

                // ACT
                viewModel.updateFilterAccount(newAccount)

                // ASSERT
                val updatedState = viewModel.filterState.first()
                assertEquals(newAccount, updatedState.account)
            }

    @Test
        fun `updateFilterCategory updates filterState correctly`() =
            runTest {
                // ARRANGE
                val newCategory = Category(1, "Test Category", "icon", "color")

                // ACT
                viewModel.updateFilterCategory(newCategory)

                // ASSERT
                val updatedState = viewModel.filterState.first()
                assertEquals(newCategory, updatedState.category)
            }

    @Test
        fun `updateFilterTransactionType updates filterState correctly`() =
            runTest {
                // ARRANGE
                val newType = AnalysisTransactionType.INCOME

                // ACT
                viewModel.updateFilterTransactionType(newType)

                // ASSERT
                val updatedState = viewModel.filterState.first()
                assertEquals(newType, updatedState.transactionType)
            }

    @Test
        fun `clearFilters resets filterState to default`() =
            runTest {
                // ARRANGE
                viewModel.updateFilterKeyword("test")
                viewModel.updateFilterAccount(Account(1, "Test", "Bank"))
                viewModel.updateFilterCategory(Category(1, "Test", "icon", "color"))
                advanceUntilIdle()

                // Pre-condition check
                val currentState = viewModel.filterState.first()
                assertNotEquals("", currentState.keyword)
                assertNotNull(currentState.account)
                assertNotNull(currentState.category)

                // ACT
                viewModel.clearFilters()
                advanceUntilIdle() // Ensure the state update is processed

                // ASSERT
                val clearedState = viewModel.filterState.first()
                assertEquals("", clearedState.keyword)
                assertNull(clearedState.account)
                assertNull(clearedState.category)
            }

    @Test
        fun `onFilterClick sets showFilterSheet to true`() =
            runTest {
                viewModel.showFilterSheet.test {
                    assertFalse("Sheet should be hidden initially", awaitItem())
                    viewModel.onFilterClick()
                    assertTrue("Sheet should be visible after click", awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `onFilterSheetDismiss sets showFilterSheet to false`() =
            runTest {
                viewModel.showFilterSheet.test {
                    assertFalse("Sheet hidden initially", awaitItem())
                    viewModel.onFilterClick()
                    assertTrue("Sheet visible after click", awaitItem())
                    viewModel.onFilterSheetDismiss()
                    assertFalse("Sheet hidden after dismiss", awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            }

        // --- End of Filter Logic Tests ---

    @Test
        fun `onMerchantSearchQueryChanged triggers merchantPredictions flow`() =
            runTest {
                // Arrange
                val query = "Coffee"
                val mockPredictions =
                    listOf(
                        MerchantPrediction("Coffee Shop", 1, "Food", "icon", "color", null, null),
                    )
                whenever(transactionRepository.searchMerchants(query)).thenReturn(flowOf(mockPredictions))

                // Act & Assert
                viewModel.merchantPredictions.test(timeout = 5.seconds) {
                    assertEquals("Initial state should be empty", emptyList<MerchantPrediction>(), awaitItem())

                    viewModel.onMerchantSearchQueryChanged(query)
                    advanceTimeBy(301) // Wait for debounce

                    assertEquals("Predictions should be emitted", mockPredictions, awaitItem())
                    verify(transactionRepository).searchMerchants(query)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `clearMerchantSearch clears merchantPredictions flow`() =
            runTest {
                // Arrange
                val query = "Coffee"
                val mockPredictions =
                    listOf(
                        MerchantPrediction("Coffee Shop", 1, "Food", "icon", "color", null, null),
                    )
                whenever(transactionRepository.searchMerchants(query)).thenReturn(flowOf(mockPredictions))

                // Act & Assert
                viewModel.merchantPredictions.test(timeout = 5.seconds) {
                    assertEquals("Initial state should be empty", emptyList<MerchantPrediction>(), awaitItem())

                    // Set a query first
                    viewModel.onMerchantSearchQueryChanged(query)
                    advanceTimeBy(301) // Wait for debounce
                    assertEquals("Predictions should be emitted", mockPredictions, awaitItem())

                    // Now clear it
                    viewModel.clearMerchantSearch()
                    advanceTimeBy(301) // Wait for debounce (query is now "")

                    assertEquals("Predictions should be cleared", emptyList<MerchantPrediction>(), awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            }

}
