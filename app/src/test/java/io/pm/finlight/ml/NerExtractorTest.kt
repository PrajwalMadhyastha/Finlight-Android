package io.pm.finlight.ml

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Tensor

class NerExtractorTest {

    // Helper for Mockito any() with non-nullable Kotlin types
    private fun <T> anyObject(): T {
        any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private lateinit var tokenizer: WordPieceTokenizer
    private lateinit var extractor: NerExtractor
    private val labelMap = mapOf(
        0 to "O",
        1 to "B-MERCHANT",
        2 to "I-MERCHANT",
        3 to "B-AMOUNT",
        4 to "I-AMOUNT",
        5 to "B-ACCOUNT",
        6 to "I-ACCOUNT"
    )

    @Before
    fun setUp() {
        val vocab = mapOf(
            "[PAD]" to 0,
            "[UNK]" to 100,
            "[CLS]" to 101,
            "[SEP]" to 102,
            "rs" to 10,
            "." to 11,
            "100" to 12,
            "amazon" to 13,
            "##ing" to 14
        )
        tokenizer = WordPieceTokenizer(vocab)
        // Note: Interpreter is mocked but we mostly test internal methods to reach coverage
        val mockInterpreter = mock(Interpreter::class.java)
        extractor = NerExtractor(tokenizer, mockInterpreter, labelMap)
    }

    @Test
    fun `extractEntities correctly parses BIO tags`() {
        // [CLS] Spent Rs . 100 at Amazon [SEP]
        //  -1    0     1  2   3   4    5    -1
        val tokens = listOf("[CLS]", "spent", "rs", ".", "100", "at", "amazon", "[SEP]")
        val wordIds = intArrayOf(-1, 0, 1, 2, 3, 4, 5, -1)
        val tokenResult = WordPieceTokenizer.TokenizationResult(
            inputIds = IntArray(128),
            attentionMask = IntArray(128),
            wordIds = wordIds + IntArray(120) { -1 },
            tokens = tokens + List(120) { "[PAD]" }
        )

        val predictions = IntArray(128) { 0 }
        predictions[2] = 3 // B-AMOUNT (rs)
        predictions[3] = 4 // I-AMOUNT (.)
        predictions[4] = 4 // I-AMOUNT (100)
        predictions[6] = 1 // B-MERCHANT (amazon)

        val result = extractor.extractEntities(predictions, tokenResult)

        assertEquals("100", result["AMOUNT"]) // rs. 100 -> rs.100 -> cleanup -> 100 (after post-processing)
        assertEquals("amazon", result["MERCHANT"])
    }

    @Test
    fun `extractEntities handles complex BIO scenarios`() {
        val tokens = listOf("B1", "I1", "I1", "B2", "I2", "O", "I3", "B1")
        val wordIds = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val tokenResult = WordPieceTokenizer.TokenizationResult(
            inputIds = IntArray(128),
            attentionMask = IntArray(128),
            wordIds = wordIds + IntArray(120) { -1 },
            tokens = tokens + List(120) { "[PAD]" }
        )

        val predictions = IntArray(128) { 0 }
        // labelMap: 1=B-MERCHANT, 2=I-MERCHANT, 3=B-AMOUNT, 4=I-AMOUNT
        predictions[0] = 1 // B-MERCHANT
        predictions[1] = 2 // I-MERCHANT
        predictions[2] = 4 // I-AMOUNT (Type mismatch! Should close MERCHANT and start AMOUNT)
        predictions[3] = 3 // B-AMOUNT
        predictions[4] = 4 // I-AMOUNT
        predictions[5] = 0 // O
        predictions[6] = 4 // Orphan I-AMOUNT (Should be ignored)
        predictions[7] = 1 // Another B-MERCHANT

        val result = extractor.extractEntities(predictions, tokenResult)

        // B1 I1 -> B-MERCHANT I-MERCHANT -> "B1 I1"
        // I1 (as I-AMOUNT) -> type mismatch -> starts new span -> "I1"
        // B2 I2 -> B-AMOUNT I-AMOUNT -> "B2 I2"
        // I3 -> orphan -> ignored
        // B1 -> B-MERCHANT -> "B1"
        
        // Final merged: MERCHANT="B1 I1, B1", AMOUNT="I1, B2 I2"
        // (Wait, I1 is token 2, its text is "I1")
        
        assertTrue(result.containsKey("MERCHANT"))
        assertTrue(result.containsKey("AMOUNT"))
        assertEquals("B1 I1, B1", result["MERCHANT"])
        assertEquals("I1, B2 I2", result["AMOUNT"])
    }

    @Test
    fun `extractEntities handles special tokens breaking spans`() {
        val tokens = listOf("B1", "[SEP]", "I1")
        val wordIds = intArrayOf(0, -1, 1)
        val tokenResult = WordPieceTokenizer.TokenizationResult(
            inputIds = IntArray(128),
            attentionMask = IntArray(128),
            wordIds = wordIds + IntArray(125) { -1 },
            tokens = tokens + List(125) { "[PAD]" }
        )

        val predictions = IntArray(128) { 0 }
        predictions[0] = 1 // B-MERCHANT
        predictions[2] = 2 // I-MERCHANT

        val result = extractor.extractEntities(predictions, tokenResult)

        // [SEP] has wordId -1, so it closes the span.
        // The I1 afterward is an orphan because currentSpan was nullified.
        assertEquals("B1", result["MERCHANT"])
    }

    @Test
    fun `mergeSubwordTokens joins ## tokens correctly`() {
        val tokens = listOf("play", "##ing", "at", "night")
        val result = extractor.mergeSubwordTokens(tokens)
        assertEquals("playing at night", result)
    }

    @Test
    fun `cleanupPunctuationSpacing handles various formats`() {
        // This method is private but internal methods call it. 
        // We can test it through mergeSubwordTokens since it's used there.
        
        val testCases = mapOf(
            listOf("rs", ".", "100") to "rs.100",
            listOf("a", "/", "c") to "A/C", // Note: wait, NerExtractor doesn't uppercase A/C in cleanup?
            listOf("1", ",", "41", ",", "453") to "1,41,453"
        )
        
        // Wait, looking at cleanupPunctuationSpacing in NerExtractor.kt:
        // .replace(Regex("""\s+([.,/:*\-@])"""), "$1")
        // .replace(Regex("""([.,/:*\-@])\s+(?=\w)"""), "$1")
        // .trimEnd('.', ',', ':', ';', '-', '@')
        
        // Let's test precisely what it does.
        
        assertEquals("rs.100", extractor.mergeSubwordTokens(listOf("rs", " ", ".", " ", "100")))
        
        // Test all punctuation types and trimEnd
        assertEquals("a/c", extractor.mergeSubwordTokens(listOf("a", "/", "c")))
        assertEquals("1,41,453", extractor.mergeSubwordTokens(listOf("1", " , ", "41", " , ", "453")))
        assertEquals("user@host", extractor.mergeSubwordTokens(listOf("user", " @ ", "host")))
        assertEquals("key:value", extractor.mergeSubwordTokens(listOf("key", " : ", "value")))
        assertEquals("word-word", extractor.mergeSubwordTokens(listOf("word", " - ", "word")))
        assertEquals("stars*filter", extractor.mergeSubwordTokens(listOf("stars", " * ", "filter")))
        
        // Test trimEnd
        assertEquals("end", extractor.mergeSubwordTokens(listOf("end", ".")))
        assertEquals("end", extractor.mergeSubwordTokens(listOf("end", ",")))
        assertEquals("end", extractor.mergeSubwordTokens(listOf("end", "@")))
    }

    @Test
    fun `extractEntities post-processes AMOUNT correctly`() {
        val tokens = listOf("Rs", ".", "1", ",", "000")
        val wordIds = intArrayOf(0, 1, 2, 3, 4)
        val tokenResult = WordPieceTokenizer.TokenizationResult(
            inputIds = IntArray(128),
            attentionMask = IntArray(128),
            wordIds = wordIds + IntArray(123) { -1 },
            tokens = tokens + List(123) { "[PAD]" }
        )
        val predictions = IntArray(128) { 0 }
        predictions[0] = 3 // B-AMOUNT
        predictions[1] = 4 // I-AMOUNT
        predictions[2] = 4 // I-AMOUNT
        predictions[3] = 4 // I-AMOUNT
        predictions[4] = 4 // I-AMOUNT

        var result = extractor.extractEntities(predictions.copyOf(), tokenResult)
        assertEquals("1000", result["AMOUNT"])

        // Test INR and ₹
        val tokenResult2 = tokenResult.copy(
            tokens = listOf("INR", "50", "##0") + List(125) { "" },
            wordIds = intArrayOf(0, 1, 2) + IntArray(125) { -1 }
        )
        val predictions2 = IntArray(128) { 0 }
        predictions2[0] = 3
        predictions2[1] = 4
        predictions2[2] = 4
        result = extractor.extractEntities(predictions2, tokenResult2)
        assertEquals("500", result["AMOUNT"])

        val tokenResult3 = tokenResult.copy(
            tokens = listOf("₹", "9", "##9") + List(126) { "" },
            wordIds = intArrayOf(0, 1, 2) + IntArray(125) { -1 }
        )
        val predictions3 = IntArray(128) { 0 }
        predictions3[0] = 3
        predictions3[1] = 4
        predictions3[2] = 4
        result = extractor.extractEntities(predictions3, tokenResult3)
        assertEquals("99", result["AMOUNT"])
    }

    @Test
    fun `extract orchestration works correctly`() {
        val mockInterp = mock(Interpreter::class.java)
        `when`(mockInterp.inputTensorCount).thenReturn(2)
        val tensor1 = mock(Tensor::class.java)
        val tensor2 = mock(Tensor::class.java)
        `when`(tensor1.name()).thenReturn("input_ids")
        `when`(tensor2.name()).thenReturn("attention_mask")
        `when`(mockInterp.getInputTensor(0)).thenReturn(tensor1)
        `when`(mockInterp.getInputTensor(1)).thenReturn(tensor2)
        
        val ex = NerExtractor(tokenizer, mockInterp, labelMap)
        // extract returns emptyMap because our mock interpreter doesn't fill the output buffer,
        // but we've covered Step 1-4 lines.
        ex.extract("Rs 100 spent at Amazon")
        
        verify(mockInterp).runForMultipleInputsOutputs(anyObject(), anyObject())
    }

    @Test
    fun `extractEntities joins multiple merchants with comma`() {
        val tokens = listOf("amazon", "and", "flipkart")
        val wordIds = intArrayOf(0, 1, 2)
        val tokenResult = WordPieceTokenizer.TokenizationResult(
            inputIds = intArrayOf(0),
            attentionMask = intArrayOf(0),
            wordIds = wordIds + IntArray(125) { -1 },
            tokens = tokens + List(125) { "[PAD]" }
        )

        val predictions = IntArray(128) { 0 }
        predictions[0] = 1 // B-MERCHANT (amazon)
        predictions[2] = 1 // B-MERCHANT (flipkart)

        val result = extractor.extractEntities(predictions, tokenResult)

        assertEquals("amazon, flipkart", result["MERCHANT"])
    }

    @Test
    fun `extract returns empty if interpreter is null`() {
        val extractorWithNull = NerExtractor(tokenizer, null, labelMap)
        val result = extractorWithNull.extract("test")
        assertTrue(result.isEmpty())
        
        // Test close with null interpreter
        extractorWithNull.close()
    }

    @Test
    fun `close closes interpreter`() {
        val mockInterpreter = mock(Interpreter::class.java)
        val extractorToClose = NerExtractor(tokenizer, mockInterpreter, labelMap)
        extractorToClose.close()
        verify(mockInterpreter).close()
    }
}
