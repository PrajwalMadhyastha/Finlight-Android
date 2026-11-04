package io.pm.finlight

interface ISmsParseTemplateRepository : SmsParseTemplateProvider {
    suspend fun insert(template: SmsParseTemplate)

    suspend fun insertAll(templates: List<SmsParseTemplate>)

    override suspend fun getAllTemplates(): List<SmsParseTemplate>

    override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate>

    suspend fun deleteAll()
}
