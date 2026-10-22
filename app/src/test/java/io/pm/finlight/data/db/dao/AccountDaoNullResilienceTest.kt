package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountDaoNullResilienceTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    @Test
    fun getAccountsWithBalance_withNullNameAndType_returnsCoalescedDefaultsWithoutNpe() =
        runTest {
            val db = dbRule.db.openHelper.writableDatabase

            // Recreate accounts table without NOT NULL constraints to simulate legacy migration
            db.execSQL("DROP TABLE IF EXISTS accounts")
            db.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, type TEXT)")
            db.execSQL("INSERT INTO accounts (id, name, type) VALUES (99, NULL, NULL)")

            val dao = dbRule.db.accountDao()
            val accountsWithBalance = dao.getAccountsWithBalance().first()

            val accountWithNull = accountsWithBalance.find { it.account.id == 99 }
            assertNotNull(accountWithNull)
            assertEquals("Unnamed Account", accountWithNull?.account?.name)
            assertEquals("General", accountWithNull?.account?.type)
            assertEquals(0.0, accountWithNull?.balance ?: -1.0, 0.001)

            // Test snapshot query also doesn't NPE
            val snapshot = dao.getAllAccountsSnapshot()
            val snapshotAccount = snapshot.find { it.id == 99 }
            assertNotNull(snapshotAccount)
            assertEquals("Unnamed Account", snapshotAccount?.name)
            assertEquals("General", snapshotAccount?.type)
        }
}
