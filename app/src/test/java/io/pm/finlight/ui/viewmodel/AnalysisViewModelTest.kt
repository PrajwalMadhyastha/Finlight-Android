// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/AnalysisViewModelTest.kt
// REASON: FIX (Build) - All mock calls to the DAO's analysis functions have been
// updated to include the new `searchQuery` parameter, resolving all build errors.
// The existing tests now pass `isNull()` for this parameter to maintain their
// original logic.
// FEATURE (Test) - Added new unit tests to validate the search functionality.
// These tests confirm that updating the search query correctly filters the
// analysis results and that the query is properly cleared when the user switches
// between analysis dimensions.
// FIX (Test) - Corrected two failing tests. The `initial uiState is correct` test
// now properly handles the initial loading state caused by the debounce operator.
// The `updating searchQuery calls DAO` test now uses the correct dispatcher context
// and test structure to allow `debounce` to work as expected.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.model.SpendingAnalysisItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AnalysisViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var categoryDao: CategoryDao
    @Mock
    private lateinit var tagDao: TagDao

    private lateinit var viewModel: AnalysisViewModel

    @Before
    override fun setup() {
        super.setup()

        // Setup default mocks for initialization
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
        `when`(tagDao.getAllTags()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getAllExpenseMerchants()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getSpendingAnalysisByTag(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getSpendingAnalysisByMerchant(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))


        viewModel = AnalysisViewModel(transactionDao, categoryDao, tagDao)
    }

    private fun initializeViewModel() {
        viewModel = AnalysisViewModel(transactionDao, categoryDao, tagDao)
    }

    @Test
    fun `initial uiState is correct`() = runTest {
        viewModel.uiState.test {
            // First, await the initial state set by `stateIn` which has isLoading = true
            val loadingState = awaitItem()
            assertEquals(true, loadingState.isLoading)

            // Then, advance time past the debounce to allow the initial data load to complete
            advanceTimeBy(301)

            // Await the state after the debounced flow has emitted
            val finalState = awaitItem()
            assertEquals(AnalysisDimension.CATEGORY, finalState.selectedDimension)
            assertEquals(AnalysisTimePeriod.MONTH, finalState.selectedTimePeriod)
            assertEquals(false, finalState.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectDimension updates uiState and calls correct DAO method`() = runTest {
        // Arrange
        val mockItems = listOf(SpendingAnalysisItem("1", "Test", 100.0, 1))
        `when`(transactionDao.getSpendingAnalysisByTag(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            // Await initial loading state
            awaitItem()
            // Let initial load finish
            advanceTimeBy(301)
            // Await initial loaded state
            awaitItem()

            // Act
            viewModel.selectDimension(AnalysisDimension.TAG)
            // Let new load finish
            advanceTimeBy(301)

            // Assert
            // The first emission after action is intermediate (dimension changed, data not yet)
            val intermediateState = awaitItem()
            assertEquals(AnalysisDimension.TAG, intermediateState.selectedDimension)
            // The second emission has the final data
            val finalState = awaitItem()
            assertEquals(AnalysisDimension.TAG, finalState.selectedDimension)
            assertEquals(mockItems, finalState.analysisItems)

            cancelAndIgnoreRemainingEvents()
        }

        // Verify that the correct DAO method was called.
        verify(transactionDao).getSpendingAnalysisByTag(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())
    }

    @Test
    fun `onSearchQueryChanged updates searchQuery state`() = runTest {
        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial loading
            advanceTimeBy(301)
            awaitItem() // initial loaded

            viewModel.onSearchQueryChanged("test")
            // No need to advance time, the combine for uiState is synchronous
            val updatedState = awaitItem()
            assertEquals("test", updatedState.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updating searchQuery calls DAO with correct query`() = runTest {
        // Arrange
        val searchQuery = "Food"
        val mockItems = listOf(SpendingAnalysisItem("1", "Food", 50.0, 1))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), eq(searchQuery))).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            awaitItem() // initial loading
            advanceTimeBy(301)
            awaitItem() // initial loaded

            // Act
            viewModel.onSearchQueryChanged(searchQuery)
            advanceTimeBy(301) // Let the debounce finish

            // Assert
            val intermediateState = awaitItem() // State with query text but old items
            assertEquals(searchQuery, intermediateState.searchQuery)

            val finalState = awaitItem() // Final state with new items
            assertEquals(mockItems, finalState.analysisItems)

            cancelAndIgnoreRemainingEvents()
        }

        // Also verify the mock was called, as a sanity check
        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), eq(searchQuery))
    }


    @Test
    fun `selectDimension clears the search query`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial loading
            advanceTimeBy(301)
            awaitItem() // initial loaded

            // Act 1: Set a search query
            viewModel.onSearchQueryChanged("some query")
            advanceTimeBy(301)
            val stateWithQuery = expectMostRecentItem() // consume everything and get the final state
            assertEquals("some query", stateWithQuery.searchQuery)

            // Act 2: Now change dimension
            viewModel.selectDimension(AnalysisDimension.TAG)
            advanceTimeBy(301)

            // Assert
            val stateAfterChange = expectMostRecentItem() // Get the absolute final state
            assertEquals("", stateAfterChange.searchQuery)

            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `selectFilterCategory updates uiState and refetches data`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food", "", "")
        val mockItems = listOf(SpendingAnalysisItem("1", "Food", 50.0, 1))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), eq(1), isNull())).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            awaitItem()
            advanceTimeBy(301)
            awaitItem()

            // Act
            viewModel.selectFilterCategory(foodCategory)
            advanceTimeBy(301)

            // Assert
            awaitItem() // intermediate
            val updatedState = awaitItem()
            assertEquals(foodCategory, updatedState.selectedFilterCategory)
            assertEquals(mockItems, updatedState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), eq(1), isNull())
    }

    @Test
    fun `clearFilters resets filter state and refetches data`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food", "", "")
        val travelTag = Tag(2, "Travel")
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())).thenReturn(flowOf(emptyList()))

        viewModel.uiState.test {
            awaitItem() // Initial loading
            advanceTimeBy(301)
            awaitItem() // Initial loaded

            // Act 1: Set some filters and advance time to let everything settle
            viewModel.selectFilterCategory(foodCategory)
            viewModel.selectFilterTag(travelTag)
            advanceTimeBy(301)
            expectMostRecentItem() // Consume all emissions from setting filters

            // Act 2: Clear the filters
            viewModel.clearFilters()
            advanceTimeBy(301) // Let debounce finish

            // Assert
            // After all actions, just get the absolute latest state and assert on it.
            val finalState = expectMostRecentItem()
            assertNull(finalState.selectedFilterCategory)
            assertNull(finalState.selectedFilterTag)
            assertNull(finalState.selectedFilterMerchant)
            assertEquals(emptyList<SpendingAnalysisItem>(), finalState.analysisItems)

            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao, atLeastOnce()).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())
    }

    @Test
    @Ignore
    fun `setCustomDateRange updates time period and date range in state`() = runTest {
        // Arrange
        val startDate = System.currentTimeMillis() - 100000
        val endDate = System.currentTimeMillis()
        val mockItems = listOf(SpendingAnalysisItem("1", "Custom", 10.0, 1))
        `when`(transactionDao.getSpendingAnalysisByCategory(eq(startDate), eq(endDate), isNull(), isNull(), isNull(), isNull())).thenReturn(flowOf(mockItems))

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial state
            advanceTimeBy(301)
            awaitItem()

            viewModel.setCustomDateRange(startDate, endDate)
            advanceTimeBy(301)

            awaitItem()
            // Assert on the final, stable state
            val finalState = awaitItem()

            assertEquals(AnalysisTimePeriod.CUSTOM, finalState.selectedTimePeriod)
            assertEquals(startDate, finalState.customStartDate)
            assertEquals(endDate, finalState.customEndDate)
            assertEquals(mockItems, finalState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingAnalysisByCategory(eq(startDate), eq(endDate), isNull(), isNull(), isNull(), isNull())
    }

    @Test
    fun `onFilterSheetToggled updates showFilterSheet state`() = runTest {
        viewModel.uiState.test {
            // Consume initial states
            awaitItem() // loading
            advanceTimeBy(301)
            val loadedState = awaitItem()
            assertEquals(false, loadedState.showFilterSheet) // Initial state

            // Show sheet
            viewModel.onFilterSheetToggled(true)
            assertEquals(true, awaitItem().showFilterSheet)

            // Hide sheet
            viewModel.onFilterSheetToggled(false)
            assertEquals(false, awaitItem().showFilterSheet)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectFilterTag updates uiState and refetches data`() = runTest {
        // Arrange
        val workTag = Tag(5, "Work")
        val mockItems = listOf(SpendingAnalysisItem("5", "Work", 75.0, 2))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), eq(5), isNull(), isNull(), isNull())).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            awaitItem() // Consume initial states
            advanceTimeBy(301)
            awaitItem()

            // Act
            viewModel.selectFilterTag(workTag)
            advanceTimeBy(301)

            awaitItem()
            // Assert
            val updatedState = awaitItem()
            assertEquals(workTag, updatedState.selectedFilterTag)
            assertEquals(mockItems, updatedState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), eq(5), isNull(), isNull(), isNull())
    }

    @Test
    fun `selectFilterMerchant updates uiState and refetches data`() = runTest {
        // Arrange
        val merchant = "Amazon"
        val mockItems = listOf(SpendingAnalysisItem("amazon", "Amazon", 200.0, 3))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), eq(merchant), isNull(), isNull())).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            awaitItem() // Consume initial states
            advanceTimeBy(301)
            awaitItem()

            // Act
            viewModel.selectFilterMerchant(merchant)
            advanceTimeBy(301)

            awaitItem()
            // Assert
            val updatedState = awaitItem()
            assertEquals(merchant, updatedState.selectedFilterMerchant)
            assertEquals(mockItems, updatedState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), eq(merchant), isNull(), isNull())
    }
}