// =================================================================================
// FILE: ./core/src/main/java/io/pm/finlight/core/RuleProviders.kt
// REASON: FEATURE - Added a new CategoryFinderProvider interface. This decouples
// the core SmsParser from the Android-specific CategoryIconHelper, allowing it to
// resolve a category ID from a name in a module-agnostic way.
// =================================================================================
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

/**
 * An interface that defines a contract for finding a category's ID by its name.
 * This is used by the heuristic parser.
 */
interface CategoryFinderProvider {
    fun getCategoryIdByName(name: String): Int?
}
