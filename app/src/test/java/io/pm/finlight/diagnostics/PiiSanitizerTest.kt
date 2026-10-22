package io.pm.finlight.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiSanitizerTest {
    @Test
    fun sanitize_emptyAndNull_returnsEmpty() {
        assertEquals("", PiiSanitizer.sanitize(null))
        assertEquals("", PiiSanitizer.sanitize(""))
        assertEquals("", PiiSanitizer.sanitizeThrowable(null))
    }

    @Test
    fun sanitize_redactsCardAndAccountNumbers() {
        val input = "Transferred from account 123456789012 to card 4111222233334444"
        val output = PiiSanitizer.sanitize(input)
        assertEquals("Transferred from account [REDACTED_NUM] to card [REDACTED_NUM]", output)
    }

    @Test
    fun sanitize_redactsCurrencies() {
        val input = "Paid ₹ 1,500.50, deposited $250, received EUR 99.00 and INR 4500"
        val output = PiiSanitizer.sanitize(input)
        assertEquals("Paid [REDACTED_AMOUNT], deposited [REDACTED_AMOUNT], received [REDACTED_AMOUNT] and [REDACTED_AMOUNT]", output)
    }

    @Test
    fun sanitize_redactsNegativeCurrencies() {
        val input = "Balances: ₹ -500, $-50.00, -₹250, -$10 and - $75"
        val output = PiiSanitizer.sanitize(input)
        assertEquals("Balances: [REDACTED_AMOUNT], [REDACTED_AMOUNT], [REDACTED_AMOUNT], [REDACTED_AMOUNT] and [REDACTED_AMOUNT]", output)
    }

    @Test
    fun sanitize_redactsEmails() {
        val input = "Contact user at support.finlight@example.com for help"
        val output = PiiSanitizer.sanitize(input)
        assertEquals("Contact user at [REDACTED_EMAIL] for help", output)
    }

    @Test
    fun sanitize_redactsPhoneNumbers() {
        val input1 = "Call +91 98765 43210 or +1-555-123-4567"
        val output1 = PiiSanitizer.sanitize(input1)
        assertEquals("Call [REDACTED_PHONE] or [REDACTED_PHONE]", output1)

        val input2 = "Reach us at (555) 123-4567"
        val output2 = PiiSanitizer.sanitize(input2)
        assertEquals("Reach us at [REDACTED_PHONE]", output2)
    }

    @Test
    fun sanitize_preservesStackTraceLineNumbersAndFrames() {
        val input =
            """
            java.lang.NullPointerException: Account 123456789 with balance ₹ 200.00 is null
            	at io.pm.finlight.ui.screens.AccountListScreen.AccountListItem(AccountListScreen.kt:126)
            	at io.pm.finlight.ui.viewmodel.AccountViewModel.checkForPotentialMerges(AccountViewModel.kt:95)
            	... 45 more
            """.trimIndent()

        val output = PiiSanitizer.sanitize(input)

        val lines = output.lines()
        assertEquals("java.lang.NullPointerException: Account [REDACTED_NUM] with balance [REDACTED_AMOUNT] is null", lines[0])
        assertEquals("\tat io.pm.finlight.ui.screens.AccountListScreen.AccountListItem(AccountListScreen.kt:126)", lines[1])
        assertEquals("\tat io.pm.finlight.ui.viewmodel.AccountViewModel.checkForPotentialMerges(AccountViewModel.kt:95)", lines[2])
        assertEquals("\t... 45 more", lines[3])
    }

    @Test
    fun sanitizeThrowable_sanitizesExceptionWithCause() {
        val root = IllegalArgumentException("Invalid card 4111222233334444 balance $500")
        val exception = RuntimeException("Wrapper error for user@example.com", root)

        val sanitized = PiiSanitizer.sanitizeThrowable(exception)

        assertTrue(sanitized.contains("[REDACTED_NUM]"))
        assertTrue(sanitized.contains("[REDACTED_AMOUNT]"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
        assertTrue(sanitized.contains("at io.pm.finlight.diagnostics.PiiSanitizerTest"))
    }

    @Test
    fun sanitize_redactsIndianRupeeVariations() {
        val input = "Paid Rs. 1,500.50, transferred Rs 250 and RS.50"
        val output = PiiSanitizer.sanitize(input)
        assertEquals("Paid [REDACTED_AMOUNT], transferred [REDACTED_AMOUNT] and [REDACTED_AMOUNT]", output)
    }

    @Test
    fun sanitize_preservesIsoDates() {
        val input = "Crash occurred on 2026-10-22 at 2026-10-22T08:32:01Z for account 123456789"
        val output = PiiSanitizer.sanitize(input)
        assertEquals("Crash occurred on 2026-10-22 at 2026-10-22T08:32:01Z for account [REDACTED_NUM]", output)
    }
}
