package io.pm.finlight.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDaoTest : BaseDaoTest() {

    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setup() {
        accountDao = db.accountDao()
        transactionDao = db.transactionDao()
    }

    @Test
    fun insertAndReadAccount() = runBlocking {
        val account = Account(name = "HDFC Bank", type = "Bank")
        val id = accountDao.insert(account)

        val loaded = accountDao.getAccountById(id.toInt()).first()
        assertNotNull(loaded)
        assertEquals("HDFC Bank", loaded?.name)
        assertEquals("Bank", loaded?.type)
    }

    @Test
    fun testCaseInsensitiveUniqueConstraint() = runBlocking {
        // 1. Insert "Chase"
        val account1 = Account(name = "Chase", type = "Credit Card")
        val id1 = accountDao.insert(account1)
        assertTrue("First insert should succeed", id1 > 0)

        // 2. Attempt to insert "chase" (lowercase)
        // Since conflict strategy is IGNORE, this should return -1
        val account2 = Account(name = "chase", type = "Bank")
        val id2 = accountDao.insert(account2)

        assertEquals("Duplicate insert (different case) should be ignored/return -1", -1L, id2)

        // 3. Verify only one account exists
        val allAccounts = accountDao.getAllAccounts().first()
        assertEquals(1, allAccounts.size)
    }

    @Test
    fun getAccountsWithBalance_calculatesCorrectly() = runBlocking {
        // 1. Create Account
        val accountId = accountDao.insert(Account(name = "Savings", type = "Bank")).toInt()

        // 2. Add Transactions (Income +5000, Expense -1000, Excluded -500)
        transactionDao.insert(Transaction(
            description = "Salary", amount = 5000.0, transactionType = "income",
            date = System.currentTimeMillis(), accountId = accountId, categoryId = null, notes = null
        ))

        transactionDao.insert(Transaction(
            description = "Rent", amount = 1000.0, transactionType = "expense",
            date = System.currentTimeMillis(), accountId = accountId, categoryId = null, notes = null
        ))

        // Excluded transaction should NOT affect balance calculation based on AccountDao query logic
        // (Check AccountDao SQL: WHERE isExcluded = 0)
        transactionDao.insert(Transaction(
            description = "Hidden Gift", amount = 500.0, transactionType = "expense",
            date = System.currentTimeMillis(), accountId = accountId, categoryId = null, notes = null, isExcluded = true
        ))

        // 3. Fetch account with balance
        val accountWithBalanceList = accountDao.getAccountsWithBalance().first()
        val accountData = accountWithBalanceList.find { it.account.id == accountId }

        assertNotNull(accountData)
        // Expected: 5000 - 1000 = 4000. The 500 excluded expense is ignored.
        assertEquals(4000.0, accountData!!.balance, 0.01)
    }

    @Test
    fun updateAccount() = runBlocking {
        val id = accountDao.insert(Account(name = "Old Name", type = "Bank")).toInt()
        val original = accountDao.getAccountById(id).first()!!

        val updated = original.copy(name = "New Name")
        accountDao.update(updated)

        val loaded = accountDao.getAccountById(id).first()
        assertEquals("New Name", loaded?.name)
    }

    @Test
    fun deleteAccount() = runBlocking {
        val id = accountDao.insert(Account(name = "To Delete", type = "Bank")).toInt()
        val account = accountDao.getAccountById(id).first()!!

        accountDao.delete(account)

        val loaded = accountDao.getAccountById(id).first()
        assertNull(loaded)
    }
}