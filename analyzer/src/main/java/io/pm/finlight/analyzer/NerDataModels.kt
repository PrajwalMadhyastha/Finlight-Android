package io.pm.finlight.analyzer

import kotlinx.serialization.Serializable

// --- INPUT: From Classifier Tool's "Export Verified (NER)" ---
@Serializable
data class VerifiedTransactionItem(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isTransaction: Boolean? = null,
    val confidence: Float = 0.0f,
    val silverLabel: SerializablePotentialTransaction? = null,
    val ignoreReason: String? = null
)

// --- WORKING MODEL: NER Labeling State ---
data class NerLabelItem(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val tokens: List<String> = emptyList(),
    var labels: NerLabels? = null,
    var parserSuggestions: ParserSuggestions? = null
)

@Serializable
data class NerLabels(
    val merchantTokenIndices: List<Int> = emptyList(),
    val amountTokenIndices: List<Int> = emptyList(),
    val accountTokenIndices: List<Int> = emptyList(),
    val balanceTokenIndices: List<Int> = emptyList()
)

data class ParserSuggestions(
    val merchantText: String? = null,
    val amountText: String? = null,
    val currencyCode: String? = null,
    val accountText: String? = null,
    val balanceText: String? = null
)

// --- PERSISTENCE: Serializable versions for workspace files ---
@Serializable
data class SerializableNerLabelItem(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val tokens: List<String> = emptyList(),
    val labels: SerializableNerLabels? = null
)

@Serializable
data class SerializableNerLabels(
    val merchantTokenIndices: List<Int> = emptyList(),
    val amountTokenIndices: List<Int> = emptyList(),
    val accountTokenIndices: List<Int> = emptyList(),
    val balanceTokenIndices: List<Int> = emptyList()
)

@Serializable
data class NerWorkspace(
    val items: List<SerializableNerLabelItem>,
    val patternLibrary: PatternLibrary = PatternLibrary()
)

// --- EXPORT: NER Training Data Format ---
@Serializable
data class NerTrainingItem(
    val id: String,
    val text: String,
    val tokens: List<String>,
    val entities: List<NerEntity>
)

@Serializable
data class NerEntity(
    val type: String,  // "MERCHANT", "AMOUNT", "ACCOUNT"
    val tokenIndices: List<Int>,
    val text: String
)

@Serializable
data class NerCompletenessMetadata(
    val hasMerchant: Boolean,
    val hasAmount: Boolean,
    val hasAccount: Boolean,
    val hasBalance: Boolean,
    val qualityScore: Float  // 0.0 to 1.0 (percentage of entities present)
)

@Serializable
data class NerTrainingItemWithMetadata(
    val id: String,
    val text: String,
    val tokens: List<String>,
    val entities: List<NerEntity>,
    val completeness: NerCompletenessMetadata
)

// Export filter options
enum class ExportFilter {
    ALL,              // Any entity labeled
    WITH_AMOUNT,      // Must have amount
    HIGH_QUALITY,     // At least 2 entities
    COMPLETE          // All 3 entities
}

// --- MAPPERS ---
fun NerLabelItem.toSerializable() = SerializableNerLabelItem(
    id = id,
    sender = sender,
    body = body,
    timestamp = timestamp,
    tokens = tokens,
    labels = labels?.let {
        SerializableNerLabels(
            merchantTokenIndices = it.merchantTokenIndices,
            amountTokenIndices = it.amountTokenIndices,
            accountTokenIndices = it.accountTokenIndices,
            balanceTokenIndices = it.balanceTokenIndices
        )
    }
)

fun SerializableNerLabelItem.toDomain() = NerLabelItem(
    id = id,
    sender = sender,
    body = body,
    timestamp = timestamp,
    tokens = tokens,
    labels = labels?.let {
        NerLabels(
            merchantTokenIndices = it.merchantTokenIndices,
            amountTokenIndices = it.amountTokenIndices,
            accountTokenIndices = it.accountTokenIndices,
            balanceTokenIndices = it.balanceTokenIndices
        )
    }
)

// --- UTILITY: Character Type for Tokenization ---
private enum class CharType {
    DIGIT,          // 0-9
    LETTER,         // a-z, A-Z
    NUMBER_PUNCT,   // , or . (when part of numbers)
    SEPARATOR       // -, /, :, and other punctuation
}

// --- UTILITY: Smart Tokenization ---
fun tokenize(text: String): List<String> {
    val tokens = mutableListOf<String>()
    
    // First split on whitespace
    val words = text.split(Regex("\\s+"))
    
    for (word in words) {
        if (word.isBlank()) continue
        
        // Check if word contains mixed content (e.g., "1,00,00.00-SBI")
        val subTokens = splitMixedToken(word)
        tokens.addAll(subTokens)
    }
    
    return tokens.filter { it.isNotBlank() }
}

private fun splitMixedToken(token: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var lastCharType: CharType? = null
    
    for (i in token.indices) {
        val char = token[i]
        val charType = classifyChar(char, token, i)
        
        // Determine if we should split before this character
        if (lastCharType != null && shouldSplit(lastCharType, charType, current.toString(), char)) {
            if (current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
        }
        
        // Add character to current token or start new token
        when (charType) {
            CharType.SEPARATOR -> {
                // Flush current token and skip separator
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
            }
            else -> {
                current.append(char)
            }
        }
        
        // Update last type (but don't let separators change it)
        if (charType != CharType.SEPARATOR) {
            lastCharType = charType
        }
    }
    
    // Add final token
    if (current.isNotEmpty()) {
        result.add(current.toString())
    }
    
    return result.ifEmpty { listOf(token) }
}

private fun classifyChar(char: Char, token: String, index: Int): CharType {
    return when {
        char.isDigit() -> CharType.DIGIT
        char.isLetter() || char == '₹' -> CharType.LETTER
        char == '/' -> {
            // Keep slash as part of abbreviations like "a/c" (account)
            val prevIsLetter = index > 0 && token[index - 1].isLetter()
            val nextIsLetter = index < token.length - 1 && token[index + 1].isLetter()
            if (prevIsLetter && nextIsLetter) CharType.LETTER else CharType.SEPARATOR
        }
        char in listOf(',', '.') -> {
            // Check if comma/period is part of a number
            val prevIsDigit = index > 0 && token[index - 1].isDigit()
            val nextIsDigit = index < token.length - 1 && token[index + 1].isDigit()
            if (prevIsDigit || nextIsDigit) CharType.NUMBER_PUNCT else CharType.SEPARATOR
        }
        else -> CharType.SEPARATOR  // -, :, etc.
    }
}

private fun shouldSplit(lastType: CharType, currentType: CharType, current: String, char: Char): Boolean {
    return when {
        // Keep commas and periods within numbers
        currentType == CharType.NUMBER_PUNCT && lastType == CharType.DIGIT -> false
        lastType == CharType.NUMBER_PUNCT && currentType == CharType.DIGIT -> false
        
        // Split between numbers and letters (e.g., "1742SBI" -> "1742", "SBI")
        lastType == CharType.DIGIT && currentType == CharType.LETTER -> true
        lastType == CharType.LETTER && currentType == CharType.DIGIT -> true
        
        // Keep number punctuation together with digits
        lastType == CharType.DIGIT && currentType == CharType.NUMBER_PUNCT -> false
        lastType == CharType.NUMBER_PUNCT && currentType == CharType.DIGIT -> false
        
        else -> false
    }
}
