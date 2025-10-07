package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.entity.AccountAlias
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
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountAliasDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setup() = runTest {
        accountAliasDao = dbRule.db.accountAliasDao()
        accountDao = dbRule.db.accountDao()
    }

    @Test
    fun `findByAlias is case insensitive`() = runTest {
        // Arrange
        val destAccountId = accountDao.insert(Account(name = "ICICI Bank", type = "Bank")).toInt()
        val alias = AccountAlias(aliasName = "ICICI - xx1234", destinationAccountId = destAccountId)
        accountAliasDao.insertAll(listOf(alias))

        // Act
        val foundAlias = accountAliasDao.findByAlias("icici - xx1234")

        // Assert
        assertNotNull(foundAlias)
        assertEquals(destAccountId, foundAlias?.destinationAccountId)
    }

    @Test
    fun `getAll returns all inserted aliases`() = runTest {
        // Arrange
        val destAccountId1 = accountDao.insert(Account(name = "Account 1", type = "Bank")).toInt()
        val destAccountId2 = accountDao.insert(Account(name = "Account 2", type = "Bank")).toInt()
        val aliases = listOf(
            AccountAlias(aliasName = "Alias 1", destinationAccountId = destAccountId1),
            AccountAlias(aliasName = "Alias 2", destinationAccountId = destAccountId2)
        )
        accountAliasDao.insertAll(aliases)

        // Act
        val allAliases = accountAliasDao.getAll()

        // Assert
        assertEquals(2, allAliases.size)
        assertTrue(allAliases.containsAll(aliases))
    }

    @Test
    fun `deleteAll removes all aliases`() = runTest {
        // Arrange
        val destAccountId = accountDao.insert(Account(name = "Account 1", type = "Bank")).toInt()
        val aliases = listOf(
            AccountAlias(aliasName = "Alias 1", destinationAccountId = destAccountId),
            AccountAlias(aliasName = "Alias 2", destinationAccountId = destAccountId)
        )
        accountAliasDao.insertAll(aliases)

        // Act
        accountAliasDao.deleteAll()

        // Assert
        val allAliases = accountAliasDao.getAll()
        assertTrue(allAliases.isEmpty())
    }
}