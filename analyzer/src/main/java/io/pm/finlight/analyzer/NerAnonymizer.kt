package io.pm.finlight.analyzer

import kotlin.random.Random

/**
 * NER-aware anonymization that operates at the token level.
 *
 * Unlike [Anonymizer] which works on raw text (breaking token alignment),
 * this anonymizer replaces individual tokens in-place, preserving entity
 * [tokenIndices] and structural integrity of the NER export format.
 *
 * **Anonymized:** account digits, phone numbers, UPI refs, dates, UPI IDs
 * **Preserved:** amounts, balances, bank names, merchants, transaction keywords
 */
object NerAnonymizer {

    private val random = Random(System.currentTimeMillis())

    /**
     * Anonymize a single NER training export item.
     * Returns a new item with sensitive tokens replaced but entity indices unchanged.
     */
    fun anonymize(item: NerTrainingItemWithMetadata): NerTrainingItemWithMetadata {
        val tokens = item.tokens.toMutableList()

        // Collect all token indices that are part of entities
        val entityTokenMap = mutableMapOf<Int, String>() // tokenIndex -> entityType
        for (entity in item.entities) {
            for (idx in entity.tokenIndices) {
                entityTokenMap[idx] = entity.type
            }
        }

        // Anonymize each token based on context
        for (i in tokens.indices) {
            val entityType = entityTokenMap[i]

            when (entityType) {
                // AMOUNT and BALANCE: keep as-is (model needs format patterns)
                "AMOUNT", "BALANCE" -> { /* preserve */ }

                // MERCHANT: keep as-is (model needs to learn merchant names)
                "MERCHANT" -> { /* preserve */ }

                // ACCOUNT: anonymize only the numeric portion, keep bank names
                "ACCOUNT" -> {
                    tokens[i] = anonymizeAccountToken(tokens[i])
                }

                // Not part of any entity: anonymize PII patterns
                null -> {
                    tokens[i] = anonymizeNonEntityToken(tokens[i])
                }
            }
        }

        // Reconstruct text from modified tokens
        val newText = reconstructText(item.text, item.tokens, tokens)

        // Rebuild entity text from the new tokens
        val newEntities = item.entities.map { entity ->
            entity.copy(
                text = entity.tokenIndices
                    .filter { it < tokens.size }
                    .joinToString(" ") { tokens[it] }
            )
        }

        return item.copy(
            text = newText,
            tokens = tokens,
            entities = newEntities
        )
    }

    /**
     * Anonymize an ACCOUNT entity token.
     * Replaces digits (account numbers) but preserves letters (bank names, "A/c", "SB").
     */
    private fun anonymizeAccountToken(token: String): String {
        // If token is purely numeric or mostly numeric (account number), randomize digits
        val digitCount = token.count { it.isDigit() }
        val letterCount = token.count { it.isLetter() }

        return when {
            // Pure digits: replace with random digits of same length
            digitCount > 0 && letterCount == 0 -> {
                token.map { ch ->
                    when {
                        ch.isDigit() -> random.nextInt(0, 10).digitToChar()
                        else -> ch // preserve *, X, dots, etc.
                    }
                }.joinToString("")
            }
            // Mixed like "XX1234" or "A/c": keep letters, replace digits
            digitCount > 0 && letterCount > 0 -> {
                token.map { ch ->
                    if (ch.isDigit()) random.nextInt(0, 10).digitToChar() else ch
                }.joinToString("")
            }
            // Pure letters (bank name tokens like "Union", "Bank"): preserve
            else -> token
        }
    }

    /**
     * Anonymize a non-entity token (free text).
     * Targets: phone numbers, UPI refs, dates, UPI IDs, other digit sequences.
     */
    private fun anonymizeNonEntityToken(token: String): String {
        // UPI ID pattern: something@somebank
        if (token.contains("@") && token.length > 3) {
            return "user@upi"
        }

        // Check if token is purely digits
        val digitCount = token.count { it.isDigit() }
        val totalChars = token.length

        if (digitCount == 0) {
            // No digits at all — likely a keyword, keep as-is
            return token
        }

        // Phone number: 10 digits
        if (token.length == 10 && token.all { it.isDigit() }) {
            return "9876543210"
        }

        // Long digit sequences (UPI refs, transaction IDs): randomize
        if (digitCount >= 6 && token.all { it.isDigit() || it == '.' || it == '-' || it == '/' }) {
            return token.map { ch ->
                if (ch.isDigit()) random.nextInt(0, 10).digitToChar() else ch
            }.joinToString("")
        }

        // Date components (2-4 digit numbers that aren't amounts)
        // These are typically day, month, year parts like "28", "03", "2021"
        if (token.length in 2..4 && token.all { it.isDigit() }) {
            val num = token.toIntOrNull() ?: return token
            return when {
                // Day (1-31)
                token.length == 2 && num in 1..31 -> {
                    String.format("%02d", random.nextInt(1, 29))
                }
                // Month (1-12)
                token.length == 2 && num in 1..12 -> {
                    String.format("%02d", random.nextInt(1, 13))
                }
                // Year (2019-2026)
                token.length == 4 && num in 2015..2030 -> {
                    random.nextInt(2019, 2025).toString()
                }
                // Time components (hours, minutes, seconds)
                token.length == 2 && num in 0..59 -> {
                    String.format("%02d", random.nextInt(0, 60))
                }
                else -> token // Keep other short digit sequences
            }
        }

        // Mixed tokens with digits (e.g., "109030305216_payt"):
        // replace digits, keep letters
        if (digitCount > 3) {
            return token.map { ch ->
                if (ch.isDigit()) random.nextInt(0, 10).digitToChar() else ch
            }.joinToString("")
        }

        return token
    }

    /**
     * Reconstruct the full SMS text from anonymized tokens.
     *
     * Uses the original text as a template: for each original token position,
     * replaces it with the corresponding anonymized token to preserve whitespace
     * and punctuation between tokens.
     */
    private fun reconstructText(
        originalText: String,
        originalTokens: List<String>,
        newTokens: List<String>
    ): String {
        var result = originalText
        var searchFrom = 0

        for (i in originalTokens.indices) {
            val oldToken = originalTokens[i]
            val newToken = newTokens[i]

            if (oldToken == newToken) {
                // No change — just advance past this token
                val idx = result.indexOf(oldToken, searchFrom)
                if (idx != -1) {
                    searchFrom = idx + oldToken.length
                }
                continue
            }

            // Find the old token in the text and replace it
            val idx = result.indexOf(oldToken, searchFrom)
            if (idx != -1) {
                result = result.substring(0, idx) + newToken + result.substring(idx + oldToken.length)
                searchFrom = idx + newToken.length
            }
        }

        return result
    }
}
