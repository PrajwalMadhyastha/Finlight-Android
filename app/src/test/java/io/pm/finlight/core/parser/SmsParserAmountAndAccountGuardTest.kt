package io.pm.finlight.core.parser

import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParser
import io.pm.finlight.core.NerEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class SmsParserAmountAndAccountGuardTest : BaseSmsParserTest() {
    // -------------------------------------------------------------------------
    // Amount Positivity Guard Tests
    // -------------------------------------------------------------------------

    @Test
    fun `parseWithReason returns NotParsed for zero amount`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited by Rs 0.00 for Coffee at Starbucks",
                    date = System.currentTimeMillis(),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            assertTrue("Expected ParseResult.NotParsed but got $result", result is ParseResult.NotParsed)
            val notParsed = result as ParseResult.NotParsed
            assertEquals("No parsing method succeeded.", notParsed.reason)
        }

    @Test
    fun `parse returns null for zero amount`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited by Rs 0 for Coffee at Starbucks",
                    date = System.currentTimeMillis(),
                )

            val result =
                SmsParser.parse(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            assertNull("Expected parse to return null for zero amount", result)
        }

    @Test
    fun `parseWithReason returns NotParsed for negative amount in regex`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited by Rs -150.00 for Book at Amazon",
                    date = System.currentTimeMillis(),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            assertTrue("Expected ParseResult.NotParsed for negative amount but got $result", result is ParseResult.NotParsed)
        }

    @Test
    fun `parseWithReason returns NotParsed for NER zero amount override`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited for purchase at Starbucks",
                    date = System.currentTimeMillis(),
                )
            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity(value = "0.00", confidence = 0.95f),
                    "MERCHANT" to NerEntity(value = "Starbucks", confidence = 0.95f),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertTrue("Expected ParseResult.NotParsed for NER zero amount but got $result", result is ParseResult.NotParsed)
        }

    @Test
    fun `parseWithReason returns NotParsed for NER negative amount override`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited for purchase at Starbucks",
                    date = System.currentTimeMillis(),
                )
            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity(value = "-50.00", confidence = 0.95f),
                    "MERCHANT" to NerEntity(value = "Starbucks", confidence = 0.95f),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertTrue("Expected ParseResult.NotParsed for NER negative amount but got $result", result is ParseResult.NotParsed)
        }

    @Test
    fun `parseWithReason returns NotParsed for NER NaN amount override`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited for purchase at Starbucks",
                    date = System.currentTimeMillis(),
                )
            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity(value = "NaN", confidence = 0.95f),
                    "MERCHANT" to NerEntity(value = "Starbucks", confidence = 0.95f),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertTrue("Expected ParseResult.NotParsed for NER NaN amount but got $result", result is ParseResult.NotParsed)
        }

    @Test
    fun `parseWithReason returns NotParsed for NER Infinity amount override`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited for purchase at Starbucks",
                    date = System.currentTimeMillis(),
                )
            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity(value = "Infinity", confidence = 0.95f),
                    "MERCHANT" to NerEntity(value = "Starbucks", confidence = 0.95f),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertTrue("Expected ParseResult.NotParsed for NER Infinity amount but got $result", result is ParseResult.NotParsed)
        }

    @Test
    fun `parseWithReason parses valid positive amounts successfully`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited by Rs 0.01 for Micro-Charge at TestService",
                    date = System.currentTimeMillis(),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            assertTrue("Expected ParseResult.Success for positive amount 0.01 but got $result", result is ParseResult.Success)
            val success = result as ParseResult.Success
            assertEquals(0.01, success.transaction.amount, 0.0001)
        }

    @Test
    fun `applyTemplate rejects template match when extracted amount is zero or negative`() =
        runBlocking {
            setupTest()
            val smsBody = "Alert: debited 0.00 at Swiggy"
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = smsBody,
                    date = System.currentTimeMillis(),
                )
            val signature = SmsParser.generateSmsSignature(smsBody)
            val template =
                SmsParseTemplate(
                    templateSignature = signature,
                    correctedMerchantName = "Swiggy",
                    originalSmsBody = smsBody,
                    originalMerchantStartIndex = 19,
                    originalMerchantEndIndex = 25,
                    originalAmountStartIndex = 15,
                    originalAmountEndIndex = 19,
                )
            `when`(mockSmsParseTemplateDao.getTemplatesBySignature(signature)).thenReturn(listOf(template))

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            assertTrue("Expected template with 0.00 amount to be rejected, got $result", result is ParseResult.NotParsed)
        }

    @Test
    fun `parseWithOnlyCustomRules returns null when rule extracts zero amount`() =
        runBlocking {
            val rule =
                CustomSmsRule(
                    id = 1,
                    triggerPhrase = "custom pay",
                    merchantRegex = "at (\\w+)",
                    amountRegex = "custom pay (\\d+\\.\\d+)",
                    accountRegex = null,
                    merchantNameExample = "merchant",
                    amountExample = "0.00",
                    accountNameExample = null,
                    priority = 1,
                    sourceSmsBody = "custom pay 0.00 at merchant",
                    transactionType = "expense",
                )
            setupTest(customRules = listOf(rule))
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "TEST",
                    body = "custom pay 0.00 at merchant",
                    date = System.currentTimeMillis(),
                )

            val result =
                SmsParser.parseWithOnlyCustomRules(
                    sms = sms,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                )

            assertNull("Expected parseWithOnlyCustomRules to return null for zero amount", result)
        }

    // -------------------------------------------------------------------------
    // Account Sanitization Tests
    // -------------------------------------------------------------------------

    @Test
    fun `sanitizeAccountName returns Unknown Account for null or blank input`() {
        assertEquals("Unknown Account", SmsParser.sanitizeAccountName(null))
        assertEquals("Unknown Account", SmsParser.sanitizeAccountName(""))
        assertEquals("Unknown Account", SmsParser.sanitizeAccountName("    "))
        assertEquals("Unknown Account", SmsParser.sanitizeAccountName("\n\t\r  "))
    }

    @Test
    fun `sanitizeAccountName strips control and format characters`() {
        val input = "HDFC\u0000 Bank\n\r\t - \u200BX1234\u001F"
        val sanitized = SmsParser.sanitizeAccountName(input)

        assertEquals("HDFC Bank - X1234", sanitized)
    }

    @Test
    fun `sanitizeAccountName truncates account names longer than 60 characters`() {
        val input = "A".repeat(85)
        val sanitized = SmsParser.sanitizeAccountName(input)

        assertEquals(60, sanitized.length)
        assertEquals("A".repeat(60), sanitized)
    }

    @Test
    fun `sanitizeAccountName preserves valid account names up to 60 characters`() {
        val input = "ICICI Bank - Account ending in XX1234"
        val sanitized = SmsParser.sanitizeAccountName(input)

        assertEquals(input, sanitized)
    }

    @Test
    fun `NER hallucinated multi-sentence account entity is sanitized to max 60 chars`() =
        runBlocking {
            setupTest()
            val longHallucination = "Your account ending in 1234 has been debited by Rs 500 for a purchase at Starbucks. Please do not share OTP."
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited by Rs 500.00 for Coffee at Starbucks",
                    date = System.currentTimeMillis(),
                )
            val nerEntities =
                mapOf(
                    "ACCOUNT" to NerEntity(value = longHallucination, confidence = 0.9f),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertTrue("Expected ParseResult.Success but got $result", result is ParseResult.Success)
            val success = result as ParseResult.Success
            val formattedName = success.transaction.potentialAccount?.formattedName
            assertNotNull("potentialAccount must not be null", formattedName)
            assertTrue("Account name must not exceed 60 chars (was ${formattedName!!.length})", formattedName.length <= 60)
            assertTrue("Must not contain control characters", !formattedName.contains(Regex("[\\p{Cc}\\p{Cf}]")))
        }

    @Test
    fun `blank NER account entity falls back to regex account parser`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1L,
                    sender = "VK-HDFCBK",
                    body = "debited by Rs 100.00 from your HDFC Bank A/c X1234 for Coffee at Starbucks",
                    date = System.currentTimeMillis(),
                )
            val nerEntities =
                mapOf(
                    "ACCOUNT" to NerEntity(value = "   \n\t  ", confidence = 0.9f),
                )

            val result =
                SmsParser.parseWithReason(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertTrue("Expected ParseResult.Success but got $result", result is ParseResult.Success)
            val success = result as ParseResult.Success
            val formattedName = success.transaction.potentialAccount?.formattedName
            assertNotNull("Regex parser should have extracted account name as fallback", formattedName)
            assertEquals("HDFC Bank A/c X1234", formattedName)
        }
}
