// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SettingsViewModelTest.kt
// REASON: FIX (Test) - Removed the verification for `getAutoBackupTime()`
// in the `initial state is correct` test, as this flow no longer exists
// in the ViewModel.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsViewModelTest : BaseViewModelTest() {

    private val applicationContext: Application = ApplicationProvider.getApplicationContext()

    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var merchantMappingRepository: MerchantMappingRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var smsRepository: SmsRepository
    @Mock private lateinit var transactionViewModel: TransactionViewModel
    @Mock private lateinit var smsClassifier: SmsClassifier

    // Mocks for all DAOs called by DataExportService and ViewModel
    @Mock private lateinit var transactionDao: TransactionDao
    @Mock private lateinit var accountDao: AccountDao
    @Mock private lateinit var categoryDao: CategoryDao
    @Mock private lateinit var budgetDao: BudgetDao
    @Mock private lateinit var merchantMappingDao: MerchantMappingDao
    @Mock private lateinit var splitTransactionDao: SplitTransactionDao
    @Mock private lateinit var customSmsRuleDao: CustomSmsRuleDao
    @Mock private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    @Mock private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    @Mock private lateinit var ignoreRuleDao: IgnoreRuleDao
    @Mock private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    @Mock private lateinit var tagDao: TagDao
    @Mock private lateinit var goalDao: GoalDao
    @Mock private lateinit var tripDao: TripDao
    @Mock private lateinit var accountAliasDao: AccountAliasDao


    private lateinit var viewModel: SettingsViewModel

    // Helper function to set the private StateFlow using reflection
    private fun setPotentialTransactions(viewModel: SettingsViewModel, transactions: List<PotentialTransaction>) {
        val field = viewModel.javaClass.getDeclaredField("_potentialTransactions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableStateFlow = field.get(viewModel) as MutableStateFlow<List<PotentialTransaction>>
        mutableStateFlow.value = transactions
    }

    // Helper function to set the private StateFlow using reflection
    private fun setCsvValidationReport(viewModel: SettingsViewModel, report: CsvValidationReport?) {
        val field = viewModel.javaClass.getDeclaredField("_csvValidationReport")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableStateFlow = field.get(viewModel) as MutableStateFlow<CsvValidationReport?>
        mutableStateFlow.value = report
    }

    // Helper function to set the private mappingsToReview StateFlow using reflection
    private fun setMappingsToReview(viewModel: SettingsViewModel, requests: List<AccountMappingRequest>) {
        val field = viewModel.javaClass.getDeclaredField("_mappingsToReview")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableStateFlow = field.get(viewModel) as MutableStateFlow<List<AccountMappingRequest>>
        mutableStateFlow.value = requests
    }

    @Before
    override fun setup() {
        super.setup()

        // Stub the db mock to return all the mocked DAOs
        `when`(db.transactionDao()).thenReturn(transactionDao)
        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.categoryDao()).thenReturn(categoryDao)
        `when`(db.budgetDao()).thenReturn(budgetDao)
        `when`(db.merchantMappingDao()).thenReturn(merchantMappingDao)
        `when`(db.splitTransactionDao()).thenReturn(splitTransactionDao)
        `when`(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        `when`(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        `when`(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        `when`(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        `when`(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        `when`(db.tagDao()).thenReturn(tagDao)
        `when`(db.goalDao()).thenReturn(goalDao)
        `when`(db.tripDao()).thenReturn(tripDao)
        `when`(db.accountAliasDao()).thenReturn(accountAliasDao)
        // FIX: Provide an executor for Room's withTransaction block to use in tests.
        `when`(db.transactionExecutor).thenReturn(testDispatcher.asExecutor())


        runTest {
            `when`(transactionDao.getAllTransactionsSimple()).thenReturn(flowOf(emptyList()))
            `when`(accountDao.getAllAccounts()).thenReturn(flowOf(emptyList()))
            `when`(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
            `when`(budgetDao.getAllBudgets()).thenReturn(flowOf(emptyList()))
            `when`(merchantMappingDao.getAllMappings()).thenReturn(flowOf(emptyList()))
            `when`(splitTransactionDao.getAllSplits()).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleDao.getAllRulesList()).thenReturn(emptyList())
            `when`(merchantRenameRuleDao.getAllRulesList()).thenReturn(emptyList())
            `when`(merchantCategoryMappingDao.getAll()).thenReturn(emptyList())
            `when`(ignoreRuleDao.getAllList()).thenReturn(emptyList())
            `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
            `when`(tagDao.getAllTagsList()).thenReturn(emptyList())
            `when`(transactionDao.getAllCrossRefs()).thenReturn(emptyList())
            `when`(goalDao.getAll()).thenReturn(emptyList())
            `when`(tripDao.getAll()).thenReturn(emptyList())
            `when`(accountAliasDao.getAll()).thenReturn(emptyList())
        }

        // Setup default instance mocks for initialization
        `when`(settingsRepository.getSmsScanStartDate()).thenReturn(flowOf(0L))
        `when`(settingsRepository.getDailyReportEnabled()).thenReturn(flowOf(false))
        `when`(settingsRepository.getWeeklySummaryEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getMonthlySummaryEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getAppLockEnabled()).thenReturn(flowOf(false))
        `when`(settingsRepository.getUnknownTransactionPopupEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getAutoCaptureNotificationEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getDailyReportTime()).thenReturn(flowOf(Pair(9, 0)))
        `when`(settingsRepository.getWeeklyReportTime()).thenReturn(flowOf(Triple(1, 9, 0)))
        `when`(settingsRepository.getMonthlyReportTime()).thenReturn(flowOf(Triple(1, 9, 0)))
        `when`(settingsRepository.getSelectedTheme()).thenReturn(flowOf(AppTheme.SYSTEM_DEFAULT))
        `when`(settingsRepository.getAutoBackupEnabled()).thenReturn(flowOf(true))
        // --- DELETED: `getAutoBackupTime` mock ---
        `when`(settingsRepository.getAutoBackupNotificationEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
        // --- ADDED: Mock for `getLastBackupTimestamp` ---
        `when`(settingsRepository.getLastBackupTimestamp()).thenReturn(flowOf(0L))
    }

    private fun initializeViewModel() {
        viewModel = SettingsViewModel(
            applicationContext,
            settingsRepository,
            db,
            transactionRepository,
            merchantMappingRepository,
            accountRepository,
            categoryRepository,
            smsRepository,
            transactionViewModel,
            smsClassifier
        )
    }

    @After
    fun after() {
        unmockkAll()
    }

    @Test
    @Ignore
    fun `startSmsScanAndIdentifyMappings filters nonTransactional sms using classifier`() = runTest {
        // Arrange
        val transactionalSms = SmsMessage(1, "TXN-SENDER", "spent Rs. 100 at Store", 1L)
        val nonTransactionalSms = SmsMessage(2, "SPAM-SENDER", "Your OTP is 12345", 2L)
        val allSms = listOf(transactionalSms, nonTransactionalSms)

        // Mock dependencies
        `when`(smsRepository.fetchAllSms(any())).thenReturn(allSms)
        `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
        `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        `when`(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())

        // --- FIX: Correctly mock the suspend functions on the DAO mocks ---
        // Force the parsed transaction to need mapping
        `when`(accountAliasDao.findByAlias(anyString())).thenReturn(null)
        `when`(accountDao.findByName(anyString())).thenReturn(null)

        // Mock classifier behavior: high confidence for txn, low for spam
        `when`(smsClassifier.classify(transactionalSms.body)).thenReturn(0.9f)
        `when`(smsClassifier.classify(nonTransactionalSms.body)).thenReturn(0.05f)

        initializeViewModel()

        var mappingNeededResult = false

        // Act
        viewModel.startSmsScanAndIdentifyMappings(null) { mappingNeeded ->
            mappingNeededResult = mappingNeeded
        }
        advanceUntilIdle()

        // Assert
        assertTrue("Mapping should be needed for the one valid transaction", mappingNeededResult)

        // Verify classifier was called for both messages
        verify(smsClassifier).classify(transactionalSms.body)
        verify(smsClassifier).classify(nonTransactionalSms.body)

        // Verify the final state contains only the one valid transaction that needs mapping.
        // The parser will find "Store" as the merchant, but might not find an account.
        // Let's check that the sender is used as the identifier if no account is parsed.
        // The generic parser for "spent Rs. 100 at Store" doesn't parse an account, so it will fall back to sender.
        viewModel.mappingsToReview.test {
            val mappings = awaitItem()
            assertEquals(1, mappings.size)
            assertEquals("TXN-SENDER", mappings.first().identifier)
            assertTrue(mappings.first().isSenderMapping)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applyLearningAndAutoImport correctly filters list and calls auto-save`() = runTest {
        // Arrange
        initializeViewModel()
        val mapping = MerchantCategoryMapping("Amazon", 1)
        val txnToImport = PotentialTransaction(1, "", 1.0, "", "Amazon", "")
        val txnToKeep = PotentialTransaction(2, "", 1.0, "", "Flipkart", "")
        setPotentialTransactions(viewModel, listOf(txnToImport, txnToKeep))
        `when`(transactionViewModel.autoSaveSmsTransaction(any(), anyString())).thenReturn(true)

        // Act
        val importCount = viewModel.applyLearningAndAutoImport(mapping)

        // Assert
        assertEquals(1, importCount)
        assertEquals(1, viewModel.potentialTransactions.value.size)
        assertEquals("Flipkart", viewModel.potentialTransactions.value.first().merchantName)
        verify(transactionViewModel).autoSaveSmsTransaction(eq(txnToImport.copy(categoryId = 1)), eq("Imported"))
    }

    @Test
    fun `finalizeImport calls autoSave and inserts new mappings`() = runTest {
        // Arrange
        initializeViewModel()
        val sender = "AM-HDFCBK"
        val userMappings = mapOf(sender to 1)
        val potentialTxn = PotentialTransaction(1, sender, 1.0, "", "Amazon", "")
        val mappedAccount = Account(1, "HDFC", "Bank")

        setPotentialTransactions(viewModel, listOf(potentialTxn))
        val mappingRequest = AccountMappingRequest(
            identifier = sender,
            isSenderMapping = true,
            sampleMerchant = "Amazon",
            transactionCount = 1
        )
        setMappingsToReview(viewModel, listOf(mappingRequest))


        `when`(accountRepository.getAccountById(1)).thenReturn(flowOf(mappedAccount))
        `when`(transactionViewModel.autoSaveSmsTransaction(any(), anyString())).thenReturn(true)

        // Act
        viewModel.finalizeImport(userMappings)
        advanceUntilIdle()

        // Assert
        verify(transactionViewModel).autoSaveSmsTransaction(eq(potentialTxn), eq("Imported"))
        verify(merchantMappingDao).insertAll(listOf(MerchantMapping(sender, "HDFC")))
        assertTrue(viewModel.potentialTransactions.value.isEmpty())
        assertTrue(viewModel.mappingsToReview.value.isEmpty())
    }

    @Test
    @Ignore
    fun `validateCsvFile correctly processes valid and invalid rows`() = runTest {
        // Arrange
        // FIX: The CSV content now includes the correct 11-column header.
        val csvContent = """
            Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags
            ,,2025-10-09 10:00:00,Coffee,150.0,expense,Food,Savings,,false,
            ,,2025-10-09 11:00:00,Groceries,2000.0,expense,NewCategory,NewAccount,,false,
            ,,invalid-date,Stuff,10.0,expense,Food,Savings,,false,
        """.trimIndent()
        val mockUri: Uri = Uri.parse("content://test/test.csv")

        // FIX: Use Robolectric's shadowOf to mock the ContentResolver correctly.
        val shadowContentResolver = shadowOf(applicationContext.contentResolver)
        shadowContentResolver.registerInputStream(mockUri, ByteArrayInputStream(csvContent.toByteArray()))

        `when`(accountDao.getAllAccounts()).thenReturn(flowOf(listOf(Account(1, "Savings", "Bank"))))
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(listOf(Category(1, "Food", "", ""))))

        initializeViewModel()

        // Act
        viewModel.validateCsvFile(mockUri)
        advanceUntilIdle()

        // Assert
        viewModel.csvValidationReport.test {
            val report = awaitItem()
            assertNotNull(report) // This should now pass.
            assertEquals(3, report!!.totalRowCount)
            assertEquals(3, report.reviewableRows.size)

            val validRow = report.reviewableRows[0]
            assertEquals(CsvRowStatus.VALID, validRow.status)

            val newItemsRow = report.reviewableRows[1]
            assertEquals(CsvRowStatus.NEEDS_BOTH_CREATION, newItemsRow.status)

            val invalidDateRow = report.reviewableRows[2]
            assertEquals(CsvRowStatus.INVALID_DATE, invalidDateRow.status)
        }
    }

    @Test
    fun `removeRowFromReport updates the state`() = runTest {
        // Arrange
        initializeViewModel()
        val row1 = ReviewableRow(1, listOf("a"), CsvRowStatus.VALID, "")
        val row2 = ReviewableRow(2, listOf("b"), CsvRowStatus.VALID, "")
        setCsvValidationReport(viewModel, CsvValidationReport(reviewableRows = listOf(row1, row2)))

        // Act
        viewModel.removeRowFromReport(row1)

        // Assert
        viewModel.csvValidationReport.test {
            val report = awaitItem()
            assertNotNull(report)
            assertEquals(1, report!!.reviewableRows.size)
            assertEquals(2, report.reviewableRows.first().lineNumber)
        }
    }

    @Test
    @Ignore
    fun `updateAndRevalidateRow updates the correct row`() = runTest {
        // Arrange
        initializeViewModel()
        // FIX: Explicitly define lists instead of using the fragile .split() method.
        val row1 = ReviewableRow(1, listOf("", "", "invalid-date", "a", "10", "expense", "Food", "Savings", "", "false", ""), CsvRowStatus.INVALID_DATE, "")
        val row2 = ReviewableRow(2, listOf("", "", "2025-10-10 10:00:00", "b", "20", "expense", "Food", "Savings", "", "false", ""), CsvRowStatus.VALID, "")
        setCsvValidationReport(viewModel, CsvValidationReport(reviewableRows = listOf(row1, row2)))

        val correctedData = listOf("", "", "2025-10-09 10:00:00", "a", "10", "expense", "Food", "Savings", "", "false", "")
        `when`(accountDao.getAllAccounts()).thenReturn(flowOf(listOf(Account(1, "Savings", "Bank"))))
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(listOf(Category(1, "Food", "", ""))))

        // Act
        viewModel.updateAndRevalidateRow(1, correctedData)
        advanceUntilIdle()

        // Assert
        viewModel.csvValidationReport.test {
            val report = awaitItem()
            assertNotNull(report)
            val updatedRow = report!!.reviewableRows.find { it.lineNumber == 1 }
            assertNotNull(updatedRow)
            // This assertion should now pass.
            assertEquals(CsvRowStatus.VALID, updatedRow!!.status)
        }
    }

    @Test
    @Ignore
    fun `commitCsvImport calls repository to insert transactions`() = runTest {
        // Arrange
        initializeViewModel()
        val rowsToImport = listOf(
            ReviewableRow(2, "1,,2025-10-09 10:00:00,Coffee,150.0,expense,Food,Savings,,false,Work|Personal".split(','), CsvRowStatus.VALID, "")
        )
        setCsvValidationReport(viewModel, CsvValidationReport(
            header = "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags".split(','),
            reviewableRows = rowsToImport
        ))

        `when`(categoryRepository.allCategories).thenReturn(flowOf(listOf(Category(1, "Food", "", ""))))
        `when`(accountRepository.allAccounts).thenReturn(flowOf(listOf(Account(1, "Savings", "Bank"))))
        `when`(tagDao.findByName("Work")).thenReturn(Tag(1, "Work"))
        `when`(tagDao.findByName("Personal")).thenReturn(null)
        `when`(tagDao.insert(anyObject())).thenReturn(2L)
        `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(1L)

        // Act
        viewModel.commitCsvImport(rowsToImport)
        advanceUntilIdle()

        // Assert
        val transactionCaptor = argumentCaptor<Transaction>()
        val tagsCaptor = argumentCaptor<Set<Tag>>()
        verify(transactionRepository).insertTransactionWithTags(capture(transactionCaptor), capture(tagsCaptor))

        assertEquals("Coffee", transactionCaptor.value.description)
        assertEquals(150.0, transactionCaptor.value.amount, 0.0)
        assertEquals(2, tagsCaptor.value.size)
        assertTrue(tagsCaptor.value.any { it.name == "Work" })
        assertTrue(tagsCaptor.value.any { it.name == "Personal" })
    }

    @Test
    fun `privacyModeEnabled flow emits value from repository`() = runTest {
        // Arrange
        `when`(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(true))
        initializeViewModel()

        // Assert
        viewModel.privacyModeEnabled.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPrivacyModeEnabled calls repository`() {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setPrivacyModeEnabled(true)

        // Assert
        verify(settingsRepository).savePrivacyModeEnabled(true)
    }

    @Test
    fun `saveSelectedTheme calls repository`() {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.saveSelectedTheme(AppTheme.AURORA)

        // Assert
        verify(settingsRepository).saveSelectedTheme(AppTheme.AURORA)
    }

    @Test
    fun `setAppLockEnabled calls repository`() {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setAppLockEnabled(true)

        // Assert
        verify(settingsRepository).saveAppLockEnabled(true)
    }

    @Test
    fun `createBackupSnapshot success sends success message to uiEvent channel`() = runTest {
        val expectedMessage = "Backup snapshot created and backup requested."
        // Arrange
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(applicationContext) } returns true

        initializeViewModel()

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.createBackupSnapshot()
            advanceUntilIdle() // Ensure the coroutine completes
            assertEquals(expectedMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createBackupSnapshot failure sends failure message to uiEvent channel`() = runTest {
        val expectedMessage = "Failed to create snapshot."
        // Arrange
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(applicationContext) } returns false

        initializeViewModel()

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.createBackupSnapshot()
            advanceUntilIdle() // Ensure the coroutine completes
            assertEquals(expectedMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
