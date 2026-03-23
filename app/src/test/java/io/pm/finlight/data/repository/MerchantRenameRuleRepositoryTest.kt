package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleDao
import io.pm.finlight.MerchantRenameRuleRepository
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
class MerchantRenameRuleRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao

    private lateinit var repository: MerchantRenameRuleRepository

    @Before
    override fun setup() {
        super.setup()
        repository = MerchantRenameRuleRepository(merchantRenameRuleDao)
    }

    @Test
    fun `getAliasesAsMap correctly transforms list to map`() = runTest {
        // Arrange
        val rules = listOf(
            MerchantRenameRule(originalName = "AMZN", newName = "Amazon"),
            MerchantRenameRule(originalName = "FLPKRT", newName = "Flipkart")
        )
        `when`(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(rules))

        // Act & Assert
        repository.getAliasesAsMap().test {
            val aliasMap = awaitItem()
            assertEquals(2, aliasMap.size)
            assertEquals("Amazon", aliasMap["AMZN"])
            assertEquals("Flipkart", aliasMap["FLPKRT"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insert calls DAO`() = runTest {
        // Arrange
        val newRule = MerchantRenameRule(originalName = "SWGY", newName = "Swiggy")

        // Act
        repository.insert(newRule)

        // Assert
        verify(merchantRenameRuleDao).insert(newRule)
    }
    @Test
    fun `deleteByOriginalName calls DAO`() = runTest {
        // Arrange
        val originalName = "UBER"

        // Act
        repository.deleteByOriginalName(originalName)

        // Assert
        verify(merchantRenameRuleDao).deleteByOriginalName(originalName)
    }
}