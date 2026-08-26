package io.pm.finlight

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val DB_TYPE_EXPENSE = "expense"
const val DB_TYPE_INCOME = "income"
const val DB_TYPE_TRANSFER = "transfer"

object TransactionTypeSerializer : KSerializer<TransactionType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransactionType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TransactionType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): TransactionType {
        val string = decoder.decodeString()
        return TransactionType.fromStringOrNull(string)
            ?: throw SerializationException("Unknown TransactionType: $string")
    }
}

@Serializable(with = TransactionTypeSerializer::class)
enum class TransactionType(val dbValue: String) {
    EXPENSE(DB_TYPE_EXPENSE),
    INCOME(DB_TYPE_INCOME),
    TRANSFER(DB_TYPE_TRANSFER);

    companion object {
        const val DB_EXPENSE = DB_TYPE_EXPENSE
        const val DB_INCOME = DB_TYPE_INCOME
        const val DB_TRANSFER = DB_TYPE_TRANSFER

        fun fromString(value: String): TransactionType =
            valueOf(value.uppercase())

        fun fromStringOrNull(value: String?): TransactionType? =
            value?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
    }
}

const val DB_STATUS_CONFIRMED = "CONFIRMED"
const val DB_STATUS_PENDING = "PENDING"
const val DB_STATUS_SKIPPED = "SKIPPED"

object TransactionStatusSerializer : KSerializer<TransactionStatus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransactionStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TransactionStatus) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): TransactionStatus {
        val string = decoder.decodeString()
        return TransactionStatus.fromStringOrNull(string)
            ?: throw SerializationException("Unknown TransactionStatus: $string")
    }
}

@Serializable(with = TransactionStatusSerializer::class)
enum class TransactionStatus(val dbValue: String) {
    CONFIRMED(DB_STATUS_CONFIRMED),
    PENDING(DB_STATUS_PENDING),
    SKIPPED(DB_STATUS_SKIPPED);

    companion object {
        const val DB_CONFIRMED = DB_STATUS_CONFIRMED
        const val DB_PENDING = DB_STATUS_PENDING
        const val DB_SKIPPED = DB_STATUS_SKIPPED

        fun fromString(value: String): TransactionStatus =
            valueOf(value.uppercase())

        fun fromStringOrNull(value: String?): TransactionStatus? =
            value?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
    }
}
