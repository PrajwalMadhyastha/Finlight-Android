package io.pm.finlight.diagnostics

/**
 * Utility to redact sensitive financial PII (Personally Identifiable Information)
 * from crash reports, stack traces, and diagnostics logs before display or export.
 */
object PiiSanitizer {
    private const val REDACTED_NUM = "[REDACTED_NUM]"
    private const val REDACTED_AMOUNT = "[REDACTED_AMOUNT]"
    private const val REDACTED_EMAIL = "[REDACTED_EMAIL]"
    private const val REDACTED_PHONE = "[REDACTED_PHONE]"

    // Email pattern
    private val EMAIL_REGEX =
        Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")

    // Currency symbols followed by amounts (e.g., ₹ 1,500.50, $250, EUR 99.00)
    private val CURRENCY_AMOUNT_REGEX =
        Regex("""(?i)(?:₹|INR|USD|\$|EUR|€|GBP|£)\s*(?:\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\b""")

    // Phone numbers: international with +, bracketed area codes, or 3-3-4 / 5-5 separated digits
    private val PHONE_REGEX =
        Regex("""(?:\+\d{1,3}[-.\s]?(?:\d{5}[-.\s]?\d{5}|\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}|\d{10})|\(\d{3}\)[-.\s]?\d{3}[-.\s]?\d{4}|\b\d{3}[-.\s]\d{3}[-.\s]\d{4}\b|\b\d{5}[-.\s]\d{5}\b)""")

    // Numeric strings of 4 to 19 digits (bank accounts, credit/debit card numbers, PINs)
    private val ACCOUNT_OR_CARD_REGEX =
        Regex("""\b\d{4,19}\b""")

    /**
     * Sanitizes a given text string, line by line.
     * Code stack trace lines (e.g. `\s*at io.pm.finlight.*(File.kt:123)`) are preserved
     * without altering file line numbers or symbols.
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrEmpty()) return ""

        return input.lines().joinToString("\n") { line ->
            if (isStackTraceFrameLine(line)) {
                line
            } else {
                sanitizeLine(line)
            }
        }
    }

    /**
     * Sanitizes a Throwable's stack trace and message.
     */
    fun sanitizeThrowable(throwable: Throwable?): String {
        if (throwable == null) return ""
        return sanitize(throwable.stackTraceToString())
    }

    private fun isStackTraceFrameLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("at ") || (trimmed.startsWith("... ") && trimmed.endsWith(" more"))
    }

    private fun sanitizeLine(line: String): String {
        var result = EMAIL_REGEX.replace(line, REDACTED_EMAIL)
        result = CURRENCY_AMOUNT_REGEX.replace(result, REDACTED_AMOUNT)
        result = PHONE_REGEX.replace(result, REDACTED_PHONE)
        result = ACCOUNT_OR_CARD_REGEX.replace(result, REDACTED_NUM)
        return result
    }
}
