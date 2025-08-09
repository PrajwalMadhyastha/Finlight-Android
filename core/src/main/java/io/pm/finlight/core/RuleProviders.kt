package io.pm.finlight

/**
 * An interface that defines a contract for providing ignore rules.
 * This allows the core SmsParser to remain independent of the data source (e.g., Room database).
 */
interface IgnoreRuleProvider {
    suspend fun getEnabledRules(): List<IgnoreRule>
}

/**
 * An interface that defines a contract for providing custom SMS parsing rules.
 */
interface CustomSmsRuleProvider {
    suspend fun getAllRules(): List<CustomSmsRule>
}

/**
 * An interface that defines a contract for providing merchant rename rules.
 */
interface MerchantRenameRuleProvider {
    suspend fun getAllRules(): List<MerchantRenameRule>
}

/**
 * An interface that defines a contract for providing learned merchant-to-category mappings.
 */
interface MerchantCategoryMappingProvider {
    suspend fun getCategoryIdForMerchant(merchantName: String): Int?
}
