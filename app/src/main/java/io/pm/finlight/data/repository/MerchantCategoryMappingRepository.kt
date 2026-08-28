package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Repository that abstracts access to the MerchantCategoryMapping data source.
 * This provides a clean API for the ViewModel to interact with the learning feature.
 */
class MerchantCategoryMappingRepository(private val dao: MerchantCategoryMappingDao) :
    IMerchantCategoryMappingRepository {
    /**
     * Inserts or updates a merchant-category mapping.
     *
     * @param mapping The mapping to save.
     */
    override suspend fun insert(mapping: MerchantCategoryMapping) {
        dao.insert(mapping)
    }

    /**
     * Retrieves all merchant-category mappings as a reactive Flow.
     */
    override fun getAllMappings(): Flow<List<MerchantCategoryMapping>> = dao.getAllMappingsFlow()
}
