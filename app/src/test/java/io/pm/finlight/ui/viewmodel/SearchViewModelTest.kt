// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SearchViewModelTest.kt
// REASON: REFACTOR (Domain 5 / Issue #339) - Migrate SearchViewModelTest to use repository doubles:
// 1. Replaced raw DAO mocks with ITransactionRepository, IAccountRepository,
//    ICategoryRepository, ITagRepository, and TestDispatcherProvider.
// 2. Added comprehensive tests for hasSearched flag, reactive metadata combination,
//    filter combinations, and search debouncing.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Account
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Category
import io.pm.finlight.FinancialSummary
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.model.SearchFilter
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SearchViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    @Mock
    private lateinit var accountRepository: IAccountRepository

    @Mock
    private lateinit var categoryRepository: ICategoryRepository

    @Mock
    private lateinit var tagRepository: ITagRepository

    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var viewModel: SearchViewModel

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)

        // Default repository doubles to prevent crashes during initialization
        whenever(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
        whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        whenever(tagRepository.allTags).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.searchTransactions(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.getFinancialSummaryForRangeFlow(any(), any())).thenReturn(flowOf(null))
    }

    private fun initializeViewModel(
        initialCategoryId: Int? = null,
        initialDateMillis: Long? = null,
        initialQuery: String? = null,
    ) {
        viewModel =
            SearchViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                tagRepository = tagRepository,
                dispatcherProvider = testDispatcherProvider,
                initialCategoryId = initialCategoryId,
                initialDateMillis = initialDateMillis,
                initialQuery = initialQuery,
            )
    }

    @Test
    fun `clearFilters resets uiState and search results`() =
        runTest {
            // ARRANGE
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
            assertNotNull(populatedState.displayDate)
            assertTrue(populatedState.hasSearched)

            // ACT
            viewModel.clearFilters()
            advanceUntilIdle()

            // ASSERT
            val state = viewModel.uiState.first()
            assertEquals("", state.keyword)
            assertNull(state.selectedCategory)
            assertNull(state.selectedAccount)
            assertNull(state.displayDate)
            assertFalse(state.hasSearched)
            assertEquals(emptyList<TransactionDetails>(), viewModel.searchResults.first())
        }

    @Test
    fun `searchResults updates reactively on keyword change with debounce`() =
        runTest(testDispatcher) {
            // Arrange
            val results1 =
                listOf(
                    TransactionDetails(
                        Transaction(id = 1, description = "Coffee", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            val results2 =
                listOf(
                    TransactionDetails(
                        Transaction(id = 2, description = "Tea", amount = 2.0, date = 0, accountId = 1, categoryId = 1, notes = null),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            whenever(transactionRepository.searchTransactions(eq("Coffee"), any())).thenReturn(flowOf(results1))
            whenever(transactionRepository.searchTransactions(eq("Tea"), any())).thenReturn(flowOf(results2))
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
                verify(transactionRepository, never()).searchTransactions(eq("Coffee"), any())
                verify(transactionRepository).searchTransactions(eq("Tea"), any())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init with initialCategoryId sets selectedCategory`() =
        runTest {
            // Arrange
            val foodCategory = Category(1, "Food", "icon", "color")
            whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
            initializeViewModel(initialCategoryId = 1)
            advanceUntilIdle()

            // Assert
            assertEquals(foodCategory, viewModel.uiState.value.selectedCategory)
            assertTrue(viewModel.uiState.value.hasSearched)
        }

    @Test
    fun `init with initialDateMillis sets date range and displayDate`() =
        runTest {
            // Arrange
            val cal = Calendar.getInstance()
            val dateMillis = cal.timeInMillis
            initializeViewModel(initialDateMillis = dateMillis)
            advanceUntilIdle()

            // Assert
            val state = viewModel.uiState.value
            assertNotNull(state.startDate)
            assertNotNull(state.endDate)
            assertEquals(
                cal.get(Calendar.DAY_OF_YEAR),
                Calendar.getInstance().apply { timeInMillis = state.startDate!! }.get(Calendar.DAY_OF_YEAR),
            )

            val dateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val expectedDate = dateFormatter.format(cal.time)
            assertEquals(expectedDate, state.displayDate)
            assertTrue(state.hasSearched)
        }

    @Test
    fun `init without initialDateMillis leaves displayDate null`() =
        runTest {
            // Arrange
            initializeViewModel(initialDateMillis = null)
            advanceUntilIdle()

            // Assert
            val state = viewModel.uiState.value
            assertNull(state.startDate)
            assertNull(state.endDate)
            assertNull(state.displayDate)
            assertFalse(state.hasSearched)
        }

    @Test
    fun `onAccountChange updates uiState and sets hasSearched after debounce`() =
        runTest(testDispatcher) {
            // Arrange
            initializeViewModel()
            val account = Account(1, "Test Account", "Bank")

            // Act & Assert
            viewModel.uiState.test {
                awaitItem() // initial
                viewModel.onAccountChange(account)
                val updated = awaitItem()
                assertEquals(account, updated.selectedAccount)
                assertFalse(updated.hasSearched)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(350L)
            assertTrue(viewModel.uiState.value.hasSearched)
        }

    @Test
    fun `onCategoryChange updates uiState and sets hasSearched after debounce`() =
        runTest(testDispatcher) {
            // Arrange
            initializeViewModel()
            val category = Category(1, "Test Category", "", "")

            // Act & Assert
            viewModel.uiState.test {
                awaitItem() // initial
                viewModel.onCategoryChange(category)
                val updated = awaitItem()
                assertEquals(category, updated.selectedCategory)
                assertFalse(updated.hasSearched)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(350L)
            assertTrue(viewModel.uiState.value.hasSearched)
        }

    @Test
    fun `onTagChange updates uiState and sets hasSearched after debounce`() =
        runTest(testDispatcher) {
            // Arrange
            initializeViewModel()
            val tag = Tag(1, "Test Tag")

            // Act & Assert
            viewModel.uiState.test {
                awaitItem() // initial
                viewModel.onTagChange(tag)
                val updated = awaitItem()
                assertEquals(tag, updated.selectedTag)
                assertFalse(updated.hasSearched)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(350L)
            assertTrue(viewModel.uiState.value.hasSearched)
        }

    @Test
    fun `onTypeChange updates uiState and sets hasSearched after debounce`() =
        runTest(testDispatcher) {
            // Arrange
            initializeViewModel()
            val type = "Income"

            // Act & Assert
            viewModel.uiState.test {
                awaitItem() // initial
                viewModel.onTypeChange(type)
                val updated = awaitItem()
                assertEquals(type, updated.transactionType)
                assertFalse(updated.hasSearched)
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(350L)
            assertTrue(viewModel.uiState.value.hasSearched)
        }

    @Test
    fun `onTypeChange with null resets to All`() =
        runTest {
            initializeViewModel()
            viewModel.onTypeChange("Income")
            viewModel.onTypeChange(null)

            assertEquals("All", viewModel.uiState.value.transactionType)
            assertFalse(viewModel.uiState.value.hasSearched)
        }

    @Test
    fun `onStartDateSelected and onClearStartDate update uiState`() =
        runTest(testDispatcher) {
            // Arrange
            initializeViewModel()
            val date = 123456789L
            val expectedStart =
                Calendar.getInstance().apply {
                    timeInMillis = date
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

            // Act & Assert
            viewModel.uiState.test {
                awaitItem() // initial
                viewModel.onStartDateSelected(date)
                val startSelected = awaitItem()
                assertEquals(expectedStart, startSelected.startDate)
                assertFalse(startSelected.hasSearched)

                viewModel.onClearStartDate()
                val cleared = awaitItem()
                assertNull(cleared.startDate)
                assertFalse(cleared.hasSearched)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onEndDateSelected and onClearEndDate update uiState`() =
        runTest(testDispatcher) {
            // Arrange
            initializeViewModel()
            val date = 987654321L

            // Act & Assert
            viewModel.uiState.test {
                awaitItem() // initial
                viewModel.onEndDateSelected(date)
                val endSelected = awaitItem()
                assertNotNull(endSelected.endDate)
                assertFalse(endSelected.hasSearched)

                viewModel.onClearEndDate()
                val cleared = awaitItem()
                assertNull(cleared.endDate)
                assertFalse(cleared.hasSearched)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onShowStartDatePicker and onShowEndDatePicker update uiState`() =
        runTest {
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

    @Test
    fun `init with initialQuery sets drilldown mode and keyword`() =
        runTest {
            // Arrange
            val query = "Starbucks"

            // Act
            initializeViewModel(initialQuery = query)
            advanceUntilIdle()

            // Assert
            val state = viewModel.uiState.value
            assertEquals("Keyword should match initial query", query, state.keyword)
            assertTrue("isDrilldown should be true", state.isDrilldown)
            assertTrue(state.hasSearched)
        }

    @Test
    fun `init without initialQuery does not set drilldown mode`() =
        runTest {
            // Act
            initializeViewModel(initialQuery = null)
            advanceUntilIdle()

            // Assert
            val state = viewModel.uiState.value
            assertEquals("", state.keyword)
            assertFalse("isDrilldown should be false by default", state.isDrilldown)
            assertFalse(state.hasSearched)
        }

    @Test
    fun `onTypeChange with specific types triggers search with correct TransactionType enum`() =
        runTest {
            initializeViewModel()
            advanceUntilIdle()

            viewModel.searchResults.test {
                awaitItem() // initial empty

                viewModel.onTypeChange("expense")
                advanceTimeBy(350L)
                verify(transactionRepository).searchTransactions(
                    any(),
                    eq(SearchFilter(transactionType = TransactionType.EXPENSE)),
                )

                viewModel.onTypeChange("income")
                advanceTimeBy(350L)
                verify(transactionRepository).searchTransactions(
                    any(),
                    eq(SearchFilter(transactionType = TransactionType.INCOME)),
                )

                viewModel.onTypeChange("All Types")
                advanceTimeBy(350L)
                verify(transactionRepository, atLeastOnce()).searchTransactions(
                    any(),
                    eq(SearchFilter(transactionType = null)),
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `constructor initializes SearchViewModel properly with default dispatcher`() =
        runTest {
            val vm =
                SearchViewModel(
                    transactionRepository = transactionRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    tagRepository = tagRepository,
                    initialCategoryId = 1,
                    initialDateMillis = 1000L,
                    initialQuery = "query",
                )
            assertNotNull(vm)
            assertEquals("query", vm.uiState.value.keyword)
            assertTrue(vm.uiState.value.isDrilldown)
        }

    @Test
    fun `metadata flows combine reactively into uiState`() =
        runTest {
            val accounts = listOf(Account(1, "Checking", "Bank"))
            val categories = listOf(Category(1, "Food", "icon", "color"))
            val tags = listOf(Tag(1, "Trip"))

            whenever(accountRepository.allAccounts).thenReturn(flowOf(accounts))
            whenever(categoryRepository.allCategories).thenReturn(flowOf(categories))
            whenever(tagRepository.allTags).thenReturn(flowOf(tags))

            initializeViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(accounts, state.accounts)
            assertEquals(categories, state.categories)
            assertEquals(tags, state.tags)
        }

    @Test
    fun `onAccountChange triggers search with accountId filter after debounce`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            val account = Account(id = 42, name = "Checking", type = "Bank")

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onAccountChange(account)
                advanceTimeBy(350L)

                verify(transactionRepository).searchTransactions(
                    eq(""),
                    eq(SearchFilter(accountId = 42)),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onCategoryChange triggers search with categoryId filter after debounce`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            val category = Category(id = 7, name = "Groceries", iconKey = "icon", colorKey = "green")

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onCategoryChange(category)
                advanceTimeBy(350L)

                verify(transactionRepository).searchTransactions(
                    eq(""),
                    eq(SearchFilter(categoryId = 7)),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onTagChange triggers search with tagId filter after debounce`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            val tag = Tag(id = 99, name = "Vacation")

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onTagChange(tag)
                advanceTimeBy(350L)

                verify(transactionRepository).searchTransactions(
                    eq(""),
                    eq(SearchFilter(tagId = 99)),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onStartDateSelected and onEndDateSelected trigger search with date range filter after debounce`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            val startMillis = 100_000L
            val expectedStartMillis =
                Calendar.getInstance().apply {
                    timeInMillis = startMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            val endInputMillis = 200_000L
            val expectedEndMillis =
                Calendar.getInstance().apply {
                    timeInMillis = endInputMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onStartDateSelected(startMillis)
                advanceTimeBy(350L)
                verify(transactionRepository).searchTransactions(
                    eq(""),
                    eq(SearchFilter(startDate = expectedStartMillis)),
                )

                viewModel.onEndDateSelected(endInputMillis)
                advanceTimeBy(350L)
                verify(transactionRepository).searchTransactions(
                    eq(""),
                    eq(SearchFilter(startDate = expectedStartMillis, endDate = expectedEndMillis)),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onEndDateSelected with null sets endDate to null`() =
        runTest {
            initializeViewModel()
            viewModel.onEndDateSelected(null)
            assertNull(viewModel.uiState.value.endDate)
        }

    @Test
    fun `init with initialDateMillis minus one does not configure date range`() =
        runTest {
            initializeViewModel(initialDateMillis = -1L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.startDate)
            assertNull(state.endDate)
            assertNull(state.displayDate)
            assertFalse(state.hasSearched)
            verify(transactionRepository, never()).getFinancialSummaryForRangeFlow(any(), any())
        }

    @Test
    fun `init with initialCategoryId when category is not found leaves selectedCategory null`() =
        runTest {
            val categories = listOf(Category(id = 2, name = "Health", iconKey = "h", colorKey = "blue"))
            whenever(categoryRepository.allCategories).thenReturn(flowOf(categories))

            initializeViewModel(initialCategoryId = 999)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedCategory)
        }

    @Test
    fun `metadata flow does not overwrite existing selectedCategory when initialCategoryId is provided`() =
        runTest {
            val manualCategory = Category(id = 10, name = "Custom", iconKey = "c", colorKey = "red")
            val loadedCategories =
                listOf(
                    Category(id = 5, name = "Initial", iconKey = "i", colorKey = "yellow"),
                    manualCategory,
                )
            val categoriesFlow = MutableSharedFlow<List<Category>>()
            whenever(categoryRepository.allCategories).thenReturn(categoriesFlow)

            initializeViewModel(initialCategoryId = 5)
            // Pre-select a category before metadata emission completes
            viewModel.onCategoryChange(manualCategory)

            categoriesFlow.emit(loadedCategories)
            advanceUntilIdle()

            assertEquals(manualCategory, viewModel.uiState.value.selectedCategory)
        }

    @Test
    fun `onTypeChange with case insensitive all does not trigger type filter`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onTypeChange("ALL")
                advanceTimeBy(350L)

                verify(transactionRepository, never()).searchTransactions(any(), any())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SearchQueryParameters with blank transactionType does not activate filters`() {
        val params = SearchQueryParameters(transactionType = "   ")
        assertFalse(params.filtersAreActive)
        assertFalse(params.shouldSearch)
    }

    @Test
    fun `daySummary collects emissions when initialDateMillis is provided`() =
        runTest {
            val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 1500.0)
            whenever(transactionRepository.getFinancialSummaryForRangeFlow(any(), any()))
                .thenReturn(flowOf(summary))

            initializeViewModel(initialDateMillis = 1000L)
            advanceUntilIdle()

            assertEquals(summary, viewModel.daySummary.first())
        }

    @Test
    fun `clearFilters resets daySummary to null`() =
        runTest {
            val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 1500.0)
            whenever(transactionRepository.getFinancialSummaryForRangeFlow(any(), any()))
                .thenReturn(flowOf(summary))

            initializeViewModel(initialDateMillis = 1000L)
            advanceUntilIdle()
            assertEquals(summary, viewModel.daySummary.first())

            viewModel.clearFilters()
            advanceUntilIdle()

            assertNull(viewModel.daySummary.first())
        }

    @Test
    fun `combined search triggers repository with all filter fields populated`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            val account = Account(id = 1, name = "Bank", type = "Checking")
            val category = Category(id = 2, name = "Food", iconKey = "f", colorKey = "orange")
            val tag = Tag(id = 3, name = "Dining")
            val startMillis = 500L
            val expectedStartMillis =
                Calendar.getInstance().apply {
                    timeInMillis = startMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            val endMillis = 900L

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onKeywordChange("Dinner")
                viewModel.onAccountChange(account)
                viewModel.onCategoryChange(category)
                viewModel.onTagChange(tag)
                viewModel.onTypeChange("expense")
                viewModel.onStartDateSelected(startMillis)
                viewModel.onEndDateSelected(endMillis)

                advanceTimeBy(350L)

                verify(transactionRepository).searchTransactions(
                    eq("Dinner"),
                    eq(
                        SearchFilter(
                            accountId = 1,
                            categoryId = 2,
                            tagId = 3,
                            transactionType = TransactionType.EXPENSE,
                            startDate = expectedStartMillis,
                            endDate =
                                Calendar.getInstance().apply {
                                    timeInMillis = endMillis
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }.timeInMillis,
                        ),
                    ),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `keystroke does not set hasSearched during debounce to prevent empty-state flicker`() =
        runTest(testDispatcher) {
            whenever(transactionRepository.searchTransactions(eq("Coffee"), any()))
                .thenReturn(flowOf(emptyList()))
            initializeViewModel()
            advanceUntilIdle()

            // Initial state
            assertFalse(viewModel.uiState.value.hasSearched)
            assertEquals(emptyList<TransactionDetails>(), viewModel.searchResults.value)

            // User types keystroke
            viewModel.onKeywordChange("Coffee")

            // Advance partially into debounce window (150ms of 300ms)
            advanceTimeBy(150L)

            // ASSERT: During debounce, hasSearched remains false, preventing "No transactions match your criteria."
            assertFalse(
                "hasSearched must be false during debounce to avoid UI flicker",
                viewModel.uiState.value.hasSearched,
            )
            assertEquals(emptyList<TransactionDetails>(), viewModel.searchResults.value)

            // Advance past debounce window (300ms total)
            advanceTimeBy(151L)

            // ASSERT: After search execution completes, hasSearched becomes true
            assertTrue(
                "hasSearched must be true after search execution completes",
                viewModel.uiState.value.hasSearched,
            )
        }

    @Test
    fun `subsequent allCategories emissions after clearFilters do not resurrect initialCategoryId`() =
        runTest {
            val initialCategory = Category(id = 5, name = "Groceries", iconKey = "g", colorKey = "green")
            val updatedCategories =
                listOf(
                    initialCategory,
                    Category(id = 6, name = "Dining", iconKey = "d", colorKey = "orange"),
                )
            val categoriesFlow = MutableSharedFlow<List<Category>>()
            whenever(categoryRepository.allCategories).thenReturn(categoriesFlow)

            initializeViewModel(initialCategoryId = 5)
            // Emit initial categories
            categoriesFlow.emit(listOf(initialCategory))
            advanceUntilIdle()

            assertEquals(initialCategory, viewModel.uiState.value.selectedCategory)

            // User clears filters
            viewModel.clearFilters()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedCategory)

            // External event triggers category re-emission (e.g. background sync or SMS parse)
            categoriesFlow.emit(updatedCategories)
            advanceUntilIdle()

            // Category must NOT be resurrected
            assertNull(viewModel.uiState.value.selectedCategory)
        }

    @Test
    fun `daySummary does not re-emit after clearFilters`() =
        runTest {
            val initialSummary = FinancialSummary(totalIncome = 500.0, totalExpenses = 200.0)
            val subsequentSummary = FinancialSummary(totalIncome = 1000.0, totalExpenses = 400.0)
            val summaryFlow = MutableSharedFlow<FinancialSummary?>()
            whenever(transactionRepository.getFinancialSummaryForRangeFlow(any(), any()))
                .thenReturn(summaryFlow)

            initializeViewModel(initialDateMillis = 1000L)
            summaryFlow.emit(initialSummary)
            advanceUntilIdle()

            assertEquals(initialSummary, viewModel.daySummary.first())

            // Clear filters
            viewModel.clearFilters()
            advanceUntilIdle()
            assertNull(viewModel.daySummary.first())

            // Flow re-emits due to transaction insertion in that range
            summaryFlow.emit(subsequentSummary)
            advanceUntilIdle()

            // daySummary must remain null (coroutine job was cancelled)
            assertNull(viewModel.daySummary.first())
        }

    @Test
    fun `onStartDateSelected normalizes timestamp to start-of-day 00 00 00 000`() =
        runTest {
            initializeViewModel()
            val inputMillis = 1_700_000_123_456L
            val expectedStartMillis =
                Calendar.getInstance().apply {
                    timeInMillis = inputMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

            viewModel.onStartDateSelected(inputMillis)

            assertEquals(expectedStartMillis, viewModel.uiState.value.startDate)
        }

    @Test
    fun `searchTransactions receives trimmed keyword when filters are active`() =
        runTest(testDispatcher) {
            initializeViewModel()
            advanceUntilIdle()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.searchResults.collect {}
            }

            val account = Account(id = 1, name = "Checking", type = "Bank")
            viewModel.onAccountChange(account)
            viewModel.onKeywordChange("   ")
            advanceTimeBy(350L)

            // When user enters whitespace alongside an active filter, query passed must be trimmed to ""
            verify(transactionRepository).searchTransactions(
                eq(""),
                eq(SearchFilter(accountId = 1)),
            )

            viewModel.onKeywordChange("  Starbucks  ")
            advanceTimeBy(350L)

            // Leading and trailing whitespace should be trimmed
            verify(transactionRepository).searchTransactions(
                eq("Starbucks"),
                eq(SearchFilter(accountId = 1)),
            )
        }

    @Test
    fun `allCategories re-emission refreshes selectedCategory when entity attributes change`() =
        runTest {
            val initialCategory = Category(id = 1, name = "Food", iconKey = "food", colorKey = "green")
            val updatedCategory = Category(id = 1, name = "Groceries", iconKey = "cart", colorKey = "blue")
            val categoriesFlow = MutableSharedFlow<List<Category>>()
            whenever(categoryRepository.allCategories).thenReturn(categoriesFlow)

            initializeViewModel()
            viewModel.onCategoryChange(initialCategory)
            advanceUntilIdle()

            assertEquals("Food", viewModel.uiState.value.selectedCategory?.name)

            categoriesFlow.emit(listOf(updatedCategory))
            advanceUntilIdle()

            assertEquals("Groceries", viewModel.uiState.value.selectedCategory?.name)
            assertEquals("cart", viewModel.uiState.value.selectedCategory?.iconKey)
            assertEquals("blue", viewModel.uiState.value.selectedCategory?.colorKey)
        }

    @Test
    fun `allCategories re-emission clears selectedCategory when entity is deleted`() =
        runTest {
            val selectedCategory = Category(id = 1, name = "Food", iconKey = "food", colorKey = "green")
            val otherCategory = Category(id = 2, name = "Bills", iconKey = "bill", colorKey = "red")
            val categoriesFlow = MutableSharedFlow<List<Category>>()
            whenever(categoryRepository.allCategories).thenReturn(categoriesFlow)

            initializeViewModel()
            viewModel.onCategoryChange(selectedCategory)
            advanceUntilIdle()

            assertEquals(selectedCategory, viewModel.uiState.value.selectedCategory)

            // Emit list omitting id 1 (simulating deletion in database)
            categoriesFlow.emit(listOf(otherCategory))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedCategory)
        }

    @Test
    fun `allAccounts re-emission refreshes selectedAccount when entity attributes change`() =
        runTest {
            val initialAccount = Account(id = 1, name = "Checking", type = "Bank")
            val updatedAccount = Account(id = 1, name = "Primary Checking", type = "Bank")
            val accountsFlow = MutableSharedFlow<List<Account>>()
            whenever(accountRepository.allAccounts).thenReturn(accountsFlow)

            initializeViewModel()
            viewModel.onAccountChange(initialAccount)
            advanceUntilIdle()

            assertEquals("Checking", viewModel.uiState.value.selectedAccount?.name)

            accountsFlow.emit(listOf(updatedAccount))
            advanceUntilIdle()

            assertEquals("Primary Checking", viewModel.uiState.value.selectedAccount?.name)
        }

    @Test
    fun `allAccounts re-emission clears selectedAccount when entity is deleted`() =
        runTest {
            val selectedAccount = Account(id = 1, name = "Checking", type = "Bank")
            val otherAccount = Account(id = 2, name = "Savings", type = "Bank")
            val accountsFlow = MutableSharedFlow<List<Account>>()
            whenever(accountRepository.allAccounts).thenReturn(accountsFlow)

            initializeViewModel()
            viewModel.onAccountChange(selectedAccount)
            advanceUntilIdle()

            assertEquals(selectedAccount, viewModel.uiState.value.selectedAccount)

            accountsFlow.emit(listOf(otherAccount))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedAccount)
        }

    @Test
    fun `allTags re-emission refreshes selectedTag when entity attributes change`() =
        runTest {
            val initialTag = Tag(id = 1, name = "Trip")
            val updatedTag = Tag(id = 1, name = "Vacation")
            val tagsFlow = MutableSharedFlow<List<Tag>>()
            whenever(tagRepository.allTags).thenReturn(tagsFlow)

            initializeViewModel()
            viewModel.onTagChange(initialTag)
            advanceUntilIdle()

            assertEquals("Trip", viewModel.uiState.value.selectedTag?.name)

            tagsFlow.emit(listOf(updatedTag))
            advanceUntilIdle()

            assertEquals("Vacation", viewModel.uiState.value.selectedTag?.name)
        }

    @Test
    fun `allTags re-emission clears selectedTag when entity is deleted`() =
        runTest {
            val selectedTag = Tag(id = 1, name = "Trip")
            val otherTag = Tag(id = 2, name = "Groceries")
            val tagsFlow = MutableSharedFlow<List<Tag>>()
            whenever(tagRepository.allTags).thenReturn(tagsFlow)

            initializeViewModel()
            viewModel.onTagChange(selectedTag)
            advanceUntilIdle()

            assertEquals(selectedTag, viewModel.uiState.value.selectedTag)

            tagsFlow.emit(listOf(otherTag))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedTag)
        }

    @Test
    fun `searchTransactions flow exception is caught by catch operator and safely emits emptyList`() =
        runTest(testDispatcher) {
            val validResult =
                listOf(
                    TransactionDetails(
                        Transaction(id = 1, description = "Test", amount = 10.0, date = 0L, accountId = 1, categoryId = 1, notes = null),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            whenever(transactionRepository.searchTransactions(eq("Valid"), any()))
                .thenReturn(flowOf(validResult))
            whenever(transactionRepository.searchTransactions(eq("Error"), any()))
                .thenReturn(
                    flow {
                        throw RuntimeException("Simulated SQLite failure")
                    },
                )
            initializeViewModel()
            advanceUntilIdle()

            viewModel.searchResults.test {
                assertEquals(emptyList<TransactionDetails>(), awaitItem())

                viewModel.onKeywordChange("Valid")
                advanceTimeBy(350L)
                assertEquals(validResult, awaitItem())

                viewModel.onKeywordChange("Error")
                advanceTimeBy(350L)
                assertEquals(emptyList<TransactionDetails>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `date mutations and clears reset displayDate cancel daySummaryJob and clear daySummary`() =
        runTest {
            val cal = Calendar.getInstance()
            val initialMillis = cal.timeInMillis
            val summaryFlow = MutableSharedFlow<FinancialSummary?>()
            whenever(transactionRepository.getFinancialSummaryForRangeFlow(any(), any()))
                .thenReturn(summaryFlow)

            initializeViewModel(initialDateMillis = initialMillis)
            advanceUntilIdle()

            val initialSummary = FinancialSummary(totalIncome = 200.0, totalExpenses = 100.0)
            summaryFlow.emit(initialSummary)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.displayDate)
            assertEquals(initialSummary, viewModel.daySummary.value)

            // Test onClearStartDate clears displayDate and daySummary
            viewModel.onClearStartDate()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.displayDate)
            assertNull(viewModel.daySummary.value)

            // Reset initialDateMillis state
            initializeViewModel(initialDateMillis = initialMillis)
            advanceUntilIdle()
            summaryFlow.emit(initialSummary)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.displayDate)

            // Test onClearEndDate clears displayDate and daySummary
            viewModel.onClearEndDate()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.displayDate)
            assertNull(viewModel.daySummary.value)

            // Reset initialDateMillis state
            initializeViewModel(initialDateMillis = initialMillis)
            advanceUntilIdle()
            summaryFlow.emit(initialSummary)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.displayDate)

            // Test onStartDateSelected clears displayDate and daySummary
            viewModel.onStartDateSelected(123456L)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.displayDate)
            assertNull(viewModel.daySummary.value)

            // Reset initialDateMillis state
            initializeViewModel(initialDateMillis = initialMillis)
            advanceUntilIdle()
            summaryFlow.emit(initialSummary)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.displayDate)

            // Test onEndDateSelected clears displayDate and daySummary
            viewModel.onEndDateSelected(654321L)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.displayDate)
            assertNull(viewModel.daySummary.value)
        }
}
