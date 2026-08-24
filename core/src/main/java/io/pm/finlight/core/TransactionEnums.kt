package io.pm.finlight

const val DB_TYPE_EXPENSE = "expense"
const val DB_TYPE_INCOME = "income"
const val DB_TYPE_TRANSFER = "transfer"

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
