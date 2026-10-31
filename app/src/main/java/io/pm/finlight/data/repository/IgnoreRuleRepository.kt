package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Concrete implementation of [IIgnoreRuleRepository] wrapping [IgnoreRuleDao].
 */
class IgnoreRuleRepository(
    private val dao: IgnoreRuleDao,
) : IIgnoreRuleRepository {
    override fun getAllRules(): Flow<List<IgnoreRule>> = dao.getAll()

    override suspend fun getAllRulesList(): List<IgnoreRule> = dao.getAllList()

    override suspend fun getEnabledRules(): List<IgnoreRule> = dao.getEnabledRules()

    override suspend fun insert(rule: IgnoreRule) {
        dao.insert(rule)
    }

    override suspend fun insertAll(rules: List<IgnoreRule>) {
        dao.insertAll(rules)
    }

    override suspend fun update(rule: IgnoreRule) {
        dao.update(rule)
    }

    override suspend fun delete(rule: IgnoreRule) {
        dao.delete(rule)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    override suspend fun deleteDefaultRules() {
        dao.deleteDefaultRules()
    }
}
