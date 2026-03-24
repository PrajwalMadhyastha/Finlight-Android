// =================================================================================
// FILE: ./core/src/main/java/io/pm/finlight/core/RuleProviders.kt
// REASON: FEATURE - Added a new SmsParseTemplateProvider interface. This decouples
// the core SmsParser from the database implementation, allowing it to request
// learned heuristic templates in a module-agnostic way.
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
    suspend fun getAllRulesMap(): Map<String, String> = emptyMap() // Maps originalName (lowercase) to newName
}

/**
 * An interface that defines a contract for providing learned merchant-to-category mappings.
 */
interface MerchantCategoryMappingProvider {
    suspend fun getCategoryIdForMerchant(merchantName: String): Int?
    suspend fun getAllMappings(): Map<String, Int> = emptyMap() // Maps merchantName (lowercase) to categoryId
}

/**
 * An interface that defines a contract for finding a category's ID by its name.
 * This is used by the heuristic parser.
 */
interface CategoryFinderProvider {
    fun getCategoryIdByName(name: String): Int?
}

/**
 * An interface that defines a contract for providing learned SMS parsing templates
 * for the heuristic engine.
 */
interface SmsParseTemplateProvider {
    suspend fun getAllTemplates(): List<SmsParseTemplate>
    // --- NEW: Add a more efficient query for the parser ---
    suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate>
}