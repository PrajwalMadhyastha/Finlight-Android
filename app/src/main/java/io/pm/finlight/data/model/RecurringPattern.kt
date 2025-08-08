// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringPattern.kt
// REASON: NEW FILE - This entity is the cornerstone of the proactive recurring
// transaction detection feature. It will store a "signature" for each type of
// SMS-based transaction, along with metadata like occurrence count and timestamps,
// allowing the new worker to analyze patterns over time.
// =================================================================================
package io.pm.finlight

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a potential recurring transaction pattern identified from SMS messages.
 *
 * @param smsSignature A unique, normalized hash representing the non-volatile parts of an SMS message body. This acts as the primary key.
 * @param description The common description or merchant name associated with this pattern.
 * @param amount The most recent amount for this transaction pattern.
 * @param transactionType The type of transaction ("income" or "expense").
 * @param accountId The ID of the account most recently associated with this pattern.
 * @param categoryId The ID of the category most recently associated with this pattern.
 * @param occurrences The number of times this signature has been seen.
 * @param firstSeen The timestamp of the first time this pattern was recorded.
 * @param lastSeen The timestamp of the most recent occurrence.
 */
@Entity(
    tableName = "recurring_patterns",
    indices = [Index(value = ["lastSeen"])]
)
data class RecurringPattern(
    @PrimaryKey
    val smsSignature: String,
    val description: String,
    val amount: Double,
    val transactionType: String,
    val accountId: Int,
    val categoryId: Int?,
    var occurrences: Int,
    val firstSeen: Long,
    var lastSeen: Long
)
