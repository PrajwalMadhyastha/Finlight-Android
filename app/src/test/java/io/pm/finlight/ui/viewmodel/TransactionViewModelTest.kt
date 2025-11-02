// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TransactionViewModelTest.kt
// REASON: FIX (Test) - The `autoSaveSmsTransaction` test has been updated. It
// now correctly mocks the new `accountAliasDao.findByAlias` dependency, resolving
// a test failure caused by the recent refactoring of the account resolution logic.
// ADDED: Tests for selection mode.
// ADDED: Tests for sharing logic.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.utils.ShareImageGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.lang.RuntimeException
import kotlin.math.roundToLong

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

    // Mocks for DAOs used by the ViewModel and internal logic
    @Mock private lateinit var accountDao: AccountDao
    @Mock private lateinit var categoryDao: CategoryDao
    @Mock private lateinit var tagDao: TagDao
    @Mock private lateinit var transactionDao: TransactionDao
    @Mock private lateinit var customSmsRuleDao: CustomSmsRuleDao
    @Mock private lateinit var ignoreRuleDao: IgnoreRuleDao
    @Mock private lateinit var splitTransactionDao: SplitTransactionDao
    @Mock private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    @Mock private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    @Mock private lateinit var accountAliasDao: AccountAliasDao


    private lateinit var viewModel: TransactionViewModel

    @Before
    override fun setup() {
        super.setup()

        // Mock DAO access from the AppDatabase mock
        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.categoryDao()).thenReturn(categoryDao)
        `when`(db.tagDao()).thenReturn(tagDao)
        `when`(db.transactionDao()).thenReturn(transactionDao)
        `when`(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        `when`(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        `when`(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        `when`(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        `when`(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        `when`(db.splitTransactionDao()).thenReturn(splitTransactionDao)
        `when`(db.accountAliasDao()).thenReturn(accountAliasDao)


        // Setup default mock behaviors for ViewModel initialization
        setupDefaultMocks()

        // Create the ViewModel instance with all mocked dependencies
        initializeViewModel()
    }

    /**
     * Sets up default mock behaviors for all flows and suspend functions
     * called within the TransactionViewModel's `init` block.
     */
    private fun setupDefaultMocks() {
        runTest {
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
            `when`(db.accountDao().findByName(anyString())).thenReturn(null)
            `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
            `when`(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
        }
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
    fun `transactionsForSelectedMonth flow emits data from repository and applies aliases`() = runTest {
        // ARRANGE
        val transaction = Transaction(id = 1, description = "amzn", originalDescription = "amzn", amount = 100.0, date = 1L, accountId = 1, categoryId = 1, notes = null)
        val transactionDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)
        val aliases = mapOf("amzn" to "Amazon")
        `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(listOf(transactionDetails)))
        `when`(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

        // ACT
        initializeViewModel()

        // ASSERT
        viewModel.transactionsForSelectedMonth.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Amazon", result.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `financial summary flows correctly update from repository`() = runTest {
        // ARRANGE
        val summary = FinancialSummary(totalIncome = 10000.50, totalExpenses = 5000.25)
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(summary))

        // ACT
        initializeViewModel()

        // ASSERT
        viewModel.monthlyIncome.test {
            assertEquals(10000.50, awaitItem(), 0.01)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.monthlyExpenses.test {
            assertEquals(5000.25, awaitItem(), 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `approveSmsTransaction creates account if not exists and saves transaction`() = runTest {
        // ARRANGE
        val potentialTxn = PotentialTransaction(1L, "Test", 100.0, "expense", "Test Merchant", "Msg", PotentialAccount("New Account", "Bank"), "hash")
        val transactionCaptor = argumentCaptor<Transaction>()
        `when`(db.accountDao().findByName("New Account")).thenReturn(null).thenReturn(Account(1, "New Account", "Bank"))
        `when`(accountRepository.insert(anyObject())).thenReturn(1L)
        `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(1L)

        // ACT
        val result = viewModel.approveSmsTransaction(potentialTxn, "Test Merchant", null, null, emptySet(), false)
        advanceUntilIdle()

        // ASSERT
        assertTrue(result)
        verify(accountRepository).insert(anyObject())
        verify(transactionRepository).insertTransactionWithTags(capture(transactionCaptor), eq(emptySet()))
        assertEquals("Test Merchant", transactionCaptor.value.description)
    }

    @Test
    fun `autoSaveSmsTransaction saves transaction with correct details`() = runTest {
        // ARRANGE
        val potentialTxn = PotentialTransaction(1L, "Test", 100.0, "expense", "Auto Merchant", "Msg", PotentialAccount("Cash", "Wallet"), "hash", 1)
        val transactionCaptor = argumentCaptor<Transaction>()
        `when`(accountAliasDao.findByAlias(anyString())).thenReturn(null)
        `when`(accountDao.findByName("Cash")).thenReturn(Account(1, "Cash", "Wallet"))
        `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(1L)

        // ACT
        val result = viewModel.autoSaveSmsTransaction(potentialTxn)
        advanceUntilIdle()

        // ASSERT
        assertTrue(result)
        verify(transactionRepository).insertTransactionWithTags(capture(transactionCaptor), eq(emptySet()))
        assertEquals("Auto Merchant", transactionCaptor.value.description)
        assertEquals(1, transactionCaptor.value.categoryId)
    }

    @Test
    @Ignore
    fun `reparseTransactionFromSms updates transaction with new parsed data`() = runTest {
        // ARRANGE
        mockkStatic(SmsParser::class)
        val originalTxn = Transaction(id = 1, description = "Old", categoryId = 1, amount = 100.0, date = 0, accountId = 1, notes = null, sourceSmsId = 123L)
        val sms = SmsMessage(123L, "Sender", "New Merchant spent 150", 0L)
        val newParsedTxn = PotentialTransaction(123L, "Sender", 150.0, "expense", "New Merchant", "Msg", categoryId = 2)

        `when`(transactionRepository.getTransactionById(1)).thenReturn(flowOf(originalTxn))
        `when`(smsRepository.getSmsDetailsById(123L)).thenReturn(sms)

        // Mock the static SmsParser to return our desired new transaction
        coEvery { SmsParser.parse(any<SmsMessage>(), any(), any(), any(), any(), any(), any(), any()) } returns newParsedTxn

        // ACT
        viewModel.reparseTransactionFromSms(1)
        advanceUntilIdle()

        // ASSERT
        verify(transactionRepository).updateDescription(1, "New Merchant")
        verify(transactionRepository).updateCategoryId(1, 2)

        unmockkStatic(SmsParser::class)
    }

    @Test
    @Ignore
    fun `unsplitTransaction correctly calls DAO methods`() = runTest {
        // ARRANGE
        // Mock the withTransaction extension function to execute the lambda passed to it
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction<Unit>(any()) } coAnswers {
            val block = arg<suspend () -> Unit>(0)
            block()
        }

        val transaction = Transaction(id = 1, description = "Split", isSplit = true, originalDescription = "Original Desc", amount = 100.0, date = 0L, accountId = 1, categoryId = null, notes = null)
        val splits = listOf(SplitTransactionDetails(SplitTransaction(1, 1, 50.0, 1, null), "Cat1", "", ""))

        `when`(db.splitTransactionDao().getSplitsForParent(1)).thenReturn(flowOf(splits))

        // ACT
        viewModel.unsplitTransaction(transaction)
        advanceUntilIdle()

        // ASSERT
        verify(splitTransactionDao).deleteSplitsForParent(1)
        verify(transactionDao).unmarkAsSplit(1, "Original Desc", 1)

        unmockkStatic("androidx.room.RoomDatabaseKt")
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

    // --- NEW TESTS FOR ERROR HANDLING ---

    @Test
    fun `saveManualTransaction failure updates validationError`() = runTest {
        // ARRANGE
        val errorMessage = "An error occurred while saving."
        `when`(transactionRepository.insertTransactionWithTagsAndImages(anyObject(), anyObject(), anyObject()))
            .thenThrow(RuntimeException("Database insertion failed"))
        var onSaveCompleteCalled = false

        // ACT
        viewModel.onSaveTapped(
            description = "Test",
            amountStr = "100.0",
            accountId = 1,
            categoryId = 1,
            notes = null,
            date = 0L,
            transactionType = "expense",
            imageUris = emptyList()
        ) { onSaveCompleteCalled = true }
        advanceUntilIdle()

        // ASSERT
        viewModel.validationError.test {
            assertEquals(errorMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse("onSaveComplete should not be called on failure", onSaveCompleteCalled)
    }

    @Test
    fun `deleteTransaction failure sends uiEvent`() = runTest {
        // ARRANGE
        val transactionToDelete = Transaction(id = 1, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null)
        val errorMessage = "Failed to delete transaction. Please try again."
        `when`(transactionRepository.delete(anyObject())).thenThrow(RuntimeException("DB delete failed"))

        // ACT & ASSERT
        viewModel.uiEvent.test {
            viewModel.deleteTransaction(transactionToDelete)
            advanceUntilIdle()
            assertEquals(errorMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmDeleteSelection failure sends uiEvent`() = runTest {
        // ARRANGE
        val errorMessage = "Failed to delete transactions. Please try again."
        `when`(transactionRepository.deleteByIds(anyObject())).thenThrow(RuntimeException("DB batch delete failed"))

        viewModel.enterSelectionMode(1) // Set up the state for deletion
        advanceUntilIdle()

        // ACT & ASSERT
        viewModel.uiEvent.test {
            viewModel.onConfirmDeleteSelection()
            advanceUntilIdle()
            assertEquals(errorMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore
    fun `dataLoading failure emits emptyList and sends uiEvent`() = runTest {
        // ARRANGE
        val errorFlow = flow<List<TransactionDetails>> { throw RuntimeException("DB error") }
        `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(errorFlow)

        // ACT & ASSERT
        viewModel.uiEvent.test {
            // Re-initialize the ViewModel here. This defines the flow with the error mock.
            initializeViewModel()

            // Launch a collector for the lazy flow. This is what triggers
            // the upstream flow, causing the exception to be thrown and the `.catch`
            // block to execute. Using `backgroundScope` prevents this from blocking.
            val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.transactionsForSelectedMonth.collect {
                    // This empty lambda is required to satisfy the `collect` function's signature.
                }
            }

            // Now that collection has started, the error is triggered, caught,
            // and the event is sent. We can now safely await it.
            assertEquals("Failed to load transactions.", awaitItem())

            // We can also check the final state of the other flow.
            assertEquals(emptyList<TransactionDetails>(), viewModel.transactionsForSelectedMonth.value)

            // Clean up the collector job and the Turbine test.
            collectorJob.cancel()
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `updateDescription failure sends uiEvent`() = runTest {
        // ARRANGE
        val errorMessage = "Failed to update description. Please try again."
        `when`(transactionRepository.updateDescription(anyInt(), anyString())).thenThrow(RuntimeException("DB update failed"))
        `when`(transactionRepository.getTransactionById(anyInt())).thenReturn(flowOf(null)) // To simplify the test logic

        // ACT & ASSERT
        viewModel.uiEvent.test {
            viewModel.updateTransactionDescription(1, "New Description")
            advanceUntilIdle()
            assertEquals(errorMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateTransactionType calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val newType = "income"
        // No exception mocked, so the suspend function will just return (simulating success)

        // Act
        viewModel.updateTransactionType(transactionId, newType)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateTransactionType(transactionId, newType)
        // Ensure no error event was sent on success
        viewModel.uiEvent.test {
            expectNoEvents()
        }
    }

    @Test
    fun `updateTransactionType on repository failure sends uiEvent`() = runTest {
        // Arrange
        val transactionId = 1
        val newType = "income"
        val errorMessage = "DB Error"
        `when`(transactionRepository.updateTransactionType(anyInt(), anyString())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.updateTransactionType(transactionId, newType)
            advanceUntilIdle()
            assertEquals("Failed to update transaction type.", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Selection Mode Tests ---

    @Test
    fun `enterSelectionMode activates selection mode and selects initial transaction`() = runTest {
        // Assert initial state
        assertFalse("Selection mode should be inactive initially", viewModel.isSelectionModeActive.value)
        assertTrue("Selected IDs should be empty initially", viewModel.selectedTransactionIds.value.isEmpty())

        // Act
        viewModel.enterSelectionMode(initialTransactionId = 1)
        advanceUntilIdle() // Ensure coroutine in VM completes

        // Assert final state
        assertTrue("Selection mode should be active", viewModel.isSelectionModeActive.value)
        assertEquals(setOf(1), viewModel.selectedTransactionIds.value)
    }

    @Test
    fun `toggleTransactionSelection adds and removes ids correctly`() = runTest {
        viewModel.selectedTransactionIds.test {
            // Initial state
            assertTrue("Selected IDs should be empty initially", awaitItem().isEmpty())

            // Act: Add first item
            viewModel.toggleTransactionSelection(1)
            assertEquals(setOf(1), awaitItem())

            // Act: Add second item
            viewModel.toggleTransactionSelection(2)
            assertEquals(setOf(1, 2), awaitItem())

            // Act: Remove first item
            viewModel.toggleTransactionSelection(1)
            assertEquals(setOf(2), awaitItem())
        }
    }

    @Test
    fun `clearSelectionMode deactivates mode and clears selection`() = runTest {
        // Arrange: Start in selection mode
        viewModel.enterSelectionMode(1)
        viewModel.toggleTransactionSelection(2)
        advanceUntilIdle()
        assertTrue("Pre-condition: Selection mode should be active", viewModel.isSelectionModeActive.value)
        assertEquals("Pre-condition: 2 items selected", setOf(1, 2), viewModel.selectedTransactionIds.value)

        // Act
        viewModel.clearSelectionMode()
        advanceUntilIdle()

        // Assert
        assertFalse("Selection mode should be inactive", viewModel.isSelectionModeActive.value)
        assertTrue("Selected IDs should be empty", viewModel.selectedTransactionIds.value.isEmpty())
    }

    @Test
    fun `onDeleteSelectionClick shows delete confirmation dialog`() = runTest {
        viewModel.showDeleteConfirmation.test {
            // Initial state
            assertFalse("Dialog should be hidden initially", awaitItem())

            // Act
            viewModel.onDeleteSelectionClick()
            advanceUntilIdle()

            // Assert
            assertTrue("Dialog should be visible", awaitItem())
        }
    }

    @Test
    fun `onConfirmDeleteSelection calls repository and clears selection`() = runTest {
        // Arrange
        val idsToDelete = setOf(1, 2)
        viewModel.enterSelectionMode(1)
        viewModel.toggleTransactionSelection(2)
        viewModel.onDeleteSelectionClick() // Show dialog
        advanceUntilIdle()

        // Pre-conditions
        assertTrue(viewModel.isSelectionModeActive.value)
        assertTrue(viewModel.showDeleteConfirmation.value)
        assertEquals(idsToDelete, viewModel.selectedTransactionIds.value)

        // Act
        viewModel.onConfirmDeleteSelection()
        advanceUntilIdle() // Let the coroutine finish

        // Assert
        // Verify the repository was called with the correct list
        verify(transactionRepository).deleteByIds(eq(idsToDelete.toList()))

        // Verify state is reset
        assertFalse("Selection mode should be inactive", viewModel.isSelectionModeActive.value)
        assertFalse("Dialog should be hidden", viewModel.showDeleteConfirmation.value)
        assertTrue("Selected IDs should be empty", viewModel.selectedTransactionIds.value.isEmpty())
    }

    @Test
    fun `onCancelDeleteSelection hides delete confirmation dialog`() = runTest {
        // Arrange
        viewModel.onDeleteSelectionClick() // Show dialog
        advanceUntilIdle()
        assertTrue("Pre-condition: Dialog should be visible", viewModel.showDeleteConfirmation.value)

        // Act
        viewModel.onCancelDeleteSelection()
        advanceUntilIdle()

        // Assert
        assertFalse("Dialog should be hidden", viewModel.showDeleteConfirmation.value)
    }

    // --- NEW: Share Logic Tests ---

    @Test
    fun `onShareClick sets showShareSheet to true`() = runTest {
        viewModel.showShareSheet.test {
            assertFalse("Sheet should be hidden initially", awaitItem())
            viewModel.onShareClick()
            assertTrue("Sheet should be visible after click", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShareSheetDismiss sets showShareSheet to false`() = runTest {
        viewModel.showShareSheet.test {
            assertFalse("Sheet hidden initially", awaitItem())
            viewModel.onShareClick()
            assertTrue("Sheet visible after click", awaitItem())
            viewModel.onShareSheetDismiss()
            assertFalse("Sheet hidden after dismiss", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShareableFieldToggled adds and removes fields from state`() = runTest {
        viewModel.shareableFields.test {
            val defaultFields = awaitItem()
            assertTrue("Default fields should contain Amount", defaultFields.contains(ShareableField.Amount))

            // Act 1: Remove Amount
            viewModel.onShareableFieldToggled(ShareableField.Amount)
            val fieldsAfterRemove = awaitItem()
            assertFalse("Fields should not contain Amount after remove", fieldsAfterRemove.contains(ShareableField.Amount))
            assertEquals(defaultFields.size - 1, fieldsAfterRemove.size)

            // Act 2: Add Amount back
            viewModel.onShareableFieldToggled(ShareableField.Amount)
            val fieldsAfterAdd = awaitItem()
            assertTrue("Fields should contain Amount after add", fieldsAfterAdd.contains(ShareableField.Amount))
            assertEquals(defaultFields.size, fieldsAfterAdd.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

}

