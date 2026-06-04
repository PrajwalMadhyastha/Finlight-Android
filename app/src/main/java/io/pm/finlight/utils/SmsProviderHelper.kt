package io.pm.finlight.utils

import io.pm.finlight.CategoryFinderProvider
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleProvider
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleProvider
import io.pm.finlight.MerchantCategoryMappingProvider
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleProvider
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParseTemplateProvider
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.first

object SmsProviderHelper {
    fun getCategoryFinderProvider(): CategoryFinderProvider =
        object : CategoryFinderProvider {
            override fun getCategoryIdByName(name: String): Int? = CategoryIconHelper.getCategoryIdByName(name)
        }

    fun getCustomSmsRuleProvider(db: AppDatabase): CustomSmsRuleProvider =
        object : CustomSmsRuleProvider {
            override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
        }

    fun getMerchantRenameRuleProvider(db: AppDatabase): MerchantRenameRuleProvider =
        object : MerchantRenameRuleProvider {
            override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()

            override suspend fun getAllRulesMap(): Map<String, String> =
                db.merchantRenameRuleDao().getAllRulesList().associateBy({ it.originalName.lowercase() }, { it.newName })
        }

    fun getIgnoreRuleProvider(db: AppDatabase): IgnoreRuleProvider =
        object : IgnoreRuleProvider {
            override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
        }

    fun getMerchantCategoryMappingProvider(db: AppDatabase): MerchantCategoryMappingProvider =
        object : MerchantCategoryMappingProvider {
            override suspend fun getCategoryIdForMerchant(merchantName: String): Int? =
                db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)

            override suspend fun getAllMappings(): Map<String, Int> =
                db.merchantCategoryMappingDao().getAll().associateBy({ it.parsedName.lowercase() }, { it.categoryId })
        }

    fun getSmsParseTemplateProvider(db: AppDatabase): SmsParseTemplateProvider =
        object : SmsParseTemplateProvider {
            override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()

            override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> =
                db.smsParseTemplateDao().getTemplatesBySignature(signature)
        }
}
