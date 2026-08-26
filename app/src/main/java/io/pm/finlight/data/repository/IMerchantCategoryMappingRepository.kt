package io.pm.finlight

interface IMerchantCategoryMappingRepository {
    suspend fun insert(mapping: MerchantCategoryMapping)
}
