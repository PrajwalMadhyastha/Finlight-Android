package io.pm.finlight.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var context: Context

    private val testDbName = "test_encrypted_database.db"
    private val testPassphrase = "test_super_secret_passphrase".toByteArray()

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()

        // Ensure we start with a clean slate
        context.deleteDatabase(testDbName)

        // Initialize SQLCipher factory with our test passphrase
        val factory = SupportOpenHelperFactory(testPassphrase)

        // Build the database using the factory.
        // We use a physical file rather than in-memory to prove file encryption works as expected.
        db =
            Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
                .openHelperFactory(factory)
                // Allow main thread queries just for the test setup simplicity if needed,
                // though we'll use runBlocking for suspend functions.
                .allowMainThreadQueries()
                .build()

        transactionDao = db.transactionDao()
        accountDao = db.accountDao()
    }

    @After
    fun closeDb() {
        db.close()
        // Clean up the encrypted database file after the test
        context.deleteDatabase(testDbName)
    }

    @Test
    fun testEncryptedDatabaseCrudOperations() =
        runBlocking {
            // --- 1. Pre-requisite: Create an Account ---
            // Transaction has a foreign key to Account, so we need one first.
            val account =
                Account(
                    id = 1,
                    name = "Test Encrypted Account",
                    type = "Bank",
                )
            accountDao.insert(account)

            // --- 2. Create (Insert) ---
            val transaction =
                Transaction(
                    id = 1,
                    description = "Encrypted Coffee",
                    amount = 5.50,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = null,
                    notes = "First encrypted transaction",
                    originalDescription = "Encrypted Coffee",
                )
            transactionDao.insert(transaction)

            // --- 3. Read ---
            val readTx = transactionDao.getTransactionById(1).first()
            assertNotNull("Transaction should exist in the database", readTx)
            assertEquals("Encrypted Coffee", readTx?.description)
            assertEquals(5.50, readTx?.amount)
            assertEquals("First encrypted transaction", readTx?.notes)

            // --- 4. Update ---
            transactionDao.updateAmount(1, 7.50)
            transactionDao.updateNotes(1, "Updated note")

            val updatedTx = transactionDao.getTransactionById(1).first()
            assertNotNull("Transaction should still exist", updatedTx)
            assertEquals(7.50, updatedTx?.amount)
            assertEquals("Updated note", updatedTx?.notes)

            // --- 5. Delete ---
            // Verify delete transaction by ID works. Wait, TransactionDao might not have deleteById.
            // Let's check TransactionDao functions. Typically it has `delete(Transaction)`.
            transactionDao.delete(updatedTx!!)

            val deletedTx = transactionDao.getTransactionById(1).first()
            assertNull("Transaction should have been deleted", deletedTx)

            // Extra check to prove the database file actually exists and was created
            val dbFile: File = context.getDatabasePath(testDbName)
            assertEquals("Database file should exist", true, dbFile.exists())
        }
}
