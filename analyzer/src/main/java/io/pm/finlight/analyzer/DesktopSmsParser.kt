package io.pm.finlight.analyzer

import io.pm.finlight.*
import io.pm.finlight.SmsMessage

/**
 * Desktop wrapper for the Core SmsParser.
 * Mocks all database-backed providers to allow pure regex parsing.
 */
object DesktopSmsParser {

    private val emptyIgnore = object : IgnoreRuleProvider {
        override suspend fun getEnabledRules() = emptyList<IgnoreRule>()
    }
    
    private val emptyCustom = object : CustomSmsRuleProvider {
        override suspend fun getAllRules() = emptyList<CustomSmsRule>()
    }
    
    private val emptyRename = object : MerchantRenameRuleProvider {
        override suspend fun getAllRules() = emptyList<MerchantRenameRule>()
    }
    
    private val emptyMapping = object : MerchantCategoryMappingProvider {
        override suspend fun getCategoryIdForMerchant(merchantName: String) = null
    }
    
    private val emptyCategory = object : CategoryFinderProvider {
        override fun getCategoryIdByName(name: String) = null
    }
    
    private val emptyTemplate = object : SmsParseTemplateProvider {
        override suspend fun getAllTemplates() = emptyList<SmsParseTemplate>()
        override suspend fun getTemplatesBySignature(signature: String) = emptyList<SmsParseTemplate>()
    }

    suspend fun parse(sms: SmsMessage): PotentialTransaction? {
        return SmsParser.parse(
            sms = sms,
            mappings = emptyMap(),
            customSmsRuleProvider = emptyCustom,
            merchantRenameRuleProvider = emptyRename,
            ignoreRuleProvider = emptyIgnore,
            merchantCategoryMappingProvider = emptyMapping,
            categoryFinderProvider = emptyCategory,
            smsParseTemplateProvider = emptyTemplate
        )
    }
}
