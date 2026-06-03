package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import io.pm.finlight.core.NerEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
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
    fun `Option C - amount exceeding available balance flags transaction for review`() =
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
                    // Balance is less than Amount
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
            assertTrue("Transaction should be flagged for review due to amount > balance", transaction!!.needsReview)
            assertTrue(transaction.suspicionReason?.contains("exceeds available balance") == true)
            assertEquals(500.0, transaction.amount, 0.001)
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
}
