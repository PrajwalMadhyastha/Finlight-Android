package io.pm.finlight

/**
 * A data class to hold the structured information extracted from an SMS message.
 * This is a temporary object, created before a full 'Transaction' is saved to the database.
 *
 * @param amount The monetary value of the transaction.
 * @param transactionType The type of transaction, either 'expense' or 'income'.
 * @param merchantName The name of the merchant, if it can be determined. This may have been
 *        renamed by a [MerchantRenameRule] and is the display name.
 * @param originalMerchantName The raw merchant name extracted from the SMS, BEFORE any
 *        [MerchantRenameRule] is applied. This is what should be stored in
 *        [Transaction.originalDescription]. Always populated by SmsParser.enrichTransaction.
 *        Use this - not merchantName - as Transaction.originalDescription.
 * @param originalMessage The original SMS body, for reference and debugging.
 * @param potentialAccount Holds the parsed account name and type, if found.
 * @param categoryId The ID of a learned category, if a mapping exists for the merchant.
 * @param smsSignature A stable hash of the SMS body used for pattern detection.
 * @param isForeignCurrency A flag passed from the notification to indicate user's currency choice.
 * @param detectedCurrencyCode The currency code (e.g., "INR", "USD") found in the SMS.
 * @param date The timestamp of the original SMS message.
 */
data class PotentialTransaction(
    val sourceSmsId: Long,
    val smsSender: String,
    val amount: Double,
    val transactionType: String,
    val merchantName: String?,
    val originalMessage: String,
    val potentialAccount: PotentialAccount? = null,
    val sourceSmsHash: String? = null,
    val categoryId: Int? = null,
    val smsSignature: String? = null,
    val isForeignCurrency: Boolean? = null,
    val detectedCurrencyCode: String? = null,
    val date: Long = System.currentTimeMillis(),
    /** True when the amount parser flagged this as potentially incorrect (see Option A/C/D). */
    val needsReview: Boolean = false,
    /** Human-readable reason why this transaction was flagged for review. */
    val suspicionReason: String? = null,
    /**
     * The raw merchant name from the SMS BEFORE any rename rule was applied.
     * Always populated by [SmsParser.enrichTransaction].
     * Use this — not [merchantName] — as [Transaction.originalDescription].
     */
    val originalMerchantName: String? = null,
)
