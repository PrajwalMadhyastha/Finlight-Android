package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.IgnoreRuleRepository
import io.pm.finlight.RuleType
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class IgnoreRuleRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var ignoreRuleDao: IgnoreRuleDao

    private lateinit var repository: IgnoreRuleRepository

    @Before
    override fun setup() {
        super.setup()
        repository = IgnoreRuleRepository(ignoreRuleDao)
    }

    @Test
    fun `getAllRules returns flow from dao`() =
        runTest {
            val rules = listOf(IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM"))
            `when`(ignoreRuleDao.getAll()).thenReturn(flowOf(rules))

            repository.getAllRules().test {
                assertEquals(rules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAll alias returns flow from dao`() =
        runTest {
            val rules = listOf(IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM"))
            `when`(ignoreRuleDao.getAll()).thenReturn(flowOf(rules))

            repository.getAll().test {
                assertEquals(rules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAllRulesList returns list from dao`() =
        runTest {
            val rules = listOf(IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM"))
            `when`(ignoreRuleDao.getAllList()).thenReturn(rules)

            val result = repository.getAllRulesList()
            assertEquals(rules, result)
            verify(ignoreRuleDao).getAllList()
        }

    @Test
    fun `getEnabledRules returns list from dao`() =
        runTest {
            val rules = listOf(IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM", isEnabled = true))
            `when`(ignoreRuleDao.getEnabledRules()).thenReturn(rules)

            val result = repository.getEnabledRules()
            assertEquals(rules, result)
            verify(ignoreRuleDao).getEnabledRules()
        }

    @Test
    fun `insert delegates to dao insert`() =
        runTest {
            val rule = IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM")
            repository.insert(rule)
            verify(ignoreRuleDao).insert(rule)
        }

    @Test
    fun `insertAll delegates to dao insertAll`() =
        runTest {
            val rules = listOf(IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM"))
            repository.insertAll(rules)
            verify(ignoreRuleDao).insertAll(rules)
        }

    @Test
    fun `update delegates to dao update`() =
        runTest {
            val rule = IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM", isEnabled = false)
            repository.update(rule)
            verify(ignoreRuleDao).update(rule)
        }

    @Test
    fun `delete delegates to dao delete`() =
        runTest {
            val rule = IgnoreRule(id = 1, type = RuleType.SENDER, pattern = "SPAM")
            repository.delete(rule)
            verify(ignoreRuleDao).delete(rule)
        }

    @Test
    fun `deleteAll delegates to dao deleteAll`() =
        runTest {
            repository.deleteAll()
            verify(ignoreRuleDao).deleteAll()
        }

    @Test
    fun `deleteDefaultRules delegates to dao deleteDefaultRules`() =
        runTest {
            repository.deleteDefaultRules()
            verify(ignoreRuleDao).deleteDefaultRules()
        }
}
