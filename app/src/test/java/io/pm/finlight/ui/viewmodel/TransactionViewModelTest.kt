package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import io.mockk.*
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.utils.ShareImageGenerator
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
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.lang.RuntimeException
import java.util.Calendar
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

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
        whenever(db.accountDao()).thenReturn(accountDao)
        whenever(db.categoryDao()).thenReturn(categoryDao)
        whenever(db.tagDao()).thenReturn(tagDao)
        whenever(db.transactionDao()).thenReturn(transactionDao)
        whenever(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        whenever(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        whenever(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        whenever(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        whenever(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        whenever(db.splitTransactionDao()).thenReturn(splitTransactionDao)
        whenever(db.accountAliasDao()).thenReturn(accountAliasDao)


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
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))
            whenever(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
            whenever(transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getSpendingByMerchantForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
            whenever(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
            whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
            whenever(tagRepository.allTags).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
            whenever(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
            whenever(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))
            whenever(db.accountDao().findByName(anyString())).thenReturn(null)
            whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
            // --- NEW: Add default mock for privacy mode ---
            whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
            whenever(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
            // --- NEW: Default mocks for Quick Fill ---
            whenever(transactionRepository.getRecentManualTransactions(anyInt())).thenReturn(flowOf(emptyList()))
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
        whenever(transactionRepository.getTransactionDetailsForRange(any<Long>(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(flowOf(listOf(transactionDetails)))
        whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

        // ACT
        initializeViewModel()

        // ASSERT
        viewModel.transactionsForSelectedMonth.test {
            advanceUntilIdle()
            val result = expectMostRecentItem()
            assertEquals(1, result.size)
            assertEquals("Amazon", result.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `financial summary flows correctly update from repository`() = runTest {
        // ARRANGE
        val summary = FinancialSummary(totalIncome = 10000.50, totalExpenses = 5000.25)
        whenever(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(summary))

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
        whenever(db.accountDao().findByName("New Account")).thenReturn(null).thenReturn(Account(1, "New Account", "Bank"))
        whenever(accountRepository.insert(any())).thenReturn(1L)
        whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

        // ACT
        val result = viewModel.approveSmsTransaction(potentialTxn, "Test Merchant", null, null, emptySet(), false)
        advanceUntilIdle()

        // ASSERT
        assertTrue(result)
        verify(accountRepository).insert(any())
        verify(transactionRepository).insertTransactionWithTags(transactionCaptor.capture(), eq(emptySet()))
        assertEquals("Test Merchant", transactionCaptor.firstValue.description)
    }

    @Test
    fun `autoSaveSmsTransaction saves transaction with correct details`() = runTest {
        // ARRANGE
        val potentialTxn = PotentialTransaction(1L, "Test", 100.0, "expense", "Auto Merchant", "Msg", PotentialAccount("Cash", "Wallet"), "hash", 1)
        val transactionCaptor = argumentCaptor<Transaction>()
        whenever(accountAliasDao.findByAlias(anyString())).thenReturn(null)
        whenever(accountDao.findByName("Cash")).thenReturn(Account(1, "Cash", "Wallet"))
        whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

        // ACT
        val result = viewModel.autoSaveSmsTransaction(potentialTxn)
        advanceUntilIdle()

        // ASSERT
        assertTrue(result)
        verify(transactionRepository).insertTransactionWithTags(transactionCaptor.capture(), eq(emptySet()))
        assertEquals("Auto Merchant", transactionCaptor.firstValue.description)
        assertEquals(1, transactionCaptor.firstValue.categoryId)
    }

    @Test
    fun `reparseTransactionFromSms updates transaction with new parsed data`() = runTest {
        // ARRANGE
        mockkObject(SmsParser)
        val originalTxn = Transaction(id = 1, description = "Old", categoryId = 1, amount = 100.0, date = 0, accountId = 1, notes = null, sourceSmsId = 123L)
        val sms = SmsMessage(123L, "Sender", "New Merchant spent 150", 0L)
        val newParsedTxn = PotentialTransaction(123L, "Sender", 150.0, "expense", "New Merchant", "Msg", categoryId = 2)

        whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(originalTxn))
        whenever(smsRepository.getSmsDetailsById(123L)).thenReturn(sms)
        whenever(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        whenever(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        whenever(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        whenever(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        whenever(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        whenever(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        whenever(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())

        // Mock the object SmsParser to return our desired new transaction
        coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(newParsedTxn)

        // ACT
        viewModel.reparseTransactionFromSms(1)
        advanceUntilIdle()

        // ASSERT
        verify(transactionRepository).updateDescription(1, "New Merchant")
        verify(transactionRepository).updateCategoryId(1, 2)

        unmockkObject(SmsParser)
    }

    @Test
    fun `unsplitTransaction correctly calls DAO methods`() = runTest {
        // ARRANGE
        mockkStatic("androidx.room.RoomDatabaseKt")
        
        // Use a more robust mock for withTransaction that doesn't rely on complex arg matching if possible
        coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        val transaction = Transaction(id = 1, description = "Split", isSplit = true, originalDescription = "Original Desc", amount = 100.0, date = 0L, accountId = 1, categoryId = null, notes = null)
        val splits = listOf(SplitTransactionDetails(SplitTransaction(1, 1, 50.0, 1, null), "Cat1", "", ""))

        whenever(splitTransactionDao.getSplitsForParentSimple(1)).thenReturn(splits)

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

    // --- NEW: Filter Logic Tests ---

    @Test
    fun `updateFilterAccount updates filterState correctly`() = runTest {
        // ARRANGE
        val newAccount = Account(1, "Test Account", "Bank")

        // ACT
        viewModel.updateFilterAccount(newAccount)

        // ASSERT
        val updatedState = viewModel.filterState.first()
        assertEquals(newAccount, updatedState.account)
    }

    @Test
    fun `updateFilterCategory updates filterState correctly`() = runTest {
        // ARRANGE
        val newCategory = Category(1, "Test Category", "icon", "color")

        // ACT
        viewModel.updateFilterCategory(newCategory)

        // ASSERT
        val updatedState = viewModel.filterState.first()
        assertEquals(newCategory, updatedState.category)
    }

    @Test
    fun `clearFilters resets filterState to default`() = runTest {
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
    fun `onFilterClick sets showFilterSheet to true`() = runTest {
        viewModel.showFilterSheet.test {
            assertFalse("Sheet should be hidden initially", awaitItem())
            viewModel.onFilterClick()
            assertTrue("Sheet should be visible after click", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onFilterSheetDismiss sets showFilterSheet to false`() = runTest {
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
    fun `onSaveTapped with only amount saves directly without category nudge`() = runTest {
        // ARRANGE
        val accountId = 1
        var onSaveCompleteCalled = false
        whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any())).thenReturn(1L)

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
        verify(transactionRepository).insertTransactionWithTagsAndImages(any(), any(), any())
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
        verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
        assertFalse(onSaveCompleteCalled)
    }

    @Test
    fun `saveWithSelectedCategory saves transaction with correct data from nudge`() = runTest {
        // ARRANGE
        val accountId = 1
        var onSaveCompleteCalled = false
        val newCategoryId = 5
        val transactionCaptor = argumentCaptor<Transaction>()

        whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any())).thenReturn(1L)

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
        verify(transactionRepository).insertTransactionWithTagsAndImages(transactionCaptor.capture(), any(), any())
        val savedTransaction = transactionCaptor.firstValue
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
        whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        val transactionCaptor = argumentCaptor<Transaction>()
        whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any())).thenReturn(1L)

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
        verify(transactionRepository).insertTransactionWithTagsAndImages(transactionCaptor.capture(), any(), any())
        val savedTransaction = transactionCaptor.firstValue
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
        verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
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
        verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
    }

    @Test
    fun `onAttemptToLeaveScreen prepares retro update sheet when changes are detected`() = runTest {
        // ARRANGE
        val initialTxn = Transaction(id = 1, description = "Starbucks", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, notes = null, originalDescription = "Starbucks")
        val currentTxn = initialTxn.copy(description = "Starbucks Coffee") // Description has changed
        val similarTxns = listOf(Transaction(id = 2, description = "Starbucks", categoryId = 1, amount = 12.0, date = 0L, accountId = 1, notes = null))

        // Mock repository calls
        whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
        whenever(transactionRepository.findSimilarTransactions("Starbucks", 1)).thenReturn(similarTxns)
        // Add mocks for the methods called inside loadTransactionForDetailScreen
        whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
        whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
        whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))


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

    // --- NEW: Simple Update Function Tests (Success Path) ---

    @Test
    fun `updateTransactionAmount calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val newAmountStr = "123.45"
        val newAmountDouble = 123.45

        // Act
        viewModel.updateTransactionAmount(transactionId, newAmountStr)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateAmount(transactionId, newAmountDouble)
    }

    @Test
    fun `updateTransactionNotes calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val newNotes = "This is a test note"

        // Act
        viewModel.updateTransactionNotes(transactionId, newNotes)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateNotes(transactionId, newNotes)
    }

    @Test
    fun `updateTransactionCategory calls repository and mapping logic`() = runTest {
        // Arrange
        val transactionId = 1
        val newCategoryId = 5
        val originalDescription = "Original Merchant"
        val transaction = Transaction(
            id = transactionId,
            description = "Test",
            amount = 1.0,
            date = 0,
            accountId = 1,
            categoryId = 1,
            notes = null,
            originalDescription = originalDescription
        )

        whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(transaction))

        // Act
        viewModel.updateTransactionCategory(transactionId, newCategoryId)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateCategoryId(transactionId, newCategoryId)
        // Verify learning logic is also called
        verify(merchantCategoryMappingRepository).insert(
            MerchantCategoryMapping(
                parsedName = originalDescription,
                categoryId = newCategoryId
            )
        )
    }

    @Test
    fun `updateTransactionAccount calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val newAccountId = 2

        // Act
        viewModel.updateTransactionAccount(transactionId, newAccountId)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateAccountId(transactionId, newAccountId)
    }

    @Test
    fun `updateTransactionDate calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val newDate = 123456789L

        // Act
        viewModel.updateTransactionDate(transactionId, newDate)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateDate(transactionId, newDate)
    }

    @Test
    fun `updateTransactionExclusion calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val newExclusionStatus = true

        // Act
        viewModel.updateTransactionExclusion(transactionId, newExclusionStatus)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateExclusionStatus(transactionId, newExclusionStatus)
    }

    // --- NEW TESTS FOR ERROR HANDLING ---

    @Test
    fun `saveManualTransaction failure updates validationError`() = runTest {
        // ARRANGE
        val errorMessage = "An error occurred while saving."
        whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any()))
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
        whenever(transactionRepository.delete(any())).thenThrow(RuntimeException("DB delete failed"))

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
        whenever(transactionRepository.deleteByIds(any())).thenThrow(RuntimeException("DB batch delete failed"))

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
    fun `dataLoading failure emits emptyList and sends uiEvent`() = runTest {
        // ARRANGE
        val errorFlow = flow<List<TransactionDetails>> { throw RuntimeException("DB error") }
        whenever(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(errorFlow)

        // FIX: Re-initialize the ViewModel BEFORE the test block.
        // This ensures the current 'viewModel' instance uses the mock above.
        initializeViewModel()

        // ACT & ASSERT
        viewModel.uiEvent.test {
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
        whenever(transactionRepository.updateDescription(anyInt(), anyString())).thenThrow(RuntimeException("DB update failed"))
        whenever(transactionRepository.getTransactionById(anyInt())).thenReturn(flowOf(null)) // To simplify the test logic

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
        whenever(transactionRepository.updateTransactionType(anyInt(), anyString())).thenThrow(RuntimeException(errorMessage))

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

            // Act: Remove second item
            viewModel.toggleTransactionSelection(2)
            assertTrue("Should be empty again", awaitItem().isEmpty())
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

    // --- NEW: Simple State Update Tests ---

    @Test
    fun `onAddTransactionDescriptionChanged updates suggestion flow and resets manual select on blank`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food & Drinks", "icon", "color")
        whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
        initializeViewModel()

        viewModel.suggestedCategory.test(timeout = 5.seconds) { // Increase timeout for debounce
            assertNull("Initial suggestion should be null", awaitItem())

            // Act 1: Type a keyword
            viewModel.onAddTransactionDescriptionChanged("Coffee")
            advanceTimeBy(500) // Wait for debounce

            // Assert 1: Suggestion appears
            assertEquals("Food & Drinks", awaitItem()?.name)

            // Act 2: Manually select a category (simulated)
            viewModel.onUserManuallySelectedCategory()
            advanceUntilIdle()

            // Act 3: Clear description
            viewModel.onAddTransactionDescriptionChanged("")
            advanceTimeBy(500) // Wait for debounce

            // Assert 3: Suggestion becomes null
            assertEquals(null, awaitItem())

            // Act 4: Type keyword again to see if manual select was reset
            viewModel.onAddTransactionDescriptionChanged("Pizza")
            advanceTimeBy(500) // Wait for debounce

            // Assert 4: Suggestion should reappear, proving manual select was reset
            assertEquals("Food & Drinks", awaitItem()?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onUserManuallySelectedCategory stops suggestedCategory flow`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food & Drinks", "icon", "color")
        whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
        initializeViewModel()

        viewModel.suggestedCategory.test(timeout = 5.seconds) {
            assertNull("Initial suggestion should be null", awaitItem())

            // Act 1: Type a keyword, get suggestion
            viewModel.onAddTransactionDescriptionChanged("Coffee")
            advanceTimeBy(500)
            assertEquals("Food & Drinks", awaitItem()?.name)

            // Act 2: Trigger manual select
            viewModel.onUserManuallySelectedCategory()
            advanceUntilIdle()

            // Act 3: Type another keyword
            viewModel.onAddTransactionDescriptionChanged("Burger")
            advanceTimeBy(500)

            // Assert 3: Suggestion should be null because manual select is true
            assertEquals(null, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAddTransactionState resets all add-transaction states`() = runTest {
        // Arrange
        val foodCategory = Category(1, "Food & Drinks", "icon", "color")
        val testTag = Tag(1, "Test")
        whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
        initializeViewModel()

        // Set up a dirty state
        viewModel.onAddTransactionDescriptionChanged("Coffee")
        viewModel.onTagSelected(testTag)
        viewModel.onUserManuallySelectedCategory()
        advanceUntilIdle()

        // Pre-condition asserts
        assertEquals(setOf(testTag), viewModel.selectedTags.value)

        // Act
        viewModel.clearAddTransactionState()
        advanceUntilIdle()

        // Assert 1: Check cleared states
        assertTrue("Selected tags should be empty", viewModel.selectedTags.value.isEmpty())

        // Assert 2: Check if description and manual select were reset by testing suggestedCategory
        viewModel.suggestedCategory.test(timeout = 5.seconds) {
            assertNull("Suggestion should be null initially after clear", awaitItem())

            viewModel.onAddTransactionDescriptionChanged("Coffee")
            advanceTimeBy(500)

            assertEquals("Suggestion should appear, proving state was reset", "Food & Drinks", awaitItem()?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearOriginalSms clears the sms text flow`() = runTest {
        // Arrange
        val transactionId = 1
        val smsId = 123L
        val smsBody = "This is the original SMS body"
        val transaction = Transaction(id = transactionId, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null, sourceSmsId = smsId, originalDescription = "Test")

        // Mocks for loadTransactionForDetailScreen
        whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(transaction))
        whenever(transactionRepository.getTagsForTransaction(transactionId)).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.getImagesForTransaction(transactionId)).thenReturn(flowOf(emptyList()))
        whenever(smsRepository.getSmsDetailsById(smsId)).thenReturn(SmsMessage(smsId, "Sender", smsBody, 0L))
        whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

        initializeViewModel()

        viewModel.originalSmsText.test {
            assertNull("Initial state should be null", awaitItem())

            // Act 1: Load the SMS
            viewModel.loadTransactionForDetailScreen(transactionId)
            advanceUntilIdle()

            // Assert 1: SMS is loaded
            assertEquals(smsBody, awaitItem())

            // Act 2: Clear the SMS
            viewModel.clearOriginalSms()

            // Assert 2: SMS is cleared
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Tag Logic Tests ---

    @Test
    fun `updateTagsForTransaction calls repository successfully`() = runTest {
        // Arrange
        val transactionId = 1
        val tags = setOf(Tag(1, "test"))
        // Set the internal state that the VM function will read from
        tags.forEach { viewModel.onTagSelected(it) }
        advanceUntilIdle()

        // Act
        viewModel.updateTagsForTransaction(transactionId) // Call with only the ID
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).updateTagsForTransaction(transactionId, tags)
        // Check no error event
        viewModel.uiEvent.test {
            expectNoEvents()
        }
    }

    @Test
    fun `updateTagsForTransaction failure sends uiEvent`() = runTest {
        // Arrange
        val transactionId = 1
        val tags = setOf(Tag(1, "test"))
        val errorMessage = "Failed to update tags. Please try again."

        // Set the internal state
        tags.forEach { viewModel.onTagSelected(it) }
        advanceUntilIdle()

        // Mock the repository call to throw an exception
        whenever(transactionRepository.updateTagsForTransaction(eq(transactionId), eq(tags)))
            .thenThrow(RuntimeException("DB update failed"))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.updateTagsForTransaction(transactionId) // Call with only the ID
            advanceUntilIdle() // Re-add advanceUntilIdle to fix timeout
            assertEquals(errorMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTagSelected adds and removes tags from selectedTags flow`() = runTest {
        // Arrange
        val tag1 = Tag(1, "Groceries")
        val tag2 = Tag(2, "Fun")

        viewModel.selectedTags.test {
            // Initial state
            assertTrue("Initial tags should be empty", awaitItem().isEmpty())

            // Act 1: Add tag1
            viewModel.onTagSelected(tag1)
            assertEquals("Should contain tag1", setOf(tag1), awaitItem())

            // Act 2: Add tag2
            viewModel.onTagSelected(tag2)
            assertEquals("Should contain tag1 and tag2", setOf(tag1, tag2), awaitItem())

            // Act 3: Remove tag1
            viewModel.onTagSelected(tag1)
            assertEquals("Should only contain tag2", setOf(tag2), awaitItem())

            // Act 4: Remove tag2
            viewModel.onTagSelected(tag2)
            assertTrue("Should be empty again", awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addTagOnTheGo inserts new tag and selects it`() = runTest {
        // Arrange
        val newTagName = "NewTag"
        val newTagRowId = 10L // This is the Long ID returned from the repository
        // This mocks the repository's insert function, which should take a Tag with no ID
        // and return the new row ID (Long).
        whenever(tagRepository.insert(Tag(name = newTagName))).thenReturn(newTagRowId)

        viewModel.selectedTags.test {
            // Initial state
            assertTrue("Initial tags should be empty", awaitItem().isEmpty())

            // Act
            viewModel.addTagOnTheGo(newTagName)
            advanceUntilIdle() // Let the coroutine launch

            // Assert
            // 1. Verify repository insert was called correctly
            verify(tagRepository).insert(Tag(name = newTagName))

            // 2. Verify the selectedTags flow was updated with the new tag,
            // which should now have the ID cast to an Int for the Tag data class.
            val expectedTag = Tag(id = newTagRowId.toInt(), name = newTagName)
            assertEquals("Should contain the new tag", setOf(expectedTag), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addTagOnTheGo failure sends uiEvent`() = runTest {
        // Arrange
        val newTagName = "BadTag"
        val errorMessage = "Failed to add new tag. Please try again."
        whenever(tagRepository.insert(Tag(name = newTagName)))
            .thenThrow(RuntimeException("DB insert failed"))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.addTagOnTheGo(newTagName)
            advanceUntilIdle() // Re-add advanceUntilIdle to fix timeout

            // Assert
            assertEquals(errorMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Also check that selectedTags was not modified
        viewModel.selectedTags.test {
            assertTrue("Tags should remain empty on failure", awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Retro Update Sheet Tests ---

    /**
     * Helper to set the ViewModel's retroUpdateSheetState to a valid, non-null value.
     * This simulates the sheet being opened by onAttemptToLeaveScreen.
     */
    private fun setupRetroSheet(
        newDesc: String? = "Starbucks Coffee",
        newCatId: Int? = null,
        numSimilar: Int = 2
    ) = runTest {
        val initialTxn = Transaction(id = 1, description = "Starbucks", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, notes = null, originalDescription = "Starbucks")
        val currentTxn = initialTxn.copy(
            description = newDesc ?: initialTxn.description,
            categoryId = newCatId ?: initialTxn.categoryId
        )
        val similarTxns = (2 until 2 + numSimilar).map {
            Transaction(id = it, description = "Starbucks", categoryId = 1, amount = 12.0, date = 0L, accountId = 1, notes = null)
        }

        whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
        whenever(transactionRepository.findSimilarTransactions("Starbucks", 1)).thenReturn(similarTxns)
        whenever(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
        whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
        whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

        viewModel.loadTransactionForDetailScreen(1)
        viewModel.onAttemptToLeaveScreen { }
        advanceUntilIdle()
    }

    @Test
    fun `dismissRetroUpdateSheet sets state to null`() = runTest {
        // ARRANGE
        setupRetroSheet()
        assertNotNull("Pre-condition: Sheet state should be set", viewModel.retroUpdateSheetState.value)

        // ACT
        viewModel.dismissRetroUpdateSheet()

        // ASSERT
        assertNull("Sheet state should be null after dismiss", viewModel.retroUpdateSheetState.value)
    }

    @Test
    fun `toggleRetroUpdateSelection adds and removes id`() = runTest {
        // ARRANGE
        setupRetroSheet() // Sets initial selected IDs to {2, 3}
        assertEquals(setOf(2, 3), viewModel.retroUpdateSheetState.value?.selectedIds)

        // ACT 1: Remove ID 2
        viewModel.toggleRetroUpdateSelection(2)
        advanceUntilIdle()
        // ASSERT 1
        assertEquals(setOf(3), viewModel.retroUpdateSheetState.value?.selectedIds)

        // ACT 2: Add ID 4
        viewModel.toggleRetroUpdateSelection(4)
        advanceUntilIdle()
        // ASSERT 2
        assertEquals(setOf(3, 4), viewModel.retroUpdateSheetState.value?.selectedIds)
    }

    @Test
    fun `toggleRetroUpdateSelectAll selects all and deselects all`() = runTest {
        // ARRANGE
        setupRetroSheet(numSimilar = 3) // similar = {2, 3, 4}, selected = {2, 3, 4}
        assertEquals(setOf(2, 3, 4), viewModel.retroUpdateSheetState.value?.selectedIds)

        // ACT 1: Deselect all (since all are selected)
        viewModel.toggleRetroUpdateSelectAll()
        advanceUntilIdle()
        // ASSERT 1
        assertEquals(emptySet<Int>(), viewModel.retroUpdateSheetState.value?.selectedIds)

        // ACT 2: Select all
        viewModel.toggleRetroUpdateSelectAll()
        advanceUntilIdle()
        // ASSERT 2
        assertEquals(setOf(2, 3, 4), viewModel.retroUpdateSheetState.value?.selectedIds)
    }

    @Test
    fun `performBatchUpdate updates all fields, creates rule, and dismisses`() = runTest {
        // ARRANGE
        setupRetroSheet(newDesc = "Starbucks Coffee", newCatId = 5, numSimilar = 2)
        val expectedIdsToUpdate = listOf(2, 3)

        // Pre-condition check
        val state = viewModel.retroUpdateSheetState.value
        assertNotNull(state)
        assertEquals(setOf(2, 3), state?.selectedIds)
        assertEquals("Starbucks Coffee", state?.newDescription)
        assertEquals(5, state?.newCategoryId)

        // ACT & ASSERT
        viewModel.uiEvent.test {
            viewModel.performBatchUpdate()
            advanceUntilIdle()

            // Verify repo calls
            verify(merchantRenameRuleRepository).insert(MerchantRenameRule("Starbucks", "Starbucks Coffee"))
            verify(transactionRepository).updateDescriptionForIds(expectedIdsToUpdate, "Starbucks Coffee")
            verify(transactionRepository).updateCategoryForIds(expectedIdsToUpdate, 5)

            // Verify UI event
            assertEquals("Updated 2 transaction(s).", awaitItem())

            // Verify dismiss
            assertNull("Sheet should be dismissed", viewModel.retroUpdateSheetState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `performBatchUpdate with no IDs selected just dismisses`() = runTest {
        // ARRANGE
        setupRetroSheet()
        assertNotNull(viewModel.retroUpdateSheetState.value)

        // ACT: Deselect all
        viewModel.toggleRetroUpdateSelectAll() // Deselects {2, 3}
        advanceUntilIdle()
        assertEquals(emptySet<Int>(), viewModel.retroUpdateSheetState.value?.selectedIds)

        viewModel.performBatchUpdate()
        advanceUntilIdle()

        // ASSERT
        verify(merchantRenameRuleRepository, never()).insert(any())
        verify(transactionRepository, never()).updateDescriptionForIds(any(), anyString())
        verify(transactionRepository, never()).updateCategoryForIds(any(), anyInt())
        assertNull("Sheet should be dismissed", viewModel.retroUpdateSheetState.value)
    }

    @Test
    fun `performBatchUpdate on failure sends error event and dismisses`() = runTest {
        // ARRANGE
        setupRetroSheet()
        assertNotNull(viewModel.retroUpdateSheetState.value)
        val expectedErrorMessage = "Batch update failed. Please try again."

        // --- MODIFICATION: Mock failure ---
        whenever(transactionRepository.updateDescriptionForIds(any(), anyString()))
            .thenThrow(RuntimeException("Database failed!"))

        // ACT & ASSERT
        viewModel.uiEvent.test {
            viewModel.performBatchUpdate()

            // Note: advanceUntilIdle() is not needed here because the testDispatcher is Unconfined
            // and the coroutine will execute eagerly.

            // Verify error event
            assertEquals(expectedErrorMessage, awaitItem())

            // Verify state is still dismissed (due to finally block)
            assertNull("Sheet should be dismissed even on failure", viewModel.retroUpdateSheetState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Tests for Merchant Search ---

    @Test
    fun `onMerchantSearchQueryChanged triggers merchantPredictions flow`() = runTest {
        // Arrange
        val query = "Coffee"
        val mockPredictions = listOf(
            MerchantPrediction("Coffee Shop", 1, "Food", "icon", "color", null, null)
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
    fun `clearMerchantSearch clears merchantPredictions flow`() = runTest {
        // Arrange
        val query = "Coffee"
        val mockPredictions = listOf(
            MerchantPrediction("Coffee Shop", 1, "Food", "icon", "color", null, null)
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

    // --- NEW: Tests for Category Change Request ---

    @Test
    fun `requestCategoryChange updates flow`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null)
        val mockDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)

        // Act & Assert
        viewModel.transactionForCategoryChange.test {
            assertNull("Initial state should be null", awaitItem())
            viewModel.requestCategoryChange(mockDetails)
            assertEquals("State should be updated with details", mockDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelCategoryChange resets flow to null`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null)
        val mockDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)

        // Act & Assert
        viewModel.transactionForCategoryChange.test {
            assertNull("Initial state should be null", awaitItem())

            viewModel.requestCategoryChange(mockDetails)
            assertEquals("State should be updated with details", mockDetails, awaitItem())

            viewModel.cancelCategoryChange()
            assertNull("State should be reset to null", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Test for getSplitDetailsForTransaction ---

    @Test
    fun `getSplitDetailsForTransaction calls repository`() = runTest {
        // Arrange
        val transactionId = 1
        val mockSplits = listOf(
            SplitTransactionDetails(SplitTransaction(1, 1, 50.0, 1, null), "Cat1", "", "")
        )
        whenever(splitTransactionRepository.getSplitsForParent(transactionId)).thenReturn(flowOf(mockSplits))

        // Act
        val resultFlow = viewModel.getSplitDetailsForTransaction(transactionId)

        // Assert
        resultFlow.test {
            assertEquals("Flow should emit repository data", mockSplits, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(splitTransactionRepository).getSplitsForParent(transactionId)
    }

    // --- NEW: Tests for functions requested in the last prompt ---

    @Test
    fun `saveTransactionSplits calls DAOs within transaction`() = runTest {
        // Arrange
        val transactionId = 1
        val parentTxn = Transaction(id = transactionId, description = "Parent", amount = 100.0, date = 0L, accountId = 1, categoryId = 1, notes = null)
        val category = Category(1, "Food", "icon", "color")
        val splitItems = listOf(
            SplitItem(-1, "60.0", category, "Lunch"),
            SplitItem(-2, "40.0", null, "Snacks")
        )
        var onCompleteCalled = false

        whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))

        // Mock the withTransaction block
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
        val listCaptor = argumentCaptor<List<SplitTransaction>>()

        // Act
        viewModel.saveTransactionSplits(transactionId, splitItems) { onCompleteCalled = true }
        advanceUntilIdle()

        // Assert
        coVerify { db.withTransaction<Unit>(any()) }
        verify(transactionDao).markAsSplit(transactionId, true)
        verify(splitTransactionDao).deleteSplitsForParent(transactionId)
        verify(splitTransactionDao).insertAll(listCaptor.capture())

        val capturedSplits = listCaptor.firstValue
        assertEquals(2, capturedSplits.size)
        assertEquals(60.0, capturedSplits[0].amount, 0.001)
        assertEquals(1, capturedSplits[0].categoryId)
        assertEquals("Lunch", capturedSplits[0].notes)
        assertEquals(40.0, capturedSplits[1].amount, 0.001)
        assertNull(capturedSplits[1].categoryId)

        assertTrue(onCompleteCalled)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `findTransactionDetailsById applies aliases`() = runTest {
        // Arrange
        val transactionId = 1
        val transaction = Transaction(id = transactionId, description = "amzn", originalDescription = "amzn", amount = 100.0, date = 1L, accountId = 1, categoryId = 1, notes = null)
        val mockDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)
        val aliases = mapOf("amzn" to "Amazon")

        whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(mockDetails))
        whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

        // --- THIS IS THE FIX ---
        // Re-initialize the ViewModel *after* the test-specific mock for getAliasesAsMap is set.
        // This ensures the `merchantAliases` StateFlow in the ViewModel's init block
        // collects the correct map (with "Amazon") instead of the default `emptyMap()`.
        initializeViewModel()

        // Act
        viewModel.findTransactionDetailsById(transactionId).test {
            // Assert
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Amazon", result!!.transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionRepository).getTransactionDetailsById(transactionId)
    }

    @Test
    fun `setSelectedMonth updates selectedMonth flow`() = runTest {
        // Arrange
        val newCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

        // Act & Assert
        viewModel.selectedMonth.test {
            val initialMonth = awaitItem().get(Calendar.MONTH)

            viewModel.setSelectedMonth(newCalendar)

            val newMonth = awaitItem().get(Calendar.MONTH)
            assertEquals(newCalendar.get(Calendar.MONTH), newMonth)
            assertNotEquals(initialMonth, newMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createAccount success case`() = runTest {
        // Arrange
        val newAccountName = "New Bank"
        val newAccountType = "Bank"
        val newAccount = Account(1, newAccountName, newAccountType)
        var createdAccount: Account? = null

        whenever(db.accountDao().findByName(newAccountName)).thenReturn(null)
        whenever(accountRepository.insert(Account(name = newAccountName, type = newAccountType))).thenReturn(1L)
        whenever(accountRepository.getAccountById(1)).thenReturn(flowOf(newAccount))

        // Act
        viewModel.createAccount(newAccountName, newAccountType) { createdAccount = it }
        advanceUntilIdle()

        // Assert
        verify(accountRepository).insert(Account(name = newAccountName, type = newAccountType))
        assertEquals(newAccount, createdAccount)
    }

    @Test
    fun `createAccount failure on duplicate name`() = runTest {
        // Arrange
        val existingAccountName = "Existing Bank"
        val existingAccount = Account(1, existingAccountName, "Bank")
        var createdAccount: Account? = null

        whenever(db.accountDao().findByName(existingAccountName)).thenReturn(existingAccount)

        // Act & Assert
        viewModel.validationError.test {
            assertNull(awaitItem()) // Initial null
            viewModel.createAccount(existingAccountName, "Bank") { createdAccount = it }
            advanceUntilIdle()

            assertEquals("An account named '$existingAccountName' already exists.", awaitItem())
            assertNull(createdAccount)
            verify(accountRepository, never()).insert(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createCategory success case`() = runTest {
        // Arrange
        val newCategoryName = "New Stuff"
        val newIcon = "icon"
        val newColor = "color"
        val newCategory = Category(1, newCategoryName, newIcon, newColor)
        var createdCategory: Category? = null

        whenever(db.categoryDao().findByName(newCategoryName)).thenReturn(null)
        whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList())) // For color helper
        whenever(categoryRepository.insert(Category(name = newCategoryName, iconKey = newIcon, colorKey = newColor))).thenReturn(1L)
        whenever(categoryRepository.getCategoryById(1)).thenReturn(newCategory)

        // Act
        viewModel.createCategory(newCategoryName, newIcon, newColor) { createdCategory = it }
        advanceUntilIdle()

        // Assert
        verify(categoryRepository).insert(Category(name = newCategoryName, iconKey = newIcon, colorKey = newColor))
        assertEquals(newCategory, createdCategory)
    }

    @Test
    fun `createCategory failure on duplicate name`() = runTest {
        // Arrange
        val existingCategoryName = "Food"
        val existingCategory = Category(1, existingCategoryName, "icon", "color")
        var createdCategory: Category? = null

        whenever(db.categoryDao().findByName(existingCategoryName)).thenReturn(existingCategory)

        // Act & Assert
        viewModel.validationError.test {
            assertNull(awaitItem()) // Initial null
            viewModel.createCategory(existingCategoryName, "icon", "color") { createdCategory = it }
            advanceUntilIdle()

            assertEquals("A category named '$existingCategoryName' already exists.", awaitItem())
            assertNull(createdCategory)
            verify(categoryRepository, never()).insert(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearError resets validationError flow`() = runTest {
        // Act & Assert
        viewModel.validationError.test {
            assertNull("Initial state should be null", awaitItem())

            // Manually trigger an error to set the state
            viewModel.onSaveTapped("Test", "0.0", 1, 1, null, 0L, "expense", emptyList()) {}
            advanceUntilIdle()

            assertEquals("Error should be set", "Please enter a valid, positive amount.", awaitItem())

            // Act: Call clearError
            viewModel.clearError()

            assertNull("Error should be cleared to null", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW TEST ---
    @Test
    fun `isPrivacyModeEnabled flow emits value from settingsRepository`() = runTest {
        // Arrange
        val privacyFlow = flowOf(true)
        whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(privacyFlow)
        initializeViewModel() // Re-initialize to pick up the new mock

        // Act & Assert
        viewModel.isPrivacyModeEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // --- THE FIX ---
        // Verify times(2) because it was called once in setup() and once again
        // in this test's initializeViewModel() call.
        verify(settingsRepository, times(2)).getPrivacyModeEnabled()
    }

    @Test
    fun `onQuickFillSelected populates state correctly`() = runTest {
        // Arrange - Setup specific mocks for this test
        whenever(accountRepository.allAccounts).thenReturn(flowOf(listOf(
            Account(id = 1, name = "Cash", type = "Cash"),
            Account(id = 2, name = "Bank", type = "Bank")
        )))
        whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(
            Category(id = 10, name = "Food", iconKey = "food", colorKey = "green"),
            Category(id = 11, name = "Transport", iconKey = "car", colorKey = "blue")
        )))

        // Re-initialize ViewModel to pick up the new flows
        initializeViewModel()

        // FIX: Start collecting `allAccounts` to trigger the `stateIn(WhileSubscribed)` upstream flow.
        // Without this, `allAccounts.first()` inside the ViewModel returns the initial `emptyList()`
        // because the StateFlow hasn't connected to the repository flow yet.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allAccounts.collect { }
        }

        // Ensure values are propagated
        advanceUntilIdle()

        val mockTransaction = Transaction(
            id = 100,
            description = "Quick Lunch",
            amount = 120.50, // Has decimals
            date = System.currentTimeMillis(),
            accountId = 1, // "Cash"
            categoryId = 10, // "Food"
            source = "Manual Entry",
            transactionType = "expense",
            notes = null
        )
        val mockDetails = TransactionDetails(
            transaction = mockTransaction,
            images = emptyList(),
            accountName = "Cash",
            categoryName = "Food",
            categoryIconKey = "food",
            categoryColorKey = "green",
            tagNames = null
        )

        // Mock getting tags for this transaction (e.g., return empty list)
        whenever(transactionRepository.getTagsForTransactionSimple(100)).thenReturn(emptyList())

        // Act
        viewModel.onQuickFillSelected(mockDetails)

        // Advance time to allow coroutines launched within the VM to complete
        testScheduler.advanceUntilIdle()

        // Assert
        assertEquals("Description should match", "Quick Lunch", viewModel.addTransactionDescription.value)
        assertEquals("Amount should be formatted to 2 decimals", "120.50", viewModel.addTransactionAmount.value)
        assertEquals("Category ID should match", 10, viewModel.addTransactionCategory.value?.id)
        assertEquals("Category Name should match", "Food", viewModel.addTransactionCategory.value?.name)
        assertEquals("Account ID should match", 1, viewModel.addTransactionAccount.value?.id)
        assertEquals("Account Name should match", "Cash", viewModel.addTransactionAccount.value?.name)
    }

    @Test
    fun `createAccount validation fails for blank name or type`() = runTest {
        // Act
        viewModel.createAccount("", "Bank") { fail("Should not be called") }
        viewModel.createAccount("Name", "") { fail("Should not be called") }
        advanceUntilIdle()

        // Assert
        verify(accountRepository, never()).insert(any())
    }

    @Test
    fun `createAccount fails for existing account name`() = runTest {
        // Arrange
        whenever(db.accountDao().findByName("Savings")).thenReturn(Account(1, "Savings", "Bank"))
        
        // Act
        viewModel.createAccount("Savings", "Bank") { fail("Should not be called") }
        advanceUntilIdle()

        // Assert
        viewModel.validationError.test {
            assertEquals("An account named 'Savings' already exists.", awaitItem())
        }
    }

    @Test
    fun `createCategory validation fails for blank name`() = runTest {
        // Act
        viewModel.createCategory("", "icon", "color") { fail("Should not be called") }
        advanceUntilIdle()

        // Assert
        verify(categoryRepository, never()).insert(any())
    }

    @Test
    fun `addTagOnTheGo fails for existing tag name`() = runTest {
        // Arrange
        whenever(db.tagDao().findByName("Work")).thenReturn(Tag(1, "Work"))

        // Act
        viewModel.addTagOnTheGo("Work")
        advanceUntilIdle()

        // Assert
        viewModel.validationError.test {
            assertEquals("A tag named 'Work' already exists.", awaitItem())
        }
    }

    @Test
    fun `autoSaveSmsTransaction uses account alias if found`() = runTest {
        // Arrange
        val potentialAccount = PotentialAccount("Alias", "Bank")
        val potentialTxn = PotentialTransaction(
            sourceSmsId = 1L,
            smsSender = "S1",
            amount = 100.0,
            transactionType = "expense",
            merchantName = "M",
            originalMessage = "Msg",
            potentialAccount = potentialAccount,
            sourceSmsHash = "hash"
        )
        val alias = AccountAlias(aliasName = "Alias", destinationAccountId = 2)
        whenever(accountAliasDao.findByAlias("Alias")).thenReturn(alias)
        whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

        // Act
        val result = viewModel.autoSaveSmsTransaction(potentialTxn)
        advanceUntilIdle()

        // Assert
        assertTrue(result)
        val captor = argumentCaptor<Transaction>()
        verify(transactionRepository).insertTransactionWithTags(captor.capture(), any())
        assertEquals(2, captor.firstValue.accountId)
    }
}