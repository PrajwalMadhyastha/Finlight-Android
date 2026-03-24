package io.pm.finlight.analyzer

import io.pm.finlight.*
import io.pm.finlight.SmsMessage

/**
 * Desktop wrapper for the Core SmsParser.
 * Mocks all database-backed providers to allow pure regex parsing.
 */
object DesktopSmsParser {

    private val emptyIgnore = object : IgnoreRuleProvider {
        override suspend fun getEnabledRules() = emptyList<IgnoreRule>()
    }
    
    private val emptyCustom = object : CustomSmsRuleProvider {
        override suspend fun getAllRules() = emptyList<CustomSmsRule>()
    }
    
    private val emptyRename = object : MerchantRenameRuleProvider {
        override suspend fun getAllRules() = emptyList<MerchantRenameRule>()
    }
    
    private val emptyMapping = object : MerchantCategoryMappingProvider {
        override suspend fun getCategoryIdForMerchant(merchantName: String) = null
    }
    
    private val emptyCategory = object : CategoryFinderProvider {
        override fun getCategoryIdByName(name: String) = null
    }
    
    private val emptyTemplate = object : SmsParseTemplateProvider {
        override suspend fun getAllTemplates() = emptyList<SmsParseTemplate>()
        override suspend fun getTemplatesBySignature(signature: String) = emptyList<SmsParseTemplate>()
    }

    suspend fun parse(sms: SmsMessage): PotentialTransaction? {
        return SmsParser.parse(
            sms = sms,
            mappings = emptyMap(),
            customSmsRuleProvider = emptyCustom,
            merchantRenameRuleProvider = emptyRename,
            ignoreRuleProvider = emptyIgnore,
            merchantCategoryMappingProvider = emptyMapping,
            categoryFinderProvider = emptyCategory,
            smsParseTemplateProvider = emptyTemplate
        )
    }
    
    /**
     * Parse an SMS and extract entities as ParserSuggestions for NER labeling.
     * Returns null if parsing fails.
     */
    suspend fun parseAndExtract(sms: SmsMessage): ParserSuggestions? {
        return try {
            when (val result = SmsParser.parseWithReason(
                sms = sms,
                mappings = emptyMap(),
                customSmsRuleProvider = emptyCustom,
                merchantRenameRuleProvider = emptyRename,
                ignoreRuleProvider = emptyIgnore,
                merchantCategoryMappingProvider = emptyMapping,
                categoryFinderProvider = emptyCategory,
                smsParseTemplateProvider = emptyTemplate
            )) {
                is ParseResult.Success -> {
                    val txn = result.transaction
                    ParserSuggestions(
                        merchantText = txn.merchantName,
                        amountText = buildString {
                            if (txn.detectedCurrencyCode != null) {
                                append(txn.detectedCurrencyCode)
                                append(" ")
                            }
                            append(txn.amount)
                        }.trim(),
                        currencyCode = txn.detectedCurrencyCode,
                        accountText = txn.potentialAccount?.formattedName,
                        balanceText = detectBalance(sms.body)
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Detect available balance in SMS using regex patterns.
     * Returns the matched balance text or null if not found.
     */
    private fun detectBalance(smsBody: String): String? {
        val balancePatterns = listOf(
            // "Avl Bal Rs:9277.6", "Avl.Bal. Rs 12345.00"
            Regex("""(?:Avl\.?\s*Bal\.?)\s*:?\s*(?:Rs\.?|INR|₹)\s*:?\s*([0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE),
            // "Available Balance: Rs.1234.56", "Available Balance Rs 5000"
            Regex("""(?:Available\s*Balance)\s*:?\s*(?:Rs\.?|INR|₹)\s*([0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE),
            // "Bal: INR 5000", "Bal Rs.9277.6"
            Regex("""(?:Bal\.?)\s*:?\s*(?:Rs\.?|INR|₹)\s*:?\s*([0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE),
            // "Balance Rs.9277.6"
            Regex("""(?:Balance)\s*:?\s*(?:Rs\.?|INR|₹)\s*([0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in balancePatterns) {
            val match = pattern.find(smsBody)
            if (match != null) {
                // Return the full matched text (including currency prefix)
                return match.value
            }
        }
        
        return null
    }
}
