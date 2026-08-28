package io.pm.finlight

interface ISmsRepository {
    fun fetchAllSms(startDate: Long?): List<SmsMessage>

    fun getSmsDetailsById(lookupValue: Long): SmsMessage?
}
