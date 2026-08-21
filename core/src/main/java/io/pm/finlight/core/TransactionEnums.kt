package io.pm.finlight

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER;

    companion object {
        fun fromString(value: String): TransactionType =
            valueOf(value.uppercase())

        fun fromStringOrNull(value: String?): TransactionType? =
            value?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
    }
}

enum class TransactionStatus {
    CONFIRMED,
    PENDING,
    SKIPPED;

    companion object {
        fun fromString(value: String): TransactionStatus =
            valueOf(value.uppercase())

        fun fromStringOrNull(value: String?): TransactionStatus? =
            value?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
    }
}
