// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/SmsProcessorWorkerTest.kt
// REASON: Tests for SmsProcessorWorker, which runs the full SMS parsing
// pipeline (custom rules → ML classifier → NER → parser → save). Uses
// TestListenableWorkerBuilder to exercise the worker in isolation with zero-DAO
// repository injection via ServiceLocator.
// =================================================================================
package io.pm.finlight.workers

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import io.pm.finlight.Account
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsParseTemplateRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantMapping
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.ParseResult
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.SmsParser
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.core.NerEntity
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.utils.IWorkScheduler
import io.pm.finlight.utils.NotificationHelper
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsProcessorWorkerTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var mockTransactionRepo: ITransactionRepository
    private lateinit var mockRecurringRepo: IRecurringTransactionRepository
    private lateinit var mockMerchantRenameRepo: IMerchantRenameRuleRepository
    private lateinit var mockMerchantCategoryRepo: IMerchantCategoryMappingRepository
    private lateinit var mockCustomSmsRuleRepo: ICustomSmsRuleRepository
    private lateinit var mockIgnoreRuleRepo: IIgnoreRuleRepository
    private lateinit var mockSmsParseTemplateRepo: ISmsParseTemplateRepository
    private lateinit var mockMerchantMappingRepo: IMerchantMappingRepository
    private lateinit var mockAccountRepo: IAccountRepository
    private lateinit var mockTagRepo: ITagRepository
    private lateinit var mockSettingsRepo: ISettingsRepository
    private lateinit var mockWorkScheduler: IWorkScheduler
    private lateinit var mockClassifier: SmsClassifier
    private lateinit var mockNerExtractor: NerExtractor

    private fun buildWorker(
        sender: String,
        body: String,
        date: Long = 1L,
        runAttemptCount: Int = 0,
    ): SmsProcessorWorker {
        val inputData =
            workDataOf(
                SmsProcessorWorker.KEY_SENDER to sender,
                SmsProcessorWorker.KEY_BODY to body,
                SmsProcessorWorker.KEY_DATE to date,
            )
        return TestListenableWorkerBuilder<SmsProcessorWorker>(context, inputData)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    private fun createPotentialTransaction(
        sourceSmsId: Long = 1L,
        smsSender: String = "AM-HDFCBK",
        amount: Double = 100.0,
        transactionType: String = "expense",
        merchantName: String? = "Swiggy",
        originalMerchantName: String? = null,
        originalMessage: String = "Spent Rs.100 at Swiggy",
        sourceSmsHash: String = "defaultHash",
        detectedCurrencyCode: String? = null,
        needsReview: Boolean = false,
        suspicionReason: String? = null,
        categoryId: Int? = null,
    ): PotentialTransaction =
        PotentialTransaction(
            sourceSmsId = sourceSmsId,
            smsSender = smsSender,
            amount = amount,
            transactionType = transactionType,
            merchantName = merchantName,
            originalMerchantName = originalMerchantName,
            originalMessage = originalMessage,
            sourceSmsHash = sourceSmsHash,
            detectedCurrencyCode = detectedCurrencyCode,
            needsReview = needsReview,
            suspicionReason = suspicionReason,
            categoryId = categoryId,
        )

    private fun setupTravelMode(
        isEnabled: Boolean = true,
        tripName: String = "Trip",
        tripType: io.pm.finlight.TripType = io.pm.finlight.TripType.INTERNATIONAL,
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE,
        currencyCode: String = "USD",
        conversionRate: Float = 80f,
        homeCurrency: String? = null,
    ): io.pm.finlight.TravelModeSettings {
        val travelSettings =
            io.pm.finlight.TravelModeSettings(
                isEnabled = isEnabled,
                tripName = tripName,
                tripType = tripType,
                startDate = startDate,
                endDate = endDate,
                currencyCode = currencyCode,
                conversionRate = conversionRate,
            )
        every { mockSettingsRepo.getTravelModeSettings() } returns flowOf(travelSettings)
        coEvery { mockSettingsRepo.getCurrentTravelModeSettings() } returns travelSettings
        if (homeCurrency != null) {
            every { mockSettingsRepo.getHomeCurrency() } returns flowOf(homeCurrency)
        }
        return travelSettings
    }

    private fun createTestTransaction(
        id: Int = 10,
        description: String = "Netflix",
        amount: Double = 149.0,
        date: Long = 100L,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        recurringRuleId: Int? = null,
        accountId: Int = 1,
        categoryId: Int? = 1,
    ): Transaction =
        Transaction(
            id = id,
            description = description,
            amount = amount,
            transactionType = TransactionType.EXPENSE,
            date = date,
            accountId = accountId,
            categoryId = categoryId,
            notes = null,
            status = status,
            recurringRuleId = recurringRuleId,
        )

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

        mockTransactionRepo = mockk(relaxed = true)
        mockRecurringRepo = mockk(relaxed = true)
        mockMerchantRenameRepo = mockk(relaxed = true)
        mockMerchantCategoryRepo = mockk(relaxed = true)
        mockCustomSmsRuleRepo = mockk(relaxed = true)
        mockIgnoreRuleRepo = mockk(relaxed = true)
        mockSmsParseTemplateRepo = mockk(relaxed = true)
        mockMerchantMappingRepo = mockk(relaxed = true)
        mockAccountRepo = mockk(relaxed = true)
        mockTagRepo = mockk(relaxed = true)
        mockSettingsRepo = mockk(relaxed = true)
        mockWorkScheduler = mockk(relaxed = true)

        coEvery { mockTransactionRepo.existsBySmsHash(any()) } returns false
        coEvery { mockTransactionRepo.existsDeletedSmsHash(any()) } returns false
        coEvery { mockTransactionRepo.insertTransactionWithTags(any(), any()) } returns 1L
        coEvery { mockTransactionRepo.updateSmsHashByLegacy(any(), any()) } just runs
        coEvery { mockTransactionRepo.insertDeletedSmsHash(any()) } just runs
        coEvery { mockTransactionRepo.getTransactionSync(any()) } returns null
        coEvery { mockTransactionRepo.findRecentTransactionForMerge(any(), any(), any(), any(), any()) } returns null
        coEvery { mockTransactionRepo.updateRecurringRuleId(any(), any()) } just runs
        coEvery { mockTransactionRepo.delete(any()) } just runs
        coEvery { mockTransactionRepo.getPendingTransactions() } returns emptyList()

        coEvery { mockRecurringRepo.getRuleBySmsSenderId(any()) } returns null
        coEvery { mockRecurringRepo.updateLastRunDate(any(), any()) } just runs

        coEvery { mockMerchantMappingRepo.allMappings } returns flowOf(emptyList())
        coEvery { mockCustomSmsRuleRepo.getAllRules() } returns flowOf(emptyList())
        coEvery { mockIgnoreRuleRepo.getEnabledRules() } returns emptyList()
        coEvery { mockMerchantRenameRepo.getAllRules() } returns flowOf(emptyList())
        coEvery { mockMerchantRenameRepo.getAllRulesList() } returns emptyList()
        coEvery { mockMerchantRenameRepo.getAliasesAsMap() } returns flowOf(emptyMap())
        coEvery { mockMerchantRenameRepo.insert(any()) } just runs
        coEvery { mockMerchantCategoryRepo.getCategoryIdForMerchant(any()) } returns null
        coEvery { mockMerchantCategoryRepo.getAll() } returns emptyList()
        coEvery { mockMerchantCategoryRepo.getAllMappings() } returns flowOf(emptyList())
        coEvery { mockMerchantCategoryRepo.insert(any()) } just runs
        coEvery { mockSmsParseTemplateRepo.getAllTemplates() } returns emptyList()
        coEvery { mockSmsParseTemplateRepo.getTemplatesBySignature(any()) } returns emptyList()

        coEvery { mockAccountRepo.findAliasByName(any()) } returns null
        coEvery { mockAccountRepo.findByName(any()) } returns Account(1, "Test", "Bank Account")
        coEvery { mockAccountRepo.insert(any()) } returns 1L
        coEvery { mockAccountRepo.getAccountByIdSync(any()) } returns Account(1, "Test", "Bank Account")
        coEvery { mockTagRepo.findByName(any()) } returns null
        coEvery { mockTagRepo.insert(any()) } returns 1L

        every { mockSettingsRepo.getTravelModeSettings() } returns flowOf(null)
        coEvery { mockSettingsRepo.getCurrentTravelModeSettings() } returns null
        every { mockSettingsRepo.getHomeCurrency() } returns flowOf("INR")
        every { mockSettingsRepo.getAutoCaptureNotificationEnabled() } returns flowOf(false)

        every { mockWorkScheduler.scheduleTransactionNotification(any()) } just runs

        ServiceLocator.setTransactionRepository(mockTransactionRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)
        ServiceLocator.setMerchantRenameRuleRepository(mockMerchantRenameRepo)
        ServiceLocator.setMerchantCategoryMappingRepository(mockMerchantCategoryRepo)
        ServiceLocator.setCustomSmsRuleRepository(mockCustomSmsRuleRepo)
        ServiceLocator.setIgnoreRuleRepository(mockIgnoreRuleRepo)
        ServiceLocator.setSmsParseTemplateRepository(mockSmsParseTemplateRepo)
        ServiceLocator.setMerchantMappingRepository(mockMerchantMappingRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setTagRepository(mockTagRepo)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setWorkScheduler(mockWorkScheduler)

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
        every { NotificationHelper.showVariableBillAnomalyNotification(any(), any(), any(), any()) } just runs

        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

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
    fun `returns failure when date is missing or invalid`() =
        runTest {
            val missingDateData =
                workDataOf(
                    SmsProcessorWorker.KEY_SENDER to "AM-HDFCBK",
                    SmsProcessorWorker.KEY_BODY to "Spent Rs.100",
                )
            val workerMissing = TestListenableWorkerBuilder<SmsProcessorWorker>(context, missingDateData).build()
            assertEquals(ListenableWorker.Result.failure(), workerMissing.doWork())

            val invalidDateData =
                workDataOf(
                    SmsProcessorWorker.KEY_SENDER to "AM-HDFCBK",
                    SmsProcessorWorker.KEY_BODY to "Spent Rs.100",
                    SmsProcessorWorker.KEY_DATE to -1L,
                )
            val workerInvalid = TestListenableWorkerBuilder<SmsProcessorWorker>(context, invalidDateData).build()
            assertEquals(ListenableWorker.Result.failure(), workerInvalid.doWork())
        }

    @Test
    fun `ML classifier below threshold returns success without saving`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            every { mockClassifier.classify(any()) } returns 0.05f

            val worker = buildWorker("SPAM", "Win a prize!")
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `successful parse saves transaction and returns success`() =
        runTest {
            val txn = createPotentialTransaction(sourceSmsHash = "newhash")
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy")
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `already seen hash is skipped without saving`() =
        runTest {
            val existingHash = "existinghash"
            coEvery { mockTransactionRepo.existsBySmsHash(existingHash) } returns true

            val txn = createPotentialTransaction(sourceSmsHash = existingHash)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `skips duplicate when existsBySmsHash returns true in dynamic DB check`() =
        runTest {
            val hash = "dynamic_duplicate_hash"
            val txn = createPotentialTransaction(sourceSmsHash = hash)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { mockTransactionRepo.existsBySmsHash(hash) } returns true

            val result = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `ignored SMS returns success without saving`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Ignored("OTP rule")

            val result = buildWorker("PROMO", "Your OTP is 1234").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `custom rule match skips ML and saves directly`() =
        runTest {
            val txn =
                createPotentialTransaction(
                    smsSender = "CUSTOM",
                    amount = 200.0,
                    merchantName = "CustomMerchant",
                    originalMessage = "Custom rule body",
                    sourceSmsHash = "customhash",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("CUSTOM", "Custom rule body").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { mockClassifier.classify(any()) }
            coVerify(exactly = 1) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `auto-healing aliases are persisted on success`() =
        runTest {
            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    amount = 100.0,
                    merchantName = "Starbucks",
                    originalMessage = "Spent Rs.100 at Starbucks",
                    sourceSmsHash = "hash99",
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

            coVerify { mockMerchantRenameRepo.insert(MerchantRenameRule("STARBUCKS", "Starbucks")) }
            coVerify { mockMerchantCategoryRepo.insert(MerchantCategoryMapping("Starbucks", 4)) }
        }

    @Test
    fun `travel mode correctly applies foreign currency flag`() =
        runTest {
            setupTravelMode()

            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    merchantName = "Starbucks",
                    originalMessage = "Spent 100 USD at Starbucks",
                    sourceSmsHash = "hash101",
                    detectedCurrencyCode = "USD",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("AM-HDFCBK", "Spent 100 USD at Starbucks").doWork()

            val captor = slot<Transaction>()
            coVerify { mockTransactionRepo.insertTransactionWithTags(capture(captor), any()) }
            assertEquals("USD", captor.captured.currencyCode)
        }

    @Test
    fun `suspicious transaction triggers review notification instead of auto-capture`() =
        runTest {
            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    amount = 100000.0,
                    merchantName = "Fraud",
                    originalMessage = "Spent 100000 at Fraud",
                    sourceSmsHash = "hash102",
                    needsReview = true,
                    suspicionReason = "High amount",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("AM-HDFCBK", "Spent 100000 at Fraud").doWork()

            verify { NotificationHelper.showSuspiciousAmountNotification(any(), any(), eq("High amount")) }
        }

    @Test
    fun `auto-capture notification is enqueued when enabled`() =
        runTest {
            every { mockSettingsRepo.getAutoCaptureNotificationEnabled() } returns flowOf(true)
            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    amount = 10.0,
                    merchantName = "Coffee",
                    originalMessage = "Spent 10 at Coffee",
                    sourceSmsHash = "hash103",
                    needsReview = false,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 10 at Coffee").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 1) { mockWorkScheduler.scheduleTransactionNotification(any()) }
        }

    @Test
    fun `auto-link variable bill without anomaly`() =
        runTest {
            val rule = RecurringTransaction(id = 5, description = "Electricity", amount = 1000.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1)
            val savedTxn = Transaction(id = 10, description = "Electricity", amount = 1100.0, transactionType = TransactionType.EXPENSE, date = 100L, accountId = 1, categoryId = 1, notes = null)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockRecurringRepo.getRuleBySmsSenderId("BESCOM") } returns rule

            val txn = createPotentialTransaction(smsSender = "BESCOM", amount = 1100.0, merchantName = "Electricity", originalMessage = "Bill", sourceSmsHash = "hash200")
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("BESCOM", "Bill").doWork()

            coVerify { mockTransactionRepo.updateRecurringRuleId(10, 5) }
            coVerify { mockRecurringRepo.updateLastRunDate(5, 100L) }
            verify(exactly = 0) { NotificationHelper.showVariableBillAnomalyNotification(any(), any(), any(), any()) }
        }

    @Test
    fun `auto-link variable bill with anomaly triggers notification`() =
        runTest {
            val rule = RecurringTransaction(id = 5, description = "Electricity", amount = 1000.0, transactionType = TransactionType.EXPENSE, recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1)
            val savedTxn = Transaction(id = 10, description = "Electricity", amount = 1500.0, transactionType = TransactionType.EXPENSE, date = 100L, accountId = 1, categoryId = 1, notes = null)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockRecurringRepo.getRuleBySmsSenderId("BESCOM") } returns rule

            val txn = createPotentialTransaction(smsSender = "BESCOM", amount = 1500.0, merchantName = "Electricity", originalMessage = "Bill", sourceSmsHash = "hash201")
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("BESCOM", "Bill").doWork()

            coVerify { mockTransactionRepo.updateRecurringRuleId(10, 5) }
            coVerify { mockRecurringRepo.updateLastRunDate(5, 100L) }
            verify(exactly = 1) { NotificationHelper.showVariableBillAnomalyNotification(context, rule, 1500.0, 1000.0) }
        }

    @Test
    fun `auto-link fixed bill by finding matching pending draft`() =
        runTest {
            val savedTxn = createTestTransaction(id = 10, amount = 149.0, date = 100L)
            val draft = createTestTransaction(id = 20, amount = 149.0, date = 99L, status = TransactionStatus.PENDING, recurringRuleId = 7)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockRecurringRepo.getRuleBySmsSenderId(any()) } returns null
            coEvery { mockTransactionRepo.getPendingTransactions() } returns listOf(draft)

            val txn = createPotentialTransaction(smsSender = "NETFLIX", amount = 149.0, merchantName = "Netflix", originalMessage = "Sub", sourceSmsHash = "hash202")
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("NETFLIX", "Sub").doWork()

            coVerify { mockTransactionRepo.delete(draft) }
            coVerify { mockTransactionRepo.updateRecurringRuleId(10, 7) }
            coVerify { mockRecurringRepo.updateLastRunDate(7, 100L) }
        }

    @Test
    fun `auto-detects mergeable transaction and triggers notification`() =
        runTest {
            val savedTxn = Transaction(id = 10, description = "Amazon", amount = 100.0, transactionType = TransactionType.EXPENSE, date = 100L, accountId = 1, categoryId = 1, notes = null)
            val recentTxn = Transaction(id = 9, description = "Amazon", amount = 50.0, transactionType = TransactionType.EXPENSE, date = 50L, accountId = 1, categoryId = 1, notes = null)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockTransactionRepo.findRecentTransactionForMerge(any(), any(), any(), any(), any()) } returns recentTxn
            every { NotificationHelper.showMergeTransactionNotification(any(), any(), any()) } just runs

            val txn = createPotentialTransaction(smsSender = "AMZ", amount = 100.0, merchantName = "Amazon", originalMessage = "Bill", sourceSmsHash = "hash300")
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("AMZ", "Bill").doWork()

            verify(exactly = 1) { NotificationHelper.showMergeTransactionNotification(context, savedTxn, recentTxn) }
        }

    @Test
    fun `ner entities log outputs only entity type keys and no sensitive entity values`() =
        runTest {
            mockkStatic(Log::class)
            val logMessages = mutableListOf<String>()
            every { Log.d(any(), capture(logMessages)) } returns 0

            try {
                val nerEntities =
                    mapOf(
                        "AMOUNT" to NerEntity("99999.00", 0.95f),
                        "ACCOUNT" to NerEntity("XX9876", 0.90f),
                        "MERCHANT" to NerEntity("SecretMerchant", 0.85f),
                    )
                every { mockNerExtractor.extract(any()) } returns nerEntities
                coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
                coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Ignored("Ignored for test")

                buildWorker("AM-HDFCBK", "Sensitive SMS content 99999.00 XX9876 SecretMerchant").doWork()

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
    fun `skips and upgrades when legacy hash exists in transaction repository`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)

            val txn = createPotentialTransaction(smsSender = sender, originalMessage = body, sourceSmsHash = currentHash)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            // Legacy hash exists in DB
            coEvery { mockTransactionRepo.existsBySmsHash(legacyHash) } returns true

            val result = buildWorker(sender, body).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
            coVerify(exactly = 1) { mockTransactionRepo.updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash) }
        }

    @Test
    fun `skips and auto-heals deleted record when legacy hash is in deleted deny-list`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)

            val txn = createPotentialTransaction(smsSender = sender, originalMessage = body, sourceSmsHash = currentHash)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { mockTransactionRepo.existsDeletedSmsHash(legacyHash) } returns true

            val result = buildWorker(sender, body).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
            coVerify(exactly = 1) { mockTransactionRepo.insertDeletedSmsHash(currentHash) }
        }

    @Test
    fun `skips duplicate when current hash exists in transaction repository`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)

            val txn = createPotentialTransaction(smsSender = sender, originalMessage = body, sourceSmsHash = currentHash)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { mockTransactionRepo.existsBySmsHash(currentHash) } returns true

            val result = buildWorker(sender, body).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
            coVerify(exactly = 0) { mockTransactionRepo.updateSmsHashByLegacy(any(), any()) }
        }

    @Test
    fun `skips duplicate when current hash is in deleted deny-list`() =
        runTest {
            val sender = "AM-HDFCBK"
            val body = "Spent Rs.100 at Swiggy"
            val currentHash = SmsParser.computeSmsHash(sender, body)

            val txn = createPotentialTransaction(smsSender = sender, originalMessage = body, sourceSmsHash = currentHash)
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            coEvery { SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            coEvery { mockTransactionRepo.existsDeletedSmsHash(currentHash) } returns true

            val result = buildWorker(sender, body).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
            coVerify(exactly = 0) { mockTransactionRepo.insertDeletedSmsHash(any()) }
        }

    @Test
    fun `worker returns failure immediately when runAttemptCount is 3 or more`() =
        runTest {
            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 3)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 0) { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `unhandled exception on attempt 1 returns retry`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } throws RuntimeException("Database error")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 0)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `unhandled exception on attempt 2 returns retry`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } throws RuntimeException("TFLite error")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 1)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `unhandled exception on attempt 3 returns failure`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } throws RuntimeException("Poison pill SMS error")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 2)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `cancellation exception is rethrown and not caught by generic handler`() =
        runTest {
            coEvery {
                SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any())
            } throws CancellationException("Coroutine cancelled")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 0)

            assertFailsWith<CancellationException> {
                worker.doWork()
            }
        }

    @Test
    fun `OutOfMemoryError during processing returns failure immediately without retry`() =
        runTest {
            coEvery {
                SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any())
            } throws OutOfMemoryError("Native buffer allocation failed")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 0)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `exception during classifier inference closes classifier and returns retry`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            every { mockClassifier.classify(any()) } throws IllegalStateException("TFLite model crashed")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 0)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(atLeast = 1) { mockClassifier.close() }
        }

    @Test
    fun `exception during nerExtractor extraction closes nerExtractor and returns retry`() =
        runTest {
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns null
            every { mockClassifier.classify(any()) } returns 0.9f
            every { mockNerExtractor.extract(any()) } throws RuntimeException("NER tokenization crash")

            val worker = buildWorker("AM-HDFCBK", "Spent Rs.100 at Swiggy", runAttemptCount = 0)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(atLeast = 1) { mockClassifier.close() }
            verify(atLeast = 1) { mockNerExtractor.close() }
        }

    @Test
    fun `travel mode with home currency saves non-foreign transaction`() =
        runTest {
            setupTravelMode(homeCurrency = "INR")

            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    merchantName = "Starbucks",
                    originalMessage = "Spent 100 INR at Starbucks",
                    sourceSmsHash = "hashHomeCurrency",
                    detectedCurrencyCode = "INR",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 100 INR at Starbucks").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val captor = slot<Transaction>()
            coVerify { mockTransactionRepo.insertTransactionWithTags(capture(captor), any()) }
            assertNull(captor.captured.currencyCode)
            assertEquals(100.0, captor.captured.amount, 0.001)
        }

    @Test
    fun `travel mode with unhandled currency shows notification and returns success without saving`() =
        runTest {
            val travelSettings = setupTravelMode(homeCurrency = "INR")

            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    merchantName = "Starbucks",
                    originalMessage = "Spent 100 EUR at Starbucks",
                    sourceSmsHash = "hashEUR",
                    detectedCurrencyCode = "EUR",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 100 EUR at Starbucks").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { mockTransactionRepo.insertTransactionWithTags(any(), any()) }
            verify { NotificationHelper.showTravelModeSmsNotification(context, txn, travelSettings) }
        }

    @Test
    fun `auto-link fixed bill with null recurringRuleId does not update rule`() =
        runTest {
            val savedTxn = createTestTransaction(id = 10, amount = 149.0, date = 100L)
            val draftWithoutRule = createTestTransaction(id = 20, amount = 149.0, date = 99L, status = TransactionStatus.PENDING, recurringRuleId = null)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockRecurringRepo.getRuleBySmsSenderId(any()) } returns null
            coEvery { mockTransactionRepo.getPendingTransactions() } returns listOf(draftWithoutRule)

            val txn =
                createPotentialTransaction(
                    smsSender = "NETFLIX",
                    amount = 149.0,
                    merchantName = "Netflix",
                    originalMessage = "Sub",
                    sourceSmsHash = "hash202NullRule",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("NETFLIX", "Sub").doWork()

            coVerify { mockTransactionRepo.delete(draftWithoutRule) }
            coVerify(exactly = 0) { mockTransactionRepo.updateRecurringRuleId(any(), any()) }
            coVerify(exactly = 0) { mockRecurringRepo.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `pending draft does not match when amount difference is one or greater`() =
        runTest {
            val savedTxn = createTestTransaction(id = 10, amount = 149.0, date = 100L)
            val draft = createTestTransaction(id = 20, amount = 151.0, date = 100L, status = TransactionStatus.PENDING, recurringRuleId = 7)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockRecurringRepo.getRuleBySmsSenderId(any()) } returns null
            coEvery { mockTransactionRepo.getPendingTransactions() } returns listOf(draft)

            val txn =
                createPotentialTransaction(
                    smsSender = "NETFLIX",
                    amount = 149.0,
                    merchantName = "Netflix",
                    originalMessage = "Sub",
                    sourceSmsHash = "hash202AmountMismatch",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("NETFLIX", "Sub").doWork()

            coVerify(exactly = 0) { mockTransactionRepo.delete(any()) }
            coVerify(exactly = 0) { mockTransactionRepo.updateRecurringRuleId(any(), any()) }
            coVerify(exactly = 0) { mockRecurringRepo.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `pending draft does not match when date difference is four days or greater`() =
        runTest {
            val fourDaysMs = 4 * 24 * 60 * 60 * 1000L
            val savedTxn = createTestTransaction(id = 10, amount = 149.0, date = fourDaysMs + 1000L)
            val draft = createTestTransaction(id = 20, amount = 149.0, date = 0L, status = TransactionStatus.PENDING, recurringRuleId = 7)

            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns savedTxn
            coEvery { mockRecurringRepo.getRuleBySmsSenderId(any()) } returns null
            coEvery { mockTransactionRepo.getPendingTransactions() } returns listOf(draft)

            val txn =
                createPotentialTransaction(
                    smsSender = "NETFLIX",
                    amount = 149.0,
                    merchantName = "Netflix",
                    originalMessage = "Sub",
                    sourceSmsHash = "hash202DateMismatch",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            buildWorker("NETFLIX", "Sub").doWork()

            coVerify(exactly = 0) { mockTransactionRepo.delete(any()) }
            coVerify(exactly = 0) { mockTransactionRepo.updateRecurringRuleId(any(), any()) }
            coVerify(exactly = 0) { mockRecurringRepo.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `post notifications permission denied prevents notification dispatch`() =
        runTest {
            Shadows.shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    amount = 50000.0,
                    merchantName = "Jeweler",
                    originalMessage = "Spent 50000 at Jeweler",
                    sourceSmsHash = "hashPermissionDenied",
                    needsReview = true,
                    suspicionReason = "Very high amount",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 50000 at Jeweler").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showSuspiciousAmountNotification(any(), any(), any()) }
        }

    @Test
    fun `suspicious transaction handles null fields safely`() =
        runTest {
            val txn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "AM-HDFCBK",
                    amount = 1000.0,
                    transactionType = "invalid_type",
                    merchantName = null,
                    originalMerchantName = null,
                    originalMessage = "Spent 1000",
                    sourceSmsHash = "hashNullFields",
                    needsReview = true,
                    suspicionReason = null,
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 1000").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val captor = slot<Transaction>()
            verify {
                NotificationHelper.showSuspiciousAmountNotification(
                    context,
                    capture(captor),
                    "Amount flagged for review.",
                )
            }
            assertEquals("Unknown Merchant", captor.captured.description)
            assertNull(captor.captured.originalDescription)
            assertEquals(TransactionType.EXPENSE, captor.captured.transactionType)
        }

    @Test
    fun `suspicious transaction uses savedTxn from repository if available`() =
        runTest {
            val dbTxn =
                Transaction(
                    id = 42,
                    description = "Jeweler",
                    originalDescription = "Jeweler",
                    amount = 50000.0,
                    date = 1000L,
                    accountId = 99,
                    categoryId = 5,
                    notes = "Real DB Txn",
                    transactionType = TransactionType.EXPENSE,
                )
            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns dbTxn

            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    amount = 50000.0,
                    merchantName = "Jeweler",
                    originalMessage = "Spent 50000 at Jeweler",
                    sourceSmsHash = "hashSuspiciousWithDbTxn",
                    needsReview = true,
                    suspicionReason = "High amount",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 50000 at Jeweler").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val captor = slot<Transaction>()
            verify {
                NotificationHelper.showSuspiciousAmountNotification(
                    context,
                    capture(captor),
                    "High amount",
                )
            }
            assertEquals(99, captor.captured.accountId)
            assertEquals(42, captor.captured.id)
        }

    @Test
    fun `null savedTxn skips merge check and recurring linking`() =
        runTest {
            coEvery { mockTransactionRepo.getTransactionSync(any()) } returns null

            val txn =
                createPotentialTransaction(
                    smsSender = "AM-HDFCBK",
                    amount = 50.0,
                    merchantName = "Tea",
                    originalMessage = "Spent 50",
                    sourceSmsHash = "hashNullSavedTxn",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("AM-HDFCBK", "Spent 50").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { NotificationHelper.showMergeTransactionNotification(any(), any(), any()) }
            coVerify(exactly = 0) { mockRecurringRepo.getRuleBySmsSenderId(any()) }
        }

    @Test
    fun `resolves MerchantMappingRepository from ServiceLocator`() =
        runTest {
            val mockMappingRepo = mockk<IMerchantMappingRepository>(relaxed = true)
            every { mockMappingRepo.allMappings } returns
                flowOf(
                    listOf(MerchantMapping(smsSender = "CUSTOM_SENDER", merchantName = "CustomMerchant")),
                )
            ServiceLocator.setMerchantMappingRepository(mockMappingRepo)

            val txn =
                createPotentialTransaction(
                    smsSender = "CUSTOM_SENDER",
                    amount = 50.0,
                    merchantName = "Tea",
                    originalMessage = "Spent 50",
                    sourceSmsHash = "hashCustomSender",
                )
            coEvery { SmsParser.parseWithOnlyCustomRules(any(), any(), any(), any(), any()) } returns ParseResult.Success(txn)

            val result = buildWorker("CUSTOM_SENDER", "Spent 50").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify { mockMappingRepo.allMappings }
        }
}
