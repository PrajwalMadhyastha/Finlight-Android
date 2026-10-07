package io.pm.finlight.domain.usecase

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.pm.finlight.Account
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.MerchantMapping
import io.pm.finlight.PotentialAccount
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.SmsParser
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AutoSaveSmsTransactionUseCaseTest : BaseViewModelTest() {
    private val transactionRepository: ITransactionRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val smsRepository: ISmsRepository = mockk(relaxed = true)
    private val merchantMappingRepository: IMerchantMappingRepository = mockk(relaxed = true)
    private val deletedSmsHashDao: DeletedSmsHashDao = mockk(relaxed = true)
    private val db: AppDatabase = mockk(relaxed = true)
    private val resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase = mockk(relaxed = true)
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var useCase: AutoSaveSmsTransactionUseCase

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        every { db.deletedSmsHashDao() } returns deletedSmsHashDao
        every { merchantMappingRepository.allMappings } returns flowOf(emptyList())
        coEvery { transactionRepository.existsBySmsHash(any()) } returns false
        coEvery { deletedSmsHashDao.existsByHash(any()) } returns false
        coEvery { accountRepository.findOrCreateByName(any(), any<String>()) } returns Account(1, "Test Account", "General")
        coEvery { transactionRepository.insertTransactionWithTags(any(), any()) } returns 1L
        coEvery { resolveTravelModeTagUseCase.getFinalTags(any(), any(), any()) } returns emptySet()

        useCase =
            AutoSaveSmsTransactionUseCase(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                smsRepository = smsRepository,
                merchantMappingRepository = merchantMappingRepository,
                db = db,
                deletedSmsHashDao = deletedSmsHashDao,
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
                dispatcherProvider = testDispatcherProvider,
            )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ServiceLocator.reset()
    }

    private fun createBaseTransaction(
        amount: Double = 100.0,
        merchantName: String? = "Swiggy",
        originalMerchantName: String? = "SWIGGY-RAW",
        sourceSmsHash: String? = "hash_123",
        accountName: String? = "HDFC Bank - 1234",
        accountType: String? = "Bank Account",
        sender: String = "AM-HDFCBK",
        body: String = "Spent Rs.100 at Swiggy",
    ): PotentialTransaction {
        return PotentialTransaction(
            sourceSmsId = 1L,
            smsSender = sender,
            amount = amount,
            transactionType = "expense",
            merchantName = merchantName,
            originalMessage = body,
            potentialAccount = accountName?.let { PotentialAccount(formattedName = it, accountType = accountType ?: "General") },
            sourceSmsHash = sourceSmsHash,
            categoryId = 5,
            originalMerchantName = originalMerchantName,
        )
    }

    @Test
    fun `invoke saves valid transaction and returns true`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction()
            val txnSlot = slot<Transaction>()
            coEvery { transactionRepository.insertTransactionWithTags(capture(txnSlot), any()) } returns 42L

            val result = useCase(potentialTxn, source = "Auto-Captured")

            assertTrue(result)
            assertEquals("Swiggy", txnSlot.captured.description)
            assertEquals("SWIGGY-RAW", txnSlot.captured.originalDescription)
            assertEquals(100.0, txnSlot.captured.amount, 0.001)
            assertEquals(1, txnSlot.captured.accountId)
            assertEquals(5, txnSlot.captured.categoryId)
            assertEquals("Auto-Captured", txnSlot.captured.source)
            assertEquals(TransactionType.EXPENSE, txnSlot.captured.transactionType)
        }

    @Test
    fun `invoke drops invalid amount values and returns false`() =
        runTest(testDispatcher) {
            assertFalse(useCase(createBaseTransaction(amount = 0.0)))
            assertFalse(useCase(createBaseTransaction(amount = -10.0)))
            assertFalse(useCase(createBaseTransaction(amount = Double.NaN)))
            assertFalse(useCase(createBaseTransaction(amount = Double.POSITIVE_INFINITY)))
            assertFalse(useCase(createBaseTransaction(amount = Double.NEGATIVE_INFINITY)))

            coVerify(exactly = 0) { transactionRepository.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `invoke suppresses duplicate when current sourceSmsHash exists in transactionRepository`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(sourceSmsHash = "existing_hash")
            coEvery { transactionRepository.existsBySmsHash("existing_hash") } returns true

            val result = useCase(potentialTxn)

            assertFalse(result)
            coVerify(exactly = 0) { transactionRepository.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `invoke suppresses duplicate and upgrades hash when legacy hash exists in transactionRepository`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(sender = "AM-HDFCBK", body = "Spent Rs.100 at Swiggy", sourceSmsHash = "new_v2_hash")
            val legacyHash = SmsParser.computeLegacySmsHash("AM-HDFCBK", "Spent Rs.100 at Swiggy")
            coEvery { transactionRepository.existsBySmsHash("new_v2_hash") } returns false
            coEvery { transactionRepository.existsBySmsHash(legacyHash) } returns true

            val result = useCase(potentialTxn)

            assertFalse(result)
            coVerify(exactly = 1) { transactionRepository.updateSmsHashByLegacy(oldHash = legacyHash, newHash = "new_v2_hash") }
            coVerify(exactly = 0) { transactionRepository.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `invoke suppresses duplicate when current hash exists in deletedSmsHashDao`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(sourceSmsHash = "deleted_hash")
            coEvery { deletedSmsHashDao.existsByHash("deleted_hash") } returns true

            val result = useCase(potentialTxn)

            assertFalse(result)
            coVerify(exactly = 0) { transactionRepository.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `invoke suppresses duplicate and upgrades hash when legacy hash exists in deletedSmsHashDao`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(sender = "AM-HDFCBK", body = "Spent Rs.100 at Swiggy", sourceSmsHash = "new_hash")
            val legacyHash = SmsParser.computeLegacySmsHash("AM-HDFCBK", "Spent Rs.100 at Swiggy")
            coEvery { deletedSmsHashDao.existsByHash("new_hash") } returns false
            coEvery { deletedSmsHashDao.existsByHash(legacyHash) } returns true

            val result = useCase(potentialTxn)

            assertFalse(result)
            val insertedSlot = slot<DeletedSmsHash>()
            coVerify(exactly = 1) { deletedSmsHashDao.insert(capture(insertedSlot)) }
            assertEquals("new_hash", insertedSlot.captured.smsHash)
            coVerify(exactly = 0) { transactionRepository.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `invoke delegates existing account resolution to accountRepository`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(accountName = "ICICI Bank - 4321", accountType = "Savings")
            val existingAccount = Account(id = 88, name = "ICICI Bank - 4321", type = "Savings")
            coEvery { accountRepository.findOrCreateByName("ICICI Bank - 4321", "Savings") } returns existingAccount

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepository.insertTransactionWithTags(capture(txnSlot), any()) } returns 10L

            val result = useCase(potentialTxn)

            assertTrue(result)
            assertEquals(88, txnSlot.captured.accountId)
            coVerify(exactly = 1) { accountRepository.findOrCreateByName("ICICI Bank - 4321", "Savings") }
        }

    @Test
    fun `invoke delegates new account creation to accountRepository`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(accountName = "Brand New Bank", accountType = "Current")
            val newCreatedAccount = Account(id = 99, name = "Brand New Bank", type = "Current")
            coEvery { accountRepository.findOrCreateByName("Brand New Bank", "Current") } returns newCreatedAccount

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepository.insertTransactionWithTags(capture(txnSlot), any()) } returns 12L

            val result = useCase(potentialTxn)

            assertTrue(result)
            assertEquals(99, txnSlot.captured.accountId)
            coVerify(exactly = 1) { accountRepository.findOrCreateByName("Brand New Bank", "Current") }
        }

    @Test
    fun `invoke returns false when accountRepository throws exception`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction()
            coEvery { accountRepository.findOrCreateByName(any(), any<String>()) } throws RuntimeException("DB locked")

            val result = useCase(potentialTxn)

            assertFalse(result)
            coVerify(exactly = 0) { transactionRepository.insertTransactionWithTags(any(), any()) }
        }

    @Test
    fun `invoke resolves merchant name from merchantMappingRepository when merchantName is null`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(merchantName = null, originalMerchantName = null, sender = "VK-AMAZON")
            val mappings =
                listOf(
                    MerchantMapping(smsSender = "VK-AMAZON", merchantName = "Amazon India"),
                    MerchantMapping(smsSender = "AM-FLIPKT", merchantName = "Flipkart"),
                )
            every { merchantMappingRepository.allMappings } returns flowOf(mappings)

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepository.insertTransactionWithTags(capture(txnSlot), any()) } returns 15L

            val result = useCase(potentialTxn)

            assertTrue(result)
            assertEquals("Amazon India", txnSlot.captured.description)
            assertEquals("Amazon India", txnSlot.captured.originalDescription)
        }

    @Test
    fun `invoke resolves merchant name from merchantMappingRepository when merchantName is blank or Unknown Merchant`() =
        runTest(testDispatcher) {
            val potentialTxnBlank = createBaseTransaction(merchantName = "   ", originalMerchantName = null, sender = "AM-ZOMATO")
            val potentialTxnUnknown = createBaseTransaction(merchantName = "Unknown Merchant", originalMerchantName = null, sender = "AM-ZOMATO")
            val mappings = listOf(MerchantMapping(smsSender = "AM-ZOMATO", merchantName = "Zomato"))
            every { merchantMappingRepository.allMappings } returns flowOf(mappings)

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepository.insertTransactionWithTags(capture(txnSlot), any()) } returns 16L

            val resultBlank = useCase(potentialTxnBlank)
            assertTrue(resultBlank)
            assertEquals("Zomato", txnSlot.captured.description)

            val resultUnknown = useCase(potentialTxnUnknown)
            assertTrue(resultUnknown)
            assertEquals("Zomato", txnSlot.captured.description)
        }

    @Test
    fun `invoke falls back to Unknown Merchant when no mapping exists for sender`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction(merchantName = null, originalMerchantName = null, sender = "UNKNOWN-SENDER")
            every { merchantMappingRepository.allMappings } returns flowOf(emptyList())

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepository.insertTransactionWithTags(capture(txnSlot), any()) } returns 17L

            val result = useCase(potentialTxn)

            assertTrue(result)
            assertEquals("Unknown Merchant", txnSlot.captured.description)
            assertEquals("Unknown Merchant", txnSlot.captured.originalDescription)
        }

    @Test
    fun `invoke attaches tags from resolveTravelModeTagUseCase`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction()
            val expectedTags = setOf(Tag(id = 1, name = "Vacation"))
            coEvery { resolveTravelModeTagUseCase.getFinalTags(any(), any(), any()) } returns expectedTags

            val tagsSlot = slot<Set<Tag>>()
            coEvery { transactionRepository.insertTransactionWithTags(any(), capture(tagsSlot)) } returns 18L

            val result = useCase(potentialTxn)

            assertTrue(result)
            assertEquals(expectedTags, tagsSlot.captured)
        }

    @Test
    fun `invoke returns false when insertTransactionWithTags returns non-positive id`() =
        runTest(testDispatcher) {
            val potentialTxn = createBaseTransaction()
            coEvery { transactionRepository.insertTransactionWithTags(any(), any()) } returns -1L

            val result = useCase(potentialTxn)

            assertFalse(result)
        }

    @Test
    fun `secondary constructor instantiates successfully with context`() {
        val mockDb: AppDatabase = mockk(relaxed = true)
        val mockTagRepository: ITagRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(transactionRepository)
        ServiceLocator.setAccountRepository(accountRepository)
        ServiceLocator.setSmsRepository(smsRepository)
        ServiceLocator.setMerchantMappingRepository(merchantMappingRepository)
        ServiceLocator.setTagRepository(mockTagRepository)
        ServiceLocator.setDispatcherProvider(testDispatcherProvider)

        val constructedUseCase =
            AutoSaveSmsTransactionUseCase(
                context = ApplicationProvider.getApplicationContext(),
                resolvedDb = mockDb,
            )
        assertNotNull(constructedUseCase)
    }

    @Test
    fun `secondary constructor instantiates successfully with four repositories`() {
        val constructedUseCase =
            AutoSaveSmsTransactionUseCase(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                smsRepository = smsRepository,
                merchantMappingRepository = merchantMappingRepository,
            )
        assertNotNull(constructedUseCase)
    }
}
