package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IMerchantMappingRepository {
    val allMappings: Flow<List<MerchantMapping>>

    suspend fun insert(mapping: MerchantMapping)
}
