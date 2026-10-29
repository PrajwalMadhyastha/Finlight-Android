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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.data.health.DatabaseHealthChecker
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.utils.ReminderManager
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Calendar

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsViewModelTest : BaseViewModelTest() {
    private val applicationContext: Application = ApplicationProvider.getApplicationContext()

    @Mock private lateinit var settingsRepository: SettingsRepository

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var merchantMappingRepository: MerchantMappingRepository

    @Mock private lateinit var accountRepository: AccountRepository

    @Mock private lateinit var categoryRepository: CategoryRepository

    @Mock private lateinit var smsRepository: SmsRepository

    @Mock private lateinit var autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase

    @Mock private lateinit var smsClassifier: SmsClassifier

    @Mock private lateinit var nerExtractor: SmsEntityExtractor

    private lateinit var transactionRunner: TransactionRunner

    @Mock private lateinit var tagRepository: ITagRepository

    @Mock private lateinit var splitTransactionRepository: ISplitTransactionRepository

    @Mock private lateinit var merchantRenameRuleRepository: IMerchantRenameRuleRepository

    @Mock private lateinit var merchantCategoryMappingRepository: IMerchantCategoryMappingRepository

    @Mock private lateinit var customSmsRuleRepository: ICustomSmsRuleRepository

    @Mock private lateinit var ignoreRuleRepository: IIgnoreRuleRepository

    @Mock private lateinit var smsParseTemplateProvider: SmsParseTemplateProvider

    private lateinit var viewModel: SettingsViewModel

    // Helper function to set the private StateFlow using reflection
    private fun setPotentialTransactions(
        viewModel: SettingsViewModel,
        transactions: List<PotentialTransaction>,
    ) {
        val field = viewModel.javaClass.getDeclaredField("_potentialTransactions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableStateFlow = field.get(viewModel) as MutableStateFlow<List<PotentialTransaction>>
        mutableStateFlow.value = transactions
    }

    // Helper function to set the private StateFlow using reflection
    private fun setCsvValidationReport(
        viewModel: SettingsViewModel,
        report: CsvValidationReport?,
    ) {
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

        // --- NEW: Mock TransactionRunner to execute immediately ---
        transactionRunner = io.mockk.mockk()
        // Match generic arguments using MockK
        coEvery { transactionRunner.run<Any?>(any()) } coAnswers {
            val block = firstArg<suspend () -> Any?>()
            block()
        }

        // --- NEW: Grant SMS permissions via Shadow (backup) ---
        org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

        // --- NEW: Force permission grant by mocking ContextCompat ---
        mockkStatic(ContextCompat::class)
        io.mockk.every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        runTest {
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(emptyList())
            `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(emptyList())
            `when`(tagRepository.findByName(anyString())).thenReturn(null)
            `when`(tagRepository.allTags).thenReturn(flowOf(emptyList()))
            `when`(splitTransactionRepository.getSplitsForParent(any())).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))
            `when`(merchantCategoryMappingRepository.getAllMappings()).thenReturn(flowOf(emptyList()))
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(emptyList())
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
        `when`(settingsRepository.getSimulatorPrivacyModeEnabled()).thenReturn(flowOf(false))
        // --- ADDED: Mock for `getLastBackupTimestamp` ---
        `when`(settingsRepository.getLastBackupTimestamp()).thenReturn(flowOf(0L))
    }

    private fun initializeViewModel(
        customNerExtractorProvider: (() -> SmsEntityExtractor)? = null,
    ) {
        viewModel =
            SettingsViewModel(
                application = applicationContext,
                settingsRepository = settingsRepository,
                transactionRepository = transactionRepository,
                merchantMappingRepository = merchantMappingRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                smsRepository = smsRepository,
                autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                smsClassifier = smsClassifier,
                tagRepository = tagRepository,
                splitTransactionRepository = splitTransactionRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                customSmsRuleRepository = customSmsRuleRepository,
                ignoreRuleRepository = ignoreRuleRepository,
                smsParseTemplateProvider = smsParseTemplateProvider,
                nerExtractorProvider = customNerExtractorProvider ?: { nerExtractor },
                transactionRunner = transactionRunner,
                dispatchers = TestDispatcherProvider(testDispatcher),
            )
    }

    @After
    fun after() {
        unmockkAll()
    }

    @Test
    fun `startSmsScanAndIdentifyMappings does not call classifier for SMS matching custom rules`() =
        runTest {
            // This test verifies the optimization: classifier is NOT called if custom rule matches

            val sms = SmsMessage(1, "ICICI", "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Jun-25; DAKSHIN CAFE credited.", 1L)

            // Mock dependencies
            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            // Add a custom rule that matches the SMS
            val rule =
                CustomSmsRule(
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
                    transactionType = null,
                )
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

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
    fun `applyLearningAndAutoImport correctly filters list and calls auto-save`() =
        runTest {
            // Arrange
            initializeViewModel()
            val mapping = MerchantCategoryMapping("Amazon", 1)
            val txnToImport = PotentialTransaction(1, "", 1.0, "", "Amazon", "")
            val txnToKeep = PotentialTransaction(2, "", 1.0, "", "Flipkart", "")
            setPotentialTransactions(viewModel, listOf(txnToImport, txnToKeep))
            `when`(autoSaveSmsTransactionUseCase(any(), any(), anyOrNull())).thenReturn(true)

            // Act
            val importCount = viewModel.applyLearningAndAutoImport(mapping)

            // Assert
            assertEquals(1, importCount)
            assertEquals(1, viewModel.potentialTransactions.value.size)
            assertEquals("Flipkart", viewModel.potentialTransactions.value.first().merchantName)
            verify(autoSaveSmsTransactionUseCase).invoke(eq(txnToImport.copy(categoryId = 1)), eq("Imported"), anyOrNull())
        }

    // --- DELETED: `finalizeImport` test ---

    @Test
    fun `validateCsvFile correctly processes valid and invalid rows`() =
        runTest {
            // Arrange
            // FIX: The CSV content now includes the correct 11-column header.
            val csvContent =
                """
                Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags
                ,,2025-10-09 10:00:00,Coffee,150.0,expense,Food,Savings,,false,
                ,,2025-10-09 11:00:00,Groceries,2000.0,expense,NewCategory,NewAccount,,false,
                ,,invalid-date,Stuff,10.0,expense,Food,Savings,,false,
                """.trimIndent()
            val mockUri: Uri = Uri.parse("content://test/test.csv")

            // FIX: Use Robolectric's shadowOf to mock the ContentResolver correctly.
            val shadowContentResolver = shadowOf(applicationContext.contentResolver)
            shadowContentResolver.registerInputStream(mockUri, ByteArrayInputStream(csvContent.toByteArray()))

            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Savings", "Bank")))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(Category(1, "Food", "", "")))

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
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removeRowFromReport updates the state`() =
        runTest {
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
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateAndRevalidateRow updates the correct row`() =
        runTest {
            // Arrange
            initializeViewModel()
            // FIX: Explicitly define lists instead of using the fragile .split() method.
            val row1 =
                ReviewableRow(
                    1,
                    listOf("", "", "invalid-date", "a", "10", "expense", "Food", "Savings", "", "false", ""),
                    CsvRowStatus.INVALID_DATE,
                    "",
                )
            val row2 =
                ReviewableRow(
                    2,
                    listOf("", "", "2025-10-10 10:00:00", "b", "20", "expense", "Food", "Savings", "", "false", ""),
                    CsvRowStatus.VALID,
                    "",
                )
            setCsvValidationReport(viewModel, CsvValidationReport(reviewableRows = listOf(row1, row2)))

            val correctedData = listOf("", "", "2025-10-09 10:00:00", "a", "10", "expense", "Food", "Savings", "", "false", "")
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Savings", "Bank")))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(Category(1, "Food", "", "")))

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
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `commitCsvImport calls repository to insert transactions`() =
        runTest {
            // Arrange
            initializeViewModel()
            val rowsToImport =
                listOf(
                    ReviewableRow(
                        2,
                        "1,,2025-10-09 10:00:00,Coffee,150.0,expense,Food,Savings,,false,Work|Personal".split(','),
                        CsvRowStatus.VALID,
                        "",
                    ),
                )
            setCsvValidationReport(
                viewModel,
                CsvValidationReport(
                    header = "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags".split(','),
                    reviewableRows = rowsToImport,
                ),
            )

            `when`(categoryRepository.allCategories).thenReturn(flowOf(listOf(Category(1, "Food", "", ""))))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(Category(1, "Food", "", "")))
            `when`(categoryRepository.findByName("Food")).thenReturn(Category(1, "Food", "", ""))
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Savings", "Bank")))
            `when`(tagRepository.findByName("Work")).thenReturn(Tag(1, "Work"))
            `when`(tagRepository.findByName("Personal")).thenReturn(null)
            `when`(tagRepository.insert(anyObject())).thenReturn(2L)
            `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(1L)

            // Act
            viewModel.commitCsvImport(rowsToImport)
            advanceUntilIdle()

            // Assert
            val transactionCaptor = argumentCaptor<Transaction>()
            val tagsCaptor = argumentCaptor<Set<Tag>>()
            // Use timeout because of race condition with Dispatchers.IO
            verify(
                transactionRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertTransactionWithTags(capture(transactionCaptor), capture(tagsCaptor))

            assertEquals("Coffee", transactionCaptor.value.description)
            assertEquals(150.0, transactionCaptor.value.amount, 0.0)
            assertEquals(2, tagsCaptor.value.size)
            assertTrue(tagsCaptor.value.any { it.name == "Work" })
            assertTrue(tagsCaptor.value.any { it.name == "Personal" })
        }

    @Test
    fun `commitCsvImport creates new account when not found in snapshot`() =
        runTest {
            // Arrange
            initializeViewModel()
            val rowsToImport =
                listOf(
                    ReviewableRow(
                        2,
                        "1,,2025-10-09 10:00:00,Groceries,200.0,expense,Food,NewWallet,,false,".split(','),
                        CsvRowStatus.VALID,
                        "",
                    ),
                )
            setCsvValidationReport(
                viewModel,
                CsvValidationReport(
                    header = "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags".split(','),
                    reviewableRows = rowsToImport,
                ),
            )

            `when`(categoryRepository.allCategories).thenReturn(flowOf(listOf(Category(1, "Food", "", ""))))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(Category(1, "Food", "", "")))
            `when`(categoryRepository.findByName("Food")).thenReturn(Category(1, "Food", "", ""))
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "OtherBank", "Bank")))
            `when`(accountRepository.insert(Account(name = "NewWallet", type = "Imported"))).thenReturn(2L)
            `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(1L)

            // Act
            viewModel.commitCsvImport(rowsToImport)
            advanceUntilIdle()

            // Assert
            verify(accountRepository, org.mockito.Mockito.timeout(5000)).insert(Account(name = "NewWallet", type = "Imported"))
            val transactionCaptor = argumentCaptor<Transaction>()
            verify(
                transactionRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertTransactionWithTags(capture(transactionCaptor), anyObject())

            assertEquals(2, transactionCaptor.value.accountId)
        }

    @Test
    fun `commitCsvImport inserts split transactions and persists learned merchant category mappings`() =
        runTest {
            // Arrange
            initializeViewModel()
            val header = "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags".split(',')
            val parentRow =
                ReviewableRow(
                    lineNumber = 2,
                    rowData = "10,,2025-10-09 10:00:00,Supermarket,300.0,expense,Split Transaction,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val splitChildRow1 =
                ReviewableRow(
                    lineNumber = 3,
                    rowData = "11,10,2025-10-09 10:00:00,Food Items,200.0,expense,Groceries,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val splitChildRow2 =
                ReviewableRow(
                    lineNumber = 4,
                    rowData = "12,10,2025-10-09 10:00:00,Cleaning Items,100.0,expense,Household,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val normalRow =
                ReviewableRow(
                    lineNumber = 5,
                    rowData = "13,,2025-10-09 11:00:00,Starbucks,150.0,expense,Coffee,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val rowsToImport = listOf(parentRow, splitChildRow1, splitChildRow2, normalRow)

            setCsvValidationReport(
                viewModel,
                CsvValidationReport(
                    header = header,
                    reviewableRows = rowsToImport,
                ),
            )

            val groceriesCat = Category(id = 1, name = "Groceries", iconKey = "category", colorKey = "green")
            val householdCat = Category(id = 2, name = "Household", iconKey = "category", colorKey = "blue")
            val coffeeCat = Category(id = 3, name = "Coffee", iconKey = "category", colorKey = "brown")
            val allCats = listOf(groceriesCat, householdCat, coffeeCat)

            `when`(categoryRepository.allCategories).thenReturn(flowOf(allCats))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(allCats)
            `when`(categoryRepository.findByName("Groceries")).thenReturn(groceriesCat)
            `when`(categoryRepository.findByName("Household")).thenReturn(householdCat)
            `when`(categoryRepository.findByName("Coffee")).thenReturn(coffeeCat)
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Savings", "Bank")))
            `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(100L)

            // Act
            viewModel.commitCsvImport(rowsToImport)
            advanceUntilIdle()

            // Assert
            val splitsCaptor = argumentCaptor<List<SplitTransaction>>()
            verify(
                splitTransactionRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertAll(capture(splitsCaptor))

            val allCapturedSplits = splitsCaptor.value
            assertEquals(2, allCapturedSplits.size)
            assertTrue(allCapturedSplits.any { it.parentTransactionId == 100 && it.amount == 200.0 && it.categoryId == 1 })
            assertTrue(allCapturedSplits.any { it.parentTransactionId == 100 && it.amount == 100.0 && it.categoryId == 2 })

            val mappingsCaptor = argumentCaptor<List<MerchantCategoryMapping>>()
            verify(
                merchantCategoryMappingRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertAll(capture(mappingsCaptor))

            val learnedList = mappingsCaptor.value
            assertTrue(learnedList.any { it.parsedName == "Starbucks" && it.categoryId == 3 })
        }

    @Test
    fun `commitCsvImport catches exception during transaction execution and sends error message to uiEvent`() =
        runTest {
            // Arrange
            initializeViewModel()
            val header = listOf("Id", "Date", "Description", "Amount", "Type", "Category", "Account")
            val row =
                ReviewableRow(
                    lineNumber = 2,
                    rowData = listOf("1", "2025-10-09 10:00:00", "Coffee", "50.0", "expense", "Food", "Cash"),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            setCsvValidationReport(viewModel, CsvValidationReport(header = header, reviewableRows = listOf(row)))

            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(emptyList())
            coEvery { transactionRunner.run<Any?>(any()) } coAnswers {
                throw IllegalStateException("Database write failed")
            }

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.commitCsvImport(listOf(row))
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event.contains("CSV import failed: Database write failed"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `commitCsvImport with generic CSV imports rows as non-split transactions and learns mappings`() =
        runTest {
            // Arrange: Generic CSV without "Id" or "ParentId"
            initializeViewModel()
            val header = listOf("Date", "Description", "Amount", "Type", "Category", "Account", "Notes", "Tags")
            val row =
                ReviewableRow(
                    lineNumber = 2,
                    rowData = listOf("2025-10-09 10:00:00", "Starbucks", "120.0", "expense", "Coffee", "Checking", "Morning latte", "Work|Personal"),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val rowsToImport = listOf(row)
            setCsvValidationReport(viewModel, CsvValidationReport(header = header, reviewableRows = rowsToImport))

            val coffeeCat = Category(id = 5, name = "Coffee", iconKey = "category", colorKey = "brown")
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(coffeeCat))
            `when`(categoryRepository.findByName("Coffee")).thenReturn(coffeeCat)
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Checking", "Bank")))
            `when`(tagRepository.findByName("Work")).thenReturn(Tag(1, "Work"))
            `when`(tagRepository.findByName("Personal")).thenReturn(Tag(2, "Personal"))
            `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(50L)

            // Act
            viewModel.commitCsvImport(rowsToImport)
            advanceUntilIdle()

            // Assert
            val txnCaptor = argumentCaptor<Transaction>()
            val tagsCaptor = argumentCaptor<Set<Tag>>()
            verify(
                transactionRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertTransactionWithTags(capture(txnCaptor), capture(tagsCaptor))

            val insertedTxn = txnCaptor.value
            assertEquals("Starbucks", insertedTxn.description)
            assertEquals(120.0, insertedTxn.amount, 0.0)
            assertFalse(insertedTxn.isSplit)
            assertEquals(5, insertedTxn.categoryId)
            assertEquals(1, insertedTxn.accountId)

            val capturedTags = tagsCaptor.value
            assertEquals(2, capturedTags.size)

            val mappingsCaptor = argumentCaptor<List<MerchantCategoryMapping>>()
            verify(
                merchantCategoryMappingRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertAll(capture(mappingsCaptor))
            assertTrue(mappingsCaptor.value.any { it.parsedName == "Starbucks" && it.categoryId == 5 })
        }

    @Test
    fun `commitCsvImport skips orphan split child row when parentId is not found in idMap`() =
        runTest {
            initializeViewModel()
            val header = "Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags".split(',')
            val parentRow =
                ReviewableRow(
                    lineNumber = 2,
                    rowData = "10,,2025-10-09 10:00:00,Supermarket,300.0,expense,Split Transaction,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val orphanChildRow =
                ReviewableRow(
                    lineNumber = 3,
                    rowData = "11,999,2025-10-09 10:00:00,Orphan Split,100.0,expense,Groceries,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val validChildRow =
                ReviewableRow(
                    lineNumber = 4,
                    rowData = "12,10,2025-10-09 10:00:00,Valid Split,200.0,expense,Groceries,Savings,,false,".split(','),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val rowsToImport = listOf(parentRow, orphanChildRow, validChildRow)
            setCsvValidationReport(viewModel, CsvValidationReport(header = header, reviewableRows = rowsToImport))

            val groceriesCat = Category(id = 1, name = "Groceries", iconKey = "category", colorKey = "green")
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(groceriesCat))
            `when`(categoryRepository.findByName("Groceries")).thenReturn(groceriesCat)
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Savings", "Bank")))
            `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(100L)

            // Act
            viewModel.commitCsvImport(rowsToImport)
            advanceUntilIdle()

            // Assert
            val splitsCaptor = argumentCaptor<List<SplitTransaction>>()
            verify(
                splitTransactionRepository,
                org.mockito.Mockito.timeout(5000),
            ).insertAll(capture(splitsCaptor))

            val allSplits = splitsCaptor.value
            assertEquals(1, allSplits.size)
            assertEquals(100, allSplits[0].parentTransactionId)
            assertEquals(200.0, allSplits[0].amount, 0.0)
        }

    @Test
    fun `commitCsvImport dynamically creates category with next available color when not found`() =
        runTest {
            initializeViewModel()
            val header = listOf("Date", "Description", "Amount", "Type", "Category", "Account")
            val row =
                ReviewableRow(
                    lineNumber = 2,
                    rowData = listOf("2025-10-09 10:00:00", "Gym Membership", "50.0", "expense", "Fitness", "Checking"),
                    status = CsvRowStatus.VALID,
                    statusMessage = "",
                )
            val rowsToImport = listOf(row)
            setCsvValidationReport(viewModel, CsvValidationReport(header = header, reviewableRows = rowsToImport))

            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(emptyList())
            `when`(categoryRepository.findByName("Fitness")).thenReturn(null)
            `when`(categoryRepository.insert(anyObject())).thenReturn(42L)
            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Checking", "Bank")))
            `when`(transactionRepository.insertTransactionWithTags(anyObject(), anyObject())).thenReturn(1L)

            // Act
            viewModel.commitCsvImport(rowsToImport)
            advanceUntilIdle()

            // Assert
            val catCaptor = argumentCaptor<Category>()
            verify(categoryRepository, org.mockito.Mockito.timeout(5000)).insert(capture(catCaptor))
            assertEquals("Fitness", catCaptor.value.name)
            assertEquals("letter_default", catCaptor.value.iconKey)
            assertNotNull(catCaptor.value.colorKey)

            val txnCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository, org.mockito.Mockito.timeout(5000)).insertTransactionWithTags(capture(txnCaptor), anyObject())
            assertEquals(42, txnCaptor.value.categoryId)
        }

    @Test
    fun `commitCsvImport returns early without inserting when validation report header is null`() =
        runTest {
            initializeViewModel()
            setCsvValidationReport(viewModel, null)

            viewModel.commitCsvImport(emptyList())
            advanceUntilIdle()

            verify(transactionRepository, never()).insertTransactionWithTags(anyObject(), anyObject())
            verify(splitTransactionRepository, never()).insertAll(anyObject())
        }

    @Test
    fun `secondary constructor instantiates SettingsViewModel successfully`() =
        runTest {
            val secondaryVm =
                SettingsViewModel(
                    application = applicationContext,
                    settingsRepository = settingsRepository,
                    transactionRepository = transactionRepository,
                    merchantMappingRepository = merchantMappingRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    smsRepository = smsRepository,
                    autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                    smsClassifier = smsClassifier,
                    tagRepository = tagRepository,
                    splitTransactionRepository = splitTransactionRepository,
                    merchantRenameRuleRepository = merchantRenameRuleRepository,
                    merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                    customSmsRuleRepository = customSmsRuleRepository,
                    ignoreRuleRepository = ignoreRuleRepository,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerExtractor = nerExtractor,
                    transactionRunner = transactionRunner,
                    databaseHealthChecker = DatabaseHealthChecker,
                    dispatchers = TestDispatcherProvider(testDispatcher),
                )
            assertNotNull(secondaryVm)
        }

    @Test
    fun `privacyModeEnabled flow emits value from repository`() =
        runTest {
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
    fun `setPrivacyModeEnabled calls repository`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.setPrivacyModeEnabled(true)
            advanceUntilIdle()

            // Assert
            verify(settingsRepository).savePrivacyModeEnabled(true)
        }

    @Test
    fun `simulatorPrivacyModeEnabled flow emits value from repository`() =
        runTest {
            // Arrange
            `when`(settingsRepository.getSimulatorPrivacyModeEnabled()).thenReturn(flowOf(true))
            initializeViewModel()

            // Assert
            viewModel.simulatorPrivacyModeEnabled.test {
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSimulatorPrivacyModeEnabled calls repository`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.setSimulatorPrivacyModeEnabled(true)
            advanceUntilIdle()

            // Assert
            verify(settingsRepository).saveSimulatorPrivacyModeEnabled(true)
        }

    @Test
    fun `saveSelectedTheme calls repository`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.saveSelectedTheme(AppTheme.AURORA)
            advanceUntilIdle()

            // Assert
            verify(settingsRepository).saveSelectedTheme(AppTheme.AURORA)
        }

    @Test
    fun `setAppLockEnabled calls repository`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.setAppLockEnabled(true)
            advanceUntilIdle()

            // Assert
            verify(settingsRepository).saveAppLockEnabled(true)
        }

    @Test
    fun `appLockEnabled emits from repository flow`() =
        runTest {
            val appLockFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
            `when`(settingsRepository.getAppLockEnabled()).thenReturn(appLockFlow)
            initializeViewModel()

            val values = mutableListOf<Boolean?>()
            val job =
                launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.appLockEnabled.collect { values.add(it) }
                }

            assertTrue("appLockEnabled should reflect repository emission", values.contains(true))
            job.cancel()
        }

    // --- UPDATED: Test for backup success dialog ---
    @Test
    fun `createBackupSnapshot success sets showBackupSuccessDialog to true`() =
        runTest {
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
    fun `createBackupSnapshot failure sends failure message to uiEvent channel`() =
        runTest {
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
    fun `dismissBackupSuccessDialog sets flow to false`() =
        runTest {
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

    @Test
    fun `restoreFromBackupSnapshot success sends success message to uiEvent channel`() =
        runTest {
            val expectedMessage = "Data restored successfully from snapshot! Please restart the app."
            mockkObject(DataExportService)
            coEvery { DataExportService.restoreFromBackupSnapshot(applicationContext) } returns true

            initializeViewModel()

            viewModel.uiEvent.test {
                viewModel.restoreFromBackupSnapshot()
                advanceUntilIdle()
                assertEquals(expectedMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `restoreFromBackupSnapshot failure sends failure message to uiEvent channel`() =
        runTest {
            val expectedMessage = "No backup snapshot found or restore failed."
            mockkObject(DataExportService)
            coEvery { DataExportService.restoreFromBackupSnapshot(applicationContext) } returns false

            initializeViewModel()

            viewModel.uiEvent.test {
                viewModel.restoreFromBackupSnapshot()
                advanceUntilIdle()
                assertEquals(expectedMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiEvent buffers multiple events without suspending when no collector is active`() =
        runTest {
            val failureMessage = "No backup snapshot found or restore failed."
            mockkObject(DataExportService)
            coEvery { DataExportService.restoreFromBackupSnapshot(applicationContext) } returns false

            initializeViewModel()

            // Trigger multiple events while no collectors are listening
            viewModel.restoreFromBackupSnapshot()
            advanceUntilIdle()
            viewModel.restoreFromBackupSnapshot()
            advanceUntilIdle()

            // Attach collector afterwards and verify all buffered events are drained in FIFO order
            viewModel.uiEvent.test {
                assertEquals(failureMessage, awaitItem())
                assertEquals(failureMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- NEW: Tests for the objective ---

    @Test
    fun `rescanSmsWithNewRule finds and saves new transactions`() =
        runTest {
            // Arrange
            val sms = SmsMessage(1, "SENDER", "spent Rs 100", 1L)
            val parsedTxn = PotentialTransaction(1L, "SENDER", 100.0, "expense", "Store", "spent Rs 100", null, "hash123")

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList())) // No existing hashes

            // Mock all parser dependencies to return empty/null
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            // Mock the parser to successfully parse the transaction
            // We can do this by mocking the one rule it needs
            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            // Mock the final auto-save step
            // --- FIX: Use anyObject() for non-nullable Kotlin class ---
            `when`(autoSaveSmsTransactionUseCase(anyObject(), eq("Imported"), anyOrNull())).thenReturn(true)

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
            verify(autoSaveSmsTransactionUseCase).invoke(anyObject(), eq("Imported"), anyOrNull())
        }

    @Test
    fun `rescanSmsWithNewRule skips and upgrades transactions existing under legacy hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(listOf(legacyHash)))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(emptyList())

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            initializeViewModel()

            var newTransactionCount = -1
            viewModel.rescanSmsWithNewRule { count ->
                newTransactionCount = count
            }
            advanceUntilIdle()

            assertEquals("Should have found 0 new transactions", 0, newTransactionCount)
            verify(transactionRepository).updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash)
            verify(autoSaveSmsTransactionUseCase, never()).invoke(anyObject(), anyString(), anyOrNull())
        }

    @Test
    fun `rescanSmsWithNewRule skips and upgrades transactions existing in deleted_sms_hashes`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(listOf(legacyHash))

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            initializeViewModel()

            var newTransactionCount = -1
            viewModel.rescanSmsWithNewRule { count ->
                newTransactionCount = count
            }
            advanceUntilIdle()

            assertEquals("Should have found 0 new transactions", 0, newTransactionCount)
            verify(transactionRepository).insertDeletedSmsHash(currentHash)
            verify(autoSaveSmsTransactionUseCase, never()).invoke(anyObject(), anyString(), anyOrNull())
        }

    @Test
    fun `rescanSmsWithNewRule skips already existing transaction under current hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(listOf(currentHash)))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(emptyList())

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            initializeViewModel()

            var newTransactionCount = -1
            viewModel.rescanSmsWithNewRule { count ->
                newTransactionCount = count
            }
            advanceUntilIdle()

            assertEquals(0, newTransactionCount)
            verify(autoSaveSmsTransactionUseCase, never()).invoke(anyObject(), anyString(), anyOrNull())
        }

    @Test
    fun `rescanSmsWithNewRule skips transaction in deleted_sms_hashes under current hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(listOf(currentHash))

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            initializeViewModel()

            var newTransactionCount = -1
            viewModel.rescanSmsWithNewRule { count ->
                newTransactionCount = count
            }
            advanceUntilIdle()

            assertEquals(0, newTransactionCount)
            verify(autoSaveSmsTransactionUseCase, never()).invoke(anyObject(), anyString(), anyOrNull())
        }

    @Test
    fun `startSmsScanAndIdentifyMappings parses filters and auto-saves new transactions`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(emptyList())

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            var autoSaveCalled = false
            `when`(autoSaveSmsTransactionUseCase(anyObject(), anyString(), anyOrNull())).thenAnswer {
                autoSaveCalled = true
                true
            }

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
            initializeViewModel()

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                var importedCount = -1
                viewModel.startSmsScanAndIdentifyMappings(null) { count ->
                    importedCount = count
                }
                advanceUntilIdle()

                assertEquals(1, importedCount)
                assertTrue(autoSaveCalled)
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `startSmsScanAndIdentifyMappings skips and upgrades transaction existing under legacy hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(listOf(legacyHash)))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(emptyList())

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            var autoSaveCalled = false
            `when`(autoSaveSmsTransactionUseCase(anyObject(), anyString(), anyOrNull())).thenAnswer {
                autoSaveCalled = true
                true
            }

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
            initializeViewModel()

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                var importedCount = -1
                viewModel.startSmsScanAndIdentifyMappings(null) { count ->
                    importedCount = count
                }
                advanceUntilIdle()

                assertEquals(0, importedCount)
                verify(transactionRepository).updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash)
                assertFalse(autoSaveCalled)
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `startSmsScanAndIdentifyMappings skips already existing transaction under current hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(listOf(currentHash)))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(emptyList())

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            var autoSaveCalled = false
            `when`(autoSaveSmsTransactionUseCase(anyObject(), anyString(), anyOrNull())).thenAnswer {
                autoSaveCalled = true
                true
            }

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
            initializeViewModel()

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                var importedCount = -1
                viewModel.startSmsScanAndIdentifyMappings(null) { count ->
                    importedCount = count
                }
                advanceUntilIdle()

                assertEquals(0, importedCount)
                assertFalse(autoSaveCalled)
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `startSmsScanAndIdentifyMappings skips and upgrades transaction in deleted_sms_hashes under legacy hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(listOf(legacyHash))

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            var autoSaveCalled = false
            `when`(autoSaveSmsTransactionUseCase(anyObject(), anyString(), anyOrNull())).thenAnswer {
                autoSaveCalled = true
                true
            }

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
            initializeViewModel()

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                var importedCount = -1
                viewModel.startSmsScanAndIdentifyMappings(null) { count ->
                    importedCount = count
                }
                advanceUntilIdle()

                assertEquals(0, importedCount)
                verify(transactionRepository).insertDeletedSmsHash(currentHash)
                assertFalse(autoSaveCalled)
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `startSmsScanAndIdentifyMappings skips transaction in deleted_sms_hashes under current hash`() =
        runTest {
            val sender = "VK-HDFCBK"
            val body = "spent Rs 100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val sms = SmsMessage(1, sender, body, 1L)

            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllDeletedSmsHashes()).thenReturn(listOf(currentHash))

            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))

            var autoSaveCalled = false
            `when`(autoSaveSmsTransactionUseCase(anyObject(), anyString(), anyOrNull())).thenAnswer {
                autoSaveCalled = true
                true
            }

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
            initializeViewModel()

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                var importedCount = -1
                viewModel.startSmsScanAndIdentifyMappings(null) { count ->
                    importedCount = count
                }
                advanceUntilIdle()

                assertEquals(0, importedCount)
                assertFalse(autoSaveCalled)
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `setAutoCaptureNotificationEnabled calls repository`() =
        runTest {
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
    fun `setAutoBackupEnabled calls repository and ReminderManager`() =
        runTest {
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
    fun `setAutoBackupNotificationEnabled calls repository`() =
        runTest {
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
    fun `dismissPotentialTransaction removes transaction from state`() =
        runTest {
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
    fun `onTransactionApproved removes transaction from state`() =
        runTest {
            // Arrange
            initializeViewModel()
            // --- FIX: Use named arguments to set the correct sourceSmsId ---
            val txn1 =
                PotentialTransaction(
                    sourceSmsId = 101L,
                    smsSender = "",
                    amount = 1.0,
                    transactionType = "expense",
                    merchantName = "Amazon",
                    originalMessage = "",
                )
            val txn2 =
                PotentialTransaction(
                    sourceSmsId = 102L,
                    smsSender = "",
                    amount = 2.0,
                    transactionType = "expense",
                    merchantName = "Flipkart",
                    originalMessage = "",
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
    fun `onTransactionLinked removes transaction from state`() =
        runTest {
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
    fun `saveMerchantRenameRule calls dao insert when names are different`() =
        runTest {
            // Arrange
            whenever(transactionRepository.updateDescriptionByOriginalDescription(any(), any(), anyOrNull())).thenReturn(0)
            initializeViewModel()
            val originalName = "AMZN"
            val newName = "Amazon"

            // Act
            viewModel.saveMerchantRenameRule(originalName, newName)
            advanceUntilIdle()

            // Assert
            verify(merchantRenameRuleRepository).insert(MerchantRenameRule(originalName, newName))
            verify(transactionRepository).updateDescriptionByOriginalDescription(originalName, newName, null)
            verify(merchantRenameRuleRepository, never()).deleteByOriginalName(anyString())
        }

    @Test
    fun `saveMerchantRenameRule calls dao delete when names are same`() =
        runTest {
            // Arrange
            whenever(transactionRepository.updateDescriptionByOriginalDescription(any(), any(), anyOrNull())).thenReturn(0)
            initializeViewModel()
            val originalName = "Amazon"
            val newName = "Amazon" // Same name

            // Act
            viewModel.saveMerchantRenameRule(originalName, newName)
            advanceUntilIdle()

            // Assert
            verify(merchantRenameRuleRepository).deleteByOriginalName(originalName)
            verify(transactionRepository).updateDescriptionByOriginalDescription(originalName, originalName, null)
            verify(merchantRenameRuleRepository, never()).insert(any())
        }

    @Test
    fun `saveMerchantRenameRule returns early when originalName or newName is blank`() =
        runTest {
            initializeViewModel()

            viewModel.saveMerchantRenameRule("", "Amazon")
            viewModel.saveMerchantRenameRule("Amazon", "   ")
            advanceUntilIdle()

            verify(merchantRenameRuleRepository, never()).insert(any())
            verify(merchantRenameRuleRepository, never()).deleteByOriginalName(any())
        }

    @Test
    fun `saveMerchantRenameRule passes oldDescription from existing rule when updating`() =
        runTest {
            val existingRule = MerchantRenameRule("Uber", "Uber Rides")
            `when`(merchantRenameRuleRepository.getRuleByOriginalName("Uber")).thenReturn(existingRule)
            whenever(transactionRepository.updateDescriptionByOriginalDescription(any(), any(), anyOrNull())).thenReturn(1)
            initializeViewModel()

            viewModel.saveMerchantRenameRule("Uber", "Uber Trip")
            advanceUntilIdle()

            verify(transactionRepository).updateDescriptionByOriginalDescription("Uber", "Uber Trip", "Uber Rides")
        }

    @Test
    fun `removeRowFromReport does nothing when csvValidationReport is null`() =
        runTest {
            initializeViewModel()
            setCsvValidationReport(viewModel, null)

            val row = ReviewableRow(1, listOf("a"), CsvRowStatus.VALID, "")
            viewModel.removeRowFromReport(row)

            assertNull(viewModel.csvValidationReport.value)
        }

    @Test
    fun `updateAndRevalidateRow handles null report and non-existent line number gracefully`() =
        runTest {
            initializeViewModel()
            setCsvValidationReport(viewModel, null)
            viewModel.updateAndRevalidateRow(99, listOf("test"))
            advanceUntilIdle()
            assertNull(viewModel.csvValidationReport.value)

            val row = ReviewableRow(1, listOf("a"), CsvRowStatus.VALID, "")
            setCsvValidationReport(viewModel, CsvValidationReport(reviewableRows = listOf(row)))
            viewModel.updateAndRevalidateRow(99, listOf("test"))
            advanceUntilIdle()
            assertEquals(1, viewModel.csvValidationReport.value?.reviewableRows?.size)
        }

    @Test
    fun `updateAndRevalidateRow revalidates row and updates report status for invalid columns, amounts, and new categories`() =
        runTest {
            initializeViewModel()
            val row1 =
                ReviewableRow(
                    1,
                    listOf("1", "", "2025-10-09 10:00:00", "Coffee", "150.0", "expense", "Food", "Savings", "", "false", ""),
                    CsvRowStatus.INVALID_DATE,
                    "",
                )
            setCsvValidationReport(viewModel, CsvValidationReport(reviewableRows = listOf(row1)))

            `when`(accountRepository.getAllAccountsSnapshot()).thenReturn(listOf(Account(1, "Savings", "Bank")))
            `when`(categoryRepository.getAllCategoriesSnapshot()).thenReturn(listOf(Category(1, "Food", "", "")))

            // 1. Invalid columns (< 8)
            viewModel.updateAndRevalidateRow(1, listOf("1", "2"))
            advanceUntilIdle()
            assertEquals(CsvRowStatus.INVALID_COLUMN_COUNT, viewModel.csvValidationReport.value?.reviewableRows?.first()?.status)

            // 2. Invalid amount (<= 0)
            viewModel.updateAndRevalidateRow(
                1,
                listOf("1", "", "2025-10-09 10:00:00", "Coffee", "-5.0", "expense", "Food", "Savings", "", "false", ""),
            )
            advanceUntilIdle()
            assertEquals(CsvRowStatus.INVALID_AMOUNT, viewModel.csvValidationReport.value?.reviewableRows?.first()?.status)

            // 3. Needs account creation
            viewModel.updateAndRevalidateRow(
                1,
                listOf("1", "", "2025-10-09 10:00:00", "Coffee", "50.0", "expense", "Food", "NewAcc", "", "false", ""),
            )
            advanceUntilIdle()
            assertEquals(CsvRowStatus.NEEDS_ACCOUNT_CREATION, viewModel.csvValidationReport.value?.reviewableRows?.first()?.status)

            // 4. Needs category creation
            viewModel.updateAndRevalidateRow(
                1,
                listOf("1", "", "2025-10-09 10:00:00", "Coffee", "50.0", "expense", "NewCat", "Savings", "", "false", ""),
            )
            advanceUntilIdle()
            assertEquals(CsvRowStatus.NEEDS_CATEGORY_CREATION, viewModel.csvValidationReport.value?.reviewableRows?.first()?.status)

            // 5. Valid row
            viewModel.updateAndRevalidateRow(
                1,
                listOf("1", "", "2025-10-09 10:00:00", "Coffee", "50.0", "expense", "Food", "Savings", "", "false", ""),
            )
            advanceUntilIdle()
            assertEquals(CsvRowStatus.VALID, viewModel.csvValidationReport.value?.reviewableRows?.first()?.status)
        }

    @Test
    fun `saveMerchantRenameRule is case-insensitive for delete check`() =
        runTest {
            // Arrange
            whenever(transactionRepository.updateDescriptionByOriginalDescription(any(), any(), anyOrNull())).thenReturn(0)
            initializeViewModel()
            val originalName = "Amazon"
            val newName = "amazon" // Same name, different case

            // Act
            viewModel.saveMerchantRenameRule(originalName, newName)
            advanceUntilIdle()

            // Assert
            verify(merchantRenameRuleRepository).deleteByOriginalName(originalName)
            verify(transactionRepository).updateDescriptionByOriginalDescription(originalName, originalName, null)
            verify(merchantRenameRuleRepository, never()).insert(any())
        }

    @Test
    fun `saveSmsScanStartDate calls repository`() =
        runTest {
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
    fun `setDailyReportEnabled calls repository and ReminderManager`() =
        runTest {
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
    fun `saveDailyReportTime calls repository and schedules when enabled`() =
        runTest {
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
    fun `saveDailyReportTime calls repository and does not schedule when disabled`() =
        runTest {
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
    fun `setWeeklySummaryEnabled calls repository and ReminderManager`() =
        runTest {
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
    fun `saveWeeklyReportTime calls repository and schedules when enabled`() =
        runTest {
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
    fun `saveWeeklyReportTime calls repository and does not schedule when disabled`() =
        runTest {
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
    fun `setMonthlySummaryEnabled calls repository and ReminderManager`() =
        runTest {
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
    fun `saveMonthlyReportTime calls repository and schedules when enabled`() =
        runTest {
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
    fun `saveMonthlyReportTime calls repository and does not schedule when disabled`() =
        runTest {
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
    fun `setUnknownTransactionPopupEnabled calls repository`() =
        runTest {
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
    fun `clearCsvValidationReport sets flow to null`() =
        runTest {
            // Arrange
            initializeViewModel()
            // Set a non-null report
            setCsvValidationReport(
                viewModel,
                CsvValidationReport(
                    reviewableRows =
                        listOf(
                            ReviewableRow(1, emptyList(), CsvRowStatus.VALID, ""),
                        ),
                ),
            )
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
    fun `startSmsScanAndIdentifyMappings returns 0 when no SMS permission`() =
        runTest {
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
    fun `startSmsScanAndIdentifyMappings sends event when no SMS found`() =
        runTest {
            // Arrange
            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)
            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(emptyList())

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
    fun `startSmsScanAndIdentifyMappings filters non-transactional SMS using classifier`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            // Re-init with unconfined for this test to ensure immediate execution of async tasks
            viewModel =
                SettingsViewModel(
                    application = applicationContext,
                    settingsRepository = settingsRepository,
                    transactionRepository = transactionRepository,
                    merchantMappingRepository = merchantMappingRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    smsRepository = smsRepository,
                    autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                    smsClassifier = smsClassifier,
                    tagRepository = tagRepository,
                    splitTransactionRepository = splitTransactionRepository,
                    merchantRenameRuleRepository = merchantRenameRuleRepository,
                    merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                    customSmsRuleRepository = customSmsRuleRepository,
                    ignoreRuleRepository = ignoreRuleRepository,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerExtractorProvider = { nerExtractor },
                    transactionRunner = transactionRunner,
                    dispatchers = TestDispatcherProvider(testDispatcher),
                )

            // This tests the ML classifier filtering path
            val transactionalSms = SmsMessage(1, "BANK", "Your account credited with Rs 500", 1L)
            val nonTransactionalSms = SmsMessage(2, "PROMO", "Check out our latest deals!", 2L)

            `when`(smsClassifier.classify("Your account credited with Rs 500")).thenReturn(0.9f)
            `when`(smsClassifier.classify("Check out our latest deals!")).thenReturn(0.05f)

            whenever(smsRepository.fetchAllSms(anyOrNull()))
                .thenReturn(listOf(transactionalSms, nonTransactionalSms))

            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

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
    fun `startSmsScanAndIdentifyMappings updates progress state flows during scan`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            viewModel =
                SettingsViewModel(
                    application = applicationContext,
                    settingsRepository = settingsRepository,
                    transactionRepository = transactionRepository,
                    merchantMappingRepository = merchantMappingRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    smsRepository = smsRepository,
                    autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                    smsClassifier = smsClassifier,
                    tagRepository = tagRepository,
                    splitTransactionRepository = splitTransactionRepository,
                    merchantRenameRuleRepository = merchantRenameRuleRepository,
                    merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                    customSmsRuleRepository = customSmsRuleRepository,
                    ignoreRuleRepository = ignoreRuleRepository,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerExtractorProvider = { nerExtractor },
                    transactionRunner = transactionRunner,
                    dispatchers = TestDispatcherProvider(testDispatcher),
                )

            val sms1 = SmsMessage(1, "BANK", "Transaction 1", 1L)
            val sms2 = SmsMessage(2, "BANK", "Transaction 2", 2L)

            whenever(smsRepository.fetchAllSms(anyOrNull()))
                .thenReturn(listOf(sms1, sms2))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())
            `when`(smsClassifier.classify(anyString())).thenReturn(0.05f)

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

            val eventJob = launch { viewModel.uiEvent.collect() }

            val totalSmsCounts = mutableListOf<Int>()
            // Use a conflated collection or just collectLatest to see distinct values?
            // StateFlow coalesces values. We might miss '2' if it flips back to '0' quickly.
            // But with Unconfined, it should happen sequentially.
            val totalSmsJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
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
    fun `startSmsScanAndIdentifyMappings resets state after completion`() =
        runTest {
            // Arrange
            whenever(smsRepository.fetchAllSms(anyOrNull()))
                .thenReturn(listOf(SmsMessage(1, "BANK", "Test", 1L)))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())
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

    @Test
    fun `rescanSmsWithNewRule invokes nerExtractorProvider and closes extractor on completion`() =
        runTest {
            val sms = SmsMessage(1, "SENDER", "spent Rs 100", 1L)

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "at (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))
            `when`(autoSaveSmsTransactionUseCase(anyObject(), eq("Imported"), anyOrNull())).thenReturn(true)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            viewModel.rescanSmsWithNewRule { }
            advanceUntilIdle()

            assertEquals(1, providerInvocationCount)
            verify(nerExtractor).close()
        }

    @Test
    fun `rescanSmsWithNewRule does not invoke nerExtractor extract when custom rules match`() =
        runTest {
            val sms = SmsMessage(1, "SENDER", "spent Rs 100 on Food", 1L)

            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            val rule = CustomSmsRule(1, "spent Rs", "on (.*)", "spent Rs ([\\d.]+)", null, null, null, null, 10, "")
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(listOf(rule)))
            `when`(autoSaveSmsTransactionUseCase(anyObject(), eq("Imported"), anyOrNull())).thenReturn(true)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            viewModel.rescanSmsWithNewRule { }
            advanceUntilIdle()

            assertEquals(1, providerInvocationCount)
            verify(nerExtractor).close()
            verify(nerExtractor, never()).extract(anyString())
        }

    @Test
    fun `rescanSmsWithNewRule does not invoke nerExtractorProvider when no SMS found`() =
        runTest {
            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(emptyList())
            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            viewModel.rescanSmsWithNewRule { }
            advanceUntilIdle()

            assertEquals(0, providerInvocationCount)
            verify(nerExtractor, never()).close()
        }

    @Test
    fun `rescanSmsWithNewRule closes nerExtractor even when parsing encounters error`() =
        runTest {
            val sms = SmsMessage(1, "SENDER", "spent Rs 100", 1L)
            whenever(smsRepository.fetchAllSms(anyLong())).thenReturn(listOf(sms))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())
            `when`(nerExtractor.extract(anyString())).thenThrow(RuntimeException("NER inference error"))

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            viewModel.rescanSmsWithNewRule { }
            advanceUntilIdle()

            assertEquals(1, providerInvocationCount)
            verify(nerExtractor).close()
        }

    @Test
    fun `startSmsScanAndIdentifyMappings invokes nerExtractorProvider and closes extractor on completion`() =
        runTest {
            val sms = SmsMessage(1, "BANK", "Test body", 1L)
            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())
            `when`(smsClassifier.classify(anyString())).thenReturn(0.05f)

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                viewModel.startSmsScanAndIdentifyMappings(null) { }
                advanceUntilIdle()

                assertEquals(1, providerInvocationCount)
                verify(nerExtractor).close()
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `startSmsScanAndIdentifyMappings does not invoke nerExtractorProvider when no SMS found`() =
        runTest {
            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(emptyList())
            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                viewModel.startSmsScanAndIdentifyMappings(null) { }
                advanceUntilIdle()

                assertEquals(0, providerInvocationCount)
                verify(nerExtractor, never()).close()
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `startSmsScanAndIdentifyMappings closes nerExtractor even when chunk processing encounters error`() =
        runTest {
            val sms = SmsMessage(1, "BANK", "Test body", 1L)
            whenever(smsRepository.fetchAllSms(anyOrNull())).thenReturn(listOf(sms))
            `when`(transactionRepository.getAllSmsHashes()).thenReturn(flowOf(emptyList<String>()))
            `when`(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            `when`(customSmsRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(merchantRenameRuleRepository.getAllRules()).thenReturn(flowOf(emptyList()))
            `when`(ignoreRuleRepository.getEnabledRules()).thenReturn(emptyList())
            `when`(merchantCategoryMappingRepository.getCategoryIdForMerchant(anyString())).thenReturn(null)
            `when`(smsParseTemplateProvider.getAllTemplates()).thenReturn(emptyList())
            `when`(smsParseTemplateProvider.getTemplatesBySignature(anyString())).thenReturn(emptyList())
            `when`(smsClassifier.classify(anyString())).thenReturn(0.9f)
            `when`(nerExtractor.extract(anyString())).thenThrow(RuntimeException("NER error"))

            org.robolectric.shadows.ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

            var providerInvocationCount = 0
            val countingProvider: () -> SmsEntityExtractor = {
                providerInvocationCount++
                nerExtractor
            }

            initializeViewModel(customNerExtractorProvider = countingProvider)

            val eventJob = launch { viewModel.uiEvent.collect() }
            try {
                viewModel.startSmsScanAndIdentifyMappings(null) { }
                advanceUntilIdle()

                assertEquals(1, providerInvocationCount)
                verify(nerExtractor).close()
            } finally {
                eventJob.cancel()
            }
        }

    @Test
    fun `onCleared closes smsClassifier but does not interact with nerExtractor`() {
        initializeViewModel()

        val onClearedMethod =
            SettingsViewModel::class.java.getDeclaredMethod("onCleared").apply {
                isAccessible = true
            }
        onClearedMethod.invoke(viewModel)

        verify(smsClassifier).close()
        verify(nerExtractor, never()).close()
    }

    @Test
    fun `secondary constructor accepts direct SmsEntityExtractor instance`() {
        val vm =
            SettingsViewModel(
                application = applicationContext,
                settingsRepository = settingsRepository,
                transactionRepository = transactionRepository,
                merchantMappingRepository = merchantMappingRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                smsRepository = smsRepository,
                autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                smsClassifier = smsClassifier,
                tagRepository = tagRepository,
                splitTransactionRepository = splitTransactionRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                customSmsRuleRepository = customSmsRuleRepository,
                ignoreRuleRepository = ignoreRuleRepository,
                smsParseTemplateProvider = smsParseTemplateProvider,
                nerExtractor = nerExtractor,
                transactionRunner = transactionRunner,
                dispatchers = TestDispatcherProvider(testDispatcher),
            )

        val extractedNer = vm.nerExtractorProvider()
        assertEquals(nerExtractor, extractedNer)
    }

    @Test
    fun `dismissHealthCheckResult clears healthCheckResult state`() =
        runTest {
            initializeViewModel()
            viewModel.dismissHealthCheckResult()
            org.junit.Assert.assertNull(viewModel.healthCheckResult.value)
        }

    @Test
    fun `clearRepairResultMessage clears repairResultMessage state`() =
        runTest {
            initializeViewModel()
            viewModel.clearRepairResultMessage()
            org.junit.Assert.assertNull(viewModel.repairResultMessage.value)
        }

    @Test
    fun `runDatabaseHealthCheck executes and updates healthCheckResult`() =
        runTest {
            initializeViewModel()
            mockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            try {
                val mockResult =
                    io.pm.finlight.data.health.HealthCheckResult(
                        isHealthy = true,
                        integrityCheckPassed = true,
                        corruptedAccountsCount = 0,
                        duplicateAccountsCount = 0,
                        orphanTransactionsCount = 0,
                        corruptedPreferencesCount = 0,
                        issuesDescription = emptyList(),
                    )
                coEvery { io.pm.finlight.data.health.DatabaseHealthChecker.checkHealth(any<android.content.Context>()) } returns mockResult

                viewModel.runDatabaseHealthCheck()

                assertEquals(mockResult, viewModel.healthCheckResult.value)
                assertFalse(viewModel.isCheckingHealth.value)
            } finally {
                io.mockk.unmockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            }
        }

    @Test
    fun `runDatabaseHealthCheck does nothing if already checking or repairing`() =
        runTest {
            initializeViewModel()
            val field = viewModel.javaClass.getDeclaredField("_isCheckingHealth")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val stateFlow = field.get(viewModel) as MutableStateFlow<Boolean>
            stateFlow.value = true

            mockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            try {
                viewModel.runDatabaseHealthCheck()
                coVerify(exactly = 0) { io.pm.finlight.data.health.DatabaseHealthChecker.checkHealth(any<android.content.Context>()) }
            } finally {
                io.mockk.unmockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            }
        }

    @Test
    fun `repairDatabase executes and updates repairResultMessage with fixed issues count`() =
        runTest {
            initializeViewModel()
            mockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            try {
                val repairOutcome =
                    io.pm.finlight.data.health.RepairResult(
                        success = true,
                        repairedAccountsCount = 1,
                        relinkedTransactionsCount = 2,
                        repairedPreferencesCount = 1,
                        message = "Success",
                    )
                coEvery { io.pm.finlight.data.health.DatabaseHealthChecker.repair(any<android.content.Context>()) } returns repairOutcome

                viewModel.repairDatabase()

                assertEquals("Database repaired: 4 issues fixed.", viewModel.repairResultMessage.value)
                assertNull(viewModel.healthCheckResult.value)
                assertFalse(viewModel.isRepairingDb.value)
            } finally {
                io.mockk.unmockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            }
        }

    @Test
    fun `repairDatabase executes and updates repairResultMessage when database is clean`() =
        runTest {
            initializeViewModel()
            mockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            try {
                val repairOutcome =
                    io.pm.finlight.data.health.RepairResult(
                        success = true,
                        repairedAccountsCount = 0,
                        relinkedTransactionsCount = 0,
                        repairedPreferencesCount = 0,
                        message = "Success",
                    )
                coEvery { io.pm.finlight.data.health.DatabaseHealthChecker.repair(any<android.content.Context>()) } returns repairOutcome

                viewModel.repairDatabase()

                assertEquals("Database verified and clean.", viewModel.repairResultMessage.value)
                assertFalse(viewModel.isRepairingDb.value)
            } finally {
                io.mockk.unmockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            }
        }

    @Test
    fun `repairDatabase executes and updates repairResultMessage on failure`() =
        runTest {
            initializeViewModel()
            mockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            try {
                val repairOutcome =
                    io.pm.finlight.data.health.RepairResult(
                        success = false,
                        repairedAccountsCount = 0,
                        relinkedTransactionsCount = 0,
                        repairedPreferencesCount = 0,
                        message = "Disk I/O error",
                    )
                coEvery { io.pm.finlight.data.health.DatabaseHealthChecker.repair(any<android.content.Context>()) } returns repairOutcome

                viewModel.repairDatabase()

                assertEquals("Database self-repair failed: Disk I/O error", viewModel.repairResultMessage.value)
                assertFalse(viewModel.isRepairingDb.value)
            } finally {
                io.mockk.unmockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            }
        }

    @Test
    fun `repairDatabase does nothing if already repairing`() =
        runTest {
            initializeViewModel()
            val field = viewModel.javaClass.getDeclaredField("_isRepairingDb")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val stateFlow = field.get(viewModel) as MutableStateFlow<Boolean>
            stateFlow.value = true

            mockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            try {
                viewModel.repairDatabase()
                coVerify(exactly = 0) { io.pm.finlight.data.health.DatabaseHealthChecker.repair(any<android.content.Context>()) }
            } finally {
                io.mockk.unmockkObject(io.pm.finlight.data.health.DatabaseHealthChecker)
            }
        }
}
