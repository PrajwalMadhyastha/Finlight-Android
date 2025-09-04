// =================================================================================
// FILE: ./core/src/test/java/io/pm/finlight/BaseSmsParserTest.kt
// REASON: FIX - Removed the unnecessary stubbing for the `customSmsRuleDao`.
// This mock was being prepared for all tests but was only used by a subset,
// causing Mockito's strict stubbing checks to raise an
// `UnnecessaryStubbingException`. Tests that require custom rules will now
// mock this dependency individually.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.*
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

/**
 * An abstract base class for all SMS Parser tests. It handles the boilerplate setup
 * for mocking DAOs and creating the necessary rule providers.
 */
@RunWith(MockitoJUnitRunner::class)
abstract class BaseSmsParserTest {

    @Mock
    protected lateinit var mockCustomSmsRuleDao: CustomSmsRuleDao

    @Mock
    protected lateinit var mockMerchantRenameRuleDao: MerchantRenameRuleDao

    @Mock
    protected lateinit var mockIgnoreRuleDao: IgnoreRuleDao

    @Mock
    protected lateinit var mockMerchantCategoryMappingDao: MerchantCategoryMappingDao

    @Mock
    protected lateinit var mockSmsParseTemplateDao: SmsParseTemplateDao

    protected val emptyMappings = emptyMap<String, String>()

    // Providers that will be used by the SmsParser
    protected lateinit var customSmsRuleProvider: CustomSmsRuleProvider
    protected lateinit var merchantRenameRuleProvider: MerchantRenameRuleProvider
    protected lateinit var ignoreRuleProvider: IgnoreRuleProvider
    protected lateinit var merchantCategoryMappingProvider: MerchantCategoryMappingProvider
    protected lateinit var categoryFinderProvider: CategoryFinderProvider
    protected lateinit var smsParseTemplateProvider: SmsParseTemplateProvider

    @Before
    fun setUp() {
        // Create anonymous implementations of the providers that use our DAO mocks.
        // This adapts the test to the new decoupled architecture.
        customSmsRuleProvider = object : CustomSmsRuleProvider {
            override suspend fun getAllRules(): List<CustomSmsRule> = mockCustomSmsRuleDao.getAllRules().first()
        }
        merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
            override suspend fun getAllRules(): List<MerchantRenameRule> = mockMerchantRenameRuleDao.getAllRules().first()
        }
        ignoreRuleProvider = object : IgnoreRuleProvider {
            override suspend fun getEnabledRules(): List<IgnoreRule> = mockIgnoreRuleDao.getEnabledRules()
        }
        merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
            override suspend fun getCategoryIdForMerchant(merchantName: String): Int? =
                mockMerchantCategoryMappingDao.getCategoryIdForMerchant(merchantName)
        }
        categoryFinderProvider = object : CategoryFinderProvider {
            override fun getCategoryIdByName(name: String): Int? {
                return CategoryIconHelper.getCategoryIdByName(name)
            }
        }
        smsParseTemplateProvider = object : SmsParseTemplateProvider {
            override suspend fun getAllTemplates(): List<SmsParseTemplate> = mockSmsParseTemplateDao.getAllTemplates()
        }
    }

    /**
     * Helper function to set up the mock DAOs for a specific test scenario.
     */
    protected suspend fun setupTest(
        customRules: List<CustomSmsRule> = emptyList(),
        renameRules: List<MerchantRenameRule> = emptyList(),
        ignoreRules: List<IgnoreRule> = DEFAULT_IGNORE_PHRASES
    ) {
        // Mock the DAO methods. The providers above will then use these mocks.
        // --- UPDATED: Removed unnecessary stubbing for custom rules. ---
        // `when`(mockCustomSmsRuleDao.getAllRules()).thenReturn(flowOf(customRules))
        `when`(mockMerchantRenameRuleDao.getAllRules()).thenReturn(flowOf(renameRules))
        `when`(mockIgnoreRuleDao.getEnabledRules()).thenReturn(ignoreRules.filter { it.isEnabled })
        // --- UPDATED: The mock is now always set up to prevent NullPointerExceptions ---
        `when`(mockSmsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
    }
}
