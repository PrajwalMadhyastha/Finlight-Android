// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SearchViewModelTest.kt
// REASON: FEATURE (Test) - Added new tests for the "Drilldown" feature (initialQuery).
// - `init with initialQuery sets drilldown mode and keyword` verifies that
//   passing a query string correctly puts the VM into drilldown mode.
// - `init without initialQuery does not set drilldown mode` verifies the default state.
//
// REASON: FEATURE (Test) - Added new tests for the `displayDate` feature.
// - `init with initialDateMillis sets date range and displayDate` now
//   verifies the user-friendly formatted date string is correctly set.
// - `init without initialDateMillis leaves displayDate null` ensures the
//   date display doesn't appear on a normal search.
// - `clearFilters resets uiState` is updated to confirm `displayDate` is
//   reset to null.
//
// REASON: FIX (Test) - Added a mock for `transactionDao.getFinancialSummaryForRangeFlow`
// in the `setup` block.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SearchViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var accountDao: AccountDao
    @Mock
    private lateinit var categoryDao: CategoryDao
    @Mock
    private lateinit var tagDao: TagDao

    private lateinit var viewModel: SearchViewModel

    @Before
    override fun setup() {
        super.setup()

        // Default mocks to prevent crashes during initialization
        `when`(accountDao.getAllAccounts()).thenReturn(flowOf(emptyList()))
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
        `when`(tagDao.getAllTags()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.searchTransactions(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        // --- FIX: Add mock for the new dependency in the init block ---
        `when`(transactionDao.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
    }

    private fun initializeViewModel(
        initialCategoryId: Int? = null,
        initialDateMillis: Long? = null,
        initialQuery: String? = null // --- UPDATED: Added initialQuery parameter
    ) {
        viewModel = SearchViewModel(
            transactionDao,
            accountDao,
            categoryDao,
            tagDao,
            initialCategoryId,
            initialDateMillis,
            initialQuery // --- UPDATED: Pass to constructor
        )
    }

    @Test
    fun `clearFilters resets uiState and search results`() = runTest {
        // ARRANGE
        // Set up a viewmodel with an initial date to ensure displayDate is populated
        val cal = Calendar.getInstance()
        initializeViewModel(initialDateMillis = cal.timeInMillis)
        advanceUntilIdle()

        // Set some filters
        viewModel.onKeywordChange("Test")
        viewModel.onCategoryChange(Category(1, "Food", "icon", "color"))
        advanceUntilIdle()

        // Pre-condition check
        val populatedState = viewModel.uiState.first()
        assertEquals("Test", populatedState.keyword)
        assertNotNull(populatedState.selectedCategory)
        assertNotNull(populatedState.displayDate) // Ensure displayDate was set

        // ACT
        viewModel.clearFilters()
        advanceUntilIdle()

        // ASSERT
        val state = viewModel.uiState.first()
        assertEquals("", state.keyword)
        assertNull(state.selectedCategory)
        assertNull(state.selectedAccount)
        assertNull(state.displayDate) // Verify displayDate is also reset to null
        assertEquals(emptyList<TransactionDetails>(), viewModel.searchResults.first())
    }

    @Test
    fun `searchResults updates reactively on keyword change with debounce`() = runTest(testDispatcher) {
        // Arrange
        val results1 = listOf(TransactionDetails(Transaction(id = 1, description = "Coffee", amount = 1.0, date=0, accountId = 1, categoryId=1, notes=null), emptyList(), null,null,null,null,null))
        val results2 = listOf(TransactionDetails(Transaction(id = 2, description = "Tea", amount = 2.0, date=0, accountId = 1, categoryId=1, notes=null), emptyList(), null,null,null,null,null))
        `when`(transactionDao.searchTransactions("Coffee", null, null, null, null, null, null)).thenReturn(flowOf(results1))
        `when`(transactionDao.searchTransactions("Tea", null, null, null, null, null, null)).thenReturn(flowOf(results2))
        initializeViewModel()

        viewModel.searchResults.test {
            assertEquals(emptyList<TransactionDetails>(), awaitItem()) // Initial state

            // Act
            viewModel.onKeywordChange("Coffee")
            advanceTimeBy(100) // Not enough time for debounce
            viewModel.onKeywordChange("Tea")
            advanceTimeBy(301) // Debounce time passed

            // Assert
            assertEquals(results2, awaitItem())
            verify(transactionDao, never()).searchTransactions("Coffee", null, null, null, null, null, null)
            verify(transactionDao).searchTransactions("Tea", null, null, null, null, null, null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init with initialCategoryId sets selectedCategory`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food", "icon", "color")
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(listOf(foodCategory)))
        initializeViewModel(initialCategoryId = 1)
        advanceUntilIdle()

        // Assert
        assertEquals(foodCategory, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun `init with initialDateMillis sets date range and displayDate`() = runTest {
        // Arrange
        val cal = Calendar.getInstance()
        val dateMillis = cal.timeInMillis
        initializeViewModel(initialDateMillis = dateMillis)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertNotNull(state.startDate)
        assertNotNull(state.endDate)
        assertEquals(cal.get(Calendar.DAY_OF_YEAR), Calendar.getInstance().apply { timeInMillis = state.startDate!! }.get(Calendar.DAY_OF_YEAR))

        // --- NEW ASSERTION ---
        val dateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val expectedDate = dateFormatter.format(cal.time)
        assertEquals(expectedDate, state.displayDate)
    }

    @Test
    fun `init without initialDateMillis leaves displayDate null`() = runTest {
        // Arrange
        initializeViewModel(initialDateMillis = null)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertNull(state.startDate)
        assertNull(state.endDate)
        assertNull(state.displayDate)
    }

    @Test
    fun `onAccountChange updates uiState`() = runTest {
        // Arrange
        initializeViewModel()
        val account = Account(1, "Test Account", "Bank")

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onAccountChange(account)
            val updated = awaitItem()
            assertEquals(account, updated.selectedAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCategoryChange updates uiState`() = runTest {
        // Arrange
        initializeViewModel()
        val category = Category(1, "Test Category", "", "")

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onCategoryChange(category)
            assertEquals(category, awaitItem().selectedCategory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTagChange updates uiState`() = runTest {
        // Arrange
        initializeViewModel()
        val tag = Tag(1, "Test Tag")

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onTagChange(tag)
            assertEquals(tag, awaitItem().selectedTag)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTypeChange updates uiState`() = runTest {
        // Arrange
        initializeViewModel()
        val type = "Income"

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onTypeChange(type)
            assertEquals(type, awaitItem().transactionType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStartDateSelected and onClearStartDate update uiState`() = runTest {
        // Arrange
        initializeViewModel()
        val date = 123456789L

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onStartDateSelected(date)
            assertEquals(date, awaitItem().startDate)

            viewModel.onClearStartDate()
            assertNull(awaitItem().startDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEndDateSelected and onClearEndDate update uiState`() = runTest {
        // Arrange
        initializeViewModel()
        val date = 987654321L

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onEndDateSelected(date)
            assertNotNull(awaitItem().endDate) // Date is adjusted to end of day

            viewModel.onClearEndDate()
            assertNull(awaitItem().endDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShowStartDatePicker and onShowEndDatePicker update uiState`() = runTest {
        // Arrange
        initializeViewModel()

        // Act & Assert for Start Date Picker
        viewModel.uiState.test {
            assertFalse(awaitItem().showStartDatePicker)
            viewModel.onShowStartDatePicker(true)
            assertTrue(awaitItem().showStartDatePicker)
            viewModel.onShowStartDatePicker(false)
            assertFalse(awaitItem().showStartDatePicker)
            cancelAndIgnoreRemainingEvents()
        }

        // Act & Assert for End Date Picker
        viewModel.uiState.test {
            assertFalse(awaitItem().showEndDatePicker)
            viewModel.onShowEndDatePicker(true)
            assertTrue(awaitItem().showEndDatePicker)
            viewModel.onShowEndDatePicker(false)
            assertFalse(awaitItem().showEndDatePicker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW TESTS: Drilldown Mode ---

    @Test
    fun `init with initialQuery sets drilldown mode and keyword`() = runTest {
        // Arrange
        val query = "Starbucks"

        // Act
        initializeViewModel(initialQuery = query)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Keyword should match initial query", query, state.keyword)
        assertTrue("isDrilldown should be true", state.isDrilldown)
    }

    @Test
    fun `init without initialQuery does not set drilldown mode`() = runTest {
        // Act
        initializeViewModel(initialQuery = null)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("", state.keyword)
        assertFalse("isDrilldown should be false by default", state.isDrilldown)
    }
}