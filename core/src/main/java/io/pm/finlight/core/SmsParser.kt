// =================================================================================
// FILE: ./core/src/main/java/io/pm/finlight/core/SmsParser.kt
// REASON: FIX - Corrected a regex pattern for ICICI Bank messages by making it
// more specific (requiring a period after the word "credited") and increasing
// its priority in the pattern list. This resolves a bug where the merchant name
// was being parsed incorrectly for certain debit transactions.
// =================================================================================
package io.pm.finlight

import io.pm.finlight.core.CATEGORY_KEYWORD_MAP
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.math.min

// --- Sealed Class for detailed parse results ---
sealed class ParseResult {
    data class Success(val transaction: PotentialTransaction) : ParseResult()
    data class Ignored(val reason: String) : ParseResult()
    data class NotParsed(val reason: String) : ParseResult()
    data class IgnoredByClassifier(val confidence: Float, val reason: String = "Ignored by ML model") : ParseResult()
}

object SmsParser {
    private const val TAG = "SmsParser"

    private val AMOUNT_WITH_HIGH_CONFIDENCE_KEYWORDS_REGEX = "(?:debited by|spent|debited for|credited with|sent|tranx of|transferred from|debited with)\\s+(?:(INR|RS|USD|SGD|MYR|EUR|GBP)[:.]?\\s*)?([\\d,]+\\.?\\d*)|(?:Rs|INR)[:.]?\\s*([\\d,]+\\.?\\d*)".toRegex(RegexOption.IGNORE_CASE)
    private val FALLBACK_AMOUNT_REGEX = "([\\d,]+\\.?\\d*)(INR|RS|USD|SGD|MYR|EUR|GBP)|(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)(?![a-zA-Z])[ .]*)?([\\d,]+\\.?\\d*)|([\\d,]+\\.?\\d*)\\s*(?:\\b(INR|RS|USD|SGD|MYR|EUR|GBP)\\b)".toRegex(RegexOption.IGNORE_CASE)
    val EXPENSE_KEYWORDS_REGEX = "\\b(spent|debited|paid|charged|debit instruction for|tranx of|deducted for|sent to|sent|withdrawn|DEBIT with amount|spent on|purchase of|transferred from|frm|debited by|has a debit by transfer of|without OTP/PIN|successfully debited with|was spent from|Deducted!?|Dr with|debit of|debit)\\b|transaction has been recorded".toRegex(RegexOption.IGNORE_CASE)
    val INCOME_KEYWORDS_REGEX = "\\b(credited|received|deposited|refund of|added|credited with salary of|reversal of transaction|unsuccessful and will be reversed|loaded with|has credit for|CREDIT with amount|CREDITED to your account|has a credit|has been CREDITED to your|is Credited for|We have credited)\\b".toRegex(RegexOption.IGNORE_CASE)

    private val ACCOUNT_PATTERNS =
        listOf(
            "debited from (A/c X*\\d{4}) for NEFT".toRegex(RegexOption.IGNORE_CASE),
            "credited on your (credit card ending \\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "from your (HDFC Bank A/c X*\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on (HDFC Bank Prepaid Card \\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "From (HDFC Bank A/[Cc] X*\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "spent Card no\\. (XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (AC XXXXX\\d+) Debited".toRegex(RegexOption.IGNORE_CASE),
            "credited to your (Acc No\\. XXXXX\\d+).*-(SBI)".toRegex(RegexOption.IGNORE_CASE),
            "(Ac XXXXXXXX\\d+).*-(PNB)".toRegex(RegexOption.IGNORE_CASE),
            "withdrawn at.*?from\\s+(A/cX\\d+)".toRegex(RegexOption.IGNORE_CASE),
            "(?:DEBITED|CREDITED) to your (account XXX\\d+)\\s".toRegex(RegexOption.IGNORE_CASE),
            "(Account\\s+No\\.\\s+X+\\d+)\\s+(?:DEBIT|CREDIT)".toRegex(RegexOption.IGNORE_CASE),
            "(SB A/c \\*\\d{4}) (?:Debited|Credited) for".toRegex(RegexOption.IGNORE_CASE),
            "credited to your (A/c No XX\\d{4}) on".toRegex(RegexOption.IGNORE_CASE),
            "(HDFC Bank Card x\\d{4}) At".toRegex(RegexOption.IGNORE_CASE),
            "your (ICICI Bank Account X*\\d{3,4}) has been credited".toRegex(RegexOption.IGNORE_CASE),
            "(ICICI Bank Account X*\\d{3,4}) is credited with".toRegex(RegexOption.IGNORE_CASE),
            "credited your (ICICI Bank Account XX\\d{3,4}) with".toRegex(RegexOption.IGNORE_CASE),
            "(ICICI Bank Account X*\\d{3,4}) is debited with".toRegex(RegexOption.IGNORE_CASE),
            "spent on (ICICI Bank Card XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "spent using (ICICI Bank Card XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "credited to (ICICI Bank Credit Card XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(Account XX\\d{3,4}) has been debited.*-(ICICI Bank)".toRegex(RegexOption.IGNORE_CASE),
            "from (Meal Card Wallet linked to your Pluxee Card xx\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "ur (A/cX\\d{4}) credited by".toRegex(RegexOption.IGNORE_CASE),
            "linked to (Credit Card XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "your (A/c X\\d{4})-credited.*-(SBI)".toRegex(RegexOption.IGNORE_CASE),
            "A/C X(\\d{4}) debited by".toRegex(RegexOption.IGNORE_CASE),
            "On (HDFC Bank) (CREDIT Card) xx(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (Sodexo Card) has been successfully loaded".toRegex(RegexOption.IGNORE_CASE),
            "spent on (IndusInd Card) XX(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(?:credited to your|on your) (ICICI Bank Credit Card) XX(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "your (ICICI Bank Account) X*(\\d{4}) has been credited with".toRegex(RegexOption.IGNORE_CASE),
            "in your a/c no\\. XXXXXXXX(\\d{4}).*-(CANARA BANK)".toRegex(RegexOption.IGNORE_CASE),
            "a/c no\\. XXXXXXXX(\\d{4}) debited.*(Dept of Posts)".toRegex(RegexOption.IGNORE_CASE),
            "Account No\\. XXXXXX(\\d{4}) CREDIT".toRegex(RegexOption.IGNORE_CASE),
            "From (HDFC Bank) A/C \\*(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(?:from your|in your) (Kotak Bank) Ac X(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "in your (UNION BANK OF INDIA) A/C XX(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(ICICI Bank) Account X*(\\d{3,4}) credited".toRegex(RegexOption.IGNORE_CASE),
            "(HDFC Bank) : NEFT money transfer".toRegex(RegexOption.IGNORE_CASE),
            "spent from (Pluxee)\\s*(Meal Card wallet), card no\\.\\s*xx(\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on your (SBI) (Credit Card) ending with (\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "On (HDFC Bank) (Card) (\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "(ICICI Bank) Acc(?:t)? X*(\\d{3,4}) debited".toRegex(RegexOption.IGNORE_CASE),
            "Acc(?:t)? X*(\\d{3,4}) is credited.*-(ICICI Bank)".toRegex(RegexOption.IGNORE_CASE),
            "A/c \\.{3}(\\d{4}).*-\\s*(Bank of Baroda)".toRegex(RegexOption.IGNORE_CASE),
            "in (HDFC Bank A/c XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "from (A/C XXXXXX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on (HDFC Bank Card x\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "to (HDFC Bank A/c xx\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/c XX\\d{4}) has been debited".toRegex(RegexOption.IGNORE_CASE),
            "in your (HDFC Bank A/c xx\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "sent from (HDFC Bank A/c XX\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "debited from (HDFC Bank A/c \\*\\*\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "on your (SBI Credit Card) ending (\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+) Credited".toRegex(RegexOption.IGNORE_CASE),
            "frm (A/cX\\d+)\\s".toRegex(RegexOption.IGNORE_CASE),
            "your (A/c X\\d+)-debited".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+)\\s+has\\s+credit".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+) has a debit".toRegex(RegexOption.IGNORE_CASE),
            "CREDITED to your (A/c XXX\\d+)\\s".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+)\\s+has\\s+a\\s+(?:credit|debit)".toRegex(RegexOption.IGNORE_CASE),
            "Your (SB A/c \\*\\*\\d+) is Credited for".toRegex(RegexOption.IGNORE_CASE),
            "for your (SBI Debit Card ending with \\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "Your (A/C XXXXX\\d+) Debited".toRegex(RegexOption.IGNORE_CASE)
        )
    private val MERCHANT_REGEX_PATTERNS =
        listOf(
            "at\\s+'([^']+)'\\s+from".toRegex(RegexOption.IGNORE_CASE),
            "at\\s+(.*?)\\s+\\(UPI Ref No".toRegex(RegexOption.IGNORE_CASE),
            "^([A-Z0-9*\\s]+) refund of".toRegex(RegexOption.IGNORE_CASE),
            // --- FIX: Increased specificity and priority of this ICICI pattern ---
            ";\\s*([A-Za-z0-9\\s.&'-]+?)\\s+credited\\.".toRegex(RegexOption.IGNORE_CASE),
            "towards\\s+(.+?)(?:\\. UPI Ref| for Autopay)".toRegex(RegexOption.IGNORE_CASE),
            "transfer from\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\s+Ref No|$)".toRegex(RegexOption.IGNORE_CASE),
            "towards\\s+(annual maintenance charges)\\s+for".toRegex(RegexOption.IGNORE_CASE),
            "sent to\\s+(.+?)(?:-SBI)".toRegex(RegexOption.IGNORE_CASE),
            "for (NEFT transaction)".toRegex(RegexOption.IGNORE_CASE),
            "To (A/c [\\w\\s]+) IMPS".toRegex(RegexOption.IGNORE_CASE),
            "from ([A-Z\\s]+ IND) on".toRegex(RegexOption.IGNORE_CASE),
            "(?:towards)\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.|\\s+Total Avail)".toRegex(RegexOption.IGNORE_CASE),
            "without OTP/PIN.*?At\\s+([A-Za-z0-9*.'-]+?)(?:\\s+on)".toRegex(RegexOption.IGNORE_CASE),
            "on\\s+([A-Z]+\\*[A-Za-z]+\\.[a-z]+)\\s+-".toRegex(RegexOption.IGNORE_CASE),
            "towards\\s+([A-Z\\s]+?)\\s+for".toRegex(RegexOption.IGNORE_CASE),
            "by (Account linked to mobile number XXXXX\\d{5})".toRegex(RegexOption.IGNORE_CASE),
            "by VPA\\s+([A-Za-z0-9@.-]+)".toRegex(RegexOption.IGNORE_CASE),
            "(?:http|https|www)[^\\s]+\\s+(.*)$".toRegex(RegexOption.IGNORE_CASE),
            "(?:withdrawn at)\\s+([A-Za-z0-9\\s*.'-]+?)(?:\\s+from)".toRegex(RegexOption.IGNORE_CASE),
            "sent to\\s+([A-Za-z0-9\\s.&'-]+?)(?:-SBI|$)".toRegex(RegexOption.IGNORE_CASE),
            "(Payment) of Rs".toRegex(RegexOption.IGNORE_CASE),
            "has credit for\\s+(.*?)\\s+of Rs".toRegex(RegexOption.IGNORE_CASE),
            "has a credit by Transfer of.*?by\\s+([A-Za-z0-9\\s]+?)(?:\\.|\\s+Avl Bal)".toRegex(RegexOption.IGNORE_CASE),
            "towards\\s+(.+?)(?:\\. Avl Bal|-SBI)".toRegex(RegexOption.IGNORE_CASE),
            "towards\\s+(.+?)(?:\\s+for your)".toRegex(RegexOption.IGNORE_CASE),
            "(?:Rs|INR)?\\s*[\\d,.]+\\s+([A-Za-z0-9@]+)\\s+UPI\\s+frm".toRegex(RegexOption.IGNORE_CASE),
            "trf to ([A-Za-z0-9\\s.&'-]+?)(?: Refno|\\.)".toRegex(RegexOption.IGNORE_CASE),
            "transfer(?:red)? to\\s+([A-Za-z0-9\\s.-]+?)(?:\\s+Ref No|\\s*\\.\\s*Avl Balance)".toRegex(RegexOption.IGNORE_CASE),
            "by transfer from\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.|-)".toRegex(RegexOption.IGNORE_CASE),
            "by\\s+([A-Za-z0-9_\\s.&'-]+?)(?:,|\\s+INFO:|\\.|\\s+Total Bal|\\s+Avl Bal)".toRegex(RegexOption.IGNORE_CASE),
            "credited to your A/c.* by ([A-Za-z0-9\\s.&'-]+?)(?:\\.|\\s*Total bal)".toRegex(RegexOption.IGNORE_CASE),
            "credited to your account.* towards ([A-Za-z0-9\\s.&'-]+?)(?:\\.|\\s*Total Avail)".toRegex(RegexOption.IGNORE_CASE),
            "credited to.* from VPA\\s+([A-Za-z0-9\\s@.-]+?)(?:\\s*\\()".toRegex(RegexOption.IGNORE_CASE),
            "sent from.* To A/c ([A-Za-z0-9\\s*.'-]+?)(?:\\s*Ref-)".toRegex(RegexOption.IGNORE_CASE),
            "to\\s+([a-zA-Z0-9.\\-_]+@[a-zA-Z0-9]+)".toRegex(RegexOption.IGNORE_CASE),
            "to:(UPI/[\\d/]+)".toRegex(RegexOption.IGNORE_CASE),
            "at\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\.\\s+Txn no)".toRegex(RegexOption.IGNORE_CASE),
            "\\d{2}-\\d{2}-\\d{2,4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+([A-Za-z0-9\\s.&'-]+?)\\s+Avl Lmt".toRegex(RegexOption.IGNORE_CASE),
            "At\\s+([A-Za-z0-9*.'-]+?)(?:\\s+on|\\.{3}|\\.)".toRegex(RegexOption.IGNORE_CASE),
            "at\\s*\\.\\.\\s*([A-Za-z0-9_\\s]+)\\s*on".toRegex(RegexOption.IGNORE_CASE),
            "as (reversal of transaction)".toRegex(RegexOption.IGNORE_CASE),
            "(?:credited|received).*from\\s+([A-Za-z0-9\\s.&'@-]+?)(?:\\.|\\s*\\()".toRegex(RegexOption.IGNORE_CASE),
            "sent to\\s+([A-Za-z0-9\\s.-]+?)(?:\\s+on\\s+|\\s+|-|$)".toRegex(RegexOption.IGNORE_CASE),
            "(?:Info|Desc):?\\s*([A-Za-z0-9\\s*.'-]+?)(?:\\.|Avl Bal)".toRegex(RegexOption.IGNORE_CASE),
            "(?:\\bat\\b|to\\s+|deducted for your\\s+)([A-Za-z0-9\\s.&'-]+?)(?:\\s+on\\s+|\\s+for\\s+|\\.|\\s+was\\s+)".toRegex(RegexOption.IGNORE_CASE),
            "on\\s+([A-Za-z*.'_][A-Za-z0-9*.'_ ]*?)(?:\\.|\\s+Avl Bal|\\s+via)".toRegex(RegexOption.IGNORE_CASE),
            "for\\s+(?:[A-Z0-9]+-)?([A-Za-z0-9\\s.-]+?)(?:\\.Avl bal|\\.)".toRegex(RegexOption.IGNORE_CASE),
            "debited by\\s+([A-Za-z0-9\\s.-]+?)(?:\\s+Ref No|\\.)".toRegex(RegexOption.IGNORE_CASE)

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
        merchantCategoryMappingProvider: MerchantCategoryMappingProvider,
        categoryFinderProvider: CategoryFinderProvider,
        smsParseTemplateProvider: SmsParseTemplateProvider
    ): PotentialTransaction? {
        return when (val result = parseWithReason(sms, mappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)) {
            is ParseResult.Success -> result.transaction
            is ParseResult.Ignored, is ParseResult.NotParsed, is ParseResult.IgnoredByClassifier -> null
        }
    }

    /**
     * NEW: A dedicated function to check ONLY custom user rules. This is the highest priority check.
     * Returns a fully enriched PotentialTransaction if a rule matches.
     */
    suspend fun parseWithOnlyCustomRules(
        sms: SmsMessage,
        customSmsRuleProvider: CustomSmsRuleProvider,
        merchantRenameRuleProvider: MerchantRenameRuleProvider,
        merchantCategoryMappingProvider: MerchantCategoryMappingProvider,
        categoryFinderProvider: CategoryFinderProvider
    ): ParseResult? {
        val normalizedBody = sms.body.replace(Regex("\\s+"), " ").trim()
        val customRules = customSmsRuleProvider.getAllRules()

        for (rule in customRules) {
            if (normalizedBody.contains(rule.triggerPhrase, ignoreCase = true)) {
                var customAmount: Double? = null
                rule.amountRegex?.let { regex ->
                    try {
                        val match = regex.toRegex(RegexOption.IGNORE_CASE).find(normalizedBody)
                        match?.groups?.get(1)?.value?.let { customAmount = it.replace(",", "").toDoubleOrNull() }
                    } catch (e: PatternSyntaxException) { /* Ignore */ }
                }

                if (customAmount != null) {
                    var customMerchant: String? = null
                    rule.merchantRegex?.let { regex ->
                        try {
                            val match = regex.toRegex(RegexOption.IGNORE_CASE).find(normalizedBody)
                            customMerchant = match?.groups?.get(1)?.value?.trim()
                        } catch (e: PatternSyntaxException) { /* Ignore */ }
                    }

                    var customAccountStr: String? = null
                    rule.accountRegex?.let { regex ->
                        try {
                            val match = regex.toRegex(RegexOption.IGNORE_CASE).find(normalizedBody)
                            customAccountStr = match?.groups?.get(1)?.value?.trim()
                        } catch (e: PatternSyntaxException) { /* Ignore */ }
                    }

                    val potentialTxn = PotentialTransaction(
                        sourceSmsId = sms.id,
                        smsSender = sms.sender,
                        amount = customAmount,
                        transactionType = if (EXPENSE_KEYWORDS_REGEX.containsMatchIn(normalizedBody)) "expense" else "income",
                        merchantName = customMerchant,
                        originalMessage = sms.body,
                        potentialAccount = customAccountStr?.let { PotentialAccount(it, "Unknown") },
                        date = sms.date
                    )
                    // Enrich and return immediately if a custom rule matches
                    val finalTxn = enrichTransaction(potentialTxn, merchantRenameRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, normalizedBody, sms.sender)
                    return ParseResult.Success(finalTxn)
                }
            }
        }
        return null // No custom rule matched
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
        merchantCategoryMappingProvider: MerchantCategoryMappingProvider,
        categoryFinderProvider: CategoryFinderProvider,
        smsParseTemplateProvider: SmsParseTemplateProvider
    ): ParseResult {
        val normalizedBody = sms.body.replace(Regex("\\s+"), " ").trim()

        // --- Stage 1: Check Ignore Rules ---
        val allIgnoreRules = ignoreRuleProvider.getEnabledRules()
        val senderIgnoreRules = allIgnoreRules.filter { it.type == RuleType.SENDER }
        val bodyIgnoreRules = allIgnoreRules.filter { it.type == RuleType.BODY_PHRASE }

        for (rule in senderIgnoreRules) {
            try {
                if (wildcardToRegex(rule.pattern).matches(sms.sender)) {
                    return ParseResult.Ignored("Sender matches ignore pattern: '${rule.pattern}'")
                }
            } catch (e: PatternSyntaxException) { /* Ignore invalid regex */ }
        }

        for (rule in bodyIgnoreRules) {
            try {
                if (rule.pattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(normalizedBody)) {
                    return ParseResult.Ignored("Body contains ignore phrase: '${rule.pattern}'")
                }
            } catch (e: PatternSyntaxException) { /* Ignore invalid regex */ }
        }

        var potentialTxn: PotentialTransaction? = null

        // --- Stage 2: Attempt Heuristic Template Parsing ---
        val smsSignature = generateSmsSignature(normalizedBody)
        val matchingTemplates = smsParseTemplateProvider.getTemplatesBySignature(smsSignature)

        if (matchingTemplates.size == 1) {
            // Confident match, apply this single template.
            potentialTxn = applyTemplate(normalizedBody, matchingTemplates.first(), sms)
        }
        // If size is 0 or > 1, potentialTxn remains null, and we fall through.


        // --- Stage 3: If still no match, fall back to Generic Regex Parsing ---
        if (potentialTxn == null) {
            var extractedAmount: Double? = null
            var detectedCurrency: String? = null

            val highConfidenceMatch = AMOUNT_WITH_HIGH_CONFIDENCE_KEYWORDS_REGEX.find(normalizedBody)
            if (highConfidenceMatch != null) {
                val currencyStr = highConfidenceMatch.groupValues[1].ifEmpty { null }
                val amountStr = highConfidenceMatch.groupValues[2].ifEmpty { highConfidenceMatch.groupValues[3] }
                extractedAmount = amountStr.replace(",", "").toDoubleOrNull()
                detectedCurrency = if (currencyStr != null) if (currencyStr.equals("RS", ignoreCase = true)) "INR" else currencyStr.uppercase() else "INR"
            } else {
                val bestMatch = FALLBACK_AMOUNT_REGEX.findAll(normalizedBody).firstOrNull()
                if (bestMatch != null) {
                    val (amount, currency) = parseAmountAndCurrency(bestMatch)
                    extractedAmount = amount
                    detectedCurrency = currency
                }
            }

            val amount = extractedAmount
            if (amount != null) {
                val transactionType = if (EXPENSE_KEYWORDS_REGEX.containsMatchIn(normalizedBody)) "expense" else if (INCOME_KEYWORDS_REGEX.containsMatchIn(normalizedBody)) "income" else null
                if (transactionType != null) {
                    var merchantName = mappings[sms.sender]
                    if (merchantName == null) {
                        for (pattern in MERCHANT_REGEX_PATTERNS) {
                            val match = pattern.find(normalizedBody)
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

                    potentialTxn = PotentialTransaction(
                        sourceSmsId = sms.id,
                        smsSender = sms.sender,
                        amount = amount,
                        transactionType = transactionType,
                        merchantName = merchantName,
                        originalMessage = sms.body,
                        detectedCurrencyCode = detectedCurrency,
                        date = sms.date
                    )
                }
            }
        }

        // --- Final Stage: Enrichment and Return ---
        if (potentialTxn == null) {
            return ParseResult.NotParsed("No parsing method succeeded.")
        }

        val finalTxn = enrichTransaction(potentialTxn, merchantRenameRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, normalizedBody, sms.sender)
        return ParseResult.Success(finalTxn)
    }

    /**
     * A helper function to apply all enrichment steps to a PotentialTransaction.
     */
    private suspend fun enrichTransaction(
        txn: PotentialTransaction,
        merchantRenameRuleProvider: MerchantRenameRuleProvider,
        merchantCategoryMappingProvider: MerchantCategoryMappingProvider,
        categoryFinderProvider: CategoryFinderProvider,
        normalizedBody: String,
        sender: String
    ): PotentialTransaction {
        val renameRules = merchantRenameRuleProvider.getAllRules().associateBy({ it.originalName.lowercase() }, { it.newName })
        var finalCategoryId: Int? = null

        // --- FIX: Perform category lookup on the ORIGINAL merchant name FIRST ---
        txn.merchantName?.let { originalMerchant ->
            finalCategoryId = merchantCategoryMappingProvider.getCategoryIdForMerchant(originalMerchant)
            // Fallback to keyword if no direct mapping exists
            if (finalCategoryId == null) {
                finalCategoryId = findCategoryIdByKeyword(originalMerchant, categoryFinderProvider)
            }
        }

        // --- Now, apply the rename rule ---
        val txnAfterRename = txn.merchantName?.let { currentMerchantName ->
            renameRules[currentMerchantName.lowercase()]?.let { newMerchantName ->
                txn.copy(merchantName = newMerchantName)
            }
        } ?: txn

        // --- If we haven't found a category yet, try again with the RENAMED merchant name ---
        if (finalCategoryId == null) {
            txnAfterRename.merchantName?.let { renamedMerchant ->
                finalCategoryId = merchantCategoryMappingProvider.getCategoryIdForMerchant(renamedMerchant)
            }
        }


        // --- Finally, build the transaction, applying the category ID we found earlier ---
        val txnAfterCategorization = if (finalCategoryId != null) {
            txnAfterRename.copy(categoryId = finalCategoryId)
        } else {
            txnAfterRename
        }

        val finalAccount = txnAfterCategorization.potentialAccount ?: parseAccount(normalizedBody, sender)
        val smsHash = (sender.filter { it.isDigit() }.takeLast(10) + normalizedBody).hashCode().toString()
        val smsSignature = generateSmsSignature(normalizedBody)

        return txnAfterCategorization.copy(
            potentialAccount = finalAccount,
            sourceSmsHash = smsHash,
            smsSignature = smsSignature
        )
    }

    // --- Private Helper Functions ---

    fun generateSmsSignature(body: String): String {
        var signature = body.lowercase()
        VOLATILE_DATA_REGEX.forEach { regex ->
            signature = regex.replace(signature, "")
        }
        return signature.replace(Regex("\\s+"), " ").trim()
    }

    private fun findCategoryIdByKeyword(merchantName: String, categoryFinderProvider: CategoryFinderProvider): Int? {
        val lowerCaseMerchant = merchantName.lowercase()
        for ((categoryName, keywords) in CATEGORY_KEYWORD_MAP) {
            if (keywords.any { keyword -> lowerCaseMerchant.contains(keyword) }) {
                return categoryFinderProvider.getCategoryIdByName(categoryName)
            }
        }
        return null
    }

    fun parseAccount(smsBody: String, sender: String): PotentialAccount? {
        for (pattern in ACCOUNT_PATTERNS) {
            val match = pattern.find(smsBody)
            if (match != null) {
                return when (pattern.pattern) {
                    "debited from (A/c X*\\d{4}) for NEFT" ->
                        PotentialAccount(formattedName = "HDFC Bank - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "credited on your (credit card ending \\d{4})" ->
                        PotentialAccount(formattedName = "HDFC Bank Card - ${match.groupValues[1].trim()}", accountType = "Credit Card")
                    "from your (HDFC Bank A/c X*\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "on (HDFC Bank Prepaid Card \\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Prepaid Card")
                    "From (HDFC Bank A/[Cc] X*\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "spent Card no\\. (XX\\d{4})" ->
                        PotentialAccount(formattedName = "Axis Bank Card - ${match.groupValues[1].trim()}", accountType = "Card")
                    "Your (AC XXXXX\\d+) Debited" ->
                        PotentialAccount(formattedName = "SBI - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "credited to your (Acc No\\. XXXXX\\d+).*-(SBI)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "(Ac XXXXXXXX\\d+).*-(PNB)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "withdrawn at.*?from\\s+(A/cX\\d+)" ->
                        PotentialAccount(formattedName = "SBI - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "(?:DEBITED|CREDITED) to your (account XXX\\d+)\\s" ->
                        PotentialAccount(formattedName = "Canara Bank - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "(Account\\s+No\\.\\s+X+\\d+)\\s+(?:DEBIT|CREDIT)" ->
                        PotentialAccount(formattedName = match.groupValues[1].replace(Regex("[\\s\\u00A0]+"), " ").trim(), accountType = "Bank Account")
                    "(SB A/c \\*\\d{4}) (?:Debited|Credited) for" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "credited to your (A/c No XX\\d{4}) on" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "(HDFC Bank Card x\\d{4}) At" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Card")
                    "your (ICICI Bank Account X*\\d{3,4}) has been credited" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "(ICICI Bank Account X*\\d{3,4}) is credited with" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "credited your (ICICI Bank Account XX\\d{3,4}) with" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "(ICICI Bank Account X*\\d{3,4}) is debited with" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "spent on (ICICI Bank Card XX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Card")
                    "spent using (ICICI Bank Card XX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Card")
                    "credited to (ICICI Bank Credit Card XX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Credit Card")
                    "(Account XX\\d{3,4}) has been debited.*-(ICICI Bank)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "from (Meal Card Wallet linked to your Pluxee Card xx\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Meal Card")
                    "ur (A/cX\\d{4}) credited by" ->
                        PotentialAccount(formattedName = "SBI - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "linked to (Credit Card XX\\d{4})" ->
                        PotentialAccount(formattedName = "Indusind Bank - ${match.groupValues[1].trim()}", accountType = "Credit Card")
                    "your (A/c X\\d{4})-credited.*-(SBI)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - ${match.groupValues[1].trim()}", accountType = "Bank Account")
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
                    "your (ICICI Bank Account) X*(\\d{4}) has been credited with" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "in your a/c no\\. XXXXXXXX(\\d{4}).*-(CANARA BANK)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - xx${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "a/c no\\. XXXXXXXX(\\d{4}) debited.*(Dept of Posts)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - xx${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "Account No\\. XXXXXX(\\d{4}) CREDIT" ->
                        PotentialAccount(formattedName = "Bank Account - xx${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "From (HDFC Bank) A/C \\*(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - *${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "(?:from your|in your) (Kotak Bank) Ac X(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - x${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "in your (UNION BANK OF INDIA) A/C XX(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "(ICICI Bank) Account X*(\\d{3,4}) credited" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Bank Account")
                    "(HDFC Bank) : NEFT money transfer" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "spent from (Pluxee)\\s*(Meal Card wallet), card no\\.\\s*xx(\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = match.groupValues[2].trim())
                    "on your (SBI) (Credit Card) ending with (\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = match.groupValues[2].trim())
                    "On (HDFC Bank) (Card) (\\d{4})" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[3].trim()}", accountType = "Card")
                    "(ICICI Bank) Acc(?:t)? X*(\\d{3,4}) debited" ->
                        PotentialAccount(formattedName = "${match.groupValues[1].trim()} - xx${match.groupValues[2].trim()}", accountType = "Savings Account")
                    "Acc(?:t)? X*(\\d{3,4}) is credited.*-(ICICI Bank)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - xx${match.groupValues[1].trim()}", accountType = "Savings Account")
                    "A/c \\.{3}(\\d{4}).*-\\s*(Bank of Baroda)" ->
                        PotentialAccount(formattedName = "${match.groupValues[2].trim()} - ...${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "in (HDFC Bank A/c XX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "from (A/C XXXXXX\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "on (HDFC Bank Card x\\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Card")
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
                    "Your (A/C XXXXX\\d+)\\s+has\\s+credit" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "Your (A/C XXXXX\\d+) has a debit" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "CREDITED to your (A/c XXX\\d+)\\s" ->
                        PotentialAccount(formattedName = "Canara Bank - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "Your (A/C XXXXX\\d+)\\s+has\\s+a\\s+(?:credit|debit)" ->
                        PotentialAccount(formattedName = "SBI - ${match.groupValues[1].trim()}", accountType = "Bank Account")
                    "Your (SB A/c \\*\\*\\d+) is Credited for" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")
                    "for your (SBI Debit Card ending with \\d{4})" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Debit Card")
                    "Your (A/C XXXXX\\d+) Debited" ->
                        PotentialAccount(formattedName = match.groupValues[1].trim(), accountType = "Bank Account")

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
        val amount = (groups[1].ifEmpty { groups[4].ifEmpty { groups[5] } }).replace(",", "")
            .toDoubleOrNull()
        var currency = (groups[2].ifEmpty { groups[3].ifEmpty { groups[6] } }).uppercase()
        if (currency == "RS") currency = "INR"
        return Pair(amount, currency.ifEmpty { null })
    }

    private fun applyTemplate(newSmsBody: String, template: SmsParseTemplate, originalSms: SmsMessage): PotentialTransaction? {
        try {
            // The merchant is now the learned outcome from the template
            val merchant = template.correctedMerchantName

            val amountStr = newSmsBody.substring(
                template.originalAmountStartIndex,
                min(template.originalAmountEndIndex, newSmsBody.length)
            )
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null

            return PotentialTransaction(
                sourceSmsId = originalSms.id,
                smsSender = originalSms.sender,
                amount = amount,
                transactionType = if (EXPENSE_KEYWORDS_REGEX.containsMatchIn(template.originalSmsBody)) "expense" else "income",
                merchantName = merchant,
                originalMessage = newSmsBody,
                date = originalSms.date
            )
        } catch (e: Exception) {
            println("$TAG: Error applying heuristic template: ${e.message}")
            return null
        }
    }
}