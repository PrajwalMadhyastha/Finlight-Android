package io.pm.finlight

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a user-defined parsing rule that is independent of the SMS sender.
 * The rule is activated when its 'triggerPhrase' is found within an SMS body.
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
@Entity(
    tableName = "custom_sms_rules",
    indices = [Index(value = ["triggerPhrase"], unique = true)]
)
data class CustomSmsRule(
    @PrimaryKey(autoGenerate = true)
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
