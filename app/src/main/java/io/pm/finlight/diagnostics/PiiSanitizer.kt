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

    // Email pattern (supports standard emails and UPI VPAs e.g. user@okhdfcbank, merchant@upi, user@ybl)
    private val EMAIL_REGEX =
        Regex("""(?i)[a-zA-Z0-9._%+-]+@(?:[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}|[a-zA-Z0-9._-]{3,})\b""")

    // Currency symbols followed by amounts (e.g., ₹ 1,500.50, $250, EUR 99.00, Rs. 1500)
    // or postfix amounts followed by currency (e.g., 500.00 INR, 250.00 Rs, 150.00 USD, 500 Rs.)
    private val CURRENCY_AMOUNT_REGEX =
        Regex("""(?i)(?:(?:-\s*)?(?:₹|INR|RS\.?|USD|\$|EUR|€|GBP|£)\s*-?\s*(?:\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\b|(?:-\s*)?\b(?:\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*(?:₹|INR|RS\.?|USD|\$|EUR|€|GBP|£)(?!\w))""")

    // Phone numbers: international with +, bracketed area codes, or 3-3-4 / 5-5 separated digits
    private val PHONE_REGEX =
        Regex("""(?:\+\d{1,3}[-.\s]?(?:\d{5}[-.\s]?\d{5}|\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}|\d{10})|\(\d{3}\)[-.\s]?\d{3}[-.\s]?\d{4}|\b\d{3}[-.\s]\d{3}[-.\s]\d{4}\b|\b\d{5}[-.\s]\d{5}\b)""")

    // Numeric strings of 4 to 19 digits (bank accounts, card numbers) and masked account patterns (XX1234, *1234, etc.)
    private val ACCOUNT_OR_CARD_REGEX =
        Regex("""(?i)(?:\b\d{4,19}\b|(?<!\w)(?:[Xx*]{1,6}|acct?)-?\d{3,6}\b)""")

    // ISO-8601 date / datetime pattern to preserve timestamps and prevent redacting 4-digit years
    private val ISO_DATE_REGEX =
        Regex("""\b\d{4}-\d{2}-\d{2}(?:[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?)?\b""")

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

    private val STACK_FRAME_REGEX =
        Regex("""^\s*at\s+[\w$.<>]+(?:\.[\w$.<>]+)+\(.*\)$""")

    private fun isStackTraceFrameLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return STACK_FRAME_REGEX.matches(line) || (trimmed.startsWith("... ") && trimmed.endsWith(" more"))
    }

    private fun sanitizeLine(line: String): String {
        val dateTokens = mutableListOf<String>()
        val textWithDatesPreserved =
            ISO_DATE_REGEX.replace(line) { match ->
                dateTokens.add(match.value)
                "__FINLIGHT_DATE_TOKEN_${dateTokens.size - 1}__"
            }

        var result = EMAIL_REGEX.replace(textWithDatesPreserved, REDACTED_EMAIL)
        result = CURRENCY_AMOUNT_REGEX.replace(result, REDACTED_AMOUNT)
        result = PHONE_REGEX.replace(result, REDACTED_PHONE)
        result = ACCOUNT_OR_CARD_REGEX.replace(result, REDACTED_NUM)

        dateTokens.forEachIndexed { index, dateStr ->
            result = result.replace("__FINLIGHT_DATE_TOKEN_${index}__", dateStr)
        }

        return result
    }
}
