// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/MerchantRenameRuleRepository.kt
// REASON: FEATURE - Added `deleteByOriginalName` to allow the ViewModel to remove
// a rename rule when a user manually reverts a transaction description to its
// original value. This ensures the UI reflects the user's intent.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Repository that abstracts access to the MerchantRenameRule data source.
 */
class MerchantRenameRuleRepository(private val dao: MerchantRenameRuleDao) {
    /**
     * Retrieves all rename rules and transforms them into a key-value map
     * for efficient lookups at display time.
     * @return A Flow emitting a Map where the key is the original name and the value is the new name.
     */
    fun getAliasesAsMap(): Flow<Map<String, String>> {
        return dao.getAllRules().map { rules ->
            rules.associate { it.originalName to it.newName }
        }
    }

    /**
     * Retrieves all rename rules and transforms them into a key-value map
     * with lowercase keys for case-insensitive efficient lookups.
     * @return A Flow emitting a Map where the key is the lowercase original name and the value is the new name.
     */
    fun getLowercaseAliasesAsMap(): Flow<Map<String, String>> {
        return getAliasesAsMap().map { it.mapKeys { (key, _) -> key.lowercase(Locale.getDefault()) } }
    }

    /**
     * Inserts a new or updated rename rule into the database.
     * @param rule The rule to be saved.
     */
    suspend fun insert(rule: MerchantRenameRule) {
        dao.insert(rule)
    }

    /**
     * Deletes a rename rule based on the original merchant name.
     * @param originalName The original name key of the rule to delete.
     */
    suspend fun deleteByOriginalName(originalName: String) {
        dao.deleteByOriginalName(originalName)
    }
}
