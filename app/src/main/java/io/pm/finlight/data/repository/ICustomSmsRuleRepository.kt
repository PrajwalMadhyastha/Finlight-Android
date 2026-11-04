package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing custom SMS parsing rules.
 */
interface ICustomSmsRuleRepository {
    fun getAllRules(): Flow<List<CustomSmsRule>>

    suspend fun getAllRulesList(): List<CustomSmsRule>

    fun getRuleById(id: Int): Flow<CustomSmsRule?>

    suspend fun insert(rule: CustomSmsRule)

    suspend fun insertAll(rules: List<CustomSmsRule>)

    suspend fun update(rule: CustomSmsRule)

    suspend fun delete(rule: CustomSmsRule)

    suspend fun deleteAll()
}
