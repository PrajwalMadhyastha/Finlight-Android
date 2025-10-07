package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleDao
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MerchantRenameRuleDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao

    @Before
    fun setup() {
        merchantRenameRuleDao = dbRule.db.merchantRenameRuleDao()
    }

    @Test
    fun `deleteByOriginalName removes the correct rule`() = runTest {
        // Arrange
        val rule1 = MerchantRenameRule(originalName = "AMZN", newName = "Amazon")
        val rule2 = MerchantRenameRule(originalName = "FLPKRT", newName = "Flipkart")
        merchantRenameRuleDao.insertAll(listOf(rule1, rule2))

        // Act
        merchantRenameRuleDao.deleteByOriginalName("AMZN")

        // Assert
        merchantRenameRuleDao.getAllRules().test {
            val rules = awaitItem()
            assertEquals(1, rules.size)
            assertEquals("Flipkart", rules.first().newName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insert and getAllRulesList works`() = runTest {
        // Arrange
        val rule = MerchantRenameRule(originalName = "Test", newName = "Test-Renamed")

        // Act
        merchantRenameRuleDao.insert(rule)
        val rules = merchantRenameRuleDao.getAllRulesList()

        // Assert
        assertEquals(1, rules.size)
        assertEquals("Test-Renamed", rules.first().newName)
    }

    @Test
    fun `deleteAll removes all rules`() = runTest {
        // Arrange
        val rules = listOf(
            MerchantRenameRule("Rule1", "New1"),
            MerchantRenameRule("Rule2", "New2")
        )
        merchantRenameRuleDao.insertAll(rules)

        // Act
        merchantRenameRuleDao.deleteAll()

        // Assert
        val result = merchantRenameRuleDao.getAllRulesList()
        assertTrue(result.isEmpty())
    }
}