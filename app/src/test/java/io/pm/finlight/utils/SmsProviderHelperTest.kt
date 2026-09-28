package io.pm.finlight.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockk
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.IgnoreRule
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.RuleType
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.TestApplication
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantCategoryMappingDao
import io.pm.finlight.MerchantRenameRuleDao
import io.pm.finlight.SmsParseTemplateDao
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsProviderHelperTest : BaseViewModelTest() {
    // --- Database-backed Providers ---

    @Test
    fun `getCategoryFinderProvider returns category ID for valid category name and null for unknown`() {
        val provider = SmsProviderHelper.getCategoryFinderProvider()

        val foodId = provider.getCategoryIdByName("Food & Drinks")
        assertEquals(4, foodId)

        val unknownId = provider.getCategoryIdByName("UnknownNonExistentCategoryXYZ")
        assertNull(unknownId)
    }

    @Test
    fun `getCustomSmsRuleProvider delegates to customSmsRuleDao`() =
        runTest {
            val mockDb = mockk<AppDatabase>()
            val mockDao = mockk<CustomSmsRuleDao>()
            val rule =
                CustomSmsRule(
                    id = 1,
                    triggerPhrase = "spent at",
                    merchantRegex = null,
                    amountRegex = null,
                    accountRegex = null,
                    merchantNameExample = null,
                    amountExample = null,
                    accountNameExample = null,
                    priority = 1,
                    sourceSmsBody = "spent at store"
                )
            coEvery { mockDb.customSmsRuleDao() } returns mockDao
            coEvery { mockDao.getAllRules() } returns flowOf(listOf(rule))

            val provider = SmsProviderHelper.getCustomSmsRuleProvider(mockDb)
            val result = provider.getAllRules()

            assertEquals(listOf(rule), result)
        }

    @Test
    fun `getMerchantRenameRuleProvider delegates to merchantRenameRuleDao`() =
        runTest {
            val mockDb = mockk<AppDatabase>()
            val mockDao = mockk<MerchantRenameRuleDao>()
            val rule = MerchantRenameRule(originalName = "Swiggy_IN", newName = "Swiggy")
            coEvery { mockDb.merchantRenameRuleDao() } returns mockDao
            coEvery { mockDao.getAllRules() } returns flowOf(listOf(rule))
            coEvery { mockDao.getAllRulesList() } returns listOf(rule)

            val provider = SmsProviderHelper.getMerchantRenameRuleProvider(mockDb)
            val rules = provider.getAllRules()
            val rulesMap = provider.getAllRulesMap()

            assertEquals(listOf(rule), rules)
            assertEquals(mapOf("swiggy_in" to "Swiggy"), rulesMap)
        }

    @Test
    fun `getIgnoreRuleProvider delegates to ignoreRuleDao`() =
        runTest {
            val mockDb = mockk<AppDatabase>()
            val mockDao = mockk<IgnoreRuleDao>()
            val rule = IgnoreRule(id = 1, type = RuleType.BODY_PHRASE, pattern = "OTP", isEnabled = true)
            coEvery { mockDb.ignoreRuleDao() } returns mockDao
            coEvery { mockDao.getEnabledRules() } returns listOf(rule)

            val provider = SmsProviderHelper.getIgnoreRuleProvider(mockDb)
            val result = provider.getEnabledRules()

            assertEquals(listOf(rule), result)
        }

    @Test
    fun `getMerchantCategoryMappingProvider delegates to merchantCategoryMappingDao`() =
        runTest {
            val mockDb = mockk<AppDatabase>()
            val mockDao = mockk<MerchantCategoryMappingDao>()
            val mapping = MerchantCategoryMapping(parsedName = "Amazon", categoryId = 42)
            coEvery { mockDb.merchantCategoryMappingDao() } returns mockDao
            coEvery { mockDao.getCategoryIdForMerchant("Amazon") } returns 42
            coEvery { mockDao.getCategoryIdForMerchant("Flipkart") } returns null
            coEvery { mockDao.getAll() } returns listOf(mapping)

            val provider = SmsProviderHelper.getMerchantCategoryMappingProvider(mockDb)
            assertEquals(42, provider.getCategoryIdForMerchant("Amazon"))
            assertNull(provider.getCategoryIdForMerchant("Flipkart"))
            assertEquals(mapOf("amazon" to 42), provider.getAllMappings())
        }

    @Test
    fun `getSmsParseTemplateProvider delegates to smsParseTemplateDao`() =
        runTest {
            val mockDb = mockk<AppDatabase>()
            val mockDao = mockk<SmsParseTemplateDao>()
            val template =
                SmsParseTemplate(
                    templateSignature = "sig123",
                    correctedMerchantName = "Uber",
                    originalSmsBody = "Uber ride ₹200",
                    originalAmountStartIndex = 10,
                    originalAmountEndIndex = 14
                )
            coEvery { mockDb.smsParseTemplateDao() } returns mockDao
            coEvery { mockDao.getAllTemplates() } returns listOf(template)
            coEvery { mockDao.getTemplatesBySignature("sig123") } returns listOf(template)
            coEvery { mockDao.getTemplatesBySignature("missing") } returns emptyList()

            val provider = SmsProviderHelper.getSmsParseTemplateProvider(mockDb)
            assertEquals(listOf(template), provider.getAllTemplates())
            assertEquals(listOf(template), provider.getTemplatesBySignature("sig123"))
            assertTrue(provider.getTemplatesBySignature("missing").isEmpty())
        }

    // --- Pre-cached / In-Memory Providers ---

    @Test
    fun `createPreCachedCustomSmsRuleProvider returns pre-cached rules`() =
        runTest {
            val rule =
                CustomSmsRule(
                    id = 1,
                    triggerPhrase = "debited by",
                    merchantRegex = null,
                    amountRegex = null,
                    accountRegex = null,
                    merchantNameExample = null,
                    amountExample = null,
                    accountNameExample = null,
                    priority = 2,
                    sourceSmsBody = "debited by store"
                )
            val provider = SmsProviderHelper.createPreCachedCustomSmsRuleProvider(listOf(rule))

            assertEquals(listOf(rule), provider.getAllRules())
        }

    @Test
    fun `createPreCachedMerchantRenameRuleProvider uses default lowercased map`() =
        runTest {
            val rule1 = MerchantRenameRule(originalName = "Zomato_BLR", newName = "Zomato")
            val rule2 = MerchantRenameRule(originalName = "UBER*TRIP", newName = "Uber")
            val provider = SmsProviderHelper.createPreCachedMerchantRenameRuleProvider(listOf(rule1, rule2))

            assertEquals(listOf(rule1, rule2), provider.getAllRules())
            val map = provider.getAllRulesMap()
            assertEquals(2, map.size)
            assertEquals("Zomato", map["zomato_blr"])
            assertEquals("Uber", map["uber*trip"])
        }

    @Test
    fun `createPreCachedMerchantRenameRuleProvider with custom map returns custom map`() =
        runTest {
            val rule = MerchantRenameRule(originalName = "Test", newName = "Renamed")
            val customMap = mapOf("test" to "Renamed")
            val provider = SmsProviderHelper.createPreCachedMerchantRenameRuleProvider(listOf(rule), customMap)

            assertEquals(listOf(rule), provider.getAllRules())
            assertEquals(customMap, provider.getAllRulesMap())
        }

    @Test
    fun `createPreCachedIgnoreRuleProvider returns pre-cached enabled rules`() =
        runTest {
            val rule = IgnoreRule(id = 5, type = RuleType.BODY_PHRASE, pattern = "PROMO", isEnabled = true)
            val provider = SmsProviderHelper.createPreCachedIgnoreRuleProvider(listOf(rule))

            assertEquals(listOf(rule), provider.getEnabledRules())
        }

    @Test
    fun `createPreCachedMerchantCategoryMappingProvider looks up case-insensitively and returns null for missing`() =
        runTest {
            val mappingsMap = mapOf("swiggy" to 1, "amazon" to 2)
            val provider = SmsProviderHelper.createPreCachedMerchantCategoryMappingProvider(mappingsMap)

            assertEquals(1, provider.getCategoryIdForMerchant("SWIGGY"))
            assertEquals(1, provider.getCategoryIdForMerchant("Swiggy"))
            assertEquals(1, provider.getCategoryIdForMerchant("swiggy"))
            assertEquals(2, provider.getCategoryIdForMerchant("AMAZON"))
            assertNull(provider.getCategoryIdForMerchant("Zomato"))
            assertEquals(mappingsMap, provider.getAllMappings())
        }

    @Test
    fun `createPreCachedSmsParseTemplateProvider groups by signature by default and handles hit and miss`() =
        runTest {
            val template1 =
                SmsParseTemplate(
                    templateSignature = "sig_hdfc",
                    correctedMerchantName = "Swiggy",
                    originalSmsBody = "Spent at Swiggy",
                    originalAmountStartIndex = 9,
                    originalAmountEndIndex = 15
                )
            val template2 =
                SmsParseTemplate(
                    templateSignature = "sig_hdfc",
                    correctedMerchantName = "Zomato",
                    originalSmsBody = "Spent at Zomato",
                    originalAmountStartIndex = 9,
                    originalAmountEndIndex = 15
                )
            val template3 =
                SmsParseTemplate(
                    templateSignature = "sig_sbi",
                    correctedMerchantName = "Amazon",
                    originalSmsBody = "Debited for Amazon",
                    originalAmountStartIndex = 12,
                    originalAmountEndIndex = 18
                )
            val allTemplates = listOf(template1, template2, template3)
            val provider = SmsProviderHelper.createPreCachedSmsParseTemplateProvider(allTemplates)

            assertEquals(allTemplates, provider.getAllTemplates())
            assertEquals(listOf(template1, template2), provider.getTemplatesBySignature("sig_hdfc"))
            assertEquals(listOf(template3), provider.getTemplatesBySignature("sig_sbi"))
            assertTrue(provider.getTemplatesBySignature("sig_unknown").isEmpty())
        }

    @Test
    fun `createPreCachedSmsParseTemplateProvider with explicit map returns matching or empty`() =
        runTest {
            val template =
                SmsParseTemplate(
                    templateSignature = "custom_sig",
                    correctedMerchantName = "Starbucks",
                    originalSmsBody = "Paid to Starbucks",
                    originalAmountStartIndex = 8,
                    originalAmountEndIndex = 17
                )
            val explicitMap = mapOf("custom_sig" to listOf(template))
            val provider = SmsProviderHelper.createPreCachedSmsParseTemplateProvider(listOf(template), explicitMap)

            assertEquals(listOf(template), provider.getAllTemplates())
            assertEquals(listOf(template), provider.getTemplatesBySignature("custom_sig"))
            assertTrue(provider.getTemplatesBySignature("non_existent").isEmpty())
        }
}
