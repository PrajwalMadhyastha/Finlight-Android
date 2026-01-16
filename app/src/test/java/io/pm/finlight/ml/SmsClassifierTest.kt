// =================================================================================
// FILE: app/src/test/java/io/pm/finlight/ml/SmsClassifierTest.kt
// REASON: Comprehensive unit tests for SmsClassifier to achieve 100%
// code coverage for the io.pm.finlight.ml package.
// Uses the @VisibleForTesting internal constructor for testability.
// =================================================================================
package io.pm.finlight.ml

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.pm.finlight.TestApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

/**
 * Comprehensive unit tests for [SmsClassifier].
 *
 * These tests verify:
 * 1. Text preprocessing (tokenization) - tested via internal tokenize() method
 * 2. Classification behavior - tested with mocked interpreter
 * 3. Resource cleanup - tested via state verification
 * 4. Thread safety - tested with concurrent calls
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsClassifierTest {

    private lateinit var mockInterpreter: Interpreter
    
    // Sample vocabulary for testing (index 0 = empty, 1 = [UNK], 2+ = words)
    private val testVocab = mapOf(
        "" to 0,
        "[UNK]" to 1,
        "bank" to 2,
        "debited" to 3,
        "credited" to 4,
        "rs" to 5,
        "your" to 6,
        "account" to 7,
        "for" to 8,
        "on" to 9,
        "at" to 10,
        "from" to 11,
        "to" to 12,
        "hello" to 13,
        "world" to 14,
        "yes" to 15,
        "ok" to 16,
        "good" to 17,
        "done" to 18
    )

    @Before
    fun setUp() {
        mockInterpreter = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        // Cleanup handled by GC
    }

    // =========================================================================
    // classify() Tests
    // =========================================================================

    @Test
    fun `classify returns 0 when interpreter is null`() {
        val classifier = SmsClassifier(testVocab, null)

        val result = classifier.classify("Test message")

        assertEquals("Should return 0.0f when interpreter is null", 0.0f, result, 0.001f)
    }

    @Test
    fun `classify calls interpreter run with input and output buffers`() {
        val outputSlot = slot<ByteBuffer>()

        every { mockInterpreter.run(any(), capture(outputSlot)) } answers {
            outputSlot.captured.putFloat(0.75f)
        }

        val classifier = SmsClassifier(testVocab, mockInterpreter)
        val result = classifier.classify("bank debited rs 500")

        verify { mockInterpreter.run(any(), any()) }
        assertEquals("Should return the value from output buffer", 0.75f, result, 0.001f)
    }

    @Test
    fun `classify handles empty string`() {
        val outputSlot = slot<ByteBuffer>()

        every { mockInterpreter.run(any(), capture(outputSlot)) } answers {
            outputSlot.captured.putFloat(0.1f)
        }

        val classifier = SmsClassifier(testVocab, mockInterpreter)
        val result = classifier.classify("")

        assertEquals("Score should match mocked output", 0.1f, result, 0.001f)
    }

    @Test
    fun `classify handles very long text`() {
        val outputSlot = slot<ByteBuffer>()

        every { mockInterpreter.run(any(), capture(outputSlot)) } answers {
            outputSlot.captured.putFloat(0.6f)
        }

        val classifier = SmsClassifier(testVocab, mockInterpreter)
        val longText = "bank ".repeat(300)
        val result = classifier.classify(longText)

        assertEquals("Should handle long text", 0.6f, result, 0.001f)
    }

    // =========================================================================
    // tokenize() Tests
    // =========================================================================

    @Test
    fun `tokenize converts text to lowercase`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val tokens = classifier.tokenize("BANK DEBITED")
        val tokensLower = classifier.tokenize("bank debited")

        assertArrayEquals("Uppercase and lowercase should produce same tokens", tokensLower, tokens)
    }

    @Test
    fun `tokenize removes punctuation`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val withPunctuation = classifier.tokenize("bank, debited!")
        val withoutPunctuation = classifier.tokenize("bank debited")

        assertArrayEquals("Punctuation should be removed", withoutPunctuation, withPunctuation)
    }

    @Test
    fun `tokenize collapses multiple spaces`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val multipleSpaces = classifier.tokenize("bank    debited")
        val singleSpace = classifier.tokenize("bank debited")

        assertArrayEquals("Multiple spaces should be collapsed", singleSpace, multipleSpaces)
    }

    @Test
    fun `tokenize pads short sequences to MAX_SEQUENCE_LENGTH`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val tokens = classifier.tokenize("bank")

        assertEquals("Token array should have MAX_SEQUENCE_LENGTH elements", 250, tokens.size)
        assertEquals("Last token should be padding (0)", 0L, tokens.last())
    }

    @Test
    fun `tokenize truncates long sequences to MAX_SEQUENCE_LENGTH`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val longText = (1..300).joinToString(" ") { "word$it" }
        val tokens = classifier.tokenize(longText)

        assertEquals("Token array should be truncated to MAX_SEQUENCE_LENGTH", 250, tokens.size)
    }

    @Test
    fun `tokenize maps unknown words to UNKNOWN_TOKEN`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val tokens = classifier.tokenize("unknownxyzword")

        assertEquals("Unknown word should map to UNKNOWN_TOKEN (1)", 1L, tokens[0])
    }

    @Test
    fun `tokenize maps known vocabulary words correctly`() {
        val classifier = SmsClassifier(testVocab, null)
        
        // 'bank' is in our test vocabulary at index 2
        val tokens = classifier.tokenize("bank")

        assertEquals("'bank' should map to vocab index 2", 2L, tokens[0])
    }

    @Test
    fun `tokenize handles common punctuation characters`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val textWithPunctuation = "hello! world? yes. ok, good; done:"
        val expected = classifier.tokenize("hello world yes ok good done")
        val actual = classifier.tokenize(textWithPunctuation)

        assertArrayEquals("Common punctuation should be removed", expected, actual)
    }

    @Test
    fun `tokenize handles leading and trailing whitespace`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val withWhitespace = classifier.tokenize("   bank   ")
        val withoutWhitespace = classifier.tokenize("bank")

        assertArrayEquals("Leading/trailing whitespace should be trimmed", withoutWhitespace, withWhitespace)
    }

    @Test
    fun `tokenize handles text with only punctuation`() {
        val classifier = SmsClassifier(testVocab, null)
        
        val tokens = classifier.tokenize("!!!...???")

        assertEquals("Should have MAX_SEQUENCE_LENGTH elements", 250, tokens.size)
        // All should be padding tokens (0) since no words remain
        assertTrue("All tokens should be padding", tokens.all { it == 0L })
    }

    // =========================================================================
    // close() Tests
    // =========================================================================

    @Test
    fun `close calls interpreter close method`() {
        val classifier = SmsClassifier(testVocab, mockInterpreter)
        
        classifier.close()

        verify { mockInterpreter.close() }
    }

    @Test
    fun `classify returns 0 after close is called`() {
        val classifier = SmsClassifier(testVocab, mockInterpreter)
        
        classifier.close()
        val result = classifier.classify("bank debited rs 500")

        assertEquals("Should return 0.0f after close()", 0.0f, result, 0.001f)
    }

    // =========================================================================
    // Thread Safety Tests
    // =========================================================================

    @Test
    fun `classify is thread-safe with concurrent calls`() {
        val outputSlot = slot<ByteBuffer>()
        
        every { mockInterpreter.run(any(), capture(outputSlot)) } answers {
            Thread.sleep(10)
            outputSlot.captured.putFloat(0.5f)
        }

        val classifier = SmsClassifier(testVocab, mockInterpreter)
        val results = mutableListOf<Float>()
        val threads = (1..5).map { index ->
            Thread {
                try {
                    val result = classifier.classify("Transaction $index")
                    synchronized(results) {
                        results.add(result)
                    }
                } catch (e: Exception) {
                    fail("Thread $index threw exception: ${e.message}")
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        assertEquals("All 5 threads should complete successfully", 5, results.size)
    }

    // =========================================================================
    // Input/Output Buffer Tests
    // =========================================================================

    @Test
    fun `classify creates correct input buffer size`() {
        val inputSlot = slot<ByteBuffer>()
        val outputSlot = slot<ByteBuffer>()

        every { mockInterpreter.run(capture(inputSlot), capture(outputSlot)) } answers {
            outputSlot.captured.putFloat(0.5f)
        }

        val classifier = SmsClassifier(testVocab, mockInterpreter)
        classifier.classify("bank")

        // MAX_SEQUENCE_LENGTH (250) * 8 bytes per Long = 2000 bytes
        assertEquals("Input buffer should be 2000 bytes", 2000, inputSlot.captured.capacity())
    }

    @Test
    fun `classify creates correct output buffer size`() {
        val inputSlot = slot<ByteBuffer>()
        val outputSlot = slot<ByteBuffer>()

        every { mockInterpreter.run(capture(inputSlot), capture(outputSlot)) } answers {
            outputSlot.captured.putFloat(0.5f)
        }

        val classifier = SmsClassifier(testVocab, mockInterpreter)
        classifier.classify("bank")

        // 1 float * 4 bytes = 4 bytes
        assertEquals("Output buffer should be 4 bytes", 4, outputSlot.captured.capacity())
    }

    // =========================================================================
    // Companion Object Tests
    // =========================================================================

    @Test
    fun `MAX_SEQUENCE_LENGTH is 250`() {
        assertEquals("MAX_SEQUENCE_LENGTH should be 250", 250, SmsClassifier.MAX_SEQUENCE_LENGTH)
    }

    @Test
    fun `UNKNOWN_TOKEN is 1`() {
        assertEquals("UNKNOWN_TOKEN should be 1", 1L, SmsClassifier.UNKNOWN_TOKEN)
    }
}
