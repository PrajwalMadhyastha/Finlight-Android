package io.pm.finlight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a learned SMS parsing template.
 *
 * This entity is created when a user manually corrects a transaction that was
 * originally parsed from an SMS. It stores the structure of that SMS, allowing
 * the heuristic engine to parse similar messages in the future.
 *
 * @param id The unique identifier for the template.
 * @param templateSignature A normalized, simplified version of the SMS body used for efficient initial lookups.
 * @param originalSmsBody The full, unmodified body of the source SMS.
 * @param originalMerchantStartIndex The starting index of the merchant name in the originalSmsBody.
 * @param originalMerchantEndIndex The ending index of the merchant name in the originalSmsBody.
 * @param originalAmountStartIndex The starting index of the amount in the originalSmsBody.
 * @param originalAmountEndIndex The ending index of the amount in the originalSmsBody.
 */
@Serializable
@Entity(
    tableName = "sms_parse_templates",
    indices = [Index(value = ["templateSignature"])]
)
data class SmsParseTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo
    val templateSignature: String,
    val originalSmsBody: String,
    val originalMerchantStartIndex: Int,
    val originalMerchantEndIndex: Int,
    val originalAmountStartIndex: Int,
    val originalAmountEndIndex: Int
)