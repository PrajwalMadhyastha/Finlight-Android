package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing SMS ignore rules.
 */
interface IIgnoreRuleRepository {
    fun getAllRules(): Flow<List<IgnoreRule>>

    fun getAll(): Flow<List<IgnoreRule>> = getAllRules()

    suspend fun getAllRulesList(): List<IgnoreRule>

    suspend fun getEnabledRules(): List<IgnoreRule>

    suspend fun insert(rule: IgnoreRule)

    suspend fun insertAll(rules: List<IgnoreRule>)

    suspend fun update(rule: IgnoreRule)

    suspend fun delete(rule: IgnoreRule)

    suspend fun deleteAll()

    suspend fun deleteDefaultRules()
}
