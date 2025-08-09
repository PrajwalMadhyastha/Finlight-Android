package io.pm.finlight

/**
 * A simple data class to hold the relevant information from an SMS message.
 */
data class SmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val date: Long,
)
