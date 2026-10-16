package io.pm.finlight.data.model

import io.pm.finlight.TransactionType

/**
 * Filter parameters for searching transactions across accounts, categories, tags, types, and dates.
 */
data class SearchFilter(
    val accountId: Int? = null,
    val categoryId: Int? = null,
    val tagId: Int? = null,
    val transactionType: TransactionType? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
) {
    val isActive: Boolean
        get() =
            accountId != null ||
                categoryId != null ||
                tagId != null ||
                transactionType != null ||
                startDate != null ||
                endDate != null
}
