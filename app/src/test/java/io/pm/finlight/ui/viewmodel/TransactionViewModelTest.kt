// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TransactionViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class has been updated to extend the new
// `BaseViewModelTest`. All boilerplate for JUnit rules, coroutine dispatchers,
// and Mockito initialization has been removed and is now inherited from the base
// class.
// FIX (Testing) - Refactored the failing 'saveManualTransaction applies currency
// conversion' test. The test logic was incorrectly wrapped in a Turbine `test`
// block that was asserting the initial state of a StateFlow. The test now
// correctly sets up the ViewModel state and directly tests the side effects of
// the onSaveTapped method, resolving the AssertionError.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionViewModelTest : BaseViewModelTest() {

    private val applicationContext: Application = ApplicationProvider.getApplicationContext()

    // Mocks for all dependencies
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var tagRepository: TagRepository
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var smsRepository: SmsRepository
    @Mock private lateinit var merchantRenameRuleRepository: MerchantRenameRuleRepository
    @Mock private lateinit var merchantCategoryMappingRepository: MerchantCategoryMappingRepository
    @Mock private lateinit var merchantMappingRepository: MerchantMappingRepository
    @Mock private lateinit var splitTransactionRepository: SplitTransactionRepository
    @Mock private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    @Mock private lateinit var accountDao: AccountDao
    @Mock private lateinit var categoryDao: CategoryDao
    @Mock private lateinit var tagDao: TagDao
    @Mock private lateinit var transactionDao: TransactionDao

    private lateinit var viewModel: TransactionViewModel

    @Before
    override fun setup() {
        super.setup()

        // Mock DAO access from the AppDatabase mock
        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.categoryDao()).thenReturn(categoryDao)
        `when`(db.tagDao()).thenReturn(tagDao)
        `when`(db.transactionDao()).thenReturn(transactionDao)

        // Setup default mock behaviors for ViewModel initialization
        `when`(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
        `when`(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))
        `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), Mockito.nullable(String::class.java), Mockito.nullable(Int::class.java), Mockito.nullable(Int::class.java))).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
        `when`(transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), Mockito.nullable(String::class.java), Mockito.nullable(Int::class.java), Mockito.nullable(Int::class.java))).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getSpendingByMerchantForMonth(anyLong(), anyLong(), Mockito.nullable(String::class.java), Mockito.nullable(Int::class.java), Mockito.nullable(Int::class.java))).thenReturn(flowOf(emptyList()))
        `when`(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(tagRepository.allTags).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))

        // Create the ViewModel instance with all mocked dependencies
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModel = TransactionViewModel(
            application = applicationContext,
            db = db,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            settingsRepository = settingsRepository,
            smsRepository = smsRepository,
            merchantRenameRuleRepository = merchantRenameRuleRepository,
            merchantCategoryMappingRepository = merchantCategoryMappingRepository,
            merchantMappingRepository = merchantMappingRepository,
            splitTransactionRepository = splitTransactionRepository,
            smsParseTemplateDao = smsParseTemplateDao
        )
    }

    @Test
    fun `updateFilterKeyword updates filterState correctly`() = runTest {
        // ARRANGE
        val newKeyword = "Coffee"

        // ACT
        viewModel.updateFilterKeyword(newKeyword)

        // ASSERT
        val updatedState = viewModel.filterState.first()
        assertEquals(newKeyword, updatedState.keyword)
    }

    @Test
    fun `onSaveTapped with only amount saves directly without category nudge`() = runTest {
        // ARRANGE
        val accountId = 1
        var onSaveCompleteCalled = false
        `when`(transactionRepository.insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())).thenReturn(1L)

        // ACT
        viewModel.onSaveTapped(
            description = "", // No description
            amountStr = "100.0",
            accountId = accountId,
            categoryId = null,
            notes = null,
            date = 0L,
            transactionType = "expense",
            imageUris = emptyList()
        ) { onSaveCompleteCalled = true }
        advanceUntilIdle()

        // ASSERT
        assertNull(viewModel.showCategoryNudge.value)
        verify(transactionRepository).insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())
        assertTrue(onSaveCompleteCalled)
    }

    @Test
    fun `onSaveTapped with description and amount shows category nudge`() = runTest {
        // ARRANGE
        val accountId = 1
        var onSaveCompleteCalled = false

        // ACT
        viewModel.onSaveTapped(
            description = "Coffee", // Description is present
            amountStr = "100.0",
            accountId = accountId,
            categoryId = null, // No category selected yet
            notes = null,
            date = 0L,
            transactionType = "expense",
            imageUris = emptyList()
        ) { onSaveCompleteCalled = true }
        advanceUntilIdle()

        // ASSERT
        assertNotNull(viewModel.showCategoryNudge.value)
        assertEquals("Coffee", viewModel.showCategoryNudge.value?.description)
        verify(transactionRepository, never()).insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())
        assertFalse(onSaveCompleteCalled)
    }

    @Test
    fun `saveWithSelectedCategory saves transaction with correct data from nudge`() = runTest {
        // ARRANGE
        val accountId = 1
        var onSaveCompleteCalled = false
        val newCategoryId = 5
        val transactionCaptor = argumentCaptor<Transaction>()

        `when`(transactionRepository.insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())).thenReturn(1L)

        // First, trigger the nudge
        viewModel.onSaveTapped(
            description = "Lunch",
            amountStr = "250.0",
            accountId = accountId,
            categoryId = null,
            notes = "With friends",
            date = 12345L,
            transactionType = "expense",
            imageUris = emptyList()
        ) {}
        advanceUntilIdle()
        assertNotNull(viewModel.showCategoryNudge.value)

        // ACT
        viewModel.saveWithSelectedCategory(newCategoryId) { onSaveCompleteCalled = true }
        advanceUntilIdle()

        // ASSERT
        verify(transactionRepository).insertTransactionWithTagsAndImages(capture(transactionCaptor), anyObject(), anyObject())
        val savedTransaction = transactionCaptor.value
        assertEquals("Lunch", savedTransaction.description)
        assertEquals(250.0, savedTransaction.amount, 0.0)
        assertEquals(accountId, savedTransaction.accountId)
        assertEquals(newCategoryId, savedTransaction.categoryId)
        assertEquals("With friends", savedTransaction.notes)
        assertEquals(12345L, savedTransaction.date)
        assertTrue(onSaveCompleteCalled)
        assertNull(viewModel.showCategoryNudge.value) // Nudge should be cleared
    }

    @Test
    fun `saveManualTransaction applies currency conversion when international travel mode is active`() = runTest {
        // ARRANGE
        val travelSettings = TravelModeSettings(
            isEnabled = true,
            tripType = TripType.INTERNATIONAL,
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            conversionRate = 85.0f,
            currencyCode = "USD",
            tripName = "US Trip"
        )
        // Set up the mock to return the active travel settings
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        val transactionCaptor = argumentCaptor<Transaction>()
        `when`(transactionRepository.insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())).thenReturn(1L)

        // Re-initialize the ViewModel to pick up the new mock behavior
        initializeViewModel()
        // Ensure the StateFlow in the ViewModel has time to collect the value from the repository
        advanceUntilIdle()

        // ACT
        viewModel.onSaveTapped(
            description = "Starbucks",
            amountStr = "5.0", // 5 USD
            accountId = 1,
            categoryId = 1, // Category provided, so no nudge
            notes = null,
            date = 1000L, // Within trip date range
            transactionType = "expense",
            imageUris = emptyList()
        ) {}
        advanceUntilIdle() // Run the coroutine launched by onSaveTapped

        // ASSERT
        verify(transactionRepository).insertTransactionWithTagsAndImages(capture(transactionCaptor), anyObject(), anyObject())
        val savedTransaction = transactionCaptor.value
        assertEquals(425.0, savedTransaction.amount, 0.0) // 5.0 * 85.0
        assertEquals(5.0, savedTransaction.originalAmount!!, 0.0)
        assertEquals("USD", savedTransaction.currencyCode)
        assertEquals(85.0, savedTransaction.conversionRate!!, 0.0)
    }


    @Test
    fun `onSaveTapped shows validation error for zero amount`() = runTest {
        // ACT
        viewModel.onSaveTapped("Test", "0.0", 1, 1, null, 0L, "expense", emptyList()) {}
        advanceUntilIdle()

        // ASSERT
        viewModel.validationError.test {
            assertEquals("Please enter a valid, positive amount.", awaitItem())
        }
        verify(transactionRepository, never()).insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())
    }

    @Test
    fun `onSaveTapped shows validation error for missing account`() = runTest {
        // ACT
        viewModel.onSaveTapped("Test", "100.0", null, 1, null, 0L, "expense", emptyList()) {}
        advanceUntilIdle()

        // ASSERT
        viewModel.validationError.test {
            assertEquals("An account must be selected.", awaitItem())
        }
        verify(transactionRepository, never()).insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject())
    }

    @Test
    fun `onAttemptToLeaveScreen prepares retro update sheet when changes are detected`() = runTest {
        // ARRANGE
        val initialTxn = Transaction(id = 1, description = "Starbucks", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, notes = null, originalDescription = "Starbucks")
        val currentTxn = initialTxn.copy(description = "Starbucks Coffee") // Description has changed
        val similarTxns = listOf(Transaction(id = 2, description = "Starbucks", categoryId = 1, amount = 12.0, date = 0L, accountId = 1, notes = null))

        // Mock repository calls
        `when`(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
        `when`(transactionRepository.findSimilarTransactions("Starbucks", 1)).thenReturn(similarTxns)
        // Add mocks for the methods called inside loadTransactionForDetailScreen
        `when`(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
        `when`(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
        `when`(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))


        // ACT
        viewModel.loadTransactionForDetailScreen(1)
        var hasNavigated = false
        viewModel.onAttemptToLeaveScreen { hasNavigated = true }
        advanceUntilIdle()

        // ASSERT
        val sheetState = viewModel.retroUpdateSheetState.first()
        assertNotNull("Sheet state should not be null", sheetState)
        assertEquals(false, hasNavigated) // Should not navigate yet
        assertEquals("Starbucks", sheetState?.originalDescription)
        assertEquals("Starbucks Coffee", sheetState?.newDescription)
        assertEquals(null, sheetState?.newCategoryId) // Category didn't change
        assertEquals(1, sheetState?.similarTransactions?.size)
        assertEquals(2, sheetState?.selectedIds?.first()) // The ID of the similar transaction
    }
}