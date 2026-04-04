package io.pm.finlight.core.utils

object MerchantCleaner {
    // Known payment gateway prefixes (often followed by non-alphanumeric chars like * or -)
    private val GATEWAY_PREFIX_REGEX = "^(?:PYU|RAZ|PAYTM|CCA|MBI|BIL|UPI|VDC|AMZ|FLIP)[^a-zA-Z0-9]+".toRegex(RegexOption.IGNORE_CASE)
    
    // Legal boilerplate suffixes (must be whole words at the end of the string, can repeat)
    private val LEGAL_BOILERPLATE_REGEX = "(?:\\b(?:Pvt\\s*Ltd|Private\\s*Limited|LLP|Retail|Ltd|Limited|INC)\\b[^a-zA-Z0-9]*)+\$".toRegex(RegexOption.IGNORE_CASE)

    fun clean(rawMerchant: String?): String? {
        if (rawMerchant.isNullOrBlank()) return rawMerchant

        var cleaned = rawMerchant.trim()

        // 1. Strip gateway prefix
        cleaned = cleaned.replace(GATEWAY_PREFIX_REGEX, "")

        // 2. Strip UPI suffixes (e.g. zomatoupi@hdfc -> zomatoupi -> zomato)
        if (cleaned.contains("@")) {
            cleaned = cleaned.substringBefore("@").trim()
            if (cleaned.lowercase().endsWith("upi") && cleaned.length > 3) {
                cleaned = cleaned.substring(0, cleaned.length - 3)
            }
        }

        // 3. Strip legal entity boilerplate at the end
        cleaned = cleaned.replace(LEGAL_BOILERPLATE_REGEX, "")

        // Trim any trailing/leading special characters that might be left hanging
        cleaned = cleaned.trim(',', '.', ' ', '-', '*', '/')

        return if (cleaned.isBlank()) rawMerchant else cleaned
    }
}
