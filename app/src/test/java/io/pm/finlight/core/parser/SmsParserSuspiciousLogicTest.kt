package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParser
import io.pm.finlight.core.NerEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class SmsParserSuspiciousLogicTest : BaseSmsParserTest() {
    @Test
    fun `Option A - amount exceeding 100,000 flags transaction for review`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1,
                    sender = "AM-HDFCBK",
                    body = "Rs 150000.00 debited from a/c **4321 on 12-07-25 to VPA swiggy@hdfcbank.",
                    date = System.currentTimeMillis(),
                )

            // Mock NER returning a high confidence large amount
            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity("150000.00", 0.95f),
                    "MERCHANT" to NerEntity("swiggy@hdfcbank", 0.90f),
                )

            val transaction =
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertNotNull("Should parse successfully", transaction)
            assertTrue("Transaction should be flagged for review due to large amount", transaction!!.needsReview)
            assertTrue(transaction.suspicionReason?.contains("exceeds the auto-save threshold") == true)
            assertEquals(150000.0, transaction.amount, 0.001)
        }

    @Test
    fun `Amount exceeding remaining balance does not flag transaction for review`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 2,
                    sender = "DM-ICIBNK",
                    body = "ICICI Bank Acct XX823 debited for Rs 500.00 on 28-Jul-25; DAKSHIN CAFE credited. Available Balance is Rs. 100.00.",
                    date = System.currentTimeMillis(),
                )

            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity("500.00", 0.95f),
                    "MERCHANT" to NerEntity("DAKSHIN CAFE", 0.90f),
                    // Remaining balance reported is less than Amount (normal in banking debits)
                    "BALANCE" to NerEntity("100.00", 0.95f),
                )

            val transaction =
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertNotNull("Should parse successfully", transaction)
            assertFalse("Transaction should NOT be flagged for review when amount > remaining balance", transaction!!.needsReview)
            assertNull(transaction.suspicionReason)
            assertEquals(500.0, transaction.amount, 0.001)
        }

    @Test
    fun `Toll Paid SMS with remaining wallet balance lower than amount does not flag for review`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 20,
                    sender = "HDFCBK",
                    body = "Toll Paid! Rs.100 for KA11MB1113 At Omalur Toll Plaza On 2026-08-16 19:32:36 Wallet Bal: Rs.45.00 Not you? Visit https://1.hdfc.bank.in/HDFCBK/jf/5dc66c74 HDFC Bank",
                    date = System.currentTimeMillis(),
                )

            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity("100", 0.95f),
                    "MERCHANT" to NerEntity("Omalur Toll Plaza", 0.90f),
                    "BALANCE" to NerEntity("45.00", 0.95f),
                )

            val transaction =
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertNotNull("Should parse successfully", transaction)
            assertFalse("Toll transaction should NOT be flagged for review", transaction!!.needsReview)
            assertNull(transaction.suspicionReason)
            assertEquals(100.0, transaction.amount, 0.001)
        }

    @Test
    fun `Option D - NER confidence below threshold flags transaction for review`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 3,
                    sender = "AM-AXIS",
                    body = "INR 450.00 sent from Axis Bank A/C XX6789 to VPA zomato@icici.",
                    date = System.currentTimeMillis(),
                )

            // Mock NER returning low confidence
            val nerEntities =
                mapOf(
                    // 0.65 is < 0.70 threshold
                    "AMOUNT" to NerEntity("450.00", 0.65f),
                    "MERCHANT" to NerEntity("zomato@icici", 0.90f),
                )

            val transaction =
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertNotNull("Should parse successfully", transaction)
            assertTrue("Transaction should be flagged for review due to low NER confidence", transaction!!.needsReview)
            assertTrue(transaction.suspicionReason?.contains("NER model uncertainty") == true)
            assertEquals(450.0, transaction.amount, 0.001)
        }

    @Test
    fun `Normal transaction does not flag for review`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 4,
                    sender = "AM-HDFCBK",
                    body = "Money Sent! Rs.250.00 From HDFC Bank A/C **4321 To VPA priyanka@ybl",
                    date = System.currentTimeMillis(),
                )

            // Mock NER returning safe values
            val nerEntities =
                mapOf(
                    // Confidence > 0.70, Amount < 100000
                    "AMOUNT" to NerEntity("250.00", 0.95f),
                    "MERCHANT" to NerEntity("priyanka@ybl", 0.90f),
                    // No balance extracted
                )

            val transaction =
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )

            assertNotNull("Should parse successfully", transaction)
            assertFalse("Transaction should NOT be flagged for review", transaction!!.needsReview)
            assertNull("Suspicion reason should be null", transaction.suspicionReason)
            assertEquals(250.0, transaction.amount, 0.001)
        }

    @Test
    fun `Option A - amount exceeding 100,000 does not log SMS body`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 1,
                    sender = "AM-HDFCBK",
                    body = "Rs 150000.00 debited from a/c **4321 on 12-07-25 to VPA swiggy@hdfcbank.",
                    date = System.currentTimeMillis(),
                )

            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity("150000.00", 0.95f),
                    "MERCHANT" to NerEntity("swiggy@hdfcbank", 0.90f),
                )

            val logRecords = mutableListOf<java.util.logging.LogRecord>()
            val handler =
                object : java.util.logging.Handler() {
                    override fun publish(record: java.util.logging.LogRecord) {
                        logRecords.add(record)
                    }

                    override fun flush() {}

                    override fun close() {}
                }
            val julLogger = java.util.logging.Logger.getLogger("SmsParser")
            julLogger.addHandler(handler)
            try {
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )
            } finally {
                julLogger.removeHandler(handler)
            }

            val logText = logRecords.joinToString("\n") { it.message }
            assertTrue("Log should contain sender hash", logText.contains("SenderHash: ${sms.sender.hashCode()}"))
            assertTrue("Log should contain large amount message", logText.contains("Large amount 150000.0 exceeds threshold"))
            assertFalse("Log must not contain SMS body", logText.contains(sms.body))
            assertFalse("Log must not contain sensitive account substring", logText.contains("**4321"))
            assertFalse("Log must not contain sensitive merchant substring", logText.contains("swiggy@hdfcbank"))
        }

    @Test
    fun `Option D - NER confidence below threshold does not log SMS body`() =
        runBlocking {
            setupTest()
            val sms =
                SmsMessage(
                    id = 3,
                    sender = "AM-AXIS",
                    body = "INR 450.00 sent from Axis Bank A/C XX6789 to VPA zomato@icici.",
                    date = System.currentTimeMillis(),
                )

            val nerEntities =
                mapOf(
                    "AMOUNT" to NerEntity("450.00", 0.65f),
                    "MERCHANT" to NerEntity("zomato@icici", 0.90f),
                )

            val logRecords = mutableListOf<java.util.logging.LogRecord>()
            val handler =
                object : java.util.logging.Handler() {
                    override fun publish(record: java.util.logging.LogRecord) {
                        logRecords.add(record)
                    }

                    override fun flush() {}

                    override fun close() {}
                }
            val julLogger = java.util.logging.Logger.getLogger("SmsParser")
            julLogger.addHandler(handler)
            try {
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                    nerEntities = nerEntities,
                )
            } finally {
                julLogger.removeHandler(handler)
            }

            val logText = logRecords.joinToString("\n") { it.message }
            assertTrue("Log should contain sender hash", logText.contains("SenderHash: ${sms.sender.hashCode()}"))
            assertTrue("Log should contain amount", logText.contains("Amount: 450.0"))
            assertTrue("Log should contain NER confidence", logText.contains("Low NER confidence for AMOUNT (0.65)"))
            assertFalse("Log must not contain SMS body", logText.contains(sms.body))
            assertFalse("Log must not contain sensitive account substring", logText.contains("XX6789"))
            assertFalse("Log must not contain sensitive merchant substring", logText.contains("zomato@icici"))
        }

    @Test
    fun `Template error logs exception class name and does not log SMS body or exception message`() =
        runBlocking {
            setupTest()
            val smsBody = "Short body"
            val sms =
                SmsMessage(
                    id = 5,
                    sender = "AM-HDFCBK",
                    body = smsBody,
                    date = System.currentTimeMillis(),
                )

            val sig = SmsParser.generateSmsSignature(smsBody)
            val template =
                SmsParseTemplate(
                    templateSignature = sig,
                    correctedMerchantName = "TargetMerchant",
                    originalSmsBody = "Some template body",
                    originalAmountStartIndex = 100,
                    originalAmountEndIndex = 200,
                )
            `when`(mockSmsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(listOf(template))

            val logRecords = mutableListOf<java.util.logging.LogRecord>()
            val handler =
                object : java.util.logging.Handler() {
                    override fun publish(record: java.util.logging.LogRecord) {
                        logRecords.add(record)
                    }

                    override fun flush() {}

                    override fun close() {}
                }
            val julLogger = java.util.logging.Logger.getLogger("SmsParser")
            julLogger.addHandler(handler)
            try {
                SmsParser.parse(
                    sms,
                    emptyMappings,
                    customSmsRuleProvider,
                    merchantRenameRuleProvider,
                    ignoreRuleProvider,
                    merchantCategoryMappingProvider,
                    categoryFinderProvider,
                    smsParseTemplateProvider,
                )
            } finally {
                julLogger.removeHandler(handler)
            }

            val logText = logRecords.joinToString("\n") { it.message }
            assertTrue(
                "Should log sanitized template error with exception class",
                logText.contains("Error applying heuristic template: StringIndexOutOfBoundsException"),
            )
            assertFalse("Should not log SMS body in error", logText.contains(smsBody))
            assertFalse("Should not log template body in error", logText.contains("Some template body"))
        }
}
