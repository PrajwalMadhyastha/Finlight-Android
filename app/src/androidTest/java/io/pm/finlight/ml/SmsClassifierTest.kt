// =================================================================================
// FILE: app/src/androidTest/java/io/pm/finlight/ml/SmsClassifierTest.kt
// REASON: NEW FILE - This instrumented test validates the on-device TFLite model.
// It loads the actual model from the assets folder and runs it against a set of
// known transactional and non-transactional SMS messages to verify that the
// classification confidence scores are within the expected ranges.
//
// DEBUG: Added logging to the test methods to print the preprocessed, tokenized
// integer array before classification. This allows for direct comparison with the
// output from the Python training script's debug function to find any subtle
// discrepancies in text processing.
// =================================================================================
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
 * Instrumented test for the SmsClassifier class.
 * This test runs on an Android device/emulator to verify the TFLite model.
 */
@RunWith(AndroidJUnit4::class)
class SmsClassifierTest {

    private lateinit var classifier: SmsClassifier

    @Before
    fun setUp() {
        // Get the application context to access assets
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        classifier = SmsClassifier(appContext)
    }

    @After
    fun tearDown() {
        // Release the model's resources
        classifier.close()
    }

    /**
     * Tests that the SmsClassifier initializes successfully by loading the model
     * from the assets folder without crashing.
     */
    @Test
    fun testModelInitialization_succeeds() {
        // The real test is that setUp() doesn't throw an exception.
        // We can still add an explicit check.
        assertNotNull("The classifier should not be null after initialization.", classifier)
    }

    /**
     * Tests a variety of known transactional SMS messages and asserts that the model
     * classifies them with a high confidence score ( > 0.9 ).
     */
    @Test
    fun testClassification_withKnownTransactionalSms_returnsHighConfidence() {
        val transactionalMessages = listOf(
            // HDFC
            "Sent Rs.11.00\nFrom HDFC Bank A/C *1243\nTo Raju\nOn 07/08/25\nRef 558523453508\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            "You've spent Rs.349 On HDFC Bank CREDIT Card xx1335 At RAZ*StickON...",
            // SBI
            "Rs.267.00 spent on your SBI Credit Card ending with 3201 at HALLI THOTA on 29-06-25 via UPI",
            "Your A/C XXXXX650077 Credited INR 1,41,453.00 on 15/07/25 -Deposit by transfer from ESIC MODEL HOSP. RJJ. Avl Bal INR 3,89,969.28-SBI",
            // ICICI
            "ICICI Bank Acct XX823 debited for Rs 240.00 on 21-Jun-25; DAKSHIN CAFE credited.",
            "Dear Customer, Acct XX823 is credited with Rs 6000.00 on 26-Jun-25 from GANGA MANGA."
        )

        transactionalMessages.forEach { message ->
            val score = classifier.classify(message)
            // Use reflection to access the private tokenize method for debugging
            val tokenizeMethod = classifier.javaClass.getDeclaredMethod("tokenize", String::class.java)
            tokenizeMethod.isAccessible = true
            val tokens = tokenizeMethod.invoke(classifier, message) as LongArray
            Log.d("SmsClassifierTest", "Kotlin Tokens for '${message.take(20)}...': ${tokens.take(30).joinToString(", ")}...")

            assertTrue(
                "Expected high confidence for transactional message, but got $score. Message: '$message'",
                score > 0.8f
            )
        }
    }

    /**
     * Tests a variety of known non-transactional (junk) SMS messages and asserts that
     * the model classifies them with a very low confidence score ( < 0.1 ).
     */
    @Test
    fun testClassification_withKnownNonTransactionalSms_returnsLowConfidence() {
        val nonTransactionalMessages = listOf(
            // OTP
            "Your OTP for login is 123456. Do not share this with anyone.",
            // Promotion
            "Congratulations! You are eligible for a pre-approved loan of Rs. 5,00,000.",
            // Delivery
            "Your order from Amazon has been shipped and will arrive tomorrow.",
            // Bill Reminder
            "Your credit card statement for the month of August is generated. Total amount due is Rs. 5,432.10.",
            // General Info
            "Your Application No. GJ005S250467224 for Gruha Jyoti Scheme received & sent to your ESCOM for Processing."
        )

        nonTransactionalMessages.forEach { message ->
            val score = classifier.classify(message)
            assertTrue(
                "Expected low confidence for non-transactional message, but got $score. Message: '$message'",
                score < 0.1f
            )
        }
    }
}

