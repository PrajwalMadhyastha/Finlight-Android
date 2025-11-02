// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/AnalysisViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.model.SpendingAnalysisItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
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

    // --- FIX: Override dispatcher for this class ---
    private val standardTestDispatcher = StandardTestDispatcher()

    @Before
    override fun setup() {
        // Manually initialize mocks (super.setup() is not called)
        MockitoAnnotations.openMocks(this)
        // Set Main dispatcher to our controllable StandardTestDispatcher
        Dispatchers.setMain(standardTestDispatcher)

        // Setup default mocks for initialization
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
        `when`(tagDao.getAllTags()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getAllExpenseMerchants()).thenReturn(flowOf(emptyList()))

        // --- FIX: Make default mocks specific to avoid conflicts and NPEs ---
        // Default for init (all nulls)
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())).thenReturn(flowOf(emptyList()))
        // Default for search (any string)
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), anyString())).thenReturn(flowOf(emptyList()))
        // Generic fallbacks for other dimensions
        `when`(transactionDao.getSpendingAnalysisByTag(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getSpendingAnalysisByMerchant(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))

        viewModel = AnalysisViewModel(transactionDao, categoryDao, tagDao)
    }

    @After
    override fun tearDown() {
        // Reset Main dispatcher
        Dispatchers.resetMain()
    }


    private fun initializeViewModel() {
        viewModel = AnalysisViewModel(transactionDao, categoryDao, tagDao)
    }

    @Test
    fun `initial uiState is correct`() = runTest(standardTestDispatcher) {
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
    fun `selectDimension updates uiState and calls correct DAO method`() = runTest(standardTestDispatcher) {
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

            // Assert intermediate state (dimension changed, items not yet updated)
            val intermediateState = awaitItem()
            assertEquals(AnalysisDimension.TAG, intermediateState.selectedDimension)

            // Let new load finish
            advanceTimeBy(301)

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
    fun `onSearchQueryChanged updates searchQuery state`() = runTest(standardTestDispatcher) {
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
    fun `updating searchQuery calls DAO with correct query`() = runTest(standardTestDispatcher) {
        // Arrange
        val searchQuery = "Food"
        val mockItems = listOf(SpendingAnalysisItem("1", "Food", 50.0, 1))
        // This specific mock will be used instead of the default `anyString()` in setup()
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), eq(searchQuery))).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            awaitItem() // initial loading
            advanceTimeBy(301)
            awaitItem() // initial loaded

            // Act
            viewModel.onSearchQueryChanged(searchQuery)

            // Assert intermediate state (query text updated, items not yet)
            val intermediateState = awaitItem()
            assertEquals(searchQuery, intermediateState.searchQuery)

            // Advance time for debounce
            advanceTimeBy(301)

            // Assert final state (new items loaded)
            val finalState = awaitItem()
            assertEquals(mockItems, finalState.analysisItems)

            cancelAndIgnoreRemainingEvents()
        }

        // Also verify the mock was called, as a sanity check
        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), eq(searchQuery))
    }


    @Test
    fun `selectDimension clears the search query`() = runTest(standardTestDispatcher) {
        // --- FIX: Add mock for the filtered query to return non-empty data ---
        val queriedItems = listOf(SpendingAnalysisItem("1", "Queried", 1.0, 1))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), eq("some query")))
            .thenReturn(flowOf(queriedItems))
        // Mock for the new dimension's (TAG) cleared query
        `when`(transactionDao.getSpendingAnalysisByTag(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(flowOf(emptyList()))

        viewModel.uiState.test {
            awaitItem() // initial loading
            advanceTimeBy(301)
            awaitItem() // initial loaded (items=[])

            // Act 1: Set a search query
            viewModel.onSearchQueryChanged("some query")
            awaitItem() // intermediate state (query = "some query", items=[])
            advanceTimeBy(301)
            val stateWithQuery = awaitItem() // final state with query (query = "some query", items=[...Queried...])

            // Pre-condition check
            assertEquals(queriedItems, stateWithQuery.analysisItems)

            // Act 2: Now change dimension
            viewModel.selectDimension(AnalysisDimension.TAG)

            // Assert intermediate state (dimension changed, query cleared, items not yet updated)
            val stateAfterChange = awaitItem()
            assertEquals("", stateAfterChange.searchQuery)
            assertEquals(AnalysisDimension.TAG, stateAfterChange.selectedDimension)
            assertEquals(queriedItems, stateAfterChange.analysisItems) // Still has old items

            advanceTimeBy(301) // Let debounce for new dimension finish
            val finalState = awaitItem() // final loaded state for new dimension

            // Assert final state
            assertEquals(AnalysisDimension.TAG, finalState.selectedDimension)
            assertEquals("", finalState.searchQuery)
            assertEquals(emptyList<SpendingAnalysisItem>(), finalState.analysisItems) // Now has new (empty) items

            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `selectFilterCategory updates uiState and refetches data`() = runTest(standardTestDispatcher) {
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

            val intermediateState = awaitItem() // intermediate
            assertEquals(foodCategory, intermediateState.selectedFilterCategory)

            advanceTimeBy(301)

            // Assert
            val updatedState = awaitItem()
            assertEquals(foodCategory, updatedState.selectedFilterCategory)
            assertEquals(mockItems, updatedState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), eq(1), isNull())
    }

    @Test
    fun `clearFilters resets filter state and refetches data`() = runTest(standardTestDispatcher) {
        // Arrange
        val foodCategory = Category(1, "Food", "", "")
        val travelTag = Tag(2, "Travel")
        val filteredItems = listOf(SpendingAnalysisItem("1", "Filtered", 1.0, 1))

        // --- FIX: Add mock for the filtered query to return non-empty data ---
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), eq(travelTag.id), isNull(), eq(foodCategory.id), isNull()))
            .thenReturn(flowOf(filteredItems))
        // Mock for the cleared query (already set in @Before, but good to be explicit)
        // This will be chained: first call (init) gets emptyList, second (clearFilters) gets emptyList
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(flowOf(emptyList())) // For init
            .thenReturn(flowOf(emptyList())) // For clearFilters

        viewModel.uiState.test {
            awaitItem() // Initial loading
            advanceTimeBy(301)
            awaitItem() // Initial loaded (items=[])

            // Act 1: Set some filters and advance time to let everything settle
            viewModel.selectFilterCategory(foodCategory)
            awaitItem() // intermediate cat
            viewModel.selectFilterTag(travelTag)
            awaitItem() // intermediate tag
            advanceTimeBy(301)
            val stateWithFilters = awaitItem() // final loaded with filters (items=[...Filtered...])

            // Pre-condition assert
            assertEquals(filteredItems, stateWithFilters.analysisItems)
            assertNotNull(stateWithFilters.selectedFilterCategory)

            // Act 2: Clear the filters
            viewModel.clearFilters()

            // Assert intermediate "cleared" state
            val intermediateState = awaitItem()
            assertNull(intermediateState.selectedFilterCategory)
            assertNull(intermediateState.selectedFilterTag)
            assertEquals(filteredItems, intermediateState.analysisItems) // Still has old items

            advanceTimeBy(301) // Let debounce finish

            // Assert final "cleared" state
            val finalState = awaitItem()
            assertNull(finalState.selectedFilterCategory)
            assertNull(finalState.selectedFilterTag)
            assertNull(finalState.selectedFilterMerchant)
            assertEquals(emptyList<SpendingAnalysisItem>(), finalState.analysisItems) // Now has new (empty) items

            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao, atLeastOnce()).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())
    }

    @Test
    fun `setCustomDateRange updates time period and date range in state`() = runTest(standardTestDispatcher) {
        // Arrange
        val startDate = System.currentTimeMillis() - 100000
        val endDate = System.currentTimeMillis()
        val mockItems = listOf(SpendingAnalysisItem("1", "Custom", 10.0, 1))
        `when`(transactionDao.getSpendingAnalysisByCategory(eq(startDate), eq(endDate), isNull(), isNull(), isNull(), isNull())).thenReturn(flowOf(mockItems))

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial state (isLoading=true)
            advanceTimeBy(301) // Advance for initial debounce
            awaitItem() // initial loaded state (isLoading=false, items=[])

            viewModel.setCustomDateRange(startDate, endDate)

            // This should emit the intermediate state where period/dates are set, but items are still old
            val intermediateState = awaitItem()
            assertEquals(AnalysisTimePeriod.CUSTOM, intermediateState.selectedTimePeriod)
            assertEquals(startDate, intermediateState.customStartDate)
            assertEquals(endDate, intermediateState.customEndDate)

            advanceTimeBy(301) // Advance for the setCustomDateRange debounce

            // This should emit the final state with the new items
            val finalState = awaitItem()
            assertEquals(mockItems, finalState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingAnalysisByCategory(eq(startDate), eq(endDate), isNull(), isNull(), isNull(), isNull())
    }


    @Test
    fun `onFilterSheetToggled updates showFilterSheet state`() = runTest(standardTestDispatcher) {
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
    fun `selectFilterTag updates uiState and refetches data`() = runTest(standardTestDispatcher) {
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

            val intermediateState = awaitItem()
            assertEquals(workTag, intermediateState.selectedFilterTag)

            advanceTimeBy(301)

            // Assert
            val updatedState = awaitItem()
            assertEquals(workTag, updatedState.selectedFilterTag)
            assertEquals(mockItems, updatedState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), eq(5), isNull(), isNull(), isNull())
    }

    @Test
    fun `selectFilterMerchant updates uiState and refetches data`() = runTest(standardTestDispatcher) {
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

            val intermediateState = awaitItem()
            assertEquals(merchant, intermediateState.selectedFilterMerchant)

            advanceTimeBy(301)

            // Assert
            val updatedState = awaitItem()
            assertEquals(merchant, updatedState.selectedFilterMerchant)
            assertEquals(mockItems, updatedState.analysisItems)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), eq(merchant), isNull(), isNull())
    }

    // --- NEW TESTS for selectTimePeriod ---

    @Test
    fun `selectTimePeriod updates uiState and refetches data`() = runTest(standardTestDispatcher) {
        // Arrange
        val mockItems = listOf(SpendingAnalysisItem("1", "Yearly Spend", 500.0, 5))
        // --- FIX: Use chained thenReturn to avoid mock race condition with init ---
        // The first call (from init) gets emptyList. The second (from this test) gets mockItems.
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(flowOf(emptyList())) // For the init (MONTH) call
            .thenReturn(flowOf(mockItems))  // For the YEAR call

        viewModel.uiState.test {
            // 1. Await initial loading state
            awaitItem()
            // 2. Let initial load (MONTH) finish
            advanceTimeBy(301)
            // 3. Await initial loaded state (items=[])
            val initialState = awaitItem()
            assertEquals(AnalysisTimePeriod.MONTH, initialState.selectedTimePeriod)
            // --- FIX: This assertion is now correct ---
            assertEquals(emptyList<SpendingAnalysisItem>(), initialState.analysisItems)

            // 4. Act
            viewModel.selectTimePeriod(AnalysisTimePeriod.YEAR)

            // 5. Await intermediate state (Period changed, data is still old)
            val intermediateState = awaitItem()
            assertEquals(AnalysisTimePeriod.YEAR, intermediateState.selectedTimePeriod)
            assertEquals(emptyList<SpendingAnalysisItem>(), intermediateState.analysisItems) // Still has old data from init

            // 6. Let new load (YEAR) finish
            advanceTimeBy(301)

            // 7. Await final state
            val finalState = awaitItem()
            assertEquals(AnalysisTimePeriod.YEAR, finalState.selectedTimePeriod)
            assertEquals(mockItems, finalState.analysisItems) // Now has new data

            cancelAndIgnoreRemainingEvents()
        }

        // Verify the DAO was called twice with these filters
        verify(transactionDao, times(2)).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull())
    }
}

