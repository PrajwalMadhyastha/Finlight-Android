package io.pm.finlight.data.db

import androidx.room.TypeConverter
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType

import io.pm.finlight.data.db.entity.MergeType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.dbValue

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.fromString(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus =
        TransactionStatus.fromString(value)

    @TypeConverter
    fun fromMergeType(value: MergeType): String = value.name

    @TypeConverter
    fun toMergeType(value: String): MergeType =
        MergeType.fromString(value)
}
