package io.pm.finlight

class SmsParseTemplateRepository(
    private val smsParseTemplateDao: SmsParseTemplateDao,
) : ISmsParseTemplateRepository {
    override suspend fun insert(template: SmsParseTemplate) {
        smsParseTemplateDao.insert(template)
    }

    override suspend fun insertAll(templates: List<SmsParseTemplate>) {
        smsParseTemplateDao.insertAll(templates)
    }

    override suspend fun getAllTemplates(): List<SmsParseTemplate> {
        return smsParseTemplateDao.getAllTemplates()
    }

    override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> {
        return smsParseTemplateDao.getTemplatesBySignature(signature)
    }

    override suspend fun deleteAll() {
        smsParseTemplateDao.deleteAll()
    }
}
