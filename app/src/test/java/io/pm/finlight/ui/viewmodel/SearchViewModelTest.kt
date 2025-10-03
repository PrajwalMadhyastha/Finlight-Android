// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SearchViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SearchViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

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
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Default mocks to prevent crashes during initialization
        `when`(accountDao.getAllAccounts()).thenReturn(flowOf(emptyList()))
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
        `when`(tagDao.getAllTags()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.searchTransactions(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearFilters resets uiState and search results`() = runTest {
        // ARRANGE
        viewModel = SearchViewModel(transactionDao, accountDao, categoryDao, tagDao, null, null)

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
}