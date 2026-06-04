// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/SmsProcessorWorkerTest.kt
// REASON: NEW — Tests for SmsProcessorWorker, which runs the full SMS parsing
// pipeline (custom rules → ML classifier → NER → parser → save). Uses
// TestListenableWorkerBuilder to exercise the worker in isolation.
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
import io.pm.finlight.utils.NotificationHelper
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
class SmsProcessorWorkerTest : BaseViewModelTest() {
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
    private lateinit var mockClassifier: SmsClassifier
    private lateinit var mockNerExtractor: NerExtractor

    private fun buildWorker(
        sender: String,
        body: String,
        date: Long = 1L
    ): SmsProcessorWorker {
        val inputData =
            workDataOf(
                SmsProcessorWorker.KEY_SENDER to sender,
                SmsProcessorWorker.KEY_BODY to body,
                SmsProcessorWorker.KEY_DATE to date,
            )
        return TestListenableWorkerBuilder<SmsProcessorWorker>(context, inputData).build()
    }

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
        every { anyConstructed<SettingsRepository>().isAutoCaptureNotificationEnabledBlocking() } returns false

        mockClassifier = mockk(relaxed = true)
        mockNerExtractor = mockk(relaxed = true)
        every { mockClassifier.classify(any()) } returns 0.9f
        every { mockClassifier.close() } just runs
        every { mockNerExtractor.extract(any()) } returns emptyMap()
        every { mockNerExtractor.close() } just runs

        mockkObject(MlModelFactory)
        every { MlModelFactory.getClassifier(any()) } returns mockClassifier
        every { MlModelFactory.getNerExtractor(any()) } returns mockNerExtractor

        mockkObject(NotificationHelper)
        every { NotificationHelper.showSuspiciousAmountNotification(any(), any(), any()) } just runs
        every { NotificationHelper.showTravelModeSmsNotification(any(), any(), any()) } just runs

        mockkObject(SmsParser)
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `returns failure when sender is missing`() =
        runTest {
            val inputData =
                workDataOf(
                    SmsProcessorWorker.KEY_BODY to "body",
                    SmsProcessorWorker.KEY_DATE to 1L,
                )
            val worker = TestListenableWorkerBuilder<SmsProcessorWorker>(context, inputData).build()
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `returns failure when body is missing`() =
        runTest {
            val inputData =
                workDataOf(
                    SmsProcessorWorker.KEY_SENDER to "AM-HDFCBK",
                    SmsProcessorWorker.KEY_DATE to 1L,
                )
            val worker = TestListenableWorkerBuilder<SmsProcessorWorker>(context, inputData).build()
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `ML classifier below threshold returns success without saving`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            every { mockClassifier.classify(any()) } returns 0.05f

            val worker = buildWorker("SPAM", "Win a prize!")
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionDao.insert(any()) }
        }

    @Test
    fun `successful parse saves transaction and returns success`() =
        runTest {
            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "newhash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy")
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { transactionDao.insert(any()) }
        }

    @Test
    fun `already seen hash is skipped without saving`() =
        runTest {
            val existingHash = "existinghash"
            coEvery { transactionDao.getAllSmsHashes() } returns flowOf(listOf(existingHash))

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = existingHash,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionDao.insert(any()) }
        }

    @Test
    fun `ignored SMS returns success without saving`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Ignored("OTP rule")

            val result = buildWorker("PROMO", "Your OTP is 1234").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionDao.insert(any()) }
        }

    @Test
    fun `custom rule match skips ML and saves directly`() =
        runTest {
            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "CUSTOM", amount = 200.0,
                    transactionType = "expense", merchantName = "CustomMerchant",
                    originalMessage = "Custom rule body", sourceSmsHash = "customhash",
                )
            // Custom rule returns a result — ML/NER should NOT be called
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("CUSTOM", "Custom rule body").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { mockClassifier.classify(any()) }
            coVerify(exactly = 1) { transactionDao.insert(any()) }
        }

    @Test
    fun `auto-healing aliases are persisted on success`() =
        runTest {
            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Starbucks",
                    originalMessage = "Spent Rs.100 at Starbucks", sourceSmsHash = "hash99",
                )
            val success =
                ParseResult.Success(
                    transaction = txn,
                    newlyDiscoveredRenameAlias = "STARBUCKS" to "Starbucks",
                    newlyDiscoveredCategoryAlias = "Starbucks" to 4,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns success

            buildWorker("AM-HDFCBK", "Spent Rs.100 at Starbucks").doWork()

            coVerify { merchantRenameRuleDao.insert(any()) }
            coVerify { merchantCategoryMappingDao.insert(any()) }
        }
}
