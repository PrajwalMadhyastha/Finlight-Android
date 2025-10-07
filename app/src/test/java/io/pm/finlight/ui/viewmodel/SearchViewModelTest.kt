// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SearchViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class has been updated to extend the new
// `BaseViewModelTest`. All boilerplate for JUnit rules, coroutine dispatchers,
// and Mockito initialization has been removed and is now inherited from the base
// class.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.Calendar

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
    }

    private fun initializeViewModel(initialCategoryId: Int? = null, initialDateMillis: Long? = null) {
        viewModel = SearchViewModel(transactionDao, accountDao, categoryDao, tagDao, initialCategoryId, initialDateMillis)
    }

    @Test
    fun `clearFilters resets uiState and search results`() = runTest {
        // ARRANGE
        initializeViewModel()

        // Set some filters
        viewModel.onKeywordChange("Test")
        viewModel.onCategoryChange(Category(1, "Food", "icon", "color"))
        advanceUntilIdle()

        // ACT
        viewModel.clearFilters()
        advanceUntilIdle()

        // ASSERT
        val state = viewModel.uiState.first()
        assertEquals("", state.keyword)
        assertNull(state.selectedCategory)
        assertNull(state.selectedAccount)
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
    fun `init with initialDateMillis sets date range`() = runTest {
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
    }
}

