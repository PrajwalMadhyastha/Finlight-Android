package io.pm.finlight.ml

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WordPieceTokenizerTest {

    private lateinit var tokenizer: WordPieceTokenizer
    private val vocab = mutableMapOf<String, Int>()

    @Before
    fun setUp() {
        // Standard special tokens
        vocab["[PAD]"] = 0
        vocab["[UNK]"] = 100
        vocab["[CLS]"] = 101
        vocab["[SEP]"] = 102
        
        // Some common words
        vocab["hello"] = 1001
        vocab["world"] = 1002
        vocab["pay"] = 1003
        vocab["##ing"] = 1004
        vocab["tm"] = 1005
        vocab["##all"] = 1006
        vocab["at"] = 1007
        vocab["amazon"] = 1008
        vocab["rs"] = 1009
        vocab["."] = 1010
        vocab[","] = 1011
        vocab["100"] = 1012
        
        tokenizer = WordPieceTokenizer(vocab)
    }

    @Test
    fun `tokenize simple text correctly`() {
        val result = tokenizer.tokenize("hello world")
        
        // [CLS] hello world [SEP] + 124 [PAD]
        assertEquals(101, result.inputIds[0]) // [CLS]
        assertEquals(1001, result.inputIds[1]) // hello
        assertEquals(1002, result.inputIds[2]) // world
        assertEquals(102, result.inputIds[3]) // [SEP]
        assertEquals(0, result.inputIds[4]) // [PAD]
        
        assertEquals(1, result.attentionMask[0])
        assertEquals(1, result.attentionMask[3])
        assertEquals(0, result.attentionMask[4])
        
        // Word IDs
        assertEquals(-1, result.wordIds[0]) // [CLS]
        assertEquals(0, result.wordIds[1])  // hello (word index 0)
        assertEquals(1, result.wordIds[2])  // world (word index 1)
        assertEquals(-1, result.wordIds[3]) // [SEP]
    }

    @Test
    fun `tokenize with subwords correctly`() {
        val result = tokenizer.tokenize("paying")
        
        // "paying" -> "pay", "##ing"
        assertEquals(1003, result.inputIds[1]) // pay
        assertEquals(1004, result.inputIds[2]) // ##ing
        
        assertEquals(0, result.wordIds[1])
        assertEquals(0, result.wordIds[2])
    }

    @Test
    fun `tokenize with unknown words correctly`() {
        val result = tokenizer.tokenize("unknownxyz")
        
        assertEquals(100, result.inputIds[1]) // [UNK]
        assertEquals(0, result.wordIds[1])
    }

    @Test
    fun `tokenize with punctuation correctly`() {
        val result = tokenizer.tokenize("Rs. 100, amazon")
        
        // Tokens: [CLS], rs, ., 100, ,, amazon, [SEP]
        assertEquals(1009, result.inputIds[1]) // rs
        assertEquals(1010, result.inputIds[2]) // .
        assertEquals(1012, result.inputIds[3]) // 100
        assertEquals(1011, result.inputIds[4]) // ,
        assertEquals(1008, result.inputIds[5]) // amazon
        
        assertEquals(0, result.wordIds[1]) // rs
        assertEquals(1, result.wordIds[2]) // .
        assertEquals(2, result.wordIds[3]) // 100
        assertEquals(3, result.wordIds[4]) // ,
        assertEquals(4, result.wordIds[5]) // amazon
    }

    @Test
    fun `tokenize handles mixed case by lowercasing`() {
        val result = tokenizer.tokenize("Hello WORLD")
        
        assertEquals(1001, result.inputIds[1]) // hello
        assertEquals(1002, result.inputIds[2]) // world
    }

    @Test
    fun `tokenize truncates long text`() {
        val longText = (1..200).joinToString(" ") { "hello" }
        val result = tokenizer.tokenize(longText)
        
        assertEquals(WordPieceTokenizer.MAX_SEQ_LENGTH, result.inputIds.size)
        assertEquals(101, result.inputIds[0]) // [CLS]
        assertEquals(102, result.inputIds[127]) // [SEP]
    }

    @Test
    fun `tokenize handles very long words with UNK`() {
        val veryLongWord = "a".repeat(201)
        val result = tokenizer.tokenize(veryLongWord)
        
        assertEquals(100, result.inputIds[1]) // [UNK]
    }
}
