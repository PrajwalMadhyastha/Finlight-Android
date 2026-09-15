package io.pm.finlight

interface ISmsRepository {
    suspend fun fetchAllSms(startDate: Long? = null): List<SmsMessage>

    suspend fun fetchAllSms(
        startDate: Long?,
        endDate: Long?,
    ): List<SmsMessage>

    suspend fun getSmsDetailsById(lookupValue: Long): SmsMessage?
}
