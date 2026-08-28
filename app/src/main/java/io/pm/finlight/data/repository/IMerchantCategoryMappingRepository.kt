package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IMerchantCategoryMappingRepository {
    suspend fun insert(mapping: MerchantCategoryMapping)

    fun getAllMappings(): Flow<List<MerchantCategoryMapping>>
}
