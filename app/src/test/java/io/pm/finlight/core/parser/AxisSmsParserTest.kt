// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/AxisSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from Axis Bank, refactored from the generic test file.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AxisSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses Axis Bank multi-line card spend with 'Card no' format`() = runBlocking {
        setupTest()
        val smsBody = """
            Spent
            Card no. XX9646
            INR 95
            02-07-25 20:31:00
            Nayana N
            Avl Lmt INR 413940.4
            SMS BLOCK 9646 to 919951860002, if not you - Axis Bank
        """.trimIndent()
        val mockSms = SmsMessage(
            id = 9003L,
            sender = "AM-AXISBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(95.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Nayana N", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Axis Bank Card - XX9646", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }
}
