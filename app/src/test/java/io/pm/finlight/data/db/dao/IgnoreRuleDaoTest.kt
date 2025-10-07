package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.RuleType
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
class IgnoreRuleDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var ignoreRuleDao: IgnoreRuleDao

    @Before
    fun setup() {
        ignoreRuleDao = dbRule.db.ignoreRuleDao()
    }

    @Test
    fun `inserting duplicate pattern with different case is ignored`() = runTest {
        // Arrange
        ignoreRuleDao.insert(IgnoreRule(pattern = "OTP", type = RuleType.BODY_PHRASE))

        // Act
        // This should be ignored due to the case-insensitive unique index
        ignoreRuleDao.insert(IgnoreRule(pattern = "otp", type = RuleType.BODY_PHRASE))

        // Assert
        ignoreRuleDao.getAll().test {
            val rules = awaitItem()
            assertEquals(1, rules.size)
            assertEquals("OTP", rules.first().pattern)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteDefaultRules only removes default rules`() = runTest {
        // Arrange
        ignoreRuleDao.insertAll(listOf(
            IgnoreRule(pattern = "Default Rule 1", isDefault = true),
            IgnoreRule(pattern = "Custom Rule 1", isDefault = false),
            IgnoreRule(pattern = "Default Rule 2", isDefault = true)
        ))

        // Act
        ignoreRuleDao.deleteDefaultRules()

        // Assert
        ignoreRuleDao.getAll().test {
            val rules = awaitItem()
            assertEquals(1, rules.size)
            assertEquals("Custom Rule 1", rules.first().pattern)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllList returns all rules as a list`() = runTest {
        // Arrange
        val rules = listOf(
            IgnoreRule(pattern = "Rule 1"),
            IgnoreRule(pattern = "Rule 2")
        )
        ignoreRuleDao.insertAll(rules)

        // Act
        val result = ignoreRuleDao.getAllList()

        // Assert
        assertEquals(2, result.size)
    }

    @Test
    fun `getEnabledRules returns only enabled rules`() = runTest {
        // Arrange
        val rules = listOf(
            IgnoreRule(pattern = "Enabled", isEnabled = true),
            IgnoreRule(pattern = "Disabled", isEnabled = false)
        )
        ignoreRuleDao.insertAll(rules)

        // Act
        val result = ignoreRuleDao.getEnabledRules()

        // Assert
        assertEquals(1, result.size)
        assertEquals("Enabled", result.first().pattern)
    }

    @Test
    fun `update modifies rule correctly`() = runTest {
        // Arrange
        ignoreRuleDao.insert(IgnoreRule(id = 1, pattern = "Test", isEnabled = true))

        // Act
        ignoreRuleDao.update(IgnoreRule(id = 1, pattern = "Test", isEnabled = false))

        // Assert
        val result = ignoreRuleDao.getEnabledRules()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `delete removes a rule`() = runTest {
        // Arrange
        val ruleToDelete = IgnoreRule(id = 1, pattern = "Test")
        ignoreRuleDao.insert(ruleToDelete)

        // Act
        ignoreRuleDao.delete(ruleToDelete)

        // Assert
        val result = ignoreRuleDao.getAllList()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteAll removes all rules`() = runTest {
        // Arrange
        ignoreRuleDao.insertAll(listOf(
            IgnoreRule(pattern = "Rule 1"),
            IgnoreRule(pattern = "Rule 2")
        ))

        // Act
        ignoreRuleDao.deleteAll()

        // Assert
        val result = ignoreRuleDao.getAllList()
        assertTrue(result.isEmpty())
    }
}