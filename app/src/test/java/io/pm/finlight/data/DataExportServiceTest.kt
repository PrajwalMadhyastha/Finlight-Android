package io.pm.finlight.data

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.data.model.AppDataBackup
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DataExportServiceTest : BaseViewModelTest() {

    private lateinit var context: Application
    private lateinit var db: AppDatabase

    // Mock all DAOs
    private val transactionDao: TransactionDao = mockk(relaxed = true)
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

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Mock the static AppDatabase.getInstance() to return our mocked db
        db = mockk()
        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db

        // Link the DAOs to the mocked db instance
        every { db.transactionDao() } returns transactionDao
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
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    private fun setupMockData() {
        // Setup DAOs to return some mock data
        coEvery { transactionDao.getAllTransactionsSimple() } returns flowOf(listOf(Transaction(id = 1, description = "Test Tx", amount = 100.0, date = 1L, accountId = 1, categoryId = 1, notes = null)))
        coEvery { accountDao.getAllAccounts() } returns flowOf(listOf(Account(id = 1, name = "Test Acc", type = "Bank")))
        coEvery { categoryDao.getAllCategories() } returns flowOf(listOf(Category(id = 1, name = "Test Cat", iconKey = "icon", colorKey = "color")))
        coEvery { budgetDao.getAllBudgets() } returns flowOf(listOf(Budget(id = 1, categoryName = "Test Cat", amount = 500.0, month = 1, year = 2025)))
        coEvery { merchantMappingDao.getAllMappings() } returns flowOf(listOf(MerchantMapping(smsSender = "TestSender", merchantName = "Test Merchant")))
        coEvery { splitTransactionDao.getAllSplits() } returns flowOf(listOf(SplitTransaction(id = 1, parentTransactionId = 1, amount = 50.0, categoryId = 1, notes = "Split")))
        coEvery { customSmsRuleDao.getAllRulesList() } returns listOf(CustomSmsRule(id = 1, triggerPhrase = "test", priority = 1, sourceSmsBody = "test sms", merchantRegex = null, amountRegex = null, accountRegex = null, merchantNameExample = null, amountExample = null, accountNameExample = null))
        coEvery { merchantRenameRuleDao.getAllRulesList() } returns listOf(MerchantRenameRule(originalName = "Old", newName = "New"))
        coEvery { merchantCategoryMappingDao.getAll() } returns listOf(MerchantCategoryMapping("Old", 1))
        coEvery { ignoreRuleDao.getAllList() } returns listOf(IgnoreRule(id = 1, pattern = "ignore"))
        coEvery { smsParseTemplateDao.getAllTemplates() } returns listOf(SmsParseTemplate(templateSignature = "sig", correctedMerchantName = "merchant", originalSmsBody = "body", originalMerchantStartIndex = 0, originalMerchantEndIndex = 1, originalAmountStartIndex = 2, originalAmountEndIndex = 3))
        coEvery { tagDao.getAllTagsList() } returns listOf(Tag(id = 1, name = "Test Tag"))
        coEvery { transactionDao.getAllCrossRefs() } returns listOf(TransactionTagCrossRef(transactionId = 1, tagId = 1))
        coEvery { goalDao.getAll() } returns listOf(Goal(id = 1, name = "Test Goal", targetAmount = 1000.0, savedAmount = 100.0, targetDate = null, accountId = 1))
        coEvery { tripDao.getAll() } returns listOf(Trip(id = 1, name = "Test Trip", startDate = 1L, endDate = 2L, tagId = 1, tripType = TripType.DOMESTIC, currencyCode = null, conversionRate = null))
        coEvery { accountAliasDao.getAll() } returns listOf(AccountAlias(aliasName = "Alias Acc", destinationAccountId = 1))
    }

    @Test
    fun `exportToJsonString serializes all data correctly`() = runTest {
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
        assertEquals(1, backupData.trips.size)
        assertEquals(1, backupData.accountAliases.size)
    }

    @Test
    fun `createBackupSnapshot creates a compressed file`() = runTest {
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
    fun `restoreFromBackupSnapshot restores data and deletes file`() = runTest {
        // Arrange
        // 1. Create a dummy backup data and JSON string
        val backupData = AppDataBackup(
            transactions = listOf(Transaction(id = 5, description = "Restored Tx", amount = 555.0, date = 1L, accountId = 1, categoryId = 1, notes = null)),
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
            trips = emptyList(),
            accountAliases = emptyList()
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
        coJustRun { transactionDao.deleteAll() }
        coJustRun { tagDao.deleteAll() }
        coJustRun { accountDao.deleteAll() }
        coJustRun { categoryDao.deleteAll() }
        coJustRun { budgetDao.deleteAll() }
        coJustRun { merchantMappingDao.deleteAll() }
        coJustRun { goalDao.deleteAll() }
        coJustRun { tripDao.deleteAll() }
        coJustRun { accountAliasDao.deleteAll() }
        coJustRun { customSmsRuleDao.deleteAll() }
        coJustRun { merchantRenameRuleDao.deleteAll() }
        coJustRun { merchantCategoryMappingDao.deleteAll() }
        coJustRun { ignoreRuleDao.deleteAll() }
        coJustRun { smsParseTemplateDao.deleteAll() }

        coJustRun { accountDao.insertAll(any()) }
        coJustRun { categoryDao.insertAll(any()) }
        coJustRun { tagDao.insertAll(any()) }
        coJustRun { transactionDao.insertAll(any()) }
        coJustRun { transactionDao.addTagsToTransaction(any()) }


        // Act
        val success = DataExportService.restoreFromBackupSnapshot(context)

        // Assert
        assertTrue("Restore should be successful", success)
        assertFalse("Snapshot file should be deleted after successful restore", snapshotFile.exists())

        // Verify that the import logic was actually called
        coVerifyOrder {
            splitTransactionDao.deleteAll()
            transactionDao.deleteAll()
            tagDao.deleteAll()
            accountDao.deleteAll()
            categoryDao.deleteAll()
            budgetDao.deleteAll()
            merchantMappingDao.deleteAll()
            goalDao.deleteAll()
            tripDao.deleteAll()
            accountAliasDao.deleteAll()
            customSmsRuleDao.deleteAll()
            merchantRenameRuleDao.deleteAll()
            merchantCategoryMappingDao.deleteAll()
            ignoreRuleDao.deleteAll()
            smsParseTemplateDao.deleteAll()
        }


        coVerify { accountDao.insertAll(backupData.accounts) }
        coVerify { categoryDao.insertAll(backupData.categories) }
        coVerify { tagDao.insertAll(backupData.tags) }
        coVerify { transactionDao.insertAll(backupData.transactions) }
        coVerify { transactionDao.addTagsToTransaction(backupData.transactionTagCrossRefs) }
    }

    @Test
    fun `restoreFromBackupSnapshot returns false if no file exists`() = runTest {
        // Arrange
        val snapshotFile = File(context.filesDir, "backup_snapshot.gz")
        if (snapshotFile.exists()) snapshotFile.delete()

        // Act
        val success = DataExportService.restoreFromBackupSnapshot(context)

        // Assert
        assertFalse("Restore should fail if no snapshot file exists", success)
    }
}

