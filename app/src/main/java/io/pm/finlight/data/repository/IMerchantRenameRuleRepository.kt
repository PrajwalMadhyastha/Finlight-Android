package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IMerchantRenameRuleRepository {
    fun getAliasesAsMap(): Flow<Map<String, String>>

    suspend fun insert(rule: MerchantRenameRule)

    suspend fun deleteByOriginalName(originalName: String)
}
