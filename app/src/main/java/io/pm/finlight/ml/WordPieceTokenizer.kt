package io.pm.finlight.ml

/**
 * BERT-compatible WordPiece tokenizer for MobileBERT NER inference.
 *
 * Replicates the behavior of HuggingFace's MobileBertTokenizer:
 * - Lowercases input (do_lower_case=true)
 * - Splits on whitespace and punctuation
 * - Applies WordPiece subword splitting with "##" prefix
 * - Adds [CLS] and [SEP] special tokens
 * - Pads/truncates to MAX_SEQ_LENGTH
 *
 * The tokenization must exactly match the Python training tokenizer to ensure
 * correct NER predictions on-device.
 */
class WordPieceTokenizer(private val vocab: Map<String, Int>) {

    companion object {
        const val MAX_SEQ_LENGTH = 128

        // Special token IDs (standard BERT vocab positions)
        const val PAD_TOKEN_ID = 0
        const val UNK_TOKEN_ID = 100
        const val CLS_TOKEN_ID = 101
        const val SEP_TOKEN_ID = 102

        private const val MAX_WORD_LENGTH = 200
        private const val SUBWORD_PREFIX = "##"
    }

    /**
     * Result of tokenization, containing both the token IDs and the mapping
     * from token positions back to original word indices.
     *
     * @param inputIds Token IDs for the model (length = MAX_SEQ_LENGTH)
     * @param attentionMask 1 for real tokens, 0 for padding (length = MAX_SEQ_LENGTH)
     * @param wordIds Maps each token position to the original word index, or -1 for
     *               special tokens ([CLS], [SEP]) and padding. Used for reconstructing
     *               entity text from BIO predictions.
     * @param tokens The actual token strings for debugging/entity reconstruction.
     */
    data class TokenizationResult(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val wordIds: IntArray,
        val tokens: List<String>,
    )

    /**
     * Tokenize input text using BERT WordPiece tokenization.
     *
     * Produces: [CLS] token1 token2 ... tokenN [SEP] [PAD] ... [PAD]
     *
     * @param text The raw SMS text to tokenize.
     * @return TokenizationResult with input_ids, attention_mask, word_ids, and tokens.
     */
    fun tokenize(text: String): TokenizationResult {
        // Step 1: Lowercase (do_lower_case=true)
        val lowered = text.lowercase()

        // Step 2: Split on whitespace and punctuation, keeping punctuation as tokens
        val words = basicTokenize(lowered)

        // Step 3: Apply WordPiece to each word
        val allTokens = mutableListOf<String>()
        val allWordIds = mutableListOf<Int>()

        for ((wordIdx, word) in words.withIndex()) {
            val subTokens = wordPieceTokenize(word)
            for (subToken in subTokens) {
                allTokens.add(subToken)
                allWordIds.add(wordIdx)
            }
        }

        // Step 4: Truncate to fit [CLS] ... [SEP] within MAX_SEQ_LENGTH
        val maxTokens = MAX_SEQ_LENGTH - 2 // Reserve space for [CLS] and [SEP]
        val truncatedTokens = allTokens.take(maxTokens)
        val truncatedWordIds = allWordIds.take(maxTokens)

        // Step 5: Build final sequences
        val finalTokens = mutableListOf("[CLS]")
        val finalWordIds = mutableListOf(-1) // [CLS] has no word

        finalTokens.addAll(truncatedTokens)
        finalWordIds.addAll(truncatedWordIds)

        finalTokens.add("[SEP]")
        finalWordIds.add(-1) // [SEP] has no word

        // Step 6: Convert to IDs
        val inputIds = IntArray(MAX_SEQ_LENGTH) { PAD_TOKEN_ID }
        val attentionMask = IntArray(MAX_SEQ_LENGTH) { 0 }
        val wordIdsFinal = IntArray(MAX_SEQ_LENGTH) { -1 }

        for (i in finalTokens.indices) {
            inputIds[i] = vocab[finalTokens[i]] ?: UNK_TOKEN_ID
            attentionMask[i] = 1
            wordIdsFinal[i] = finalWordIds[i]
        }

        return TokenizationResult(
            inputIds = inputIds,
            attentionMask = attentionMask,
            wordIds = wordIdsFinal,
            tokens = finalTokens + List(MAX_SEQ_LENGTH - finalTokens.size) { "[PAD]" },
        )
    }

    /**
     * Basic tokenization: split on whitespace and punctuation.
     *
     * Matches BERT's BasicTokenizer behavior:
     * - Splits on whitespace
     * - Isolates punctuation characters as separate tokens
     * - Strips accents (not needed for SMS content)
     */
    private fun basicTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        for (char in text) {
            when {
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                isPunctuation(char) -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    tokens.add(char.toString())
                }
                else -> {
                    current.append(char)
                }
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /**
     * WordPiece tokenization for a single word.
     *
     * Attempts to split the word into known subword units using a greedy
     * longest-match-first algorithm. Subsequent pieces get the "##" prefix.
     *
     * Example: "playing" → ["play", "##ing"]
     *          "unknownxyz" → ["[UNK]"] (if no split works)
     */
    private fun wordPieceTokenize(word: String): List<String> {
        if (word.length > MAX_WORD_LENGTH) {
            return listOf("[UNK]")
        }

        val tokens = mutableListOf<String>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var found = false

            while (start < end) {
                val substr = if (start > 0) {
                    "$SUBWORD_PREFIX${word.substring(start, end)}"
                } else {
                    word.substring(start, end)
                }

                if (vocab.containsKey(substr)) {
                    tokens.add(substr)
                    found = true
                    break
                }
                end--
            }

            if (!found) {
                // Character not in vocab even as single char — use [UNK] for entire word
                return listOf("[UNK]")
            }
            start = end
        }
        return tokens
    }

    /**
     * Check if a character is punctuation (matching BERT's definition).
     * Covers ASCII punctuation and Unicode general punctuation categories.
     */
    private fun isPunctuation(char: Char): Boolean {
        val cp = char.code
        // ASCII punctuation ranges
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) {
            return true
        }
        // Unicode punctuation category
        val category = Character.getType(char).toByte()
        return category == Character.CONNECTOR_PUNCTUATION ||
                category == Character.DASH_PUNCTUATION ||
                category == Character.END_PUNCTUATION ||
                category == Character.FINAL_QUOTE_PUNCTUATION ||
                category == Character.INITIAL_QUOTE_PUNCTUATION ||
                category == Character.OTHER_PUNCTUATION ||
                category == Character.START_PUNCTUATION
    }
}
