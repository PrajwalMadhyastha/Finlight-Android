package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.CustomSmsRuleRepository
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
class CustomSmsRuleRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var customSmsRuleDao: CustomSmsRuleDao

    private lateinit var repository: CustomSmsRuleRepository

    private fun createTestRule(
        id: Int = 1,
        triggerPhrase: String = "spent"
    ) =
        CustomSmsRule(
            id = id,
            triggerPhrase = triggerPhrase,
            merchantRegex = null,
            amountRegex = null,
            accountRegex = null,
            merchantNameExample = null,
            amountExample = null,
            accountNameExample = null,
            priority = 10,
            sourceSmsBody = "test body",
        )

    @Before
    override fun setup() {
        super.setup()
        repository = CustomSmsRuleRepository(customSmsRuleDao)
    }

    @Test
    fun `getAllRules returns flow from dao`() =
        runTest {
            val rules = listOf(createTestRule(1, "spent"))
            `when`(customSmsRuleDao.getAllRules()).thenReturn(flowOf(rules))

            repository.getAllRules().test {
                assertEquals(rules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getAllRulesList returns list from dao`() =
        runTest {
            val rules = listOf(createTestRule(1, "spent"))
            `when`(customSmsRuleDao.getAllRulesList()).thenReturn(rules)

            val result = repository.getAllRulesList()
            assertEquals(rules, result)
            verify(customSmsRuleDao).getAllRulesList()
        }

    @Test
    fun `getRuleById returns flow from dao`() =
        runTest {
            val rule = createTestRule(42, "debited")
            `when`(customSmsRuleDao.getRuleById(42)).thenReturn(flowOf(rule))

            repository.getRuleById(42).test {
                assertEquals(rule, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `insert delegates to dao insert`() =
        runTest {
            val rule = createTestRule(1, "spent")
            repository.insert(rule)
            verify(customSmsRuleDao).insert(rule)
        }

    @Test
    fun `insertAll delegates to dao insertAll`() =
        runTest {
            val rules = listOf(createTestRule(1, "spent"))
            repository.insertAll(rules)
            verify(customSmsRuleDao).insertAll(rules)
        }

    @Test
    fun `update delegates to dao update`() =
        runTest {
            val rule = createTestRule(1, "updated")
            repository.update(rule)
            verify(customSmsRuleDao).update(rule)
        }

    @Test
    fun `delete delegates to dao delete`() =
        runTest {
            val rule = createTestRule(1, "to delete")
            repository.delete(rule)
            verify(customSmsRuleDao).delete(rule)
        }

    @Test
    fun `deleteAll delegates to dao deleteAll`() =
        runTest {
            repository.deleteAll()
            verify(customSmsRuleDao).deleteAll()
        }
}
