package io.pm.finlight

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

// --- Sealed Class for Parse Result ---
sealed class ParseResult {
    data class Success(val transaction: PotentialTransaction) : ParseResult()
    data class Ignored(val reason: String) : ParseResult()
}

object SmsParser {
    // --- UPDATED: Reordered regex to be specific-first, and enhanced to capture currency codes without spaces (e.g., "15000INR") ---
    private val AMOUNT_WITH_CURRENCY_REGEX = "([\\d,]+\\.?\\d*)(INR|RS|USD|SGD|MYR|EUR|GBP)|(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)\\b[ .]*)?([\\d,]+\\.?\\d*)|([\\d,]+\\.?\\d*)\\s*(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)\\b)".toRegex(RegexOption.IGNORE_CASE)
    private val EXPENSE_KEYWORDS_REGEX = "\\b(spent|debited|paid|charged|debit instruction for|tranx of|deducted for|sent to|sent|withdrawn|DEBIT with amount|spent on|purchase of)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val INCOME_KEYWORDS_REGEX = "\\b(credited|received|deposited|refund of|added|credited with salary of|reversal of transaction|unsuccessful and will be reversed|loaded with)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val ACCOUNT_PATTERNS =
        listOf(
            "On (HDFC Bank) (CREDIT Card) xx(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (Sodexo Card) has been successfully loaded".toRegex(RegexOption.IGNORE_CASE),
            "spent on (IndusInd Card) XX(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(?:credited to your|on your) (ICICI Bank Credit Card) XX(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "your (ICICI Bank Account) XX(\\d{4}) has been credited with".toRegex(RegexOption.IGNORE_CASE),
            "in your a/c no\\. XXXXXXXX(\\d{4}).*-(CANARA BANK)".toRegex(RegexOption.IGNORE_CASE),
            "a/c no\\. XXXXXXXX(\\d{4}) debited.*(Dept of Posts)".toRegex(RegexOption.IGNORE_CASE),
            "Account No\\. XXXXXX(\\d{4}) DEBIT".toRegex(RegexOption.IGNORE_CASE),
            "From (HDFC Bank) A/C \\*(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
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
            // --- NEW: Added a pattern to capture merchants following the word "by" ---
            "by\\s+([A-Za-z0-9_\\s.]+?)(?:\\.|\\s+Total Bal|$)".toRegex(RegexOption.IGNORE_CASE),
            "as (reversal of transaction)".toRegex(RegexOption.IGNORE_CASE),
            "(?:Info|Desc):?\\s*([A-Za-z0-9\\s*.'-]+?)(?:\\.|Avl Bal|$)".toRegex(RegexOption.IGNORE_CASE),
            "At\\s+([A-Za-z0-9*.'-]+?)(?:\\s+on|\\.{3}|$)".toRegex(RegexOption.IGNORE_CASE),
            "credited to VPA\\s+([^@]+)@".toRegex(RegexOption.IGNORE_CASE),
            "(?:credited|received).*from\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.|$)".toRegex(RegexOption.IGNORE_CASE),
            "at\\s*\\.\\.\\s*([A-Za-z0-9_\\s]+)\\s*on".toRegex(RegexOption.IGNORE_CASE),
            ";\\s*([A-Za-z0-9\\s.&'-]+?)\\s*credited".toRegex(RegexOption.IGNORE_CASE),
            "UPI.*(?:to|\\bat\\b)\\s+([A-Za-z0-9\\s.&'()]+?)(?:\\s+on|\\s+Ref|$)".toRegex(RegexOption.IGNORE_CASE),
            "to\\s+([a-zA-Z0-9.\\-_]+@[a-zA-Z0-9]+)".toRegex(RegexOption.IGNORE_CASE),
            "(?:\\bat\\b|to\\s+|deducted for your\\s+)([A-Za-z0-9\\s.&'-]+?)(?:\\s+on\\s+|\\s+for\\s+|\\.|$|\\s+was\\s+)".toRegex(RegexOption.IGNORE_CASE),
            "on\\s+([A-Za-z0-9*.'_ ]+?)(?:\\.|\\s+Avl Bal|$|\\s+via)".toRegex(RegexOption.IGNORE_CASE)
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

    /**
     * Legacy parse function for backward compatibility.
     */
    suspend fun parse(
        sms: SmsMessage,
        mappings: Map<String, String>,
        customSmsRuleProvider: CustomSmsRuleProvider,
        merchantRenameRuleProvider: MerchantRenameRuleProvider,
        ignoreRuleProvider: IgnoreRuleProvider,
        merchantCategoryMappingProvider: MerchantCategoryMappingProvider
    ): PotentialTransaction? {
        return when (val result = parseWithReason(sms, mappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)) {
            is ParseResult.Success -> result.transaction
            is ParseResult.Ignored -> null
        }
    }

    /**
     * Primary parsing function with detailed result.
     */
    suspend fun parseWithReason(
        sms: SmsMessage,
        mappings: Map<String, String>,
        customSmsRuleProvider: CustomSmsRuleProvider,
        merchantRenameRuleProvider: MerchantRenameRuleProvider,
        ignoreRuleProvider: IgnoreRuleProvider,
        merchantCategoryMappingProvider: MerchantCategoryMappingProvider
    ): ParseResult {
        val allIgnoreRules = ignoreRuleProvider.getEnabledRules()
        val senderIgnoreRules = allIgnoreRules.filter { it.type == RuleType.SENDER }
        val bodyIgnoreRules = allIgnoreRules.filter { it.type == RuleType.BODY_PHRASE }

        for (rule in senderIgnoreRules) {
            try {
                if (wildcardToRegex(rule.pattern).matches(sms.sender)) {
                    return ParseResult.Ignored("Sender matches ignore pattern: '${rule.pattern}'")
                }
            } catch (e: PatternSyntaxException) {
                // Ignore invalid regex patterns in rules
            }
        }

        for (rule in bodyIgnoreRules) {
            try {
                if (rule.pattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(sms.body)) {
                    return ParseResult.Ignored("Body contains ignore phrase: '${rule.pattern}'")
                }
            } catch (e: PatternSyntaxException) {
                // Ignore invalid regex in body phrase
            }
        }

        var extractedMerchant: String? = null
        var extractedAmount: Double? = null
        var extractedAccount: PotentialAccount? = null
        var detectedCurrency: String? = null

        val allRules = customSmsRuleProvider.getAllRules()
        val renameRules = merchantRenameRuleProvider.getAllRules().associateBy({ it.originalName }, { it.newName })

        // Custom rule processing (if any)
        for (rule in allRules) {
            if (sms.body.contains(rule.triggerPhrase, ignoreCase = true)) {
                // ... (custom rule logic can be expanded here if needed)
                break
            }
        }

        if (extractedAmount == null) {
            val allAmountMatches = AMOUNT_WITH_CURRENCY_REGEX.findAll(sms.body).toList()
            val matchWithCurrency = allAmountMatches.firstOrNull {
                // --- FIX: Check the correct group (group 2) for the no-space currency pattern ---
                val currencyPart1 = it.groups[2]?.value?.ifEmpty { null } // no-space currency
                val currencyPart2 = it.groups[3]?.value?.ifEmpty { null } // currency before
                val currencyPart3 = it.groups[6]?.value?.ifEmpty { null } // currency after
                currencyPart1 != null || currencyPart2 != null || currencyPart3 != null
            }
            val bestMatch = matchWithCurrency ?: allAmountMatches.firstOrNull()

            if (bestMatch != null) {
                val (amount, currency) = parseAmountAndCurrency(bestMatch)
                extractedAmount = amount
                detectedCurrency = currency
            }
        }

        val amount = extractedAmount ?: return ParseResult.Ignored("No amount found")

        val transactionType =
            when {
                EXPENSE_KEYWORDS_REGEX.containsMatchIn(sms.body) -> "expense"
                INCOME_KEYWORDS_REGEX.containsMatchIn(sms.body) -> "income"
                else -> return ParseResult.Ignored("Could not determine transaction type (debit/credit)")
            }

        var merchantName = extractedMerchant ?: mappings[sms.sender]

        if (merchantName == null) {
            for (pattern in MERCHANT_REGEX_PATTERNS) {
                val match = pattern.find(sms.body)
                if (match != null) {
                    val potentialName = match.groups[1]?.value?.replace("_", " ")?.replace(Regex("\\s+"), " ")?.trim()?.trimEnd('.')
                    if (!potentialName.isNullOrBlank() && !potentialName.contains("call", ignoreCase = true)) {
                        // --- FIX: Refined filter to reject only purely numeric reference numbers, allowing alphanumeric UPI IDs ---
                        val isLikelyRefNumber = potentialName.matches(Regex("^\\d{8,}$")) && !potentialName.startsWith("NEFT", ignoreCase = true)
                        if (!isLikelyRefNumber) {
                            merchantName = potentialName
                            break
                        }
                    }
                }
            }
        }

        if (merchantName != null && renameRules.containsKey(merchantName)) {
            merchantName = renameRules[merchantName]
        }

        var learnedCategoryId: Int? = null
        if (merchantName != null) {
            learnedCategoryId = merchantCategoryMappingProvider.getCategoryIdForMerchant(merchantName)
        }

        val potentialAccount = extractedAccount ?: parseAccount(sms.body, sms.sender)
        val normalizedSender = sms.sender.filter { it.isDigit() }.takeLast(10)
        val normalizedBody = sms.body.trim().replace(Regex("\\s+"), " ")
        val smsHash = (normalizedSender + normalizedBody).hashCode().toString()
        val smsSignature = generateSmsSignature(sms.body)

        val transaction = PotentialTransaction(
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
            detectedCurrencyCode = detectedCurrency,
            date = sms.date
        )
        return ParseResult.Success(transaction)
    }

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
                    "On (HDFC Bank) (CREDIT Card) xx(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} ${match.groupValues[2].trim()} - xx${match.groupValues[3].trim()}", accountType = "Credit Card")
                    "Your (Sodexo Card) has been successfully loaded" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Meal Card")
                    "spent on (IndusInd Card) XX(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Credit Card")
                    "(?:credited to your|on your) (ICICI Bank Credit Card) XX(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Credit Card")
                    "your (ICICI Bank Account) XX(\\d{4}) has been credited with" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "in your a/c no\\. XXXXXXXX(\\d{4}).*-(CANARA BANK)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - xx${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "a/c no\\. XXXXXXXX(\\d{4}) debited.*(Dept of Posts)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - xx${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "Account No\\. XXXXXX(\\d{4}) DEBIT" ->
                        PotentialAccount(formattedName = "Bank Account - xx${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "From (HDFC Bank) A/C \\*(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - *${match.groupValues[2].trim()}", accountType = "Bank Account")
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
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = "Card")
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

    // --- UPDATED: This helper now checks the new capture groups from the reordered amount regex ---
    private fun parseAmountAndCurrency(matchResult: MatchResult): Pair<Double?, String?> {
        val groups = matchResult.groupValues
        val amount = (groups[1].ifEmpty { groups[4].ifEmpty { groups[5] } }).replace(",", "").toDoubleOrNull()
        var currency = (groups[2].ifEmpty { groups[3].ifEmpty { groups[6] } }).uppercase()
        if (currency == "RS") currency = "INR"
        return Pair(amount, currency.ifEmpty { null })
    }
}
