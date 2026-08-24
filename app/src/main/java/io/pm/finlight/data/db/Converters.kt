package io.pm.finlight.data.db

import androidx.room.TypeConverter
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name.lowercase()

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.fromString(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus =
        TransactionStatus.fromString(value)
}
