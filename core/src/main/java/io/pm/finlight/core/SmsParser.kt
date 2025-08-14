package io.pm.finlight

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

// --- Sealed Class for Parse Result ---
sealed class ParseResult {
    data class Success(val transaction: PotentialTransaction) : ParseResult()
    data class Ignored(val reason: String) : ParseResult()
}

object SmsParser {
    // =================================================================================
    // REASON: FIX - The high-confidence amount regex is now more robust. It includes
    // more keywords (like 'debited by') and also captures an optional currency code.
    // The parsing logic now correctly uses this captured currency instead of defaulting
    // to INR, fixing regressions with foreign currency transactions.
    // =================================================================================
    private val AMOUNT_WITH_HIGH_CONFIDENCE_KEYWORDS_REGEX = "(?:debited by|spent|debited for|credited with|sent|tranx of|transferred from|debited with)\\s+(?:(INR|RS|USD|SGD|MYR|EUR|GBP)[:.]?\\s*)?([\\d,]+\\.?\\d*)|(?:Rs|INR)[:.]?\\s*([\\d,]+\\.?\\d*)".toRegex(RegexOption.IGNORE_CASE)
    private val FALLBACK_AMOUNT_REGEX = "([\\d,]+\\.?\\d*)(INR|RS|USD|SGD|MYR|EUR|GBP)|(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)(?![a-zA-Z])[ .]*)?([\\d,]+\\.?\\d*)|([\\d,]+\\.?\\d*)\\s*(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)\\b)".toRegex(RegexOption.IGNORE_CASE)
    private val EXPENSE_KEYWORDS_REGEX = "\\b(spent|debited|paid|charged|debit instruction for|tranx of|deducted for|sent to|sent|withdrawn|DEBIT with amount|spent on|purchase of|transferred from|frm|debited by|has a debit by transfer of)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val INCOME_KEYWORDS_REGEX = "\\b(credited|received|deposited|refund of|added|credited with salary of|reversal of transaction|unsuccessful and will be reversed|loaded with|has credit for|CREDIT with amount|CREDITED to your account|has a credit|has been CREDITED to your)\\b".toRegex(RegexOption.IGNORE_CASE)

    private val ACCOUNT_PATTERNS =
        listOf(
            "(SB A/c \\*\\d{4}) Debited for".toRegex(RegexOption.IGNORE_CASE),
            "A/C X(\\d{4}) debited by".toRegex(RegexOption.IGNORE_CASE),
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
            "Acc(?:t)? XX(\\d{3,4}) is credited.*-(ICICI Bank)".toRegex(RegexOption.IGNORE_CASE),
            "A/c \\.{3}(\\d{4}).*-\\s*(Bank of Baroda)".toRegex(RegexOption.IGNORE_CASE),
            "in (HDFC Bank A/c XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "from (A/C XXXXXX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on (HDFC Bank Card x\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "From (HDFC Bank A/C x\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "to (HDFC Bank A/c xx\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/c XX\\d{4}) has been debited".toRegex(RegexOption.IGNORE_CASE),
            "in your (HDFC Bank A/c xx\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "sent from (HDFC Bank A/c XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "debited from (HDFC Bank A/c \\*\\*\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on your (SBI Credit Card) ending (\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+) Credited".toRegex(RegexOption.IGNORE_CASE),
            "frm (A/cX\\d+)\\s".toRegex(RegexOption.IGNORE_CASE),
            "your (A/c X\\d+)-debited".toRegex(RegexOption.IGNORE_CASE),
            "(Account\\s+No\\.)\\s+(XXXXXX\\d+)\\s+CREDIT".toRegex(RegexOption.IGNORE_CASE),
            "CREDITED to your (account XXX\\d+)\\s".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+)\\s+has\\s+credit".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+) has a debit".toRegex(RegexOption.IGNORE_CASE),
            "CREDITED to your (A/c XXX\\d+)\\s".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+)\\s+has\\s+a\\s+(?:credit|debit)".toRegex(RegexOption.IGNORE_CASE)
        )
    // --- FIX: Reordered patterns to prioritize keywords like 'by' and 'at' over the generic fallback. ---
    private val MERCHANT_REGEX_PATTERNS =
        listOf(
            // High-specificity patterns first
            "(?:Rs|INR)?\\s*[\\d,.]+\\s+([A-Za-z0-9@]+)\\s+UPI\\s+frm", // FIX: Prioritized this for SBI UPI format
            "trf to ([A-Za-z0-9\\s.&'-]+?)(?: Refno|\\.)", // For "trf to KAGGIS CAKES AND Refno"
            "transfer to\\s+([A-Za-z0-9\\s.-]+?)(?:\\s+Ref No|\\.)", // For "transfer to PERUMAL C Ref No"
            "by transfer from\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.|-)", // For "by transfer from ESIC MODEL HOSP"
            "credited to your A/c.* by ([A-Za-z0-9\\s.&'-]+?)(?:\\.|\\s*Total bal)", // For Canara bank credit
            "credited to your account.* towards ([A-Za-z0-9\\s.&'-]+?)(?:\\.|\\s*Total Avail)", // For Canara bank credit
            "credited to.* from VPA\\s+([A-Za-z0-9\\s@.-]+?)(?:\\s*\\()", // For HDFC VPA credit
            "sent from.* To A/c ([A-Za-z0-9\\s*.'-]+?)(?:\\s*Ref-)", // For HDFC IMPS
            "to\\s+([a-zA-Z0-9.\\-_]+@[a-zA-Z0-9]+)", // UPI IDs or emails first
            "to:(UPI/[\\d/]+)", // Specific UPI format

            // Medium-specificity patterns
            "At\\s+([A-Za-z0-9*.'-]+?)(?:\\s+on|\\.{3})", // For "At MERCHANT."
            "at\\s*\\.\\.\\s*([A-Za-z0-9_\\s]+)\\s*on", // For "at ..MERCHANT_ on"
            "by\\s+([A-Za-z0-9_\\s.]+?)(?:\\.|\\s+Total Bal|\\s+Avl Bal)", // General 'by'
            "towards\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.|Total Avail)",
            "has credit for\\s+([A-Za-z0-9\\s]+?)\\s+of",
            ";\\s*([A-Za-z0-9\\s.&'-]+?)\\s*credited",
            "as (reversal of transaction)",
            "(?:credited|received).*from\\s+([A-Za-z0-9\\s.&'@-]+?)(?:\\.|\\s*\\()",
            "sent to\\s+([A-Za-z0-9\\s.-]+?)(?:\\s+on\\s+|-|$)",
            "(?:Info|Desc):?\\s*([A-Za-z0-9\\s*.'-]+?)(?:\\.|Avl Bal)",
            "(?:\\bat\\b|to\\s+|deducted for your\\s+)([A-Za-z0-9\\s.&'-]+?)(?:\\s+on\\s+|\\s+for\\s+|\\.|\\s+was\\s+)",
            "on\\s+([A-Za-z0-9*.'_ ]+?)(?:\\.|\\s+Avl Bal|\\s+via)",
            "for\\s+(?:[A-Z0-9]+-)?([A-Za-z0-9\\s.-]+?)(?:\\.Avl bal|\\.)",

            // Lower-specificity patterns
            "debited by\\s+([A-Za-z0-9\\s.-]+?)(?:\\s+Ref No|\\.)",

            // Fallback
            "(?:-|\\s)([A-Za-z\\s]+)$"
        ).map { it.toRegex(RegexOption.IGNORE_CASE) }

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

        // --- REFACTORED: Two-pass amount parsing ---
        if (extractedAmount == null) {
            // Pass 1: High-confidence regex
            val highConfidenceMatch = AMOUNT_WITH_HIGH_CONFIDENCE_KEYWORDS_REGEX.find(sms.body)
            if (highConfidenceMatch != null) {
                val currencyStr = highConfidenceMatch.groupValues[1].ifEmpty { null }
                val amountStr = highConfidenceMatch.groupValues[2].ifEmpty { highConfidenceMatch.groupValues[3] }
                extractedAmount = amountStr.replace(",", "").toDoubleOrNull()
                detectedCurrency = if (currencyStr != null) {
                    if (currencyStr.equals("RS", ignoreCase = true)) "INR" else currencyStr.uppercase()
                } else {
                    "INR"
                }
            } else {
                // Pass 2: Fallback regex if the first pass fails
                val allAmountMatches = FALLBACK_AMOUNT_REGEX.findAll(sms.body).toList()
                val matchWithCurrency = allAmountMatches.firstOrNull {
                    val currencyPart1 = it.groups[2]?.value?.ifEmpty { null }
                    val currencyPart2 = it.groups[3]?.value?.ifEmpty { null }
                    val currencyPart3 = it.groups[6]?.value?.ifEmpty { null }
                    currencyPart1 != null || currencyPart2 != null || currencyPart3 != null
                }
                val bestMatch = matchWithCurrency ?: allAmountMatches.firstOrNull()

                if (bestMatch != null) {
                    val (amount, currency) = parseAmountAndCurrency(bestMatch)
                    extractedAmount = amount
                    detectedCurrency = currency
                }
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
                        val containsLetters = potentialName.any { it.isLetter() }
                        val containsDigits = potentialName.any { it.isDigit() }
                        val isLikelyRefNumber = potentialName.length >= 8 && containsDigits && !containsLetters && !potentialName.startsWith("NEFT", ignoreCase = true)

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
                    "(SB A/c \\*\\d{4}) Debited for" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "A/C X(\\d{4}) debited by" ->
                        PotentialAccount(formattedName = "A/C X${match.groupValues[1].trim()}", accountType = "Bank Account")
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
                    "A/c \\.{3}(\\d{4}).*-\\s*(Bank of Baroda)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - ...${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "in (HDFC Bank A/c XX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "from (A/C XXXXXX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "on (HDFC Bank Card x\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Card")
                    "From (HDFC Bank A/C x\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "to (HDFC Bank A/c xx\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "Your (A/c XX\\d{4}) has been debited" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "in your (HDFC Bank A/c xx\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "sent from (HDFC Bank A/c XX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "debited from (HDFC Bank A/c \\*\\*\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "on your (SBI Credit Card) ending (\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} ${match.groupValues[2].trim()}", accountType = "Credit Card")
                    "Your (A/C XXXXX\\d+) Credited" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "frm (A/cX\\d+)\\s" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "your (A/c X\\d+)-debited" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "(Account\\s+No\\.)\\s+(XXXXXX\\d+)\\s+CREDIT" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].replace(Regex("\\s+"), " ")} ${match.groupValues[2]}", accountType = "Bank Account")
                    "CREDITED to your (account XXX\\d+)\\s" ->
                        PotentialAccount(formattedName = "Canara Bank - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "Your (A/C XXXXX\\d+)\\s+has\\s+credit" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "Your (A/C XXXXX\\d+) has a debit" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "CREDITED to your (A/c XXX\\d+)\\s" ->
                        PotentialAccount(formattedName = "Canara Bank - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "Your (A/C XXXXX\\d+)\\s+has\\s+a\\s+(?:credit|debit)" ->
                        PotentialAccount(formattedName = "SBI - ${match.groupValues[1].trim()}", accountType = "Bank Account")

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

    private fun parseAmountAndCurrency(matchResult: MatchResult): Pair<Double?, String?> {
        val groups = matchResult.groupValues
        val amount = (groups[1].ifEmpty { groups[4].ifEmpty { groups[5] } }).replace(",", "").toDoubleOrNull()
        var currency = (groups[2].ifEmpty { groups[3].ifEmpty { groups[6] } }).uppercase()
        if (currency == "RS") currency = "INR"
        return Pair(amount, currency.ifEmpty { null })
    }
}
