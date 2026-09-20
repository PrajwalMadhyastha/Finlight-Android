// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/utils/SmsTransactionSaverTest.kt
// REASON: NEW — Unit tests for SmsTransactionSaver, the shared helper that
// contains the Bug #1 fix (OnConflictStrategy.IGNORE returns -1 → fallback to
// findByName). Tests verify every branch of account resolution.
// =================================================================================
package io.pm.finlight.utils

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsTransactionSaverTest : BaseViewModelTest() {
    private lateinit var db: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var transactionWriteDao: TransactionWriteDao
    private lateinit var transactionQueryDao: TransactionQueryDao
    private lateinit var tagDao: TagDao
    private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    private lateinit var saver: SmsTransactionSaver

    @Before
    override fun setup() {
        super.setup()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        db = mockk(relaxed = true)
        accountDao = mockk(relaxed = true)
        accountAliasDao = mockk(relaxed = true)
        transactionWriteDao =
            mockk(relaxed = true) {
                coEvery { insert(any()) } returns 1L
                coEvery { addTagsToTransaction(any()) } just runs
            }
        tagDao = mockk(relaxed = true)
        merchantRenameRuleDao = mockk(relaxed = true)
        merchantCategoryMappingDao = mockk(relaxed = true)

        transactionQueryDao = mockk(relaxed = true)
        coEvery { transactionQueryDao.existsBySmsHash(any()) } returns false

        every { db.accountDao() } returns accountDao
        every { db.accountAliasDao() } returns accountAliasDao
        every { db.transactionWriteDao() } returns transactionWriteDao
        every { db.transactionQueryDao() } returns transactionQueryDao
        every { db.tagDao() } returns tagDao
        every { db.merchantRenameRuleDao() } returns merchantRenameRuleDao
        every { db.merchantCategoryMappingDao() } returns merchantCategoryMappingDao

        val mockSettingsRepo = mockk<ISettingsRepository>()
        every { mockSettingsRepo.getTravelModeSettings() } returns flowOf(null)
        coEvery { mockSettingsRepo.getCurrentTravelModeSettings() } returns null
        every { mockSettingsRepo.getHomeCurrency() } returns flowOf("INR")

        val tagRepository = TagRepository(tagDao, db.transactionQueryDao())
        val resolveTravelModeTagUseCase = io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase(tagRepository)
        saver = SmsTransactionSaver(db, resolveTravelModeTagUseCase)
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    private fun makeTxn(account: String? = "HDFC Bank - X1234") =
        PotentialTransaction(
            sourceSmsId = 1L,
            smsSender = "AM-HDFCBK",
            amount = 100.0,
            transactionType = "expense",
            merchantName = "Swiggy",
            originalMessage = "Spent Rs.100 at Swiggy",
            sourceSmsHash = "testhash",
            potentialAccount = account?.let { PotentialAccount(it, "Bank Account") },
        )

    // -------------------------------------------------------------------------
    // Account alias path
    // -------------------------------------------------------------------------

    @Test
    fun `saves using account alias when one exists`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns AccountAlias("HDFC Bank - X1234", 42)

            val id = saver.resolveAndSaveTransaction(makeTxn())

            assertNotNull(id)
            coVerify { transactionWriteDao.insert(match { it.accountId == 42 }) }
        }

    // -------------------------------------------------------------------------
    // Account lookup path (Bug #1 — the core fix)
    // -------------------------------------------------------------------------

    @Test
    fun `saves using existing account when found by name`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(7, "HDFC Bank - X1234", "Bank Account")

            val id = saver.resolveAndSaveTransaction(makeTxn())

            assertNotNull(id)
            coVerify { transactionWriteDao.insert(match { it.accountId == 7 }) }
            // No insert should happen since account already existed
            coVerify(exactly = 0) { accountDao.insert(any()) }
        }

    @Test
    fun `creates new account when none exists`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns null
            coEvery { accountDao.insert(any()) } returns 15L
            coEvery { accountDao.getAccountByIdSync(15) } returns Account(15, "HDFC Bank - X1234", "Bank Account")

            val id = saver.resolveAndSaveTransaction(makeTxn())

            assertNotNull(id)
            coVerify { accountDao.insert(any()) }
            coVerify { transactionWriteDao.insert(match { it.accountId == 15 }) }
        }

    @Test
    fun `BUG FIX - IGNORE conflict returns -1 falls back to findByName instead of returning null`() =
        runTest {
            // This is the critical Bug #1 regression test.
            // Scenario: insert returns -1 (IGNORE conflict), and findByName finds the existing account.
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returnsMany
                listOf(
                    // first call: account doesn't exist yet
                    null,
                    // second call: fallback after IGNORE
                    Account(22, "HDFC Bank - X1234", "Bank Account")
                )
            // IGNORE conflict
            coEvery { accountDao.insert(any()) } returns -1L

            val id = saver.resolveAndSaveTransaction(makeTxn())

            // Transaction must still be saved — NOT dropped
            assertNotNull(id)
            coVerify(exactly = 2) { accountDao.findByName(any()) } // first check + fallback
            coVerify { transactionWriteDao.insert(match { it.accountId == 22 }) }
        }

    @Test
    fun `returns null when account cannot be resolved after all fallbacks`() =
        runTest {
            // Worst case: insert fails AND findByName returns null (shouldn't happen in practice)
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns null
            coEvery { accountDao.insert(any()) } returns -1L
            // After IGNORE, findByName is called again — still returns null

            val id = saver.resolveAndSaveTransaction(makeTxn())

            assertNull(id)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `falls back to Unknown Account when potentialAccount is null`() =
        runTest {
            coEvery { accountAliasDao.findByAlias("Unknown Account") } returns null
            coEvery { accountDao.findByName("Unknown Account") } returns null
            coEvery { accountDao.insert(any()) } returns 5L
            coEvery { accountDao.getAccountByIdSync(5) } returns Account(5, "Unknown Account", "General")

            val id = saver.resolveAndSaveTransaction(makeTxn(account = null))

            assertNotNull(id)
            coVerify { accountDao.insert(match { it.name == "Unknown Account" }) }
        }

    // -------------------------------------------------------------------------
    // Transaction content
    // -------------------------------------------------------------------------

    @Test
    fun `saved transaction has correct amount and merchant`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 99L

            saver.resolveAndSaveTransaction(makeTxn())

            assert(captor.captured.amount == 100.0)
            assert(captor.captured.description == "Swiggy")
            assert(captor.captured.sourceSmsHash == "testhash")
            assert(captor.captured.source == "Auto-Captured")
        }

    @Test
    fun `foreign transaction applies currency conversion`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            val travelSettings =
                TravelModeSettings(
                    isEnabled = true, tripName = "US Trip", tripType = TripType.INTERNATIONAL,
                    startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = "USD", conversionRate = 80f
                )
            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 55L

            val potentialTxn =
                makeTxn().copy(
                    merchantName = "Coffee",
                    originalMerchantName = "STARBUCKS"
                )

            saver.resolveAndSaveTransaction(potentialTxn, isForeign = true, travelSettings = travelSettings)

            assert(captor.captured.amount == 8000.0) { "Expected 100 * 80 = 8000" }
            assert(captor.captured.originalAmount == 100.0)
            assert(captor.captured.currencyCode == "USD")
            assert(captor.captured.description == "Coffee")
            assert(captor.captured.originalDescription == "STARBUCKS")
        }

    @Test
    fun `custom source label is stamped on transaction`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 1L

            saver.resolveAndSaveTransaction(makeTxn(), source = "Auto-Recovered")

            assert(captor.captured.source == "Auto-Recovered")
        }

    @Test
    fun `originalDescription uses originalMerchantName when present`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 99L

            val potentialTxnWithRename =
                makeTxn().copy(
                    merchantName = "Coffee",
                    originalMerchantName = "STARBUCKS"
                )

            saver.resolveAndSaveTransaction(potentialTxnWithRename)

            assert(captor.captured.description == "Coffee")
            assert(captor.captured.originalDescription == "STARBUCKS")
            assert(captor.captured.transactionType == TransactionType.EXPENSE)
        }

    @Test
    fun `saves income transaction with TransactionType INCOME`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 101L

            val incomeTxn = makeTxn().copy(transactionType = "income", merchantName = "Salary")
            saver.resolveAndSaveTransaction(incomeTxn)

            assert(captor.captured.transactionType == TransactionType.INCOME)
            assert(captor.captured.amount == 100.0)
        }

    @Test
    fun `resolveAndSaveTransaction returns null when sourceSmsHash already exists in database`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            coEvery { transactionQueryDao.existsBySmsHash("testhash") } returns true

            val id = saver.resolveAndSaveTransaction(makeTxn())

            assertNull(id)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `resolveAndSaveTransaction returns null when insertTransactionWithTags returns -1L`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            coEvery { transactionQueryDao.existsBySmsHash(any()) } returns false
            coEvery { transactionWriteDao.insert(any()) } returns -1L

            val id = saver.resolveAndSaveTransaction(makeTxn())

            assertNull(id)
        }

    @Test
    fun `resolveAndSaveTransaction returns null and upgrades when legacy hash exists in database`() =
        runTest {
            val potentialTxn = makeTxn()
            val legacyHash = SmsParser.computeLegacySmsHash(potentialTxn.smsSender, potentialTxn.originalMessage)

            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            coEvery { transactionQueryDao.existsBySmsHash(potentialTxn.sourceSmsHash!!) } returns false
            coEvery { transactionQueryDao.existsBySmsHash(legacyHash) } returns true
            coEvery { transactionWriteDao.updateSmsHashByLegacy(any(), any()) } just runs

            val id = saver.resolveAndSaveTransaction(potentialTxn)

            assertNull(id)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            coVerify(exactly = 1) {
                transactionWriteDao.updateSmsHashByLegacy(
                    oldHash = legacyHash,
                    newHash = potentialTxn.sourceSmsHash!!,
                )
            }
        }

    @Test
    fun `resolveAndSaveTransaction returns null when legacy hash is in deleted deny-list`() =
        runTest {
            val potentialTxn = makeTxn()
            val legacyHash = SmsParser.computeLegacySmsHash(potentialTxn.smsSender, potentialTxn.originalMessage)

            val mockDeletedDao = mockk<DeletedSmsHashDao>(relaxed = true)
            every { db.deletedSmsHashDao() } returns mockDeletedDao
            coEvery { mockDeletedDao.existsByHash(potentialTxn.sourceSmsHash!!) } returns false
            coEvery { mockDeletedDao.existsByHash(legacyHash) } returns true

            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            coEvery { transactionQueryDao.existsBySmsHash(any()) } returns false

            val id = saver.resolveAndSaveTransaction(potentialTxn)

            assertNull(id)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            coVerify(exactly = 1) { mockDeletedDao.insert(match { it.smsHash == potentialTxn.sourceSmsHash }) }
        }

    @Test
    fun `resolveAndSaveTransaction returns null when current hash is in deleted deny-list`() =
        runTest {
            val potentialTxn = makeTxn()
            val legacyHash = SmsParser.computeLegacySmsHash(potentialTxn.smsSender, potentialTxn.originalMessage)

            val mockDeletedDao = mockk<DeletedSmsHashDao>(relaxed = true)
            every { db.deletedSmsHashDao() } returns mockDeletedDao
            coEvery { mockDeletedDao.existsByHash(potentialTxn.sourceSmsHash!!) } returns true
            coEvery { mockDeletedDao.existsByHash(legacyHash) } returns false

            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            coEvery { transactionQueryDao.existsBySmsHash(any()) } returns false

            val id = saver.resolveAndSaveTransaction(potentialTxn)

            assertNull(id)
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
            coVerify(exactly = 0) { mockDeletedDao.insert(any()) }
        }

    @Test
    fun `resolveAndSaveTransaction succeeds when sourceSmsHash is null`() =
        runTest {
            val potentialTxn = makeTxn().copy(sourceSmsHash = null)

            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")

            val id = saver.resolveAndSaveTransaction(potentialTxn)

            assertNotNull(id)
            coVerify(exactly = 1) { transactionWriteDao.insert(any()) }
        }

    // -------------------------------------------------------------------------
    // Issue #305: Account Sanitization and Amount Guard Tests
    // -------------------------------------------------------------------------

    @Test
    fun `oversized account name is truncated to 60 characters before DAO calls`() =
        runTest {
            val longAccountName = "A".repeat(85)
            val expectedTruncatedName = "A".repeat(60)

            coEvery { accountAliasDao.findByAlias(expectedTruncatedName) } returns null
            coEvery { accountDao.findByName(expectedTruncatedName) } returns null
            coEvery { accountDao.insert(any()) } returns 42L
            coEvery { accountDao.getAccountByIdSync(42) } returns Account(42, expectedTruncatedName, "Bank Account")

            val id = saver.resolveAndSaveTransaction(makeTxn(account = longAccountName))

            assertNotNull(id)
            coVerify { accountAliasDao.findByAlias(expectedTruncatedName) }
            coVerify { accountDao.findByName(expectedTruncatedName) }
            coVerify { accountDao.insert(match { it.name == expectedTruncatedName }) }
        }

    @Test
    fun `account name with control characters has them stripped before DAO calls`() =
        runTest {
            val dirtyAccountName = "HDFC\n\u0000\t Bank\r"
            val expectedCleanName = "HDFC Bank"

            coEvery { accountAliasDao.findByAlias(expectedCleanName) } returns null
            coEvery { accountDao.findByName(expectedCleanName) } returns null
            coEvery { accountDao.insert(any()) } returns 43L
            coEvery { accountDao.getAccountByIdSync(43) } returns Account(43, expectedCleanName, "Bank Account")

            val id = saver.resolveAndSaveTransaction(makeTxn(account = dirtyAccountName))

            assertNotNull(id)
            coVerify { accountAliasDao.findByAlias(expectedCleanName) }
            coVerify { accountDao.findByName(expectedCleanName) }
            coVerify { accountDao.insert(match { it.name == expectedCleanName }) }
        }

    @Test
    fun `multi-sentence account hallucination does not create full-sentence account name in DB`() =
        runTest {
            val multiSentenceAccount =
                "Your account ending in 1234 has been debited by Rs 500 for a purchase at Starbucks. Please contact support."

            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns null
            coEvery { accountDao.insert(any()) } returns 44L
            coEvery { accountDao.getAccountByIdSync(44) } returns Account(44, "Truncated", "Bank Account")

            val id = saver.resolveAndSaveTransaction(makeTxn(account = multiSentenceAccount))

            assertNotNull(id)
            val insertedAccountSlot = slot<Account>()
            coVerify { accountDao.insert(capture(insertedAccountSlot)) }
            assertTrue(
                insertedAccountSlot.captured.name.length <= 60,
                "Account name must be at most 60 characters",
            )
            assertFalse(
                insertedAccountSlot.captured.name == multiSentenceAccount,
                "Account name must not be the full multi-sentence string",
            )
        }

    @Test
    fun `transaction with zero amount is dropped and returns null`() =
        runTest {
            val id = saver.resolveAndSaveTransaction(makeTxn().copy(amount = 0.0))

            assertNull(id)
            coVerify(exactly = 0) { accountDao.insert(any()) }
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `transaction with negative amount is dropped and returns null`() =
        runTest {
            val id = saver.resolveAndSaveTransaction(makeTxn().copy(amount = -75.0))

            assertNull(id)
            coVerify(exactly = 0) { accountDao.insert(any()) }
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `transaction with NaN or Infinite amount is dropped and returns null`() =
        runTest {
            val idNaN = saver.resolveAndSaveTransaction(makeTxn().copy(amount = Double.NaN))
            val idInf = saver.resolveAndSaveTransaction(makeTxn().copy(amount = Double.POSITIVE_INFINITY))
            val idNegInf = saver.resolveAndSaveTransaction(makeTxn().copy(amount = Double.NEGATIVE_INFINITY))

            assertNull(idNaN)
            assertNull(idInf)
            assertNull(idNegInf)
            coVerify(exactly = 0) { accountDao.insert(any()) }
            coVerify(exactly = 0) { transactionWriteDao.insert(any()) }
        }

    @Test
    fun `sub-60 multi-sentence account hallucination is truncated at sentence break`() =
        runTest {
            val multiSentenceAccount = "A/c debited. Do not share OTP."
            val expectedCleanName = "A/c debited"

            coEvery { accountAliasDao.findByAlias(expectedCleanName) } returns null
            coEvery { accountDao.findByName(expectedCleanName) } returns null
            coEvery { accountDao.insert(any()) } returns 45L
            coEvery { accountDao.getAccountByIdSync(45) } returns Account(45, expectedCleanName, "Bank Account")

            val id = saver.resolveAndSaveTransaction(makeTxn(account = multiSentenceAccount))

            assertNotNull(id)
            val insertedAccountSlot = slot<Account>()
            coVerify { accountDao.insert(capture(insertedAccountSlot)) }
            assertEquals(expectedCleanName, insertedAccountSlot.captured.name)
        }

    @Test
    fun `foreign transaction with non-positive or invalid conversion rate falls back to 1_0`() =
        runTest {
            coEvery { accountAliasDao.findByAlias(any()) } returns null
            coEvery { accountDao.findByName(any()) } returns Account(1, "HDFC", "Bank")
            val captor = slot<Transaction>()
            coEvery { transactionWriteDao.insert(capture(captor)) } returns 56L

            val zeroRateSettings =
                TravelModeSettings(
                    isEnabled = true, tripName = "Trip", tripType = TripType.INTERNATIONAL,
                    startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = "USD", conversionRate = 0f,
                )

            saver.resolveAndSaveTransaction(makeTxn().copy(amount = 150.0), isForeign = true, travelSettings = zeroRateSettings)
            assertEquals(150.0, captor.captured.amount, 0.001)
            assertEquals(1.0, captor.captured.conversionRate ?: 0.0, 0.001)

            val nanRateSettings =
                TravelModeSettings(
                    isEnabled = true, tripName = "Trip", tripType = TripType.INTERNATIONAL,
                    startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = "USD", conversionRate = Float.NaN,
                )
            saver.resolveAndSaveTransaction(makeTxn().copy(amount = 200.0), isForeign = true, travelSettings = nanRateSettings)
            assertEquals(200.0, captor.captured.amount, 0.001)
            assertEquals(1.0, captor.captured.conversionRate ?: 0.0, 0.001)
        }
}
