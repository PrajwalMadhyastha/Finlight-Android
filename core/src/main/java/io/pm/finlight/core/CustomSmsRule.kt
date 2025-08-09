package io.pm.finlight

/**
 * Represents a user-defined parsing rule.
 * This is a plain data class, free of Android/Room annotations.
 *
 * @param id The unique identifier for the rule.
 * @param triggerPhrase A stable, unique piece of text from an SMS that identifies
 * when this rule should be applied (e.g., "spent on your SBI Credit Card").
 * @param merchantRegex The regex pattern to extract the merchant name. Can be null.
 * @param amountRegex The regex pattern to extract the transaction amount. Can be null.
 * @param accountRegex The regex pattern to extract the account name/number. Can be null.
 * @param merchantNameExample The user-selected text for the merchant, for display purposes.
 * @param amountExample The user-selected text for the amount, for display purposes.
 * @param accountNameExample The user-selected text for the account, for display purposes.
 * @param priority The execution priority. Higher numbers are checked first.
 * @param sourceSmsBody The original SMS text this rule was created from.
 */
data class CustomSmsRule(
    val id: Int = 0,
    val triggerPhrase: String,
    val merchantRegex: String?,
    val amountRegex: String?,
    val accountRegex: String?,
    val merchantNameExample: String?,
    val amountExample: String?,
    val accountNameExample: String?,
    val priority: Int,
    val sourceSmsBody: String
)
