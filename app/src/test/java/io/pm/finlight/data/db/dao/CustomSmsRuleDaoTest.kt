package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleDao
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
class CustomSmsRuleDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var customSmsRuleDao: CustomSmsRuleDao

    @Before
    fun setup() {
        customSmsRuleDao = dbRule.db.customSmsRuleDao()
    }

    @Test
    fun `getAllRules returns rules ordered by priority descending`() = runTest {
        // Arrange
        val ruleLowPriority = CustomSmsRule(id = 1, triggerPhrase = "low", priority = 1, sourceSmsBody = "", merchantRegex = null, amountRegex = null, accountRegex = null, merchantNameExample = null, amountExample = null, accountNameExample = null)
        val ruleHighPriority = CustomSmsRule(id = 2, triggerPhrase = "high", priority = 10, sourceSmsBody = "", merchantRegex = null, amountRegex = null, accountRegex = null, merchantNameExample = null, amountExample = null, accountNameExample = null)
        val ruleMidPriority = CustomSmsRule(id = 3, triggerPhrase = "mid", priority = 5, sourceSmsBody = "", merchantRegex = null, amountRegex = null, accountRegex = null, merchantNameExample = null, amountExample = null, accountNameExample = null)

        customSmsRuleDao.insertAll(listOf(ruleLowPriority, ruleHighPriority, ruleMidPriority))

        // Act & Assert
        customSmsRuleDao.getAllRules().test {
            val rules = awaitItem()
            assertEquals(3, rules.size)
            assertEquals(10, rules[0].priority) // high
            assertEquals(5, rules[1].priority)  // mid
            assertEquals(1, rules[2].priority)  // low
            cancelAndIgnoreRemainingEvents()
        }
    }
}
