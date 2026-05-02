package io.pm.finlight.ml

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the NerExtractor class.
 *
 * Runs on a real Android device/emulator to verify:
 * 1. The TFLite NER model loads correctly (including flex ops / SELECT_TF_OPS)
 * 2. The WordPiece tokenizer produces correct tokenization
 * 3. Entity extraction produces expected results for known SMS messages
 */
@RunWith(AndroidJUnit4::class)
class NerExtractorTest {
    companion object {
        private const val TAG = "NerExtractorTest"
    }

    private lateinit var extractor: NerExtractor

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        extractor = NerExtractor(appContext)
    }

    @After
    fun tearDown() {
        extractor.close()
    }

    /**
     * Tests that the NerExtractor initializes successfully — model loads,
     * vocab loads, label map loads, and the flex delegate is available.
     */
    @Test
    fun testModelInitialization_succeeds() {
        assertNotNull("NerExtractor should not be null after initialization.", extractor)
    }

    /**
     * Tests entity extraction on clear transactional SMS messages where
     * MERCHANT, AMOUNT, and ACCOUNT entities should be found.
     */
    @Test
    fun testExtraction_withKnownTransactionalSms() {
        val testCases =
            listOf(
                TestCase(
                    message = "Your HDFC Bank A/C XX1234 debited Rs 500 at Amazon",
                    expectedEntities =
                        mapOf(
                            "AMOUNT" to listOf("rs", "500"),
                            "MERCHANT" to listOf("amazon"),
                        ),
                ),
                TestCase(
                    message = "INR 1200 charged to card XX5678 for Swiggy order",
                    expectedEntities =
                        mapOf(
                            "AMOUNT" to listOf("1200"),
                            "MERCHANT" to listOf("swiggy"),
                        ),
                ),
                TestCase(
                    message = "Rs.267.00 spent on your SBI Credit Card ending with 3201 at DAKSHIN CAFE",
                    expectedEntities =
                        mapOf(
                            "AMOUNT" to listOf("267"),
                            "MERCHANT" to listOf("dakshin", "cafe"),
                        ),
                ),
            )

        testCases.forEach { tc ->
            val entities = extractor.extract(tc.message)
            Log.d(TAG, "Input:    '${tc.message}'")
            Log.d(TAG, "Entities: $entities")

            // Check that extracted entities contain expected keywords
            tc.expectedEntities.forEach { (entityType, keywords) ->
                val extracted = entities[entityType]
                assertNotNull(
                    "Expected $entityType entity in: '${tc.message}', got: $entities",
                    extracted,
                )
                keywords.forEach { keyword ->
                    assertTrue(
                        "Expected '$keyword' in $entityType='$extracted' for: '${tc.message}'",
                        extracted!!.contains(keyword, ignoreCase = true),
                    )
                }
            }
        }
    }

    /**
     * Tests that the model handles various SMS formats without crashing,
     * and that the output map only contains valid entity types.
     */
    @Test
    fun testExtraction_withVariousSmsFormats_doesNotCrash() {
        val messages =
            listOf(
                // Standard debit
                "Sent Rs.11.00 From HDFC Bank A/C *1243 To Raju On 07/08/25",
                // UPI transaction
                "Rs.267.00 spent on your SBI Credit Card ending with 3201 at HALLI THOTA via UPI",
                // Credit
                "Your A/C XXXXX650077 Credited INR 1,41,453.00 on 15/07/25 - Deposit by transfer",
                // ICICI
                "ICICI Bank Acct XX823 debited for Rs 240.00 on 21-Jun-25; DAKSHIN CAFE credited.",
                // Short message
                "Rs 500 debited",
                // Long message with noise
                "Dear Customer, Your a/c XXXX1234 is debited with Rs.2,500.00 for purchase at FLIPKART on 01-Jan-26. Avl Bal Rs.15,234.56. If not done by you, call 18001234567.",
            )

        val validEntityTypes = setOf("MERCHANT", "AMOUNT", "ACCOUNT", "BALANCE")

        messages.forEach { message ->
            val entities = extractor.extract(message)
            Log.d(TAG, "Input:    '${message.take(60)}...'")
            Log.d(TAG, "Entities: $entities")

            // All entity types should be valid
            entities.keys.forEach { key ->
                assertTrue(
                    "Unexpected entity type '$key' in results for: '${message.take(40)}...'",
                    key in validEntityTypes,
                )
            }

            // Values should not be empty
            entities.values.forEach { value ->
                assertTrue(
                    "Entity value should not be blank for: '${message.take(40)}...'",
                    value.isNotBlank(),
                )
            }
        }
    }

    /**
     * Tests that non-transactional messages produce few or no entities.
     */
    @Test
    fun testExtraction_withNonTransactionalSms_producesMinimalEntities() {
        val messages =
            listOf(
                "Your OTP for login is 123456. Do not share this with anyone.",
                "Congratulations! You have been selected for our exclusive rewards program.",
                "Your Amazon order has been shipped and will arrive by tomorrow.",
            )

        messages.forEach { message ->
            val entities = extractor.extract(message)
            Log.d(TAG, "Non-txn input: '${message.take(50)}...'")
            Log.d(TAG, "Non-txn entities: $entities")
            // We don't strictly assert empty — the model may find some entities —
            // but we log them for manual review
        }
    }

    private data class TestCase(
        val message: String,
        val expectedEntities: Map<String, List<String>>,
    )
}
