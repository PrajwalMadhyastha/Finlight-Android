// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/SmsCatchupWorkerTest.kt
// REASON: NEW — Unit tests for the SmsCatchupWorker. Verifies that it fetches
// recent SMS messages, avoids duplicates, and saves missed transactions silently.
// =================================================================================
package io.pm.finlight.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.ml.SmsClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsCatchupWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var merchantMappingDao: MerchantMappingDao
    private lateinit var customSmsRuleDao: CustomSmsRuleDao
    private lateinit var ignoreRuleDao: IgnoreRuleDao
    private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    private lateinit var accountDao: AccountDao
    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var tagDao: TagDao
    private lateinit var smsRepository: SmsRepository
    private lateinit var mockClassifier: SmsClassifier
    private lateinit var mockNerExtractor: NerExtractor

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = mockk(relaxed = true)
        transactionDao =
            mockk(relaxed = true) {
                coEvery { insert(any()) } returns 1L
                coEvery { addTagsToTransaction(any()) } just runs
            }
        merchantMappingDao = mockk(relaxed = true)
        customSmsRuleDao = mockk(relaxed = true)
        ignoreRuleDao = mockk(relaxed = true)
        merchantRenameRuleDao = mockk(relaxed = true)
        merchantCategoryMappingDao = mockk(relaxed = true)
        smsParseTemplateDao = mockk(relaxed = true)
        accountDao = mockk(relaxed = true)
        accountAliasDao = mockk(relaxed = true)
        tagDao = mockk(relaxed = true)
        smsRepository = mockk(relaxed = true)

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao
        every { db.merchantMappingDao() } returns merchantMappingDao
        every { db.customSmsRuleDao() } returns customSmsRuleDao
        every { db.ignoreRuleDao() } returns ignoreRuleDao
        every { db.merchantRenameRuleDao() } returns merchantRenameRuleDao
        every { db.merchantCategoryMappingDao() } returns merchantCategoryMappingDao
        every { db.smsParseTemplateDao() } returns smsParseTemplateDao
        every { db.accountDao() } returns accountDao
        every { db.accountAliasDao() } returns accountAliasDao
        every { db.tagDao() } returns tagDao

        mockkConstructor(SmsRepository::class)
        coEvery { anyConstructed<SmsRepository>().fetchAllSms(any()) } returns emptyList()

        coEvery { merchantMappingDao.getAllMappings() } returns flowOf(emptyList())
        coEvery { transactionDao.getAllSmsHashes() } returns flowOf(emptyList())
        coEvery { customSmsRuleDao.getAllRules() } returns flowOf(emptyList())
        coEvery { ignoreRuleDao.getEnabledRules() } returns emptyList()
        coEvery { merchantRenameRuleDao.getAllRules() } returns flowOf(emptyList())
        coEvery { merchantRenameRuleDao.getAllRulesList() } returns emptyList()
        coEvery { merchantCategoryMappingDao.getCategoryIdForMerchant(any()) } returns null
        coEvery { merchantCategoryMappingDao.getAll() } returns emptyList()
        coEvery { smsParseTemplateDao.getAllTemplates() } returns emptyList()
        coEvery { smsParseTemplateDao.getTemplatesBySignature(any()) } returns emptyList()
        coEvery { accountAliasDao.findByAlias(any()) } returns null
        coEvery { accountDao.findByName(any()) } returns Account(1, "Test", "Bank Account")
        coEvery { accountDao.insert(any()) } returns 1L
        coEvery { accountDao.getAccountByIdBlocking(any()) } returns Account(1, "Test", "Bank Account")
        coEvery { tagDao.findByName(any()) } returns null
        coEvery { tagDao.insert(any()) } returns 1L

        mockkConstructor(SettingsRepository::class)
        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(null)
        every { anyConstructed<SettingsRepository>().getHomeCurrency() } returns flowOf("INR")

        mockClassifier = mockk(relaxed = true)
        mockNerExtractor = mockk(relaxed = true)
        every { mockClassifier.classify(any()) } returns 0.9f
        every { mockClassifier.close() } just runs
        every { mockNerExtractor.extract(any()) } returns emptyMap()
        every { mockNerExtractor.close() } just runs

        mockkObject(MlModelFactory)
        every { MlModelFactory.getClassifier(any()) } returns mockClassifier
        every { MlModelFactory.getNerExtractor(any()) } returns mockNerExtractor

        mockkObject(SmsParser)
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `returns success when no recent SMS are found`() =
        runTest {
            coEvery { anyConstructed<SmsRepository>().fetchAllSms(any()) } returns emptyList()

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionDao.insert(any()) }
        }

    @Test
    fun `recovers missed transaction silently`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { anyConstructed<SmsRepository>().fetchAllSms(any()) } returns listOf(sms)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "hash1",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)
            coEvery { transactionDao.getAllSmsHashes() } returns flowOf(emptyList()) // Hash not in DB

            val captor = slot<Transaction>()
            coEvery { transactionDao.insert(capture(captor)) } returns 99L

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { transactionDao.insert(any()) }
            assertEquals("Auto-Recovered", captor.captured.source)
        }

    @Test
    fun `skips transaction already in database`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { anyConstructed<SmsRepository>().fetchAllSms(any()) } returns listOf(sms)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "existing_hash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            // Mock DB returning the hash
            coEvery { transactionDao.getAllSmsHashes() } returns flowOf(listOf("existing_hash"))

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionDao.insert(any()) }
        }

    @Test
    fun `prevents duplicate saves within the same run`() =
        runTest {
            // Two identical SMS messages in the inbox
            val sms1 = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            val sms2 = SmsMessage(2L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis() + 1000)
            coEvery { anyConstructed<SmsRepository>().fetchAllSms(any()) } returns listOf(sms1, sms2)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "same_hash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)
            coEvery { transactionDao.getAllSmsHashes() } returns flowOf(emptyList()) // Not in DB yet

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Should only be inserted once because the worker tracks saved hashes internally during the run
            coVerify(exactly = 1) { transactionDao.insert(any()) }
        }
}
