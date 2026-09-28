package io.pm.finlight.core.parser

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.SmsParser
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsHashRoomIntegrationTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var writeDao: TransactionWriteDao
    private lateinit var queryDao: TransactionQueryDao
    private lateinit var deletedSmsHashDao: DeletedSmsHashDao

    private val testAccount = Account(id = 1, name = "HDFC Bank", type = "Bank Account")

    @Before
    fun setup() =
        runTest {
            writeDao = dbRule.db.transactionWriteDao()
            queryDao = dbRule.db.transactionQueryDao()
            deletedSmsHashDao = dbRule.db.deletedSmsHashDao()
            dbRule.db.accountDao().insert(testAccount)
        }

    @Test
    fun `transaction with 64-character SHA-256 hash persists and existsBySmsHash returns true`() =
        runTest {
            val sha256Hash = SmsParser.computeSmsHash("VK-HDFCBK", "Spent Rs 500 at Swiggy")
            assertEquals(64, sha256Hash.length)

            val txn =
                Transaction(
                    id = 101,
                    description = "Swiggy",
                    categoryId = null,
                    amount = 500.0,
                    date = System.currentTimeMillis(),
                    accountId = testAccount.id,
                    notes = null,
                    sourceSmsHash = sha256Hash,
                )

            val rowId = writeDao.insert(txn)
            assertTrue("Transaction must insert successfully", rowId > 0)
            assertTrue("existsBySmsHash must return true for inserted SHA-256 hash", queryDao.existsBySmsHash(sha256Hash))
            assertTrue(queryDao.getAllSmsHashes().first().contains(sha256Hash))
        }

    @Test
    fun `unique constraint prevents duplicate insertion of transaction with same 64-character hash`() =
        runTest {
            val sha256Hash = SmsParser.computeSmsHash("AX-AXISBK", "Paid Rs 250 at Starbucks")

            val txn1 =
                Transaction(
                    id = 201,
                    description = "Starbucks",
                    categoryId = null,
                    amount = 250.0,
                    date = System.currentTimeMillis(),
                    accountId = testAccount.id,
                    notes = null,
                    sourceSmsHash = sha256Hash,
                )
            val rowId1 = writeDao.insert(txn1)
            assertTrue(rowId1 > 0)

            val txn2 =
                Transaction(
                    id = 202,
                    description = "Starbucks Duplicate",
                    categoryId = null,
                    amount = 250.0,
                    date = System.currentTimeMillis() + 1000,
                    accountId = testAccount.id,
                    notes = null,
                    sourceSmsHash = sha256Hash,
                )
            val rowId2 = writeDao.insert(txn2)
            // OnConflictStrategy.IGNORE returns -1 on unique constraint violation
            assertEquals("Duplicate insertion on unique sourceSmsHash must be ignored (-1L)", -1L, rowId2)
        }

    @Test
    fun `updateSmsHashByLegacy atomically migrates legacy 32-bit hash to 64-character SHA-256 hash`() =
        runTest {
            val legacyHash = SmsParser.computeLegacySmsHash("VK-HDFCBK", "Spent Rs 100 at Swiggy")
            val sha256Hash = SmsParser.computeSmsHash("VK-HDFCBK", "Spent Rs 100 at Swiggy")

            val txn =
                Transaction(
                    id = 301,
                    description = "Swiggy",
                    categoryId = null,
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = testAccount.id,
                    notes = null,
                    sourceSmsHash = legacyHash,
                )
            writeDao.insert(txn)

            assertTrue("Legacy hash must exist initially", queryDao.existsBySmsHash(legacyHash))
            assertFalse("New SHA-256 hash must not exist yet", queryDao.existsBySmsHash(sha256Hash))

            // Execute atomic migration query
            writeDao.updateSmsHashByLegacy(oldHash = legacyHash, newHash = sha256Hash)

            assertFalse("Legacy hash must no longer exist after update", queryDao.existsBySmsHash(legacyHash))
            assertTrue("New SHA-256 hash must now exist in database", queryDao.existsBySmsHash(sha256Hash))
        }

    @Test
    fun `DeletedSmsHashDao supports 64-character SHA-256 hashes, existsByHash, and idempotent inserts`() =
        runTest {
            val sha256Hash = SmsParser.computeSmsHash("BW-SBIUPI", "Sent Rs 1000 to Friend")
            assertEquals(64, sha256Hash.length)

            assertFalse("Hash should not exist initially", deletedSmsHashDao.existsByHash(sha256Hash))

            deletedSmsHashDao.insert(DeletedSmsHash(sha256Hash))
            assertTrue("Hash must exist after insertion", deletedSmsHashDao.existsByHash(sha256Hash))
            assertTrue(deletedSmsHashDao.getAllHashes().contains(sha256Hash))

            // Idempotent duplicate insert
            deletedSmsHashDao.insert(DeletedSmsHash(sha256Hash))
            assertEquals(1, deletedSmsHashDao.getAllHashes().filter { it == sha256Hash }.size)
        }
}
