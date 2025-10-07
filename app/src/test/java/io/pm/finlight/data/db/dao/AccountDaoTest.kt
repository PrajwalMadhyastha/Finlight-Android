package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Account
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao

    private val account1 = Account(id = 1, name = "HDFC Bank", type = "Bank")
    private val account2 = Account(id = 2, name = "ICICI Credit Card", type = "Card")

    @Before
    fun setup() = runTest {
        accountDao = dbRule.db.accountDao()
        transactionDao = dbRule.db.transactionDao()

        accountDao.insertAll(listOf(account1, account2))
    }

    @Test
    fun `findByName is case insensitive`() = runTest {
        // Act
        val foundAccount = accountDao.findByName("hdfc bank")

        // Assert
        assertNotNull(foundAccount)
        assertEquals(account1.id, foundAccount?.id)
        assertEquals(account1.name, foundAccount?.name)
    }

    @Test
    fun `getAccountsWithBalance calculates balance correctly`() = runTest {
        // Arrange
        // Account 1: 5000 income - 1000 expense = 4000 balance
        transactionDao.insert(Transaction(description = "Salary", amount = 5000.0, transactionType = "income", date = System.currentTimeMillis(), accountId = 1, categoryId = null, notes = null))
        transactionDao.insert(Transaction(description = "Rent", amount = 1000.0, transactionType = "expense", date = System.currentTimeMillis(), accountId = 1, categoryId = null, notes = null))
        // Excluded transaction, should be ignored
        transactionDao.insert(Transaction(description = "Excluded", amount = 500.0, transactionType = "expense", date = System.currentTimeMillis(), accountId = 1, categoryId = null, notes = null, isExcluded = true))

        // Account 2: 200 expense (credit card) = -200 balance
        transactionDao.insert(Transaction(description = "Shopping", amount = 200.0, transactionType = "expense", date = System.currentTimeMillis(), accountId = 2, categoryId = null, notes = null))


        // Act & Assert
        accountDao.getAccountsWithBalance().test {
            val accountsWithBalance = awaitItem()

            assertEquals(2, accountsWithBalance.size)

            val acc1Balance = accountsWithBalance.find { it.account.id == 1 }
            assertNotNull(acc1Balance)
            assertEquals(4000.0, acc1Balance!!.balance, 0.01)

            val acc2Balance = accountsWithBalance.find { it.account.id == 2 }
            assertNotNull(acc2Balance)
            assertEquals(-200.0, acc2Balance!!.balance, 0.01)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllAccounts returns all accounts`() = runTest {
        // Act
        accountDao.getAllAccounts().test {
            val accounts = awaitItem()
            // Assert
            assertEquals(2, accounts.size)
            assertTrue(accounts.any { it.name == "HDFC Bank" })
            assertTrue(accounts.any { it.name == "ICICI Credit Card" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAccountById returns correct account`() = runTest {
        // Act & Assert
        accountDao.getAccountById(account1.id).test {
            val account = awaitItem()
            assertNotNull(account)
            assertEquals(account1.name, account?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAccountByIdBlocking returns correct account`() = runTest {
        // Act
        val account = accountDao.getAccountByIdBlocking(account2.id)
        // Assert
        assertNotNull(account)
        assertEquals(account2.name, account?.name)
    }

    @Test
    fun `update modifies account correctly`() = runTest {
        // Arrange
        val updatedAccount = account1.copy(name = "HDFC Savings")

        // Act
        accountDao.update(updatedAccount)

        // Assert
        val fromDb = accountDao.getAccountByIdBlocking(account1.id)
        assertNotNull(fromDb)
        assertEquals("HDFC Savings", fromDb?.name)
    }

    @Test
    fun `delete removes account`() = runTest {
        // Act
        accountDao.delete(account1)

        // Assert
        val fromDb = accountDao.getAccountByIdBlocking(account1.id)
        assertNull(fromDb)
    }

    @Test
    fun `deleteByIds removes specified accounts`() = runTest {
        // Arrange
        val account3 = Account(id = 3, name = "Axis", type = "Bank")
        accountDao.insert(account3)

        // Act
        accountDao.deleteByIds(listOf(account1.id, account3.id))

        // Assert
        accountDao.getAllAccounts().test {
            val remaining = awaitItem()
            assertEquals(1, remaining.size)
            assertEquals(account2.name, remaining.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll removes all accounts`() = runTest {
        // Act
        accountDao.deleteAll()
        // Assert
        accountDao.getAllAccounts().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}