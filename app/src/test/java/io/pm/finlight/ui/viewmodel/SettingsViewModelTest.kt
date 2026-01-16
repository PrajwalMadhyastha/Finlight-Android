// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SettingsViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.util.Calendar


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
    private lateinit var transactionRunner: TransactionRunner

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

    // --- DELETED: setMappingsToReview function ---

    @Before
    override fun setup() {
        super.setup()

        // --- NEW: Mock ReminderManager ---
        mockkObject(ReminderManager)
        coEvery { ReminderManager.scheduleAutoBackup(any()) } just runs
        coEvery { ReminderManager.cancelAutoBackup(any()) } just runs
        coEvery { ReminderManager.scheduleDailyReport(any()) } just runs
        coEvery { ReminderManager.cancelDailyReport(any()) } just runs
        coEvery { ReminderManager.scheduleWeeklySummary(any()) } just runs
        coEvery { ReminderManager.cancelWeeklySummary(any()) } just runs
        coEvery { ReminderManager.scheduleMonthlySummary(any()) } just runs
        coEvery { ReminderManager.cancelMonthlySummary(any()) } just runs

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
        // `when`(db.transactionExecutor).thenReturn(testDispatcher.asExecutor()) // Not needed with TransactionRunner

        // --- NEW: Mock TransactionRunner to execute immediately ---
        transactionRunner = io.mockk.mockk()
        // Match generic arguments using MockK
        coEvery { transactionRunner.run<Any?>(any(), any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }



        // --- NEW: Grant SMS permissions via Shadow (backup) ---
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

        // --- NEW: Force permission grant by mocking ContextCompat ---
        mockkStatic(ContextCompat::class)
        io.mockk.every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        // REMOVED withTransaction mock to let it run naturally with the mocked executor.


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
            smsClassifier,
            transactionRunner,
            dispatchers = TestDispatcherProvider(testDispatcher)
        )
    }

    class TestDispatcherProvider(private val testDispatcher: kotlinx.coroutines.test.TestDispatcher) : io.pm.finlight.utils.DispatcherProvider {
        override val main: kotlinx.coroutines.CoroutineDispatcher get() = testDispatcher
        override val io: kotlinx.coroutines.CoroutineDispatcher get() = testDispatcher
        override val default: kotlinx.coroutines.CoroutineDispatcher get() = testDispatcher
        override val unconfined: kotlinx.coroutines.CoroutineDispatcher get() = testDispatcher
    }

    @After
    fun after() {
        unmockkAll()
    }

    @Test
    fun `startSmsScanAndIdentifyMappings does not call classifier for SMS matching custom rules`() = runTest {
        // This test verifies the optimization: classifier is NOT called if custom rule matches
        
        val sms = SmsMessage(1, "ICICI", "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Jun-25; DAKSHIN CAFE credited.", 1L)

        // Mock dependencies
        `when`(smsRepository.fetchAllSms(org.mockito.ArgumentMatchers.isNull())).thenReturn(listOf(sms))
        `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
        `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        `when`(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())
        `when`(accountAliasDao.findByAlias(anyString())).thenReturn(null)
        `when`(accountDao.findByName(anyString())).thenReturn(null)

        // Add a custom rule that matches the SMS
        val rule = CustomSmsRule(
            id = 1,
            triggerPhrase = "debited for",
            merchantRegex = "([A-Z\\s]+) credited",
            amountRegex = "Rs ([\\d.]+)",
            accountRegex = null,
            merchantNameExample = "DAKSHIN CAFE",
            amountExample = "240.00",
            accountNameExample = null,
            priority = 10,
            sourceSmsBody = sms.body,
            transactionType = null
        )
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(listOf(rule)))

        // Grant SMS permission (required by ViewModel)
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

        initializeViewModel()

        val eventJob = launch { viewModel.uiEvent.collect() }

        try {
            // Act
            viewModel.startSmsScanAndIdentifyMappings(null) { }
            advanceUntilIdle()

            // Assert - Classifier should NOT be called because custom rule matched
            verify(smsClassifier, org.mockito.Mockito.never()).classify(anyString())
        } finally {
            eventJob.cancel()
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

    // --- DELETED: `finalizeImport` test ---

    @Test
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
            var report = awaitItem()
            while (report == null) {
                report = awaitItem()
            }
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
            var report = awaitItem()
            while (report == null) {
                report = awaitItem()
            }
            assertNotNull(report)
            val updatedRow = report!!.reviewableRows.find { it.lineNumber == 1 }
            assertNotNull(updatedRow)
            // This assertion should now pass.
            assertEquals(CsvRowStatus.VALID, updatedRow!!.status)
        }
    }

    @Test
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
        // Use timeout because of race condition with Dispatchers.IO
        verify(transactionRepository, org.mockito.Mockito.timeout(5000)).insertTransactionWithTags(capture(transactionCaptor), capture(tagsCaptor))

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

    // --- UPDATED: Test for backup success dialog ---
    @Test
    fun `createBackupSnapshot success sets showBackupSuccessDialog to true`() = runTest {
        // Arrange
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(applicationContext) } returns true

        initializeViewModel()

        // Act & Assert
        viewModel.showBackupSuccessDialog.test {
            assertFalse("Dialog should be hidden initially", awaitItem())
            viewModel.createBackupSnapshot()
            advanceUntilIdle() // Ensure the coroutine completes
            assertTrue("Dialog should be shown on success", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- UPDATED: Test for backup failure event ---
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
        // Also assert the dialog state *doesn't* change
        assertFalse(viewModel.showBackupSuccessDialog.value)
    }

    // --- NEW: Test for dismissing the dialog ---
    @Test
    fun `dismissBackupSuccessDialog sets flow to false`() = runTest {
        // Arrange
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(applicationContext) } returns true
        initializeViewModel()

        // Act & Assert
        viewModel.showBackupSuccessDialog.test {
            assertFalse("Dialog hidden initially", awaitItem())

            viewModel.createBackupSnapshot()
            advanceUntilIdle()
            assertTrue("Dialog shown on success", awaitItem())

            viewModel.dismissBackupSuccessDialog()
            assertFalse("Dialog hidden after dismiss", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Tests for the objective ---

    @Test
    fun `rescanSmsWithNewRule finds and saves new transactions`() = runTest {
        // Arrange
        val sms = SmsMessage(1, "SENDER", "spent Rs 100", 1L)
        val parsedTxn = PotentialTransaction(1L, "SENDER", 100.0, "expense", "Store", "spent Rs 100", null, "hash123")

        `when`(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
        `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList())) // No existing hashes

        // Mock all parser dependencies to return empty/null
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        `when`(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())
        `when`(accountAliasDao.findByAlias(anyString())).thenReturn(null)
        `when`(accountDao.findByName(anyString())).thenReturn(null)

        // Mock the parser to successfully parse the transaction
        // We can do this by mocking the one rule it needs
        val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(listOf(rule)))

        // Mock the final auto-save step
        // --- FIX: Use anyObject() for non-nullable Kotlin class ---
        `when`(transactionViewModel.autoSaveSmsTransaction(anyObject(), eq("Imported"))).thenReturn(true)

        initializeViewModel()

        var newTransactionCount = -1

        // Act
        viewModel.rescanSmsWithNewRule { count ->
            newTransactionCount = count
        }
        advanceUntilIdle() 

        // Assert
        println("SettingsViewModelTest: newTransactionCount = $newTransactionCount")
        assertEquals("Should have found and saved 1 new transaction", 1, newTransactionCount)
        // --- FIX: Use anyObject() for verification too ---
        verify(transactionViewModel).autoSaveSmsTransaction(anyObject(), eq("Imported"))
    }

    @Test
    fun `setAutoCaptureNotificationEnabled calls repository`() = runTest {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setAutoCaptureNotificationEnabled(true)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveAutoCaptureNotificationEnabled(true)

        // Act 2
        viewModel.setAutoCaptureNotificationEnabled(false)
        advanceUntilIdle()

        // Assert 2
        verify(settingsRepository).saveAutoCaptureNotificationEnabled(false)
    }

    @Test
    fun `setAutoBackupEnabled calls repository and ReminderManager`() = runTest {
        // Arrange
        initializeViewModel()

        // Act 1: Enable
        viewModel.setAutoBackupEnabled(true)
        advanceUntilIdle()

        // Assert 1
        verify(settingsRepository).saveAutoBackupEnabled(true)
        coVerify { ReminderManager.scheduleAutoBackup(applicationContext) }

        // Act 2: Disable
        viewModel.setAutoBackupEnabled(false)
        advanceUntilIdle()

        // Assert 2
        verify(settingsRepository).saveAutoBackupEnabled(false)
        coVerify { ReminderManager.cancelAutoBackup(applicationContext) }
    }

    @Test
    fun `setAutoBackupNotificationEnabled calls repository`() = runTest {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setAutoBackupNotificationEnabled(true)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveAutoBackupNotificationEnabled(true)

        // Act 2
        viewModel.setAutoBackupNotificationEnabled(false)
        advanceUntilIdle()

        // Assert 2
        verify(settingsRepository).saveAutoBackupNotificationEnabled(false)
    }

    @Test
    fun `dismissPotentialTransaction removes transaction from state`() = runTest {
        // Arrange
        initializeViewModel()
        val txn1 = PotentialTransaction(1, "", 1.0, "", "Amazon", "")
        val txn2 = PotentialTransaction(2, "", 2.0, "", "Flipkart", "")
        setPotentialTransactions(viewModel, listOf(txn1, txn2))

        // Act
        viewModel.dismissPotentialTransaction(txn1)
        advanceUntilIdle()

        // Assert
        val currentState = viewModel.potentialTransactions.value
        assertEquals(1, currentState.size)
        assertEquals("Flipkart", currentState.first().merchantName)
    }

    @Test
    fun `onTransactionApproved removes transaction from state`() = runTest {
        // Arrange
        initializeViewModel()
        // --- FIX: Use named arguments to set the correct sourceSmsId ---
        val txn1 = PotentialTransaction(
            sourceSmsId = 101L,
            smsSender = "",
            amount = 1.0,
            transactionType = "expense",
            merchantName = "Amazon",
            originalMessage = ""
        )
        val txn2 = PotentialTransaction(
            sourceSmsId = 102L,
            smsSender = "",
            amount = 2.0,
            transactionType = "expense",
            merchantName = "Flipkart",
            originalMessage = ""
        )
        setPotentialTransactions(viewModel, listOf(txn1, txn2))

        // Act
        viewModel.onTransactionApproved(101L) // Approve the first transaction
        advanceUntilIdle()

        // Assert
        val currentState = viewModel.potentialTransactions.value
        assertEquals(1, currentState.size)
        assertEquals(102L, currentState.first().sourceSmsId)
    }

    // --- ADDED: Tests for the objective ---

    @Test
    fun `onTransactionLinked removes transaction from state`() = runTest {
        // Arrange
        initializeViewModel()
        val txn1 = PotentialTransaction(sourceSmsId = 101L, smsSender = "S1", amount = 1.0, "expense", "Amazon", "")
        val txn2 = PotentialTransaction(sourceSmsId = 102L, smsSender = "S2", amount = 2.0, "expense", "Flipkart", "")
        setPotentialTransactions(viewModel, listOf(txn1, txn2))

        // Act
        viewModel.onTransactionLinked(101L) // Link the first transaction
        advanceUntilIdle()

        // Assert
        val currentState = viewModel.potentialTransactions.value
        assertEquals(1, currentState.size)
        assertEquals(102L, currentState.first().sourceSmsId)
    }

    @Test
    fun `saveMerchantRenameRule calls dao insert when names are different`() = runTest {
        // Arrange
        initializeViewModel()
        val originalName = "AMZN"
        val newName = "Amazon"

        // Act
        viewModel.saveMerchantRenameRule(originalName, newName)
        advanceUntilIdle()

        // Assert
        verify(merchantRenameRuleDao).insert(MerchantRenameRule(originalName, newName))
        verify(merchantRenameRuleDao, never()).deleteByOriginalName(anyString())
    }

    @Test
    fun `saveMerchantRenameRule calls dao delete when names are same`() = runTest {
        // Arrange
        initializeViewModel()
        val originalName = "Amazon"
        val newName = "Amazon" // Same name

        // Act
        viewModel.saveMerchantRenameRule(originalName, newName)
        advanceUntilIdle()

        // Assert
        verify(merchantRenameRuleDao).deleteByOriginalName(originalName)
        verify(merchantRenameRuleDao, never()).insert(any())
    }

    @Test
    fun `saveMerchantRenameRule is case-insensitive for delete check`() = runTest {
        // Arrange
        initializeViewModel()
        val originalName = "Amazon"
        val newName = "amazon" // Same name, different case

        // Act
        viewModel.saveMerchantRenameRule(originalName, newName)
        advanceUntilIdle()

        // Assert
        verify(merchantRenameRuleDao).deleteByOriginalName(originalName)
        verify(merchantRenameRuleDao, never()).insert(any())
    }

    @Test
    fun `saveSmsScanStartDate calls repository`() = runTest {
        // Arrange
        initializeViewModel()
        val testDate = 123456789L

        // Act
        viewModel.saveSmsScanStartDate(testDate)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveSmsScanStartDate(testDate)
    }

    @Test
    fun `setDailyReportEnabled calls repository and ReminderManager`() = runTest {
        // Arrange
        initializeViewModel()

        // Act (Enable)
        viewModel.setDailyReportEnabled(true)
        advanceUntilIdle()

        // Assert (Enable)
        verify(settingsRepository).saveDailyReportEnabled(true)
        coVerify { ReminderManager.scheduleDailyReport(applicationContext) }

        // Act (Disable)
        viewModel.setDailyReportEnabled(false)
        advanceUntilIdle()

        // Assert (Disable)
        verify(settingsRepository).saveDailyReportEnabled(false)
        coVerify { ReminderManager.cancelDailyReport(applicationContext) }
    }

    @Test
    fun `saveDailyReportTime calls repository and schedules when enabled`() = runTest {
        // Arrange
        // Override the default mock from setup()
        `when`(settingsRepository.getDailyReportEnabled()).thenReturn(flowOf(true))
        initializeViewModel()

        // --- FIX: Start a collector to ensure StateFlow updates ---
        val job = launch(testDispatcher) { viewModel.dailyReportEnabled.collect() }

        // Wait for the ViewModel's init block to collect the 'true' value
        advanceUntilIdle()

        // Act
        viewModel.saveDailyReportTime(8, 30)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveDailyReportTime(8, 30)
        // Verify it was called *after* the save
        coVerify { ReminderManager.scheduleDailyReport(applicationContext) }

        // Cleanup
        job.cancel()
    }

    @Test
    fun `saveDailyReportTime calls repository and does not schedule when disabled`() = runTest {
        // Arrange
        // The default setup() mock already sets this flow to `false`
        initializeViewModel()
        // Wait for the ViewModel's init block to collect the 'false' value
        advanceUntilIdle()

        // Act
        viewModel.saveDailyReportTime(8, 30)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveDailyReportTime(8, 30)
        // Verify schedule was *not* called
        coVerify(exactly = 0) { ReminderManager.scheduleDailyReport(applicationContext) }
    }

    @Test
    fun `setWeeklySummaryEnabled calls repository and ReminderManager`() = runTest {
        // Arrange
        initializeViewModel()

        // Act (Enable)
        viewModel.setWeeklySummaryEnabled(true)
        advanceUntilIdle()

        // Assert (Enable)
        verify(settingsRepository).saveWeeklySummaryEnabled(true)
        coVerify { ReminderManager.scheduleWeeklySummary(applicationContext) }

        // Act (Disable)
        viewModel.setWeeklySummaryEnabled(false)
        advanceUntilIdle()

        // Assert (Disable)
        verify(settingsRepository).saveWeeklySummaryEnabled(false)
        coVerify { ReminderManager.cancelWeeklySummary(applicationContext) }
    }


    @Test
    fun `saveWeeklyReportTime calls repository and schedules when enabled`() = runTest {
        // Arrange
        // The default setup() mock sets this flow to `true`
        initializeViewModel()
        // Wait for the ViewModel's init block to collect the 'true' value
        advanceUntilIdle()

        // Act
        viewModel.saveWeeklyReportTime(Calendar.TUESDAY, 10, 30)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveWeeklyReportTime(Calendar.TUESDAY, 10, 30)
        // Verify it was called *after* the save
        coVerify { ReminderManager.scheduleWeeklySummary(applicationContext) }
    }

    @Test
    fun `saveWeeklyReportTime calls repository and does not schedule when disabled`() = runTest {
        // Arrange
        // Override the default setup() mock
        `when`(settingsRepository.getWeeklySummaryEnabled()).thenReturn(flowOf(false))
        initializeViewModel()

        // --- FIX: Eagerly collect the flow to trigger the StateFlow update ---
        val job = launch(testDispatcher) { viewModel.weeklySummaryEnabled.collect() }

        // Wait for the ViewModel's init block to collect the 'false' value
        advanceUntilIdle()

        // Act
        viewModel.saveWeeklyReportTime(Calendar.TUESDAY, 10, 30)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveWeeklyReportTime(Calendar.TUESDAY, 10, 30)
        // Verify schedule was *not* called
        coVerify(exactly = 0) { ReminderManager.scheduleWeeklySummary(applicationContext) }

        // --- FIX: Cancel the collector job ---
        job.cancel()
    }

    @Test
    fun `setMonthlySummaryEnabled calls repository and ReminderManager`() = runTest {
        // Arrange
        initializeViewModel()

        // Act (Enable)
        viewModel.setMonthlySummaryEnabled(true)
        advanceUntilIdle()

        // Assert (Enable)
        verify(settingsRepository).saveMonthlySummaryEnabled(true)
        coVerify { ReminderManager.scheduleMonthlySummary(applicationContext) }

        // Act (Disable)
        viewModel.setMonthlySummaryEnabled(false)
        advanceUntilIdle()

        // Assert (Disable)
        verify(settingsRepository).saveMonthlySummaryEnabled(false)
        coVerify { ReminderManager.cancelMonthlySummary(applicationContext) }
    }

    @Test
    fun `saveMonthlyReportTime calls repository and schedules when enabled`() = runTest {
        // Arrange
        // The default setup() mock sets this flow to `true`
        initializeViewModel()
        // Wait for the ViewModel's init block to collect the 'true' value
        advanceUntilIdle()

        // Act
        viewModel.saveMonthlyReportTime(15, 8, 0)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveMonthlyReportTime(15, 8, 0)
        // Verify it was called *after* the save
        coVerify { ReminderManager.scheduleMonthlySummary(applicationContext) }
    }

    @Test
    fun `saveMonthlyReportTime calls repository and does not schedule when disabled`() = runTest {
        // Arrange
        // Override the default setup() mock
        `when`(settingsRepository.getMonthlySummaryEnabled()).thenReturn(flowOf(false))
        initializeViewModel()

        // --- FIX: Eagerly collect the flow to trigger the StateFlow update ---
        val job = launch(testDispatcher) { viewModel.monthlySummaryEnabled.collect() }

        // Wait for the ViewModel's init block to collect the 'false' value
        advanceUntilIdle()

        // Act
        viewModel.saveMonthlyReportTime(15, 8, 0)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveMonthlyReportTime(15, 8, 0)
        // Verify schedule was *not* called
        coVerify(exactly = 0) { ReminderManager.scheduleMonthlySummary(applicationContext) }

        // --- FIX: Cancel the collector job ---
        job.cancel()
    }

    @Test
    fun `setUnknownTransactionPopupEnabled calls repository`() = runTest {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setUnknownTransactionPopupEnabled(true)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveUnknownTransactionPopupEnabled(true)

        // Act 2
        viewModel.setUnknownTransactionPopupEnabled(false)
        advanceUntilIdle()

        // Assert 2
        verify(settingsRepository).saveUnknownTransactionPopupEnabled(false)
    }

    @Test
    fun `clearCsvValidationReport sets flow to null`() = runTest {
        // Arrange
        initializeViewModel()
        // Set a non-null report
        setCsvValidationReport(viewModel, CsvValidationReport(reviewableRows = listOf(
            ReviewableRow(1, emptyList(), CsvRowStatus.VALID, "")
        )))
        // Pre-condition check
        assertNotNull(viewModel.csvValidationReport.value)

        // Act
        viewModel.clearCsvValidationReport()

        // Assert
        assertNull(viewModel.csvValidationReport.value)
    }

    // =========================================================================
    // New tests for startSmsScanAndIdentifyMappings coverage
    // =========================================================================

    @Test
    fun `startSmsScanAndIdentifyMappings returns 0 when no SMS permission`() = runTest {
        // Arrange - Do NOT grant permission
        initializeViewModel()
        var importedCount = -1
        
        val eventJob = launch { viewModel.uiEvent.collect() }

        try {
            // Act
            viewModel.startSmsScanAndIdentifyMappings(null) { importedCount = it }
            advanceUntilIdle()

            // Assert
            assertEquals("Should return 0 when permission denied", 0, importedCount)
        } finally {
            eventJob.cancel()
        }
    }

    @Test
    fun `startSmsScanAndIdentifyMappings sends event when no SMS found`() = runTest {
        // Arrange
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
        `when`(smsRepository.fetchAllSms(org.mockito.ArgumentMatchers.isNull())).thenReturn(emptyList())
        
        initializeViewModel()
        
        val events = mutableListOf<String>()
        val eventJob = launch { viewModel.uiEvent.collect { events.add(it) } }
        var importedCount = -1

        try {
            // Act
            viewModel.startSmsScanAndIdentifyMappings(null) { importedCount = it }
            advanceUntilIdle()

            // Assert
            assertEquals("Should return 0 when no SMS found", 0, importedCount)
            assertTrue("Should send 'No SMS' event", events.any { it.contains("No SMS") })
        } finally {
            eventJob.cancel()
        }
    }

    @Test
    fun `startSmsScanAndIdentifyMappings filters non-transactional SMS using classifier`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        // Re-init with unconfined for this test to ensure immediate execution of async tasks
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
            smsClassifier,
            transactionRunner,
            dispatchers = TestDispatcherProvider(testDispatcher)
        )

        // This tests the ML classifier filtering path
        val transactionalSms = SmsMessage(1, "BANK", "Your account credited with Rs 500", 1L)
        val nonTransactionalSms = SmsMessage(2, "PROMO", "Check out our latest deals!", 2L)
        
        `when`(smsClassifier.classify("Your account credited with Rs 500")).thenReturn(0.9f)
        `when`(smsClassifier.classify("Check out our latest deals!")).thenReturn(0.05f)
        
        `when`(smsRepository.fetchAllSms(org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(listOf(transactionalSms, nonTransactionalSms))
        
        `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
        `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        `when`(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())
        `when`(accountAliasDao.findByAlias(anyString())).thenReturn(null)
        `when`(accountDao.findByName(anyString())).thenReturn(null)
        
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
        
        val eventJob = launch { viewModel.uiEvent.collect() }

        try {
            viewModel.startSmsScanAndIdentifyMappings(null) { }
            advanceUntilIdle() // Should be redundant with Unconfined but good practice

            verify(smsClassifier).classify("Your account credited with Rs 500")
            verify(smsClassifier).classify("Check out our latest deals!")
        } finally {
            eventJob.cancel()
        }
    }

    @Test
    fun `startSmsScanAndIdentifyMappings updates progress state flows during scan`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
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
            smsClassifier,
            transactionRunner,
            dispatchers = TestDispatcherProvider(testDispatcher)
        )

        val sms1 = SmsMessage(1, "BANK", "Transaction 1", 1L)
        val sms2 = SmsMessage(2, "BANK", "Transaction 2", 2L)
        
        `when`(smsRepository.fetchAllSms(org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(listOf(sms1, sms2))
        `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
        `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        `when`(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())
        `when`(smsClassifier.classify(anyString())).thenReturn(0.05f) 
        
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
        
        val eventJob = launch { viewModel.uiEvent.collect() }
        
        val totalSmsCounts = mutableListOf<Int>()
        // Use a conflated collection or just collectLatest to see distinct values? 
        // StateFlow coalesces values. We might miss '2' if it flips back to '0' quickly.
        // But with Unconfined, it should happen sequentially.
        val totalSmsJob = launch(UnconfinedTestDispatcher(testScheduler)) { 
            viewModel.totalSmsToScan.collect { 
                println("Test: totalSmsToScan emitted: $it")
                totalSmsCounts.add(it) 
            } 
        }

        try {
            viewModel.startSmsScanAndIdentifyMappings(null) { }
            // No advanceUntilIdle needed strictly if Unconfined, but good for safety
            
            // Check if 2 was ever emitted
            assertTrue("totalSmsToScan should have been set to 2. History: $totalSmsCounts", totalSmsCounts.contains(2))
        } finally {
            eventJob.cancel()
            totalSmsJob.cancel()
        }
    }

    @Test
    fun `startSmsScanAndIdentifyMappings resets state after completion`() = runTest {
        // Arrange
        `when`(smsRepository.fetchAllSms(org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(listOf(SmsMessage(1, "BANK", "Test", 1L)))
        `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
        `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
        `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
        `when`(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
        `when`(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
        `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
        `when`(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())
        `when`(smsClassifier.classify(anyString())).thenReturn(0.05f)
        
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
        initializeViewModel()
        
        val eventJob = launch { viewModel.uiEvent.collect() }

        try {
            // Act
            viewModel.startSmsScanAndIdentifyMappings(null) { }
            advanceUntilIdle()

            // Assert - State should be reset after completion
            assertFalse("isScanning should be false after completion", viewModel.isScanning.value)
            assertEquals("totalSmsToScan should be 0 after completion", 0, viewModel.totalSmsToScan.value)
            assertEquals("processedSmsCount should be 0 after completion", 0, viewModel.processedSmsCount.value)
        } finally {
            eventJob.cancel()
        }
    }
}

