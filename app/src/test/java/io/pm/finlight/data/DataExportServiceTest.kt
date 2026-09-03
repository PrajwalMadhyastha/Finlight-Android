package io.pm.finlight.data

import android.app.Application
import android.os.Build
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.data.db.entity.MergeRecord
import io.pm.finlight.data.db.entity.MergeType
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.data.model.AppDataBackup
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.Calendar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
// --- FIX: Add missing kotlin.test import ---
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DataExportServiceTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var db: AppDatabase

    // Mock all DAOs
    private val transactionQueryDao: TransactionQueryDao = mockk(relaxed = true)
    private val transactionWriteDao: TransactionWriteDao = mockk(relaxed = true)
    private val transactionAnalyticsDao: TransactionAnalyticsDao = mockk(relaxed = true)
    private val transactionReimbursementDao: TransactionReimbursementDao = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val categoryDao: CategoryDao = mockk(relaxed = true)
    private val budgetDao: BudgetDao = mockk(relaxed = true)
    private val merchantMappingDao: MerchantMappingDao = mockk(relaxed = true)
    private val splitTransactionDao: SplitTransactionDao = mockk(relaxed = true)
    private val customSmsRuleDao: CustomSmsRuleDao = mockk(relaxed = true)
    private val merchantRenameRuleDao: MerchantRenameRuleDao = mockk(relaxed = true)
    private val merchantCategoryMappingDao: MerchantCategoryMappingDao = mockk(relaxed = true)
    private val ignoreRuleDao: IgnoreRuleDao = mockk(relaxed = true)
    private val smsParseTemplateDao: SmsParseTemplateDao = mockk(relaxed = true)
    private val tagDao: TagDao = mockk(relaxed = true)
    private val goalDao: GoalDao = mockk(relaxed = true)
    private val tripDao: TripDao = mockk(relaxed = true)
    private val accountAliasDao: AccountAliasDao = mockk(relaxed = true)
    private val recurringPatternDao: RecurringPatternDao = mockk(relaxed = true)
    private val goalTransactionLinkDao: GoalTransactionLinkDao = mockk(relaxed = true)
    private val deletedSmsHashDao: DeletedSmsHashDao = mockk(relaxed = true)
    private val mergeRecordDao: MergeRecordDao = mockk(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Mock the static AppDatabase.getInstance() to return our mocked db
        db = mockk()
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db

        // Link the DAOs to the mocked db instance
        every { db.transactionQueryDao() } returns transactionQueryDao
        every { db.transactionWriteDao() } returns transactionWriteDao
        every { db.transactionAnalyticsDao() } returns transactionAnalyticsDao
        every { db.transactionReimbursementDao() } returns transactionReimbursementDao
        every { db.accountDao() } returns accountDao
        every { db.categoryDao() } returns categoryDao
        every { db.budgetDao() } returns budgetDao
        every { db.merchantMappingDao() } returns merchantMappingDao
        every { db.splitTransactionDao() } returns splitTransactionDao
        every { db.customSmsRuleDao() } returns customSmsRuleDao
        every { db.merchantRenameRuleDao() } returns merchantRenameRuleDao
        every { db.merchantCategoryMappingDao() } returns merchantCategoryMappingDao
        every { db.ignoreRuleDao() } returns ignoreRuleDao
        every { db.smsParseTemplateDao() } returns smsParseTemplateDao
        every { db.tagDao() } returns tagDao
        every { db.goalDao() } returns goalDao
        every { db.tripDao() } returns tripDao
        every { db.accountAliasDao() } returns accountAliasDao
        every { db.recurringPatternDao() } returns recurringPatternDao
        every { db.goalTransactionLinkDao() } returns goalTransactionLinkDao
        every { db.deletedSmsHashDao() } returns deletedSmsHashDao
        every { db.mergeRecordDao() } returns mergeRecordDao
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    private fun setupMockData() {
        // Setup DAOs to return some mock data
        coEvery {
            transactionQueryDao.getAllTransactionsSimple()
        } returns flowOf(listOf(Transaction(id = 1, description = "Test Tx", amount = 100.0, date = 1L, accountId = 1, categoryId = 1, notes = null)))
        coEvery { accountDao.getAllAccounts() } returns flowOf(listOf(Account(id = 1, name = "Test Acc", type = "Bank")))
        coEvery { categoryDao.getAllCategories() } returns flowOf(listOf(Category(id = 1, name = "Test Cat", iconKey = "icon", colorKey = "color")))
        coEvery {
            budgetDao.getAllBudgets()
        } returns flowOf(listOf(Budget(id = 1, categoryName = "Test Cat", amount = 500.0, month = 1, year = 2025)))
        coEvery {
            merchantMappingDao.getAllMappings()
        } returns flowOf(listOf(MerchantMapping(smsSender = "TestSender", merchantName = "Test Merchant")))
        coEvery {
            splitTransactionDao.getAllSplits()
        } returns flowOf(listOf(SplitTransaction(id = 1, parentTransactionId = 1, amount = 50.0, categoryId = 1, notes = "Split")))
        coEvery {
            customSmsRuleDao.getAllRulesList()
        } returns listOf(CustomSmsRule(id = 1, triggerPhrase = "test", priority = 1, sourceSmsBody = "test sms", merchantRegex = null, amountRegex = null, accountRegex = null, merchantNameExample = null, amountExample = null, accountNameExample = null))
        coEvery { merchantRenameRuleDao.getAllRulesList() } returns listOf(MerchantRenameRule(originalName = "Old", newName = "New"))
        coEvery { merchantCategoryMappingDao.getAll() } returns listOf(MerchantCategoryMapping("Old", 1))
        coEvery { ignoreRuleDao.getAllList() } returns listOf(IgnoreRule(id = 1, pattern = "ignore"))
        coEvery {
            smsParseTemplateDao.getAllTemplates()
        } returns listOf(SmsParseTemplate(templateSignature = "sig", correctedMerchantName = "merchant", originalSmsBody = "body", originalMerchantStartIndex = 0, originalMerchantEndIndex = 1, originalAmountStartIndex = 2, originalAmountEndIndex = 3))
        coEvery { tagDao.getAllTagsList() } returns listOf(Tag(id = 1, name = "Test Tag"))
        coEvery { transactionQueryDao.getAllCrossRefs() } returns listOf(TransactionTagCrossRef(transactionId = 1, tagId = 1))
        coEvery {
            goalDao.getAll()
        } returns listOf(Goal(id = 1, name = "Test Goal", targetAmount = 1000.0, savedAmount = 100.0, targetDate = null, accountId = 1))
        coEvery {
            tripDao.getAll()
        } returns listOf(Trip(id = 1, name = "Test Trip", startDate = 1L, endDate = 2L, tagId = 1, tripType = TripType.DOMESTIC, currencyCode = null, conversionRate = null))
        coEvery { accountAliasDao.getAll() } returns listOf(AccountAlias(aliasName = "Alias Acc", destinationAccountId = 1))
        coEvery { recurringPatternDao.getAllPatterns() } returns emptyList()
        coEvery { goalTransactionLinkDao.getAll() } returns emptyList()
        coEvery { deletedSmsHashDao.getAll() } returns listOf(DeletedSmsHash(smsHash = "deleted_hash_1"))
        coEvery { mergeRecordDao.getAll() } returns emptyList()
    }

    @Test
    fun `exportToJsonString serializes all data correctly`() =
        runTest {
            // Arrange
            setupMockData()

            // Act
            val jsonString = DataExportService.exportToJsonString(context)

            // Assert
            assertNotNull(jsonString)
            val backupData = Json.decodeFromString<AppDataBackup>(jsonString!!)
            assertEquals(1, backupData.transactions.size)
            assertEquals("Test Tx", backupData.transactions.first().description)
            assertEquals(1, backupData.accounts.size)
            assertEquals(1, backupData.categories.size)
            assertEquals(1, backupData.budgets.size)
            assertEquals(1, backupData.merchantMappings.size)
            assertEquals(1, backupData.splitTransactions.size)
            assertEquals(1, backupData.customSmsRules.size)
            assertEquals(1, backupData.merchantRenameRules.size)
            assertEquals(1, backupData.merchantCategoryMappings.size)
            assertEquals(1, backupData.ignoreRules.size)
            assertEquals(1, backupData.smsParseTemplates.size)
            assertEquals(1, backupData.tags.size)
            assertEquals(1, backupData.transactionTagCrossRefs.size)
            assertEquals(1, backupData.goals.size)
            assertEquals(0, backupData.goalTransactionLinks.size)
            assertEquals(1, backupData.trips.size)
            assertEquals(1, backupData.accountAliases.size)
            assertEquals(1, backupData.deletedSmsHashes.size)
            assertEquals("deleted_hash_1", backupData.deletedSmsHashes.first().smsHash)
            assertEquals(0, backupData.mergeRecords.size)
        }

    @Test
    fun `exportToJsonString exports deletedSmsHashes, mergeRecords, and UI preferences`() =
        runTest {
            setupMockData()
            coEvery { deletedSmsHashDao.getAll() } returns listOf(DeletedSmsHash(smsHash = "deleted_hash_abc"))
            coEvery { mergeRecordDao.getAll() } returns
                listOf(
                    MergeRecord(
                        id = 1,
                        parentTxnId = 1,
                        mergeGroupId = "group_abc",
                        mergeType = MergeType.MANUAL,
                        originalParentAmount = 100.0,
                        originalParentDate = 1000L,
                        originalParentNotes = "Notes",
                        childDescription = "Child Tx",
                        childAmount = 50.0,
                        childDate = 1000L,
                        childAccountId = 1,
                        childCategoryId = 1,
                        childNotes = null,
                    ),
                )

            val picFile = File(context.filesDir, "test_avatar.jpg")
            picFile.writeBytes("image_bytes_for_testing".toByteArray())

            context.financeSettingsDataStore.edit { prefs ->
                prefs[stringPreferencesKey("user_name")] = "Prajwal"
                prefs[stringPreferencesKey("home_currency_code")] = "INR"
                prefs[floatPreferencesKey("overall_budget_2026_09")] = 60000f
                prefs[stringPreferencesKey("selected_app_theme")] = "DARK"
                prefs[stringPreferencesKey("dashboard_card_order")] = "CARDS_ORDER"
                prefs[stringPreferencesKey("travel_mode_settings")] = "{\"enabled\":true}"
                prefs[longPreferencesKey("sms_scan_start_date")] = 123456789L
                prefs[stringSetPreferencesKey("dismissed_merge_suggestions")] = setOf("sugg_1")
                prefs[stringSetPreferencesKey("excluded_income_months")] = setOf("2026_01")
                prefs[stringSetPreferencesKey("excluded_expense_months")] = setOf("2026_02")
                prefs[booleanPreferencesKey("app_lock_enabled")] = true
                prefs[booleanPreferencesKey("privacy_mode_enabled")] = true
                prefs[booleanPreferencesKey("daily_report_enabled")] = true
                prefs[intPreferencesKey("daily_report_hour")] = 21
                prefs[intPreferencesKey("daily_report_minute")] = 45
                prefs[booleanPreferencesKey("weekly_summary_enabled")] = true
                prefs[intPreferencesKey("weekly_report_day")] = 1
                prefs[intPreferencesKey("weekly_report_hour")] = 10
                prefs[intPreferencesKey("weekly_report_minute")] = 30
                prefs[booleanPreferencesKey("monthly_summary_enabled")] = true
                prefs[intPreferencesKey("monthly_report_day")] = 28
                prefs[intPreferencesKey("monthly_report_hour")] = 18
                prefs[intPreferencesKey("monthly_report_minute")] = 0
                prefs[booleanPreferencesKey("autocapture_notification_enabled")] = true
                prefs[booleanPreferencesKey("unknown_transaction_popup_enabled")] = true
                prefs[stringPreferencesKey("profile_picture_uri")] = picFile.absolutePath
            }

            val jsonString = DataExportService.exportToJsonString(context)
            assertNotNull(jsonString)
            val backupData = Json.decodeFromString<AppDataBackup>(jsonString!!)
            assertEquals(1, backupData.deletedSmsHashes.size)
            assertEquals("deleted_hash_abc", backupData.deletedSmsHashes.first().smsHash)
            assertEquals(1, backupData.mergeRecords.size)
            assertEquals("group_abc", backupData.mergeRecords.first().mergeGroupId)
            assertEquals("Prajwal", backupData.userName)
            assertEquals("INR", backupData.homeCurrency)
            assertEquals(60000f, backupData.overallBudget)
            assertEquals("DARK", backupData.selectedAppTheme)
            assertEquals("CARDS_ORDER", backupData.dashboardCardOrder)
            assertEquals("{\"enabled\":true}", backupData.travelModeSettings)
            assertEquals(123456789L, backupData.smsScanStartDate)
            assertEquals(setOf("sugg_1"), backupData.dismissedMergeSuggestions)
            assertEquals(setOf("2026_01"), backupData.excludedIncomeMonths)
            assertEquals(setOf("2026_02"), backupData.excludedExpenseMonths)
            assertEquals(true, backupData.appLockEnabled)
            assertEquals(true, backupData.privacyModeEnabled)
            assertEquals(true, backupData.dailyReportEnabled)
            assertEquals(21, backupData.dailyReportHour)
            assertEquals(45, backupData.dailyReportMinute)
            assertEquals(true, backupData.weeklySummaryEnabled)
            assertEquals(1, backupData.weeklyReportDay)
            assertEquals(10, backupData.weeklyReportHour)
            assertEquals(30, backupData.weeklyReportMinute)
            assertEquals(true, backupData.monthlySummaryEnabled)
            assertEquals(28, backupData.monthlyReportDay)
            assertEquals(18, backupData.monthlyReportHour)
            assertEquals(0, backupData.monthlyReportMinute)
            assertEquals(true, backupData.autocaptureNotificationEnabled)
            assertEquals(true, backupData.unknownTransactionPopupEnabled)
            assertNotNull(backupData.profilePictureBase64)
            assertEquals(Base64.encodeToString("image_bytes_for_testing".toByteArray(), Base64.NO_WRAP), backupData.profilePictureBase64)
        }

    @Test
    fun `exportToJsonString exports userName and historic overall budgets`() =
        runTest {
            setupMockData()
            context.financeSettingsDataStore.edit { prefs ->
                prefs[stringPreferencesKey("user_name")] = "Prajwal"
                prefs[stringPreferencesKey("home_currency_code")] = "INR"
                prefs[floatPreferencesKey("overall_budget_2026_08")] = 50000f
                prefs[floatPreferencesKey("overall_budget_2026_09")] = 60000f
            }

            val jsonString = DataExportService.exportToJsonString(context)
            assertNotNull(jsonString)
            val backupData = Json.decodeFromString<AppDataBackup>(jsonString!!)
            assertEquals("Prajwal", backupData.userName)
            assertEquals("INR", backupData.homeCurrency)
            assertEquals(2, backupData.overallBudgets.size)
            assertEquals(50000f, backupData.overallBudgets["2026_08"])
            assertEquals(60000f, backupData.overallBudgets["2026_09"])
        }

    @Test
    fun `createBackupSnapshot creates a compressed file`() =
        runTest {
            // Arrange
            setupMockData()
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            if (snapshotFile.exists()) snapshotFile.delete()

            // Act
            val success = DataExportService.createBackupSnapshot(context)

            // Assert
            assertTrue("Snapshot creation should be successful", success)
            assertTrue("Snapshot file should exist", snapshotFile.exists())
            assertTrue("Snapshot file should not be empty", snapshotFile.length() > 0)

            // Optional: Verify content by decompressing
            val jsonString = GZIPInputStream(snapshotFile.inputStream()).bufferedReader().use { it.readText() }
            val backupData = Json.decodeFromString<AppDataBackup>(jsonString)
            assertEquals(1, backupData.transactions.size)
            assertEquals("Test Tx", backupData.transactions.first().description)
        }

    @Test
    fun `restoreFromBackupSnapshot restores data and deletes file`() =
        runTest {
            // Arrange
            // 1. Create a dummy backup data and JSON string
            val backupData =
                AppDataBackup(
                    transactions =
                        listOf(
                            Transaction(
                                id = 5,
                                description = "Restored Tx",
                                amount = 555.0,
                                date = 1L,
                                accountId = 1,
                                categoryId = 1,
                                notes = null,
                            ),
                        ),
                    accounts = listOf(Account(id = 1, name = "Restored Acc", type = "Bank")),
                    categories = listOf(Category(id = 1, name = "Restored Cat", iconKey = "icon", colorKey = "color")),
                    tags = listOf(Tag(id = 1, name = "Restored Tag")),
                    transactionTagCrossRefs = listOf(TransactionTagCrossRef(5, 1)),
                    budgets = emptyList(),
                    merchantMappings = emptyList(),
                    splitTransactions = emptyList(),
                    customSmsRules = emptyList(),
                    merchantRenameRules = emptyList(),
                    merchantCategoryMappings = emptyList(),
                    ignoreRules = emptyList(),
                    smsParseTemplates = emptyList(),
                    goals = emptyList(),
                    goalTransactionLinks = emptyList(),
                    trips = emptyList(),
                    accountAliases = emptyList(),
                )
            val jsonString = Json.encodeToString(AppDataBackup.serializer(), backupData)

            // 2. Create the compressed snapshot file for the test
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            FileOutputStream(snapshotFile).use { fos ->
                GZIPOutputStream(fos).use { gzip ->
                    gzip.write(jsonString.toByteArray())
                }
            }
            assertTrue("Test setup failed: Snapshot file should exist before restore", snapshotFile.exists())

            // 3. Mock the clear and insert calls (relaxed mocks will accept any args)
            coJustRun { splitTransactionDao.deleteAll() }
            coJustRun { transactionWriteDao.deleteAll() }
            coJustRun { tagDao.deleteAll() }
            coJustRun { accountDao.deleteAll() }
            coJustRun { categoryDao.deleteAll() }
            coJustRun { budgetDao.deleteAll() }
            coJustRun { merchantMappingDao.deleteAll() }
            coJustRun {
                goalDao.deleteAll()
                goalTransactionLinkDao.deleteAll()
            }
            coJustRun { goalTransactionLinkDao.deleteAll() }
            coJustRun { tripDao.deleteAll() }
            coJustRun { accountAliasDao.deleteAll() }
            coJustRun { customSmsRuleDao.deleteAll() }
            coJustRun { merchantRenameRuleDao.deleteAll() }
            coJustRun { merchantCategoryMappingDao.deleteAll() }
            coJustRun { ignoreRuleDao.deleteAll() }
            coJustRun { smsParseTemplateDao.deleteAll() }
            coJustRun { recurringPatternDao.deleteAll() }

            coJustRun { accountDao.insertAll(any()) }
            coJustRun { categoryDao.insertAll(any()) }
            coJustRun { tagDao.insertAll(any()) }
            coJustRun { transactionWriteDao.insertAll(any()) }
            coJustRun { transactionWriteDao.addTagsToTransaction(any()) }

            // Act
            val success = DataExportService.restoreFromBackupSnapshot(context)

            // Assert
            assertTrue("Restore should be successful", success)
            assertFalse("Snapshot file should be deleted after successful restore", snapshotFile.exists())

            // Verify that the import logic was actually called
            coVerifyOrder {
                splitTransactionDao.deleteAll()
                transactionWriteDao.deleteAll()
                tagDao.deleteAll()
                accountDao.deleteAll()
                categoryDao.deleteAll()
                budgetDao.deleteAll()
                merchantMappingDao.deleteAll()
                goalDao.deleteAll()
                goalTransactionLinkDao.deleteAll()
                tripDao.deleteAll()
                accountAliasDao.deleteAll()
                customSmsRuleDao.deleteAll()
                merchantRenameRuleDao.deleteAll()
                merchantCategoryMappingDao.deleteAll()
                ignoreRuleDao.deleteAll()
                smsParseTemplateDao.deleteAll()
                recurringPatternDao.deleteAll()
            }

            coVerify { accountDao.insertAll(backupData.accounts) }
            coVerify { categoryDao.insertAll(backupData.categories) }
            coVerify { tagDao.insertAll(backupData.tags) }
            coVerify { transactionWriteDao.insertAll(backupData.transactions) }
            coVerify { transactionWriteDao.addTagsToTransaction(backupData.transactionTagCrossRefs) }
        }

    @Test
    fun `restoreFromBackupSnapshot restores all user preferences and profile picture`() =
        runTest {
            val fakeImageData = "avatar_test_bytes".toByteArray()
            val base64Image = Base64.encodeToString(fakeImageData, Base64.NO_WRAP)

            val backupData =
                AppDataBackup(
                    transactions =
                        listOf(
                            Transaction(id = 5, description = "Restored Tx", amount = 555.0, date = 1L, accountId = 1, categoryId = 1, notes = null)
                        ),
                    accounts = listOf(Account(id = 1, name = "Restored Acc", type = "Bank")),
                    categories = listOf(Category(id = 1, name = "Restored Cat", iconKey = "icon", colorKey = "color")),
                    tags = emptyList(),
                    transactionTagCrossRefs = emptyList(),
                    budgets = emptyList(),
                    merchantMappings = emptyList(),
                    splitTransactions = emptyList(),
                    customSmsRules = emptyList(),
                    merchantRenameRules = emptyList(),
                    merchantCategoryMappings = emptyList(),
                    ignoreRules = emptyList(),
                    smsParseTemplates = emptyList(),
                    goals = emptyList(),
                    goalTransactionLinks = emptyList(),
                    trips = emptyList(),
                    accountAliases = emptyList(),
                    deletedSmsHashes = listOf(DeletedSmsHash(smsHash = "deleted_hash_xyz")),
                    mergeRecords =
                        listOf(
                            MergeRecord(
                                id = 1,
                                parentTxnId = 5,
                                mergedAt = 1000L,
                                mergeGroupId = "group_xyz",
                                mergeType = MergeType.MANUAL,
                                originalParentAmount = 100.0,
                                originalParentDate = 1000L,
                                originalParentNotes = "Notes",
                                childDescription = "Child Tx",
                                childAmount = 50.0,
                                childDate = 1000L,
                                childAccountId = 1,
                                childCategoryId = 1,
                                childNotes = null,
                            )
                        ),
                    userName = "Restored Name",
                    homeCurrency = "EUR",
                    overallBudgets = mapOf("2026_09" to 55000f),
                    selectedAppTheme = "LIGHT",
                    dashboardCardOrder = "CARDS_2",
                    travelModeSettings = "{\"enabled\":false}",
                    smsScanStartDate = 777L,
                    dismissedMergeSuggestions = setOf("sugg_restore"),
                    excludedIncomeMonths = setOf("2026_05"),
                    excludedExpenseMonths = setOf("2026_06"),
                    appLockEnabled = false,
                    privacyModeEnabled = false,
                    dailyReportEnabled = false,
                    dailyReportHour = 8,
                    dailyReportMinute = 0,
                    weeklySummaryEnabled = false,
                    weeklyReportDay = 2,
                    weeklyReportHour = 9,
                    weeklyReportMinute = 15,
                    monthlySummaryEnabled = false,
                    monthlyReportDay = 1,
                    monthlyReportHour = 12,
                    monthlyReportMinute = 45,
                    autocaptureNotificationEnabled = false,
                    unknownTransactionPopupEnabled = false,
                    profilePictureBase64 = base64Image,
                )

            val jsonString = Json.encodeToString(AppDataBackup.serializer(), backupData)
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            FileOutputStream(snapshotFile).use { fos ->
                GZIPOutputStream(fos).use { gzip ->
                    gzip.write(jsonString.toByteArray())
                }
            }

            val success = DataExportService.restoreFromBackupSnapshot(context)
            assertTrue("Restore should succeed", success)

            coVerify { deletedSmsHashDao.insertAll(match { it.size == 1 && it.first().smsHash == "deleted_hash_xyz" }) }
            coVerify { mergeRecordDao.insertAll(match { it.size == 1 && it.first().mergeGroupId == "group_xyz" }) }

            val prefs = context.financeSettingsDataStore.data.first()
            assertEquals("Restored Name", prefs[stringPreferencesKey("user_name")])
            assertEquals("EUR", prefs[stringPreferencesKey("home_currency_code")])
            assertEquals(55000f, prefs[floatPreferencesKey("overall_budget_2026_09")])
            assertEquals("LIGHT", prefs[stringPreferencesKey("selected_app_theme")])
            assertEquals("CARDS_2", prefs[stringPreferencesKey("dashboard_card_order")])
            assertEquals("{\"enabled\":false}", prefs[stringPreferencesKey("travel_mode_settings")])
            assertEquals(777L, prefs[longPreferencesKey("sms_scan_start_date")])
            assertEquals(setOf("sugg_restore"), prefs[stringSetPreferencesKey("dismissed_merge_suggestions")])
            assertEquals(setOf("2026_05"), prefs[stringSetPreferencesKey("excluded_income_months")])
            assertEquals(setOf("2026_06"), prefs[stringSetPreferencesKey("excluded_expense_months")])
            assertEquals(false, prefs[booleanPreferencesKey("app_lock_enabled")])
            assertEquals(false, prefs[booleanPreferencesKey("privacy_mode_enabled")])
            assertEquals(false, prefs[booleanPreferencesKey("daily_report_enabled")])
            assertEquals(8, prefs[intPreferencesKey("daily_report_hour")])
            assertEquals(0, prefs[intPreferencesKey("daily_report_minute")])
            assertEquals(false, prefs[booleanPreferencesKey("weekly_summary_enabled")])
            assertEquals(2, prefs[intPreferencesKey("weekly_report_day")])
            assertEquals(9, prefs[intPreferencesKey("weekly_report_hour")])
            assertEquals(15, prefs[intPreferencesKey("weekly_report_minute")])
            assertEquals(false, prefs[booleanPreferencesKey("monthly_summary_enabled")])
            assertEquals(1, prefs[intPreferencesKey("monthly_report_day")])
            assertEquals(12, prefs[intPreferencesKey("monthly_report_hour")])
            assertEquals(45, prefs[intPreferencesKey("monthly_report_minute")])
            assertEquals(false, prefs[booleanPreferencesKey("autocapture_notification_enabled")])
            assertEquals(false, prefs[booleanPreferencesKey("unknown_transaction_popup_enabled")])

            val restoredPicUri = prefs[stringPreferencesKey("profile_picture_uri")]
            assertNotNull(restoredPicUri)
            val restoredPicFile = File(restoredPicUri!!)
            assertTrue(restoredPicFile.exists())
            assertEquals("avatar_test_bytes", restoredPicFile.readText())
        }

    @Test
    fun `restoreFromBackupSnapshot restores legacy overallBudget when overallBudgets is empty`() =
        runTest {
            val backupData =
                AppDataBackup(
                    transactions =
                        listOf(
                            Transaction(id = 6, description = "Legacy Tx", amount = 10.0, date = 1L, accountId = 1, categoryId = 1, notes = null)
                        ),
                    accounts = listOf(Account(id = 1, name = "Acc", type = "Bank")),
                    categories = listOf(Category(id = 1, name = "Cat", iconKey = "icon", colorKey = "color")),
                    budgets = emptyList(),
                    merchantMappings = emptyList(),
                    overallBudget = 42000f,
                    overallBudgets = emptyMap(),
                )

            val jsonString = Json.encodeToString(AppDataBackup.serializer(), backupData)
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            FileOutputStream(snapshotFile).use { fos ->
                GZIPOutputStream(fos).use { gzip ->
                    gzip.write(jsonString.toByteArray())
                }
            }

            val success = DataExportService.restoreFromBackupSnapshot(context)
            assertTrue("Restore should succeed", success)

            val prefs = context.financeSettingsDataStore.data.first()
            val cal = Calendar.getInstance()
            val currentKey = String.format(java.util.Locale.ROOT, "overall_budget_%d_%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            assertEquals(42000f, prefs[floatPreferencesKey(currentKey)])
        }

    @Test
    fun `exportToJsonString ignores non-existent or oversized profile picture`() =
        runTest {
            setupMockData()
            context.financeSettingsDataStore.edit { prefs ->
                prefs[stringPreferencesKey("profile_picture_uri")] = "/path/to/nonexistent/pic.jpg"
            }

            val jsonString = DataExportService.exportToJsonString(context)
            assertNotNull(jsonString)
            val backupData = Json.decodeFromString<AppDataBackup>(jsonString!!)
            assertNull(backupData.profilePictureBase64)
        }

    @Test
    fun `restoreFromBackupSnapshot handles invalid profile picture base64 gracefully`() =
        runTest {
            val backupData =
                AppDataBackup(
                    transactions = listOf(Transaction(id = 7, description = "Tx", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null)),
                    accounts = listOf(Account(id = 1, name = "Acc", type = "Bank")),
                    categories = listOf(Category(id = 1, name = "Cat", iconKey = "icon", colorKey = "color")),
                    budgets = emptyList(),
                    merchantMappings = emptyList(),
                    profilePictureBase64 = "not_valid_base64_???",
                )

            val jsonString = Json.encodeToString(AppDataBackup.serializer(), backupData)
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            FileOutputStream(snapshotFile).use { fos ->
                GZIPOutputStream(fos).use { gzip ->
                    gzip.write(jsonString.toByteArray())
                }
            }

            val success = DataExportService.restoreFromBackupSnapshot(context)
            assertTrue("Restore should succeed even if profile picture is corrupt", success)
        }

    @Test
    fun `restoreFromBackupSnapshot inserts recurring patterns when present`() =
        runTest {
            // Arrange
            val pattern =
                RecurringPattern(
                    smsSignature = "sig_abc",
                    description = "Netflix",
                    amount = 199.0,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = null,
                    occurrences = 3,
                    firstSeen = 1000L,
                    lastSeen = 2000L,
                )
            val backupData =
                AppDataBackup(
                    transactions = emptyList(),
                    accounts = emptyList(),
                    categories = emptyList(),
                    budgets = emptyList(),
                    merchantMappings = emptyList(),
                    recurringPatterns = listOf(pattern),
                )
            val jsonString = Json.encodeToString(AppDataBackup.serializer(), backupData)

            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            FileOutputStream(snapshotFile).use { fos ->
                GZIPOutputStream(fos).use { gzip ->
                    gzip.write(jsonString.toByteArray())
                }
            }

            coJustRun { splitTransactionDao.deleteAll() }
            coJustRun { transactionWriteDao.deleteAll() }
            coJustRun { tagDao.deleteAll() }
            coJustRun { accountDao.deleteAll() }
            coJustRun { categoryDao.deleteAll() }
            coJustRun { budgetDao.deleteAll() }
            coJustRun { merchantMappingDao.deleteAll() }
            coJustRun {
                goalDao.deleteAll()
                goalTransactionLinkDao.deleteAll()
            }
            coJustRun { goalTransactionLinkDao.deleteAll() }
            coJustRun { tripDao.deleteAll() }
            coJustRun { accountAliasDao.deleteAll() }
            coJustRun { customSmsRuleDao.deleteAll() }
            coJustRun { merchantRenameRuleDao.deleteAll() }
            coJustRun { merchantCategoryMappingDao.deleteAll() }
            coJustRun { ignoreRuleDao.deleteAll() }
            coJustRun { smsParseTemplateDao.deleteAll() }
            coJustRun { recurringPatternDao.deleteAll() }
            coJustRun { recurringPatternDao.insert(any()) }

            // Act
            val success = DataExportService.restoreFromBackupSnapshot(context)

            // Assert
            assertTrue("Restore should succeed", success)
            coVerify(exactly = 1) { recurringPatternDao.insert(pattern) }
        }

    @Test
    fun `restoreFromBackupSnapshot returns false if no file exists`() =
        runTest {
            // Arrange
            val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
            if (snapshotFile.exists()) snapshotFile.delete()

            // Act
            val success = DataExportService.restoreFromBackupSnapshot(context)

            // Assert
            assertFalse("Restore should fail if no snapshot file exists", success)
        }

    // --- NEW: Test for regular transactions ---
    @Test
    fun `exportToCsvString handles regular transactions correctly`() =
        runTest {
            // Arrange
            val txId = 1
            val tagId = 1
            val transactionTime = 1672531200000L // 2023-01-01 00:00:00 GMT
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = sdf.format(Date(transactionTime))

            val transaction =
                Transaction(
                    id = txId,
                    description = "Regular Coffee",
                    amount = 150.0,
                    date = transactionTime,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Work expense",
                    // This is a regular transaction
                    isSplit = false,
                    transactionType = TransactionType.EXPENSE,
                    isExcluded = false,
                )
            val details =
                TransactionDetails(
                    transaction = transaction,
                    images = emptyList(),
                    accountName = "Savings",
                    categoryName = "Food",
                    categoryIconKey = "restaurant",
                    categoryColorKey = "red",
                    // This field isn't used by the exporter
                    tagNames = null,
                )
            val tags = listOf(Tag(id = tagId, name = "Work"))

            coEvery { transactionQueryDao.getAllTransactions() } returns flowOf(listOf(details))
            coEvery { transactionQueryDao.getTagsForTransactionSimple(txId) } returns tags
            // No need to mock splitTransactionDao as isSplit is false

            // Act
            val csvString = DataExportService.exportToCsvString(context)

            // Assert
            assertNotNull("CSV string should not be null", csvString)
            val lines = csvString!!.lines().filter { it.isNotBlank() }

            assertEquals("Should be 2 lines (header + 1 transaction)", 2, lines.size)
            assertEquals("Id,ParentId,Date,Description,Amount,Type,Category,Account,Notes,IsExcluded,Tags", lines[0])

            val dataRow = lines[1].split(',')
            assertEquals(11, dataRow.size)
            assertEquals(txId.toString(), dataRow[0])
            assertEquals("", dataRow[1]) // ParentId
            assertEquals(formattedDate, dataRow[2])
            assertEquals("Regular Coffee", dataRow[3])
            assertEquals("150.0", dataRow[4])
            assertEquals("expense", dataRow[5])
            assertEquals("Food", dataRow[6])
            assertEquals("Savings", dataRow[7])
            // --- FIX: Remove quotes. escapeCsvField only quotes for comma, newline, or double-quote ---
            assertEquals("Work expense", dataRow[8]) // Notes will NOT be quoted
            assertEquals("false", dataRow[9])
            assertEquals("Work", dataRow[10])
        }

    @Test
    fun `exportToCsvString handles split transactions correctly`() =
        runTest {
            // Arrange
            val parentTxId = 1
            val tagId1 = 10
            val tagId2 = 11
            val transactionTime = 1672531200000L // 2023-01-01 00:00:00 GMT
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = sdf.format(Date(transactionTime))

            // 1. Parent Transaction Details
            val parentTransaction =
                Transaction(id = parentTxId, description = "Market Visit", amount = 150.0, date = transactionTime, accountId = 1, categoryId = null, notes = "Parent Note", isSplit = true, transactionType = TransactionType.EXPENSE, isExcluded = false)
            val parentDetails =
                TransactionDetails(
                    transaction = parentTransaction,
                    images = emptyList(),
                    accountName = "Savings",
                    // Is split
                    categoryName = null,
                    categoryIconKey = null,
                    categoryColorKey = null,
                    // This is what the GROUP_CONCAT in the DAO query would produce
                    tagNames = "Groceries|Weekend",
                )

            // 2. Split Transaction Details
            val splits =
                listOf(
                    SplitTransaction(id = 1, parentTransactionId = parentTxId, amount = 100.0, categoryId = 1, notes = "Vegetables"),
                    SplitTransaction(id = 2, parentTransactionId = parentTxId, amount = 50.0, categoryId = 2, notes = "Snacks"),
                )
            val splitDetails =
                listOf(
                    SplitTransactionDetails(splits[0], "Food", "restaurant", "red"),
                    SplitTransactionDetails(splits[1], "Shopping", "shopping_bag", "blue"),
                )

            // 3. Tags
            val tags =
                listOf(
                    Tag(id = tagId1, name = "Groceries"),
                    Tag(id = tagId2, name = "Weekend"),
                )

            // 4. Mock DAO calls
            coEvery { transactionQueryDao.getAllTransactions() } returns flowOf(listOf(parentDetails))
            coEvery { transactionQueryDao.getTagsForTransactionSimple(parentTxId) } returns tags
            coEvery { splitTransactionDao.getSplitsForParentSimple(parentTxId) } returns splitDetails

            // Act
            val csvString = DataExportService.exportToCsvString(context)

            // Assert
            assertNotNull("CSV string should not be null", csvString)
            val lines = csvString!!.lines().filter { it.isNotBlank() }

            assertEquals("Should be 4 lines (header + parent + 2 children)", 4, lines.size)

            // Header validation
            val header = lines[0].split(',')
            assertEquals(11, header.size)
            assertEquals("Tags", header[10])

            // Parent row validation
            val parentRow = lines[1].split(',')
            assertEquals(11, parentRow.size)
            // --- FIX: Use the formattedDate string for a stable comparison ---
            assertEquals(formattedDate, parentRow[2])
            // --- FIX: Remove quotes. escapeCsvField only quotes for comma, newline, or double-quote ---
            assertEquals("Groceries|Weekend", parentRow[10]) // Tags column will NOT be quoted

            // Child row 1 validation
            val childRow1 = lines[2].split(',')
            assertEquals(11, childRow1.size)
            assertEquals("", childRow1[10]) // Tags column should be present but empty

            // Child row 2 validation
            val childRow2 = lines[3].split(',')
            assertEquals(11, childRow2.size)
            assertEquals("", childRow2[10]) // Tags column should be present but empty
        }

    @Test
    fun `importDataFromJson successfully parses and imports backup from URI`() =
        runTest {
            val backupData =
                AppDataBackup(
                    transactions =
                        listOf(
                            Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = null)
                        ),
                    accounts = listOf(Account(id = 1, name = "Bank", type = "Savings")),
                    categories = listOf(Category(id = 1, name = "Food", iconKey = "food", colorKey = "red")),
                    tags = emptyList(),
                    transactionTagCrossRefs = emptyList(),
                    budgets = emptyList(),
                    merchantMappings = emptyList(),
                )
            val jsonString = Json.encodeToString(AppDataBackup.serializer(), backupData)

            val tempFile = File(context.cacheDir, "test_import.json")
            tempFile.writeText(jsonString)
            val uri = android.net.Uri.fromFile(tempFile)

            coJustRun { splitTransactionDao.deleteAll() }
            coJustRun { transactionWriteDao.deleteAll() }
            coJustRun { tagDao.deleteAll() }
            coJustRun { accountDao.deleteAll() }
            coJustRun { categoryDao.deleteAll() }
            coJustRun { budgetDao.deleteAll() }
            coJustRun { merchantMappingDao.deleteAll() }
            coJustRun { goalDao.deleteAll() }
            coJustRun { goalTransactionLinkDao.deleteAll() }
            coJustRun { tripDao.deleteAll() }
            coJustRun { accountAliasDao.deleteAll() }
            coJustRun { customSmsRuleDao.deleteAll() }
            coJustRun { merchantRenameRuleDao.deleteAll() }
            coJustRun { merchantCategoryMappingDao.deleteAll() }
            coJustRun { ignoreRuleDao.deleteAll() }
            coJustRun { smsParseTemplateDao.deleteAll() }
            coJustRun { recurringPatternDao.deleteAll() }

            coJustRun { accountDao.insertAll(any()) }
            coJustRun { categoryDao.insertAll(any()) }
            coJustRun { budgetDao.insertAll(any()) }
            coJustRun { merchantMappingDao.insertAll(any()) }
            coJustRun { tagDao.insertAll(any()) }
            coJustRun { goalDao.insertAll(any()) }
            coJustRun { goalTransactionLinkDao.insertAll(any()) }
            coJustRun { tripDao.insertAll(any()) }
            coJustRun { accountAliasDao.insertAll(any()) }
            coJustRun { transactionWriteDao.insertAll(any()) }
            coJustRun { splitTransactionDao.insertAll(any()) }
            coJustRun { transactionWriteDao.addTagsToTransaction(any()) }
            coJustRun { customSmsRuleDao.insertAll(any()) }
            coJustRun { merchantRenameRuleDao.insertAll(any()) }
            coJustRun { merchantCategoryMappingDao.insertAll(any()) }
            coJustRun { ignoreRuleDao.insertAll(any()) }
            coJustRun { smsParseTemplateDao.insertAll(any()) }

            val result = DataExportService.importDataFromJson(context, uri)
            assertTrue("importDataFromJson should succeed with valid JSON file", result)
            tempFile.delete()
        }

    @Test
    fun `importDataFromJson returns false when file is empty or blank`() =
        runTest {
            val tempFile = File(context.cacheDir, "empty_import.json")
            tempFile.writeText("   ")
            val uri = android.net.Uri.fromFile(tempFile)

            val result = DataExportService.importDataFromJson(context, uri)
            assertFalse("importDataFromJson should return false for blank JSON", result)
            tempFile.delete()
        }

    @Test
    fun `importDataFromJson returns false when URI cannot be opened`() =
        runTest {
            val uri = android.net.Uri.parse("content://invalid/path/does_not_exist.json")
            val result = DataExportService.importDataFromJson(context, uri)
            assertFalse("importDataFromJson should return false for invalid content URI", result)
        }

    @Test
    fun `exportToCsvString handles transactions with null account notes and tags`() =
        runTest {
            val transaction =
                Transaction(
                    id = 10,
                    description = "Simple",
                    amount = 20.0,
                    date = 1000L,
                    transactionType = TransactionType.INCOME,
                    accountId = 1,
                    categoryId = null,
                    notes = null,
                    isExcluded = true
                )
            val details =
                TransactionDetails(
                    transaction = transaction,
                    images = emptyList(),
                    accountName = null,
                    categoryName = null,
                    categoryColorKey = null,
                    categoryIconKey = null,
                    tagNames = null
                )

            coEvery { transactionQueryDao.getAllTransactions() } returns flowOf(listOf(details))
            coEvery { transactionQueryDao.getTagsForTransactionSimple(10) } returns emptyList()

            val csv = DataExportService.exportToCsvString(context)
            assertNotNull(csv)
            assertTrue(csv!!.contains("N/A"))
            assertTrue(csv.contains("income"))
            assertTrue(csv.contains("true"))
        }

    private fun mockAllDaoImportOperations() {
        coJustRun { splitTransactionDao.deleteAll() }
        coJustRun { transactionWriteDao.deleteAll() }
        coJustRun { tagDao.deleteAll() }
        coJustRun { accountDao.deleteAll() }
        coJustRun { categoryDao.deleteAll() }
        coJustRun { budgetDao.deleteAll() }
        coJustRun { merchantMappingDao.deleteAll() }
        coJustRun { goalDao.deleteAll() }
        coJustRun { goalTransactionLinkDao.deleteAll() }
        coJustRun { tripDao.deleteAll() }
        coJustRun { accountAliasDao.deleteAll() }
        coJustRun { customSmsRuleDao.deleteAll() }
        coJustRun { merchantRenameRuleDao.deleteAll() }
        coJustRun { merchantCategoryMappingDao.deleteAll() }
        coJustRun { ignoreRuleDao.deleteAll() }
        coJustRun { smsParseTemplateDao.deleteAll() }
        coJustRun { recurringPatternDao.deleteAll() }

        coJustRun { accountDao.insertAll(any()) }
        coJustRun { categoryDao.insertAll(any()) }
        coJustRun { budgetDao.insertAll(any()) }
        coJustRun { merchantMappingDao.insertAll(any()) }
        coJustRun { tagDao.insertAll(any()) }
        coJustRun { goalDao.insertAll(any()) }
        coJustRun { goalTransactionLinkDao.insertAll(any()) }
        coJustRun { tripDao.insertAll(any()) }
        coJustRun { accountAliasDao.insertAll(any()) }
        coJustRun { transactionWriteDao.insertAll(any()) }
        coJustRun { splitTransactionDao.insertAll(any()) }
        coJustRun { transactionWriteDao.addTagsToTransaction(any()) }
        coJustRun { customSmsRuleDao.insertAll(any()) }
        coJustRun { merchantRenameRuleDao.insertAll(any()) }
        coJustRun { merchantCategoryMappingDao.insertAll(any()) }
        coJustRun { ignoreRuleDao.insertAll(any()) }
        coJustRun { smsParseTemplateDao.insertAll(any()) }
        coJustRun { recurringPatternDao.insert(any()) }
    }

    @Test
    fun `importDataFromJson successfully decodes legacy backup JSON with lowercase enum values`() =
        runTest {
            mockAllDaoImportOperations()
            val legacyJson =
                """
                {
                    "transactions": [
                        {
                            "id": 101,
                            "description": "Legacy Expense",
                            "amount": 150.0,
                            "date": 1700000000000,
                            "accountId": 1,
                            "categoryId": 1,
                            "notes": null,
                            "transactionType": "expense",
                            "status": "confirmed"
                        },
                        {
                            "id": 102,
                            "description": "Legacy Income",
                            "amount": 5000.0,
                            "date": 1700000001000,
                            "accountId": 1,
                            "categoryId": 2,
                            "notes": null,
                            "transactionType": "income",
                            "status": "pending"
                        },
                        {
                            "id": 103,
                            "description": "Legacy Transfer",
                            "amount": 200.0,
                            "date": 1700000002000,
                            "accountId": 1,
                            "categoryId": null,
                            "notes": null,
                            "transactionType": "transfer",
                            "status": "skipped"
                        }
                    ],
                    "accounts": [{"id": 1, "name": "Main Bank", "type": "Bank"}],
                    "categories": [{"id": 1, "name": "Food", "iconKey": "food", "colorKey": "green"}],
                    "budgets": [],
                    "merchantMappings": [],
                    "recurringPatterns": [
                        {
                            "smsSignature": "legacy_pattern_sig",
                            "description": "Legacy Subscription",
                            "amount": 99.0,
                            "transactionType": "expense",
                            "accountId": 1,
                            "categoryId": 1,
                            "occurrences": 3,
                            "firstSeen": 1700000000000,
                            "lastSeen": 1700000000000,
                            "isDismissed": false
                        }
                    ]
                }
                """.trimIndent()

            val tempFile = File(context.cacheDir, "legacy_backup.json")
            tempFile.writeText(legacyJson)
            val uri = android.net.Uri.fromFile(tempFile)

            val capturedTransactions = slot<List<Transaction>>()
            val capturedPatterns = slot<RecurringPattern>()
            coEvery { transactionWriteDao.insertAll(capture(capturedTransactions)) } just runs
            coEvery { recurringPatternDao.insert(capture(capturedPatterns)) } just runs

            val success = DataExportService.importDataFromJson(context, uri)
            assertTrue("Import of legacy JSON with lowercase enums should succeed", success)

            val txns = capturedTransactions.captured
            assertEquals(3, txns.size)

            assertEquals(TransactionType.EXPENSE, txns[0].transactionType)
            assertEquals(TransactionStatus.CONFIRMED, txns[0].status)

            assertEquals(TransactionType.INCOME, txns[1].transactionType)
            assertEquals(TransactionStatus.PENDING, txns[1].status)

            assertEquals(TransactionType.TRANSFER, txns[2].transactionType)
            assertEquals(TransactionStatus.SKIPPED, txns[2].status)

            assertEquals(TransactionType.EXPENSE, capturedPatterns.captured.transactionType)

            tempFile.delete()
        }

    @Test
    fun `importDataFromJson successfully decodes modern backup JSON with uppercase enum values`() =
        runTest {
            mockAllDaoImportOperations()
            val modernJson =
                """
                {
                    "transactions": [
                        {
                            "id": 201,
                            "description": "Modern Expense",
                            "amount": 250.0,
                            "date": 1700000000000,
                            "accountId": 1,
                            "categoryId": 1,
                            "notes": null,
                            "transactionType": "EXPENSE",
                            "status": "CONFIRMED"
                        }
                    ],
                    "accounts": [{"id": 1, "name": "Main Bank", "type": "Bank"}],
                    "categories": [{"id": 1, "name": "Food", "iconKey": "food", "colorKey": "green"}],
                    "budgets": [],
                    "merchantMappings": []
                }
                """.trimIndent()

            val tempFile = File(context.cacheDir, "modern_backup.json")
            tempFile.writeText(modernJson)
            val uri = android.net.Uri.fromFile(tempFile)

            val capturedTransactions = slot<List<Transaction>>()
            coEvery { transactionWriteDao.insertAll(capture(capturedTransactions)) } just runs

            val success = DataExportService.importDataFromJson(context, uri)
            assertTrue("Import of modern JSON with uppercase enums should succeed", success)

            val txns = capturedTransactions.captured
            assertEquals(1, txns.size)
            assertEquals(TransactionType.EXPENSE, txns[0].transactionType)
            assertEquals(TransactionStatus.CONFIRMED, txns[0].status)

            tempFile.delete()
        }
}
