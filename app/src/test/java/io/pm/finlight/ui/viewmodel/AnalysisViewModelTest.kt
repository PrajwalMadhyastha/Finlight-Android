// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/AnalysisViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// FIX (Testing) - Corrected all tests to properly handle intermediate state
// emissions from the ViewModel's complex `combine` flows. The tests now
// correctly await the final state after an action, resolving assertion errors.
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
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

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
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getSpendingAnalysisByTag(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getSpendingAnalysisByMerchant(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))


        viewModel = AnalysisViewModel(transactionDao, categoryDao, tagDao)
    }

    @Test
    fun `initial uiState is correct`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(AnalysisDimension.CATEGORY, initialState.selectedDimension)
            assertEquals(AnalysisTimePeriod.MONTH, initialState.selectedTimePeriod)
            assertEquals(false, initialState.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectDimension updates uiState and calls correct DAO method`() = runTest {
        // Arrange
        val mockItems = listOf(SpendingAnalysisItem("1", "Test", 100.0, 1))
        `when`(transactionDao.getSpendingAnalysisByTag(anyLong(), anyLong(), isNull(), isNull(), isNull())).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            // 1. Await the initial state (Dimension: CATEGORY, Items: [])
            awaitItem()

            // 2. Act: Change the dimension.
            viewModel.selectDimension(AnalysisDimension.TAG)

            // 3. Await intermediate state. The `combine` operator for uiState emits
            //    immediately with the new dimension but BEFORE `flatMapLatest` gets new data.
            val intermediateState = awaitItem()
            assertEquals(AnalysisDimension.TAG, intermediateState.selectedDimension)
            assertEquals(emptyList<SpendingAnalysisItem>(), intermediateState.analysisItems)

            // 4. Await the final state after the DAO has been called and data is emitted.
            val finalState = awaitItem()
            assertEquals(AnalysisDimension.TAG, finalState.selectedDimension)
            assertEquals(mockItems, finalState.analysisItems) // This now passes.

            cancelAndIgnoreRemainingEvents()
        }

        // Verify that the correct DAO method was called.
        verify(transactionDao).getSpendingAnalysisByTag(anyLong(), anyLong(), isNull(), isNull(), isNull())
    }

    @Test
    fun `selectFilterCategory updates uiState and refetches data`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food", "", "")
        val mockItems = listOf(SpendingAnalysisItem("1", "Food", 50.0, 1))
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), eq(1))).thenReturn(flowOf(mockItems))

        viewModel.uiState.test {
            awaitItem() // 1. Consume initial state

            // 2. Act
            viewModel.selectFilterCategory(foodCategory)

            // 3. Await intermediate state (Filter applied, but data not yet fetched)
            awaitItem()

            // 4. Await final state (Filter applied AND data fetched)
            val updatedState = awaitItem()
            assertEquals(foodCategory, updatedState.selectedFilterCategory)
            assertEquals(mockItems, updatedState.analysisItems) // This now passes.
            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), eq(1))
    }

    @Test
    fun `clearFilters resets filter state and refetches data`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food", "", "")
        val travelTag = Tag(2, "Travel")
        // We expect the final call to have null filters
        `when`(transactionDao.getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull())).thenReturn(flowOf(emptyList()))

        viewModel.uiState.test {
            awaitItem() // Initial state

            // Act 1: Set some filters
            viewModel.selectFilterCategory(foodCategory)
            awaitItem() // Consume state after setting category
            viewModel.selectFilterTag(travelTag)

            // Consume intermediate states until we get the one with the tag set
            val stateWithFilters = awaitItem()
            assertEquals(travelTag, stateWithFilters.selectedFilterTag)

            // Act 2: Clear the filters
            viewModel.clearFilters()

            // Calling clearFilters() updates multiple StateFlows, causing multiple emissions.
            // We need to wait for the final state where all filters are cleared.
            var finalState: AnalysisUiState? = null
            for (i in 1..3) { // Expect up to 3 emissions (one per filter change)
                val state = awaitItem()
                if (state.selectedFilterCategory == null && state.selectedFilterTag == null && state.selectedFilterMerchant == null) {
                    finalState = state
                    break
                }
            }

            // Assert
            assertNotNull("Final state after clearing should be found", finalState)
            assertNull(finalState!!.selectedFilterCategory)
            assertNull(finalState.selectedFilterTag)
            assertNull(finalState.selectedFilterMerchant)
            cancelAndIgnoreRemainingEvents()
        }

        // Verify that the DAO was called with nulls at least once after clearing
        verify(transactionDao, atLeastOnce()).getSpendingAnalysisByCategory(anyLong(), anyLong(), isNull(), isNull(), isNull())
    }
}