package io.pm.finlight.analyzer

import java.util.regex.Pattern

/**
 * Utility to mask PII and sensitive data while identifying training patterns.
 * Preserves the *structure* of data (e.g. number of digits, currency formats)
 * so that the ML model can still learn the "shape" of meaningful entities.
 */
object Anonymizer {

    fun anonymize(text: String): String {
        var processed = text

        // 1. Phone Numbers: Pattern +91-XXXXX or 10-digit mobile
        // Target: Preserving format +91-99999-99999
        processed = processed.replace(Regex("(\\+91[- ]?)?[6-9]\\d{9}")) { match ->
            val original = match.value
            if (original.startsWith("+91")) "+91 99999 99999" else "9999999999"
        }
        
        // 2. Account Numbers: 'a/c' followed by digits, or 'ending with'
        // Target: XX8888 (Preserve 'XX' prefix if present, normalize ending)
        processed = processed.replace(Regex("(?i)(a/c|acct|account no|card)\\.?\\s*[:\\-]?\\s*([xX*]*\\d{3,})")) { match ->
            val prefix = match.groupValues[1]
            val number = match.groupValues[2]
            // Keep the 'X's if they exist, replace last 4 digits with 8888
            val maskedNumber = if (number.contains("x", ignoreCase = true) || number.contains("*")) {
                "XX8888" 
            } else {
                "XX8888" // Force masking for clear text numbers
            }
            "$prefix $maskedNumber"
        }

        // 3. OTPs: Keywords followed by 4-8 digits
        // Target: 999999 (Same length)
        processed = processed.replace(Regex("(?i)(otp|code|pw|pin)\\s+(is\\s+)?[:\\-]?\\s*(\\d{4,8})")) { match ->
            val digits = match.groupValues[3]
            match.value.replace(digits, "9".repeat(digits.length))
        }

        // 4. Amounts: Digits with currency or decimal structure
        // Target: Replace digits with '9' to preserve magnitude (e.g. 1,250.00 -> 9,999.99)
        
        // 4a. Currency matches (Rs. 100, INR 500.00)
        processed = processed.replace(Regex("(?i)(Rs\\.?|INR|MYR|USD|EUR|GBP)\\s?([\\d,]+\\.?\\d{0,2})")) { match ->
             val currency = match.groupValues[1]
             val amount = match.groupValues[2]
             val maskedAmount = amount.map { if (it.isDigit()) '9' else it }.joinToString("")
             "$currency $maskedAmount"
        }
        
        // 4b. Floating point numbers that look like amounts (isolated, 2 decimals)
        // Avoids dates like 12.01.2023 by requiring space boundaries and standard dot usage
        processed = processed.replace(Regex("(?<=\\s)([\\d,]+\\.\\d{2})(?=\\s|$)")) { match ->
             val amount = match.value
             amount.map { if (it.isDigit()) '9' else it }.joinToString("")
        }

        // 5. Names: "Paid to X", "Transfer to Y"
        // Target: "PersonName" or "MerchantName"
        // This is heuristic-based.
        processed = processed.replace(Regex("(?i)(paid to|sent to|transfer to|from|beneficiary)\\s+([A-Z][a-zA-Z0-9]*(\\s[A-Z][a-zA-Z0-9]*)*)")) { match ->
            val action = match.groupValues[1]
            val name = match.groupValues[2]
            
            // Don't replace if it looks like a Bank Name (common known entities)
            if (isCommonEntity(name)) {
                match.value
            } else {
                "$action MerchantName"
            }
        }
        
        // 6. Generic UPI IDs (example@okhdfc) -> user@bank
        processed = processed.replace(Regex("[a-zA-Z0-9.\\-_]+@[a-zA-Z]+")) { "user@upi" }

        return processed
    }
    
    // Safelist of common entities we want to KEEP for training context
    private fun isCommonEntity(name: String): Boolean {
        val keepList = setOf("HDFC", "SBI", "ICICI", "Bank", "UPI", "Amazon", "Flipkart", "Swiggy", "Zomato", "Uber", "Ola", "Starbucks", "Shell")
        return keepList.any { name.contains(it, ignoreCase = true) }
    }
}
