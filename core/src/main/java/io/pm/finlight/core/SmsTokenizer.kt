package io.pm.finlight.core

/**
 * Shared tokenizer logic for SMS Classification.
 * Ensures that the Android App and the Desktop Analyzer Tool use the exact same
 * preprocessing and tokenization steps, preventing training/inference skew.
 */
class SmsTokenizer(private val vocab: Map<String, Int>) {

    companion object {
        const val MAX_SEQUENCE_LENGTH = 250
        const val UNKNOWN_TOKEN = 1L // '[UNK]'
        const val PADDING_TOKEN = 0L
    }

    /**
     * Cleans and tokenizes the input text into a sequence of integers.
     * Matches Python's standard TextVectorization:
     * - Lowercase
     * - Remove punctuation
     * - Split by whitespace
     */
    fun tokenize(text: String): LongArray {
        // 1. Preprocess: Exact match of Python's string.punctuation
        val punctuationRegex = Regex("[!\"#\$%&'()*+,-./:;<=>?@\\[\\\\\\]^_`{|}~]")
        val cleanedText = text.lowercase()
            .replace(punctuationRegex, "")       // Remove punctuation
            .replace(Regex("\\s+"), " ") // Collapse multiple spaces
            .trim()

        val words = if (cleanedText.isEmpty()) emptyList() else cleanedText.split(" ")

        // 2. Map to Integers
        val tokens = words.map { vocab.getOrDefault(it, UNKNOWN_TOKEN.toInt()).toLong() }.toMutableList()

        // 3. Pad/Truncate
        while (tokens.size < MAX_SEQUENCE_LENGTH) {
            tokens.add(PADDING_TOKEN)
        }
        return tokens.take(MAX_SEQUENCE_LENGTH).toLongArray()
    }
}
