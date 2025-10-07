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
}
