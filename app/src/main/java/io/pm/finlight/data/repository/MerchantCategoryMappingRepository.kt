// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MerchantCategoryMappingRepository.kt
// REASON: FIX - The unused `getCategoryIdForMerchant` function has been removed
// to resolve the "UnusedSymbol" warning.
// =================================================================================
package io.pm.finlight

/**
 * Repository that abstracts access to the MerchantCategoryMapping data source.
 * This provides a clean API for the ViewModel to interact with the learning feature.
 */
class MerchantCategoryMappingRepository(private val dao: MerchantCategoryMappingDao) {
    /**
     * Inserts or updates a merchant-category mapping.
     *
     * @param mapping The mapping to save.
     */
    suspend fun insert(mapping: MerchantCategoryMapping) {
        dao.insert(mapping)
    }
}
