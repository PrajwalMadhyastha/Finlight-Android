// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/SmsCatchupWorkerTest.kt
// REASON: NEW — Unit tests for the SmsCatchupWorker. Verifies that it fetches
// recent SMS messages, avoids duplicates, and saves missed transactions silently.
// =================================================================================
package io.pm.finlight.workers

import android.Manifest
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
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.ml.SmsClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import io.pm.finlight.core.NerEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsCatchupWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionQueryDao: TransactionQueryDao
    private lateinit var transactionWriteDao: TransactionWriteDao
    private lateinit var merchantMappingDao: MerchantMappingDao
    private lateinit var customSmsRuleDao: CustomSmsRuleDao
    private lateinit var ignoreRuleDao: IgnoreRuleDao
    private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    private lateinit var accountDao: AccountDao
    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var tagDao: TagDao
    private lateinit var smsRepository: ISmsRepository
    private lateinit var deletedSmsHashDao: DeletedSmsHashDao
    private lateinit var mockClassifier: SmsClassifier
    private lateinit var mockNerExtractor: NerExtractor

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.READ_SMS)

        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = mockk(relaxed = true)
        transactionWriteDao =
            mockk(relaxed = true) {
                coEvery { insert(any()) } returns 1L
                coEvery { addTagsToTransaction(any()) } just runs
            }
        transactionQueryDao = mockk(relaxed = true)
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
        every { db.transactionQueryDao() } returns transactionQueryDao
        every { db.transactionWriteDao() } returns transactionWriteDao
        every { db.merchantMappingDao() } returns merchantMappingDao
        every { db.customSmsRuleDao() } returns customSmsRuleDao
        every { db.ignoreRuleDao() } returns ignoreRuleDao
        every { db.merchantRenameRuleDao() } returns merchantRenameRuleDao
        every { db.merchantCategoryMappingDao() } returns merchantCategoryMappingDao
        every { db.smsParseTemplateDao() } returns smsParseTemplateDao
        every { db.accountDao() } returns accountDao
        every { db.accountAliasDao() } returns accountAliasDao
        every { db.tagDao() } returns tagDao

        deletedSmsHashDao = mockk(relaxed = true)
        every { db.deletedSmsHashDao() } returns deletedSmsHashDao
        coEvery { deletedSmsHashDao.getAllHashes() } returns emptyList()

        ServiceLocator.setSmsRepository(smsRepository)
        coEvery { smsRepository.fetchAllSms(any(), any()) } returns emptyList()

        coEvery { merchantMappingDao.getAllMappings() } returns flowOf(emptyList())
        coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
        coEvery { transactionQueryDao.existsBySmsHash(any()) } returns false
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
        coEvery { accountDao.getAccountByIdSync(any()) } returns Account(1, "Test", "Bank Account")
        coEvery { tagDao.findByName(any()) } returns null
        coEvery { tagDao.insert(any()) } returns 1L

        mockkConstructor(SettingsRepository::class)
        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(null)
        coEvery { anyConstructed<SettingsRepository>().getCurrentTravelModeSettings() } returns null
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
        every { SmsParser.computeLegacySmsHash(any(), any()) } answers { callOriginal() }
        every { SmsParser.computeSmsHash(any(), any()) } answers { callOriginal() }
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `returns success when no recent SMS are found`() =
        runTest {
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns emptyList()

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `recovers missed transaction silently`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "hash1",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList()) // Hash not in DB

            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 99L

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { transactionWriteDao.insert(any()) }
            assertEquals("Auto-Recovered", captor.captured.source)
        }

    @Test
    fun `skips transaction already in database`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "existing_hash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            // Mock DB returning the hash
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(listOf("existing_hash"))

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `prevents duplicate saves within the same run`() =
        runTest {
            // Two identical SMS messages in the inbox
            val sms1 = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            val sms2 = SmsMessage(2L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis() + 1000)
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms1, sms2)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "same_hash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList()) // Not in DB yet

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Should only be inserted once because the worker tracks saved hashes internally during the run
            coVerify(exactly = 1) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `skips transaction whose hash is in the deleted deny-list`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "deleted_hash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery {
                SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns ParseResult.Success(txn)

            // Hash is NOT in the live transactions table …
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
            // … but IS in the deleted deny-list
            coEvery { deletedSmsHashDao.getAllHashes() } returns listOf("deleted_hash")

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Must NOT be re-created
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `queries SMS with 10-minute cooldown window buffer`() =
        runTest {
            val startSlot = slot<Long>()
            val endSlot = slot<Long>()
            coEvery { smsRepository.fetchAllSms(capture(startSlot), capture(endSlot)) } returns emptyList()

            val before = System.currentTimeMillis()
            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()
            val after = System.currentTimeMillis()

            assertEquals(ListenableWorker.Result.success(), result)
            val expectedCooldownMs = 10L * 60 * 1000
            val expectedLookbackMs = 48L * 60 * 60 * 1000

            // endDate should be approximately now - 10 minutes
            assertTrue(endSlot.captured in (before - expectedCooldownMs - 2000)..(after - expectedCooldownMs + 2000))
            // startDate should be endDate - 48 hours
            assertEquals(endSlot.captured - expectedLookbackMs, startSlot.captured)
        }

    @Test
    fun `skips transaction when existsBySmsHash returns true in dynamic TOCTOU check`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L, smsSender = "AM-HDFCBK", amount = 100.0,
                    transactionType = "expense", merchantName = "Swiggy",
                    originalMessage = "Spent Rs.100 at Swiggy", sourceSmsHash = "concurrent_hash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            // Stale snapshot initially had no hashes
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
            // But dynamic check detects concurrent insert by real-time worker
            coEvery { transactionQueryDao.existsBySmsHash("concurrent_hash") } returns true

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Save must be dropped due to dynamic check
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `ner entities log outputs only entity type keys and no sensitive entity values`() =
        runTest {
            mockkStatic(Log::class)
            val logMessages = mutableListOf<String>()
            every { Log.d(any(), capture(logMessages)) } returns 0
            every { Log.i(any(), capture(logMessages)) } returns 0

            try {
                val sms =
                    SmsMessage(
                        1L,
                        "AM-HDFCBK",
                        "Sensitive SMS content 99999.00 XX9876 SecretMerchant",
                        System.currentTimeMillis(),
                    )
                coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

                val nerEntities =
                    mapOf(
                        "AMOUNT" to NerEntity("99999.00", 0.95f),
                        "ACCOUNT" to NerEntity("XX9876", 0.90f),
                        "MERCHANT" to NerEntity("SecretMerchant", 0.85f),
                    )
                every { mockNerExtractor.extract(any()) } returns nerEntities
                coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
                coEvery {
                    SmsParser.parseWithReason(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns ParseResult.Ignored("Ignored for test")

                val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
                worker.doWork()

                val nerLog = logMessages.firstOrNull { it.contains("Entity types found") }
                assertNotNull("Must have NER entity types log message", nerLog)
                listOf("AMOUNT", "ACCOUNT", "MERCHANT").forEach { key ->
                    assertTrue("NER log must contain key '$key'", nerLog!!.contains(key))
                }
                assertFalse(
                    "Must not log sensitive amount value",
                    logMessages.any { it.contains("99999.00") },
                )
                assertFalse(
                    "Must not log sensitive account value",
                    logMessages.any { it.contains("XX9876") },
                )
                assertFalse(
                    "Must not log sensitive merchant value",
                    logMessages.any { it.contains("SecretMerchant") },
                )
            } finally {
                unmockkStatic(Log::class)
            }
        }

    @Test
    fun `skips and upgrades transaction already in database with legacy 32-bit hash`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val sms = SmsMessage(1L, sender, body, System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = sender,
                    amount = 100.0,
                    transactionType = "expense",
                    merchantName = "Swiggy",
                    originalMessage = body,
                    sourceSmsHash = currentHash,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            // Legacy hash exists in DB snapshot
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(listOf(legacyHash))
            coEvery { transactionWriteDao.updateSmsHashByLegacy(any(), any()) } just runs

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Must NOT insert duplicate transaction
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            // Must auto-heal by upgrading legacy hash to new SHA-256 hash in DB
            coVerify(exactly = 1) { transactionWriteDao.updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash) }
        }

    @Test
    fun `skips deleted transaction recorded with legacy 32-bit hash and upgrades deny-list`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val sms = SmsMessage(1L, sender, body, System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = sender,
                    amount = 100.0,
                    transactionType = "expense",
                    merchantName = "Swiggy",
                    originalMessage = body,
                    sourceSmsHash = currentHash,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
            // Legacy hash is recorded in deleted deny-list
            coEvery { deletedSmsHashDao.getAllHashes() } returns listOf(legacyHash)
            coEvery { deletedSmsHashDao.insert(any()) } just runs

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Must NOT re-create deleted transaction
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            // Must auto-heal deleted deny-list with new SHA-256 hash
            coVerify(exactly = 1) { deletedSmsHashDao.insert(match { it.smsHash == currentHash }) }
        }

    @Test
    fun `skips and upgrades when legacy hash matches in dynamic TOCTOU check`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val sms = SmsMessage(1L, sender, body, System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = sender,
                    amount = 100.0,
                    transactionType = "expense",
                    merchantName = "Swiggy",
                    originalMessage = body,
                    sourceSmsHash = currentHash,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
            coEvery { deletedSmsHashDao.getAllHashes() } returns emptyList()

            coEvery { transactionQueryDao.existsBySmsHash(currentHash) } returns false
            coEvery { transactionQueryDao.existsBySmsHash(legacyHash) } returns true
            coEvery { transactionWriteDao.updateSmsHashByLegacy(any(), any()) } just runs

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            coVerify(exactly = 1) { transactionWriteDao.updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash) }
        }

    @Test
    fun `skips duplicate when current hash exists in existingSmsHashes snapshot`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val sms = SmsMessage(1L, sender, body, System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val currentHash = SmsParser.computeSmsHash(sender, body)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = sender,
                    amount = 100.0,
                    transactionType = "expense",
                    merchantName = "Swiggy",
                    originalMessage = body,
                    sourceSmsHash = currentHash,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(listOf(currentHash))
            coEvery { deletedSmsHashDao.getAllHashes() } returns emptyList()

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            coVerify(exactly = 0) { transactionWriteDao.updateSmsHashByLegacy(any(), any()) }
        }

    @Test
    fun `skips deleted transaction when current hash exists in deleted deny-list`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val sms = SmsMessage(1L, sender, body, System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            val currentHash = SmsParser.computeSmsHash(sender, body)

            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = sender,
                    amount = 100.0,
                    transactionType = "expense",
                    merchantName = "Swiggy",
                    originalMessage = body,
                    sourceSmsHash = currentHash,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
            coEvery { deletedSmsHashDao.getAllHashes() } returns listOf(currentHash)

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            coVerify(exactly = 0) { deletedSmsHashDao.insert(any()) }
        }

    @Test
    fun `returns success immediately when READ_SMS permission is not granted`() =
        runTest {
            ShadowApplication.getInstance().denyPermissions(Manifest.permission.READ_SMS)

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { smsRepository.fetchAllSms(any(), any()) }
        }

    @Test
    fun `respects batch cap of at most 150 SMS messages per catch-up run`() =
        runTest {
            val messages =
                (1..200).map { i ->
                    SmsMessage(i.toLong(), "AM-HDFCBK", "Spent Rs.$i at Swiggy", System.currentTimeMillis() - i * 1000)
                }
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns messages
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery {
                SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns ParseResult.Ignored("Ignored")

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 150) {
                SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `pre-caches rule queries once before processing loop and does not repeat per SMS`() =
        runTest {
            val sms1 = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            val sms2 = SmsMessage(2L, "AM-HDFCBK", "Spent Rs.200 at Swiggy", System.currentTimeMillis() - 1000)
            val sms3 = SmsMessage(3L, "AM-HDFCBK", "Spent Rs.300 at Swiggy", System.currentTimeMillis() - 2000)
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms1, sms2, sms3)

            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery {
                SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns ParseResult.Ignored("Ignored")

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { customSmsRuleDao.getAllRules() }
            coVerify(exactly = 1) { merchantRenameRuleDao.getAllRulesList() }
            coVerify(exactly = 1) { ignoreRuleDao.getEnabledRules() }
            coVerify(exactly = 1) { merchantCategoryMappingDao.getAll() }
            coVerify(exactly = 1) { smsParseTemplateDao.getAllTemplates() }
        }

    @Test
    fun `exits processing loop immediately when isStopped is true`() =
        runTest {
            val sms1 = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            val sms2 = SmsMessage(2L, "AM-HDFCBK", "Spent Rs.200 at Swiggy", System.currentTimeMillis() - 1000)
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms1, sms2)

            val worker = spyk(TestListenableWorkerBuilder<SmsCatchupWorker>(context).build())
            every { worker.isStopped } returns true

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) {
                SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `exits processing loop after first SMS when isStopped becomes true`() =
        runTest {
            val sms1 = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            val sms2 = SmsMessage(2L, "AM-HDFCBK", "Spent Rs.200 at Swiggy", System.currentTimeMillis() - 1000)
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms1, sms2)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery {
                SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns ParseResult.Ignored("Ignored")

            val worker = spyk(TestListenableWorkerBuilder<SmsCatchupWorker>(context).build())
            every { worker.isStopped } returnsMany listOf(false, true)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) {
                SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `does not starve older unparsed SMS messages when newer messages are already processed`() =
        runTest {
            // 200 SMS messages: newest 150 (indices 1..150) are already saved in DB
            val messages =
                (1..200).map { i ->
                    SmsMessage(i.toLong(), "AM-HDFCBK", "Spent Rs.$i at Swiggy", System.currentTimeMillis() - i * 1000)
                }
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns messages

            val alreadyProcessedHashes =
                messages.take(150).map { sms ->
                    SmsParser.computeSmsHash(sms.sender, sms.body)
                }
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(alreadyProcessedHashes)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery {
                SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns ParseResult.Ignored("Ignored")

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // The 150 already processed messages are filtered out, so the remaining 50 older messages get processed
            coVerify(exactly = 50) {
                SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `bypasses ML classification when messages match known hashes in loop fast-path`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            val currentHash = SmsParser.computeSmsHash(sms.sender, sms.body)
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)

            // Simulate snapshot returning empty initially, but dynamic TOCTOU query finds it in DB
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())
            coEvery { transactionQueryDao.existsBySmsHash(currentHash) } returns true

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Should skip ML inference and rule parsing entirely
            verify(exactly = 0) { mockClassifier.classify(any()) }
            coVerify(exactly = 0) { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `closes classifier even if nerExtractor fails to instantiate`() =
        runTest {
            val sms = SmsMessage(1L, "AM-HDFCBK", "Spent Rs.100 at Swiggy", System.currentTimeMillis())
            coEvery { smsRepository.fetchAllSms(any(), any()) } returns listOf(sms)
            coEvery { transactionQueryDao.getAllSmsHashes() } returns flowOf(emptyList())

            every { MlModelFactory.getNerExtractor(any()) } throws RuntimeException("NER model loading failed")

            val worker = TestListenableWorkerBuilder<SmsCatchupWorker>(context).build()
            try {
                worker.doWork()
            } catch (e: RuntimeException) {
                assertEquals("NER model loading failed", e.message)
            }

            verify(exactly = 1) { mockClassifier.close() }
        }
}
