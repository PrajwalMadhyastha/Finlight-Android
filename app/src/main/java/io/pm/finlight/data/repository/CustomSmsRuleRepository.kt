package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Concrete implementation of [ICustomSmsRuleRepository] wrapping [CustomSmsRuleDao].
 */
class CustomSmsRuleRepository(
    private val dao: CustomSmsRuleDao,
) : ICustomSmsRuleRepository {
    override fun getAllRules(): Flow<List<CustomSmsRule>> = dao.getAllRules()

    override suspend fun getAllRulesList(): List<CustomSmsRule> = dao.getAllRulesList()

    override fun getRuleById(id: Int): Flow<CustomSmsRule?> = dao.getRuleById(id)

    override suspend fun insert(rule: CustomSmsRule) {
        dao.insert(rule)
    }

    override suspend fun insertAll(rules: List<CustomSmsRule>) {
        dao.insertAll(rules)
    }

    override suspend fun update(rule: CustomSmsRule) {
        dao.update(rule)
    }

    override suspend fun delete(rule: CustomSmsRule) {
        dao.delete(rule)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
