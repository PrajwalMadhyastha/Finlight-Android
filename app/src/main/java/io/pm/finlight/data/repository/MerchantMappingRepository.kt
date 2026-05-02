package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Repository that abstracts access to the MerchantMapping data source.
 */
class MerchantMappingRepository(private val merchantMappingDao: MerchantMappingDao) {
    /**
     * Retrieves all user-defined merchant mappings from the database.
     */
    val allMappings: Flow<List<MerchantMapping>> = merchantMappingDao.getAllMappings()

    /**
     * Inserts a new or updated mapping into the database.
     */
    suspend fun insert(mapping: MerchantMapping) {
        merchantMappingDao.insert(mapping)
    }
}
