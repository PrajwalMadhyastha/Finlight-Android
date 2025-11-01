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

    // --- NEW: Test for regular transactions ---
    @Test
    fun `exportToCsvString handles regular transactions correctly`() = runTest {
        // Arrange
        val txId = 1
        val tagId = 1
        val transactionTime = 1672531200000L // 2023-01-01 00:00:00 GMT
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(Date(transactionTime))

        val transaction = Transaction(
            id = txId,
            description = "Regular Coffee",
            amount = 150.0,
            date = transactionTime,
            accountId = 1,
            categoryId = 1,
            notes = "Work expense",
            isSplit = false, // This is a regular transaction
            transactionType = "expense",
            isExcluded = false
        )
        val details = TransactionDetails(
            transaction = transaction,
            images = emptyList(),
            accountName = "Savings",
            categoryName = "Food",
            categoryIconKey = "restaurant",
            categoryColorKey = "red",
            tagNames = null // This field isn't used by the exporter
        )
        val tags = listOf(Tag(id = tagId, name = "Work"))

        coEvery { transactionDao.getAllTransactions() } returns flowOf(listOf(details))
        coEvery { transactionDao.getTagsForTransactionSimple(txId) } returns tags
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
    fun `exportToCsvString handles split transactions correctly`() = runTest {
        // Arrange
        val parentTxId = 1
        val tagId1 = 10
        val tagId2 = 11
        val transactionTime = 1672531200000L // 2023-01-01 00:00:00 GMT
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(Date(transactionTime))

        // 1. Parent Transaction Details
        val parentTransaction = Transaction(id = parentTxId, description = "Market Visit", amount = 150.0, date = transactionTime, accountId = 1, categoryId = null, notes = "Parent Note", isSplit = true, transactionType = "expense", isExcluded = false)
        val parentDetails = TransactionDetails(
            transaction = parentTransaction,
            images = emptyList(),
            accountName = "Savings",
            categoryName = null, // Is split
            categoryIconKey = null,
            categoryColorKey = null,
            tagNames = "Groceries|Weekend" // This is what the GROUP_CONCAT in the DAO query would produce
        )

        // 2. Split Transaction Details
        val splits = listOf(
            SplitTransaction(id = 1, parentTransactionId = parentTxId, amount = 100.0, categoryId = 1, notes = "Vegetables"),
            SplitTransaction(id = 2, parentTransactionId = parentTxId, amount = 50.0, categoryId = 2, notes = "Snacks")
        )
        val splitDetails = listOf(
            SplitTransactionDetails(splits[0], "Food", "restaurant", "red"),
            SplitTransactionDetails(splits[1], "Shopping", "shopping_bag", "blue")
        )

        // 3. Tags
        val tags = listOf(
            Tag(id = tagId1, name = "Groceries"),
            Tag(id = tagId2, name = "Weekend")
        )

        // 4. Mock DAO calls
        coEvery { transactionDao.getAllTransactions() } returns flowOf(listOf(parentDetails))
        coEvery { transactionDao.getTagsForTransactionSimple(parentTxId) } returns tags
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
}

