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
}
