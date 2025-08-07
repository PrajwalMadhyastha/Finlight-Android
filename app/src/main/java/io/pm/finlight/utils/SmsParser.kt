// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/SmsParser.kt
// REASON: REFACTOR - The parsing logic has been significantly hardened.
// - The expense keyword regex is now more specific to reduce false positives from
//   promotional messages (e.g., "purchase experience").
// - New keywords ("debit instruction for", "tranx of") have been added to capture
//   previously missed transactions.
// - A new high-priority regex for UPI payments improves merchant name extraction.
// - Account parsing has been expanded to support new formats from Kotak and Union Bank.
// =================================================================================
package io.pm.finlight.utils

import android.util.Log
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.MerchantCategoryMappingDao
import io.pm.finlight.MerchantRenameRuleDao
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.RuleType
import io.pm.finlight.SmsMessage
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class PotentialAccount(
    val formattedName: String,
    val accountType: String,
)

object SmsParser {
    private val AMOUNT_WITH_CURRENCY_REGEX = "(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)\\b[ .]*)?([\\d,]+\\.?\\d*)|([\\d,]+\\.?\\d*)\\s*(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)\\b)".toRegex(RegexOption.IGNORE_CASE)
    // --- UPDATED: Made keywords more specific and added new ones ---
    private val EXPENSE_KEYWORDS_REGEX = "\\b(spent|debited|paid|charged|payment of|purchase of|debit instruction for|tranx of)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val INCOME_KEYWORDS_REGEX = "\\b(credited|received|deposited|refund of)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val ACCOUNT_PATTERNS =
        listOf(
            // --- NEW: Added patterns for Kotak and Union Bank ---
            "(?:from your|in your) (Kotak Bank) Ac X(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "in your (UNION BANK OF INDIA) A/C XX(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(ICICI Bank) Account XX(\\d{3,4}) credited".toRegex(RegexOption.IGNORE_CASE),
            "(HDFC Bank) : NEFT money transfer".toRegex(RegexOption.IGNORE_CASE),
            "spent from (Pluxee)\\s*(Meal Card wallet), card no\\.\\s*xx(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on your (SBI) (Credit Card) ending with (\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "On (HDFC Bank) (Card) (\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(ICICI Bank) Acc(?:t)? XX(\\d{3,4}) debited".toRegex(RegexOption.IGNORE_CASE),
            "Acc(?:t)? XX(\\d{3,4}) is credited.*-(ICICI Bank)".toRegex(RegexOption.IGNORE_CASE)
        )
    private val MERCHANT_REGEX_PATTERNS =
        listOf(
            // --- NEW: High-priority regex to cleanly parse UPI VPAs ---
            "credited to VPA\\s+([^@]+)@".toRegex(RegexOption.IGNORE_CASE),
            "(?:Info|Desc):?\\s*([A-Za-z0-9\\s*.'-]+?)(?:\\.|Avl Bal|$)".toRegex(RegexOption.IGNORE_CASE),
            "(?:credited|received).*from\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE),
            "at\\s*\\.\\.\\s*([A-Za-z0-9_\\s]+)\\s*on".toRegex(RegexOption.IGNORE_CASE),
            ";\\s*([A-Za-z0-9\\s.&'-]+?)\\s*credited".toRegex(RegexOption.IGNORE_CASE),
            "UPI.*(?:to|\\bat\\b)\\s+([A-Za-z0-9\\s.&'()]+?)(?:\\s+on|\\s+Ref|$)".toRegex(RegexOption.IGNORE_CASE),
            "to\\s+([a-zA-Z0-9.\\-_]+@[a-zA-Z0-9]+)".toRegex(RegexOption.IGNORE_CASE),
            "(?:\\bat\\b|to\\s+)([A-Za-z0-9\\s.&'-]+?)(?:\\s+on\\s+|\\s+for\\s+|\\.|$|\\s+was\\s+)".toRegex(RegexOption.IGNORE_CASE),
            "Info:?\\s*([A-Za-z0-9\\s.&'-]+?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE)
        )

    private val VOLATILE_DATA_REGEX = listOf(
        "\\b(?:rs|inr)[\\s.]*\\d[\\d,.]*".toRegex(RegexOption.IGNORE_CASE), // Amounts (e.g., Rs. 1,234.56)
        "\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}".toRegex(), // Dates (e.g., 31-12-2024)
        "\\d{1,2}-\\w{3}-\\d{2,4}".toRegex(RegexOption.IGNORE_CASE), // Dates (e.g., 31-Dec-2024)
        "\\d{2,}:\\d{2,}(?::\\d{2,})?".toRegex(), // Times (e.g., 14:30:55)
        "\\b(?:ref no|txn id|upi ref|transaction id|ref id)\\s*[:.]?\\s*\\w*\\d+\\w*".toRegex(RegexOption.IGNORE_CASE), // Ref numbers
        "a/c no\\. \\S+".toRegex(RegexOption.IGNORE_CASE), // A/c numbers
        "avl bal[:]?[\\s.]*rs[\\s.]*\\d[\\d,.]*".toRegex(RegexOption.IGNORE_CASE), // Available balance
        "\\b\\d{4,}\\b".toRegex() // Any number with 4 or more digits (likely IDs, etc.)
    )

    private fun generateSmsSignature(body: String): String {
        var signature = body.lowercase()
        VOLATILE_DATA_REGEX.forEach { regex ->
            signature = regex.replace(signature, "")
        }
        return signature.replace(Regex("\\s+"), " ").trim().hashCode().toString()
    }


    private fun parseAccount(smsBody: String, sender: String): PotentialAccount? {
        for (pattern in ACCOUNT_PATTERNS) {
            val match = pattern.find(smsBody)
            if (match != null) {
                return when (pattern.pattern) {
                    // --- NEW: Logic to handle Kotak and Union Bank patterns ---
                    "(?:from your|in your) (Kotak Bank) Ac X(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - x${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "in your (UNION BANK OF INDIA) A/C XX(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "(ICICI Bank) Account XX(\\d{3,4}) credited" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "(HDFC Bank) : NEFT money transfer" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "spent from (Pluxee)\\s*(Meal Card wallet), card no\\.\\s*xx(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = match.groupValues[2].trim())
                    "on your (SBI) (Credit Card) ending with (\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = match.groupValues[2].trim())
                    "On (HDFC Bank) (Card) (\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = match.groupValues[2].trim())
                    "(ICICI Bank) Acc(?:t)? XX(\\d{3,4}) debited" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Savings Account")
                    "Acc(?:t)? XX(\\d{3,4}) is credited.*-(ICICI Bank)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - xx${match.groupValues[1].trim()}", accountType = "Savings Account")
                    else -> null
                }
            }
        }
        return null
    }

    private fun wildcardToRegex(pattern: String): Regex {
        val escaped = Pattern.quote(pattern).replace("*", "\\E.*\\Q")
        return escaped.toRegex(RegexOption.IGNORE_CASE)
    }

    suspend fun parse(
        sms: SmsMessage,
        mappings: Map<String, String>,
        customSmsRuleDao: CustomSmsRuleDao,
        merchantRenameRuleDao: MerchantRenameRuleDao,
        ignoreRuleDao: IgnoreRuleDao,
        merchantCategoryMappingDao: MerchantCategoryMappingDao
    ): PotentialTransaction? {
        Log.d("SmsParser", "--- Parsing SMS from: ${sms.sender} ---")

        val allIgnoreRules = ignoreRuleDao.getEnabledRules()
        val senderIgnoreRules = allIgnoreRules.filter { it.type == RuleType.SENDER }
        val bodyIgnoreRules = allIgnoreRules.filter { it.type == RuleType.BODY_PHRASE }

        for (rule in senderIgnoreRules) {
            try {
                if (wildcardToRegex(rule.pattern).matches(sms.sender)) {
                    Log.d("SmsParser", "Message sender '${sms.sender}' matches ignore pattern '${rule.pattern}'. Ignoring.")
                    return null
                }
            } catch (e: PatternSyntaxException) {
                Log.e("SmsParser", "Invalid regex from sender pattern: '${rule.pattern}'", e)
            }
        }

        for (rule in bodyIgnoreRules) {
            try {
                if (rule.pattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(sms.body)) {
                    Log.d("SmsParser", "Message body contains ignore phrase '${rule.pattern}'. Ignoring.")
                    return null
                }
            } catch (e: PatternSyntaxException) {
                Log.e("SmsParser", "Invalid regex in body phrase: '${rule.pattern}'", e)
            }
        }

        var extractedMerchant: String? = null
        var extractedAmount: Double? = null
        var extractedAccount: PotentialAccount? = null
        var detectedCurrency: String? = null

        val allRules = customSmsRuleDao.getAllRules().first()
        val renameRules = merchantRenameRuleDao.getAllRules().first().associateBy({ it.originalName }, { it.newName })
        Log.d("SmsParser", "Found ${allRules.size} custom rules and ${renameRules.size} rename rules.")


        for (rule in allRules) {
            if (sms.body.contains(rule.triggerPhrase, ignoreCase = true)) {
                Log.d("SmsParser", "SUCCESS: Found matching trigger phrase '${rule.triggerPhrase}' for rule ID ${rule.id}.")

                rule.merchantRegex?.let { regexStr ->
                    try {
                        val match = regexStr.toRegex().find(sms.body)
                        if (match != null && match.groupValues.size > 1) {
                            extractedMerchant = match.groupValues[1].trim()
                        }
                    } catch (e: PatternSyntaxException) { /* Ignore */ }
                }

                rule.amountRegex?.let { regexStr ->
                    try {
                        val match = regexStr.toRegex().find(sms.body)
                        if (match != null && match.groupValues.size > 1) {
                            val amountMatch = AMOUNT_WITH_CURRENCY_REGEX.find(match.groupValues[1])
                            if (amountMatch != null) {
                                val (amount, currency) = parseAmountAndCurrency(amountMatch)
                                extractedAmount = amount
                                detectedCurrency = currency
                            }
                        }
                    } catch (e: PatternSyntaxException) { /* Ignore */ }
                }

                rule.accountRegex?.let { regexStr ->
                    try {
                        val match = regexStr.toRegex().find(sms.body)
                        if (match != null && match.groupValues.size > 1) {
                            val accountName = match.groupValues[1].trim()
                            extractedAccount = PotentialAccount(formattedName = accountName, accountType = "Custom")
                            Log.d("SmsParser", "Extracted Account: '$accountName' using custom rule.")
                        }
                    } catch (e: PatternSyntaxException) {
                        Log.e("SmsParser", "Invalid account regex for rule ID ${rule.id}", e)
                    }
                }
                break
            }
        }

        if (extractedAmount == null) {
            val allAmountMatches = AMOUNT_WITH_CURRENCY_REGEX.findAll(sms.body).toList()
            val matchWithCurrency = allAmountMatches.firstOrNull {
                val currencyPart1 = it.groups[1]?.value?.ifEmpty { null }
                val currencyPart2 = it.groups[4]?.value?.ifEmpty { null }
                currencyPart1 != null || currencyPart2 != null
            }
            val bestMatch = matchWithCurrency ?: allAmountMatches.firstOrNull()

            if (bestMatch != null) {
                val (amount, currency) = parseAmountAndCurrency(bestMatch)
                extractedAmount = amount
                detectedCurrency = currency
            }
        }

        val amount = extractedAmount ?: return null

        val transactionType =
            when {
                EXPENSE_KEYWORDS_REGEX.containsMatchIn(sms.body) -> "expense"
                INCOME_KEYWORDS_REGEX.containsMatchIn(sms.body) -> "income"
                else -> return null
            }

        var merchantName = extractedMerchant ?: mappings[sms.sender]

        if (merchantName == null) {
            for (pattern in MERCHANT_REGEX_PATTERNS) {
                val match = pattern.find(sms.body)
                if (match != null) {
                    val potentialName = match.groups[1]?.value?.replace("_", " ")?.replace(Regex("\\s+"), " ")?.trim()
                    if (!potentialName.isNullOrBlank() && !potentialName.contains("call", ignoreCase = true)) {
                        if (potentialName.startsWith("NEFT", ignoreCase = true) || !potentialName.matches(Regex(".*\\d{6,}.*"))) {
                            merchantName = potentialName
                            break
                        }
                    }
                }
            }
        }

        if (merchantName != null && renameRules.containsKey(merchantName)) {
            val originalName = merchantName
            merchantName = renameRules[merchantName]
            Log.d("SmsParser", "Applied rename rule: '$originalName' -> '$merchantName'")
        }

        var learnedCategoryId: Int? = null
        if (merchantName != null) {
            learnedCategoryId = merchantCategoryMappingDao.getCategoryIdForMerchant(merchantName)
            if (learnedCategoryId != null) {
                Log.d("SmsParser", "Found learned category ID $learnedCategoryId for merchant '$merchantName'")
            }
        }

        val potentialAccount = extractedAccount ?: parseAccount(sms.body, sms.sender)
        val normalizedSender = sms.sender.filter { it.isDigit() }.takeLast(10)
        val normalizedBody = sms.body.trim().replace(Regex("\\s+"), " ")
        val smsHash = (normalizedSender + normalizedBody).hashCode().toString()
        val smsSignature = generateSmsSignature(sms.body)


        return PotentialTransaction(
            sourceSmsId = sms.id,
            smsSender = sms.sender,
            amount = amount,
            transactionType = transactionType,
            merchantName = merchantName,
            originalMessage = sms.body,
            potentialAccount = potentialAccount,
            sourceSmsHash = smsHash,
            categoryId = learnedCategoryId,
            smsSignature = smsSignature,
            detectedCurrencyCode = detectedCurrency
        )
    }

    private fun parseAmountAndCurrency(matchResult: MatchResult): Pair<Double?, String?> {
        val groups = matchResult.groupValues
        val amount = (groups[2].ifEmpty { groups[3] }).replace(",", "").toDoubleOrNull()
        var currency = (groups[1].ifEmpty { groups[4] }).uppercase()
        if (currency == "RS") currency = "INR"
        return Pair(amount, currency.ifEmpty { null })
    }
}
