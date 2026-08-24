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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsTransactionSaverTest : BaseViewModelTest() {
    private lateinit var db: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var transactionWriteDao: TransactionWriteDao
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

        every { db.accountDao() } returns accountDao
        every { db.accountAliasDao() } returns accountAliasDao
        every { db.transactionWriteDao() } returns transactionWriteDao
        every { db.tagDao() } returns tagDao
        every { db.merchantRenameRuleDao() } returns merchantRenameRuleDao
        every { db.merchantCategoryMappingDao() } returns merchantCategoryMappingDao

        mockkConstructor(SettingsRepository::class)
        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(null)
        every { anyConstructed<SettingsRepository>().getHomeCurrency() } returns flowOf("INR")

        saver = SmsTransactionSaver(db, SettingsRepository(context))
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
            coEvery { accountDao.getAccountByIdBlocking(15) } returns Account(15, "HDFC Bank - X1234", "Bank Account")

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
            coEvery { accountDao.getAccountByIdBlocking(5) } returns Account(5, "Unknown Account", "General")

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
}
