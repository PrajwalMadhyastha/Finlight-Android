package io.pm.finlight.analyzer

import kotlinx.serialization.Serializable

/**
 * Pattern Learning System for NER Labeler - Redesigned
 * 
 * Uses two strategies:
 * 1. Exact text matching for merchants and accounts (stable entities)
 * 2. Context-based matching for amounts and balances (variable values)
 */

sealed class EntityPattern {
    // For merchants and accounts - exact text matching
    @Serializable
    data class ExactTextPattern(
        val text: String,              // The exact text that was labeled
        val normalizedText: String,    // Normalized version for matching
        val entityType: String,        // "MERCHANT" or "ACCOUNT"
        val frequency: Int = 1,        // How many times this pattern was seen
        val confidence: Float = 1.0f   // Confidence score (0.0 - 1.0)
    ) : EntityPattern()
    
    // For amounts and balances - positional/contextual
    @Serializable
    data class ContextPattern(
        val entityType: String,                    // "AMOUNT" or "BALANCE"
        val precedingTokens: List<String> = emptyList(),  // Normalized tokens before (e.g., ["bal", "rs"])
        val followingTokens: List<String> = emptyList(),  // Normalized tokens after (e.g., ["cr"])
        val frequency: Int = 1,
        val confidence: Float = 1.0f
    ) : EntityPattern()

    // For strict structural matching (Template Learning)
    @Serializable
    data class TemplatePattern(
        val templateHash: String,              // Unique hash of the masked template
        val templateString: String,            // Readable template string (for debugging/UI)
        val schema: NerLabels,                 // The exact label indices to apply
        val frequency: Int = 1,
        val lastSeen: Long = 0L
    ) : EntityPattern()
}

@Serializable
data class PatternLibrary(
    val merchantPatterns: MutableList<EntityPattern.ExactTextPattern> = mutableListOf(),
    val merchantContextPatterns: MutableList<EntityPattern.ContextPattern> = mutableListOf(),
    val accountPatterns: MutableList<EntityPattern.ExactTextPattern> = mutableListOf(),
    val accountContextPatterns: MutableList<EntityPattern.ContextPattern> = mutableListOf(),
    val amountContextPatterns: MutableList<EntityPattern.ContextPattern> = mutableListOf(),
    val balanceContextPatterns: MutableList<EntityPattern.ContextPattern> = mutableListOf(),
    // NEW: Strict Template Patterns
    val templatePatterns: MutableList<EntityPattern.TemplatePattern> = mutableListOf()
)

class PatternLearner {
    
    companion object {
        // Configuration constants
        private const val CONFIDENCE_PENALTY = 0.2f  // Decrease per removal
        private const val REMOVAL_THRESHOLD = 0.3f   // Delete pattern if confidence drops below this
        private const val CONFIDENCE_BOOST = 0.1f    // Increase per positive label
    }
    
    /**
     * EXTRACT TEMPLATE: Mask variable tokens to create a structural fingerprint
     */
    private fun extractTemplate(tokens: List<String>): String {
        return tokens.joinToString(" ") { token ->
            when {
                // Ignore separators in template (they are structural)
                token.all { !it.isLetterOrDigit() } -> token
                
                // Mask Numbers (Amounts, balances, OTPs)
                token.matches(Regex(".*\\d.*")) -> "<NUM>"
                
                // Mask Dates (DD/MM/YYYY, etc)
                token.matches(Regex("\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}")) -> "<DATE>"
                
                // Mask Times (HH:MM:SS)
                token.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?")) -> "<TIME>"

                // Universal Structural Matching: Mask ALL words
                // This makes "To P PADMANABHA" (structure: <WORD> <WORD> <WORD>) equal "To SOMEONE ELSE" (structure: <WORD> <WORD> <WORD>)
                // This resolves the user's issue with variable payee names
                token.all { it.isLetter() } -> "<WORD>"
                
                // Keep separators and mixed alphanumeric-symbol tokens as anchors
                // e.g., "A/C" (might have /), "13-Mar-22", "UPI/123"
                else -> token.lowercase()
            }
        }
    }
    
    // Hash key generation
    private fun hashTemplate(template: String): String {
        return java.security.MessageDigest.getInstance("MD5")
            .digest(template.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    private fun normalizeForMatching(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Learn from a labeled item
     */
    fun learnFromLabels(
        item: NerLabelItem,
        library: PatternLibrary
    ): PatternLibrary {
        val labels = item.labels ?: return library
        
        println("=== LEARNING FROM LABELS ===")
        println("tokens: ${item.tokens}")
        val templateString = extractTemplate(item.tokens)
        val templateHash = hashTemplate(templateString)
        println("Template: $templateString")
        println("Hash: $templateHash")

        // 1. STRATEGIC LEARNING: Template Pattern (Highest Priority)
        // Only learn if there is at least one entity
        val hasAnyEntity = labels.merchantTokenIndices.isNotEmpty() || 
                           labels.amountTokenIndices.isNotEmpty() || 
                           labels.accountTokenIndices.isNotEmpty() ||
                           labels.balanceTokenIndices.isNotEmpty()

        if (hasAnyEntity) {
            val templatePattern = EntityPattern.TemplatePattern(
                templateHash = templateHash,
                templateString = templateString,
                schema = labels, // Save the EXACT indices
                frequency = 1,
                lastSeen = System.currentTimeMillis()
            )
            addOrUpdateTemplatePattern(library.templatePatterns, templatePattern)
            println("Learned Template Pattern!")
        }

        // 2. BACKUP LEARNING: Context Patterns (Old Logic - lower priority)
        // (Keeping this for robustness when strict templates fail)
        
        // Learn merchant pattern
        if (labels.merchantTokenIndices.isNotEmpty()) {
            val exactPattern = extractExactTextPattern(item.tokens, labels.merchantTokenIndices, "MERCHANT")
            if (exactPattern != null) {
                 addOrUpdateExactPattern(library.merchantPatterns, exactPattern)
            } else {
                val contextPattern = extractContextPattern(item.tokens, labels.merchantTokenIndices, "MERCHANT")
                if (contextPattern != null) addOrUpdateContextPattern(library.merchantContextPatterns, contextPattern)
            }
        }
        
        // Learn account pattern
        if (labels.accountTokenIndices.isNotEmpty()) {
            val exactPattern = extractExactTextPattern(item.tokens, labels.accountTokenIndices, "ACCOUNT")
            if (exactPattern != null) {
                addOrUpdateExactPattern(library.accountPatterns, exactPattern)
            } else {
                val contextPattern = extractContextPattern(item.tokens, labels.accountTokenIndices, "ACCOUNT")
                if (contextPattern != null) addOrUpdateContextPattern(library.accountContextPatterns, contextPattern)
            }
        }
        
        // Learn amount pattern
        if (labels.amountTokenIndices.isNotEmpty()) {
            val pattern = extractContextPattern(item.tokens, labels.amountTokenIndices, "AMOUNT")
            if (pattern != null) addOrUpdateContextPattern(library.amountContextPatterns, pattern)
        }
        
        // Learn balance pattern
        if (labels.balanceTokenIndices.isNotEmpty()) {
            val pattern = extractContextPattern(item.tokens, labels.balanceTokenIndices, "BALANCE")
            if (pattern != null) addOrUpdateContextPattern(library.balanceContextPatterns, pattern)
        }
        
        return library
    }

    /**
     * Find matching patterns in the library for given tokens
     */
    fun findMatchingPatterns(
        tokens: List<String>,
        library: PatternLibrary
    ): Quad<List<Int>?, List<Int>?, List<Int>?, List<Int>?> {
        
        // 1. Try Template Match (Highest Priority)
        val templateString = extractTemplate(tokens)
        val templateHash = hashTemplate(templateString)
        
        val templateMatch = library.templatePatterns.find { it.templateHash == templateHash }
        
        if (templateMatch != null) {
            println("!!! TEMPLATE MATCH FOUND (EXACT) !!!")
            println("Template: ${templateMatch.templateString}")
            return Quad(
                templateMatch.schema.merchantTokenIndices.ifEmpty { null },
                templateMatch.schema.amountTokenIndices.ifEmpty { null },
                templateMatch.schema.accountTokenIndices.ifEmpty { null },
                templateMatch.schema.balanceTokenIndices.ifEmpty { null }
            )
        }

        // 1.5 Fuzzy Template Match (Structure Alignment)
        // If exact hash failed, maybe the structure is SLIGHTLY different (e.g. name length 1 vs 3)
        // We scan all templates and try to align them.
        println("No exact template match. Trying fuzzy alignment...")
        
        val inputTemplateTokens = templateString.split(" ")
        
        // Find best fuzzy match that exceeds threshold
        var bestMatch: EntityPattern.TemplatePattern? = null
        var bestMapping: Map<Int, Int>? = null
        var bestScore = 0.0
        
        for (pattern in library.templatePatterns) {
            val patternTokens = pattern.templateString.split(" ")
            // Heuristic: If sizes differ wildly (>50%), skip
            if (kotlin.math.abs(patternTokens.size - inputTemplateTokens.size) > patternTokens.size / 2) continue
            
            // Align
            val alignment = SequenceAligner.align(patternTokens, inputTemplateTokens)
            
            // Check if this is a strong match
            // Score > 0.7 means 70% of template tokens found in input in order
            if (alignment.score > 0.7 && alignment.score > bestScore) {
                bestScore = alignment.score
                bestMatch = pattern
                bestMapping = alignment.mapping
            }
        }
        
        if (bestMatch != null && bestMapping != null) {
            println("!!! TEMPLATE MATCH FOUND (FUZZY) !!!")
            println("Score: $bestScore")
            println("Template: ${bestMatch.templateString}")
            
            // Map the indices
            fun mapIndices(indices: List<Int>): List<Int>? {
                val mapped = indices.mapNotNull { bestMapping!![it] }
                // Only return if we found ALL critical tokens? 
                // Or partials? Usually we want all or high %?
                // For safety: if we mapped at least 1 token, return it.
                // But if amount was 2 tokens and we only mapped 1, that's bad.
                // Strict: Must map ALL indices for that entity.
                if (mapped.size != indices.size) return null
                return mapped
            }

            return Quad(
                mapIndices(bestMatch.schema.merchantTokenIndices),
                mapIndices(bestMatch.schema.amountTokenIndices),
                mapIndices(bestMatch.schema.accountTokenIndices),
                mapIndices(bestMatch.schema.balanceTokenIndices)
            )
        }
        
        // 2. Fallback to Context/Exact Match (Lower Priority)
        println("No fuzzy template match. Falling back to context matching...")
        
        var merchantIndices = findExactTextMatch(tokens, library.merchantPatterns)
        if (merchantIndices == null) merchantIndices = findContextMatch(tokens, library.merchantContextPatterns)
        
        var accountIndices = findExactTextMatch(tokens, library.accountPatterns)
        if (accountIndices == null) accountIndices = findContextMatch(tokens, library.accountContextPatterns)
        
        val amountIndices = findContextMatch(tokens, library.amountContextPatterns)
        val balanceIndices = findContextMatch(tokens, library.balanceContextPatterns)
        
        return Quad(merchantIndices, amountIndices, accountIndices, balanceIndices)
    }

    // --- HELPER FUNCTIONS (UNCHANGED BUT REQUIRED) ---

    private fun extractExactTextPattern(tokens: List<String>, indices: List<Int>, entityType: String): EntityPattern.ExactTextPattern? {
        if (indices.isEmpty()) return null
        val text = indices.joinToString(" ") { tokens[it] }
        val normalizedText = normalizeForMatching(text)
        if (entityType == "MERCHANT" && text.matches(Regex(".*\\d{5,}.*"))) return null
        if (entityType == "ACCOUNT" && text.matches(Regex(".*\\d{3,}.*"))) return null
        return EntityPattern.ExactTextPattern(text, normalizedText, entityType)
    }
    
    private fun extractContextPattern(tokens: List<String>, indices: List<Int>, entityType: String): EntityPattern.ContextPattern? {
        if (indices.isEmpty()) return null
        val targetIndex = indices.first()
        val precedingTokens = mutableListOf<String>()
        if (targetIndex > 0) precedingTokens.add(normalizeForMatching(tokens[targetIndex - 1]))
        if (targetIndex > 1) precedingTokens.add(0, normalizeForMatching(tokens[targetIndex - 2]))
        val lastIndex = indices.last()
        val followingTokens = mutableListOf<String>()
        if (lastIndex < tokens.size - 1) followingTokens.add(normalizeForMatching(tokens[lastIndex + 1]))
        if (lastIndex < tokens.size - 2) followingTokens.add(normalizeForMatching(tokens[lastIndex + 2]))
        return EntityPattern.ContextPattern(entityType, precedingTokens, followingTokens)
    }

    private fun addOrUpdateTemplatePattern(patterns: MutableList<EntityPattern.TemplatePattern>, newPattern: EntityPattern.TemplatePattern) {
        val existing = patterns.find { it.templateHash == newPattern.templateHash }
        if (existing != null) {
            val index = patterns.indexOf(existing)
            // Update to latest schema (user might have corrected it)
            patterns[index] = existing.copy(
                schema = newPattern.schema, // Always trust the latest label
                frequency = existing.frequency + 1,
                lastSeen = System.currentTimeMillis()
            )
        } else {
            patterns.add(newPattern)
        }
    }

    private fun addOrUpdateExactPattern(patterns: MutableList<EntityPattern.ExactTextPattern>, newPattern: EntityPattern.ExactTextPattern) {
        val existing = patterns.find { it.normalizedText == newPattern.normalizedText }
        if (existing != null) {
            val index = patterns.indexOf(existing)
            patterns[index] = existing.copy(frequency = existing.frequency + 1, confidence = minOf(1.0f, existing.confidence + CONFIDENCE_BOOST))
        } else {
            patterns.add(newPattern)
        }
    }
    
    private fun addOrUpdateContextPattern(patterns: MutableList<EntityPattern.ContextPattern>, newPattern: EntityPattern.ContextPattern) {
        val existing = patterns.find { it.precedingTokens == newPattern.precedingTokens && it.followingTokens == newPattern.followingTokens }
        if (existing != null) {
            val index = patterns.indexOf(existing)
            patterns[index] = existing.copy(frequency = existing.frequency + 1, confidence = minOf(1.0f, existing.confidence + CONFIDENCE_BOOST))
        } else {
            patterns.add(newPattern)
        }
    }

    private fun findExactTextMatch(tokens: List<String>, patterns: List<EntityPattern.ExactTextPattern>): List<Int>? {
        val sortedPatterns = patterns.sortedByDescending { it.confidence * it.frequency }
        for (pattern in sortedPatterns) {
            val patternTokens = pattern.text.split(" ")
            for (i in 0..(tokens.size - patternTokens.size)) {
                var match = true
                for (j in patternTokens.indices) {
                    if (!tokensMatch(tokens[i + j], patternTokens[j])) {
                        match = false; break
                    }
                }
                if (match) return (i until i + patternTokens.size).toList()
            }
        }
        return null
    }
    
    private fun findContextMatch(tokens: List<String>, patterns: List<EntityPattern.ContextPattern>): List<Int>? {
        val sortedPatterns = patterns.sortedByDescending { it.confidence * it.frequency }
        for (pattern in sortedPatterns) {
            for (i in tokens.indices) {
                if ((pattern.entityType in listOf("AMOUNT", "BALANCE")) && !tokens[i].matches(Regex(".*[0-9].*"))) continue
                val precedingMatch = pattern.precedingTokens.isEmpty() || pattern.precedingTokens.any { pt -> (i > 0 && normalizeForMatching(tokens[i-1]).contains(pt)) || (i > 1 && normalizeForMatching(tokens[i-2]).contains(pt)) }
                val followingMatch = pattern.followingTokens.isEmpty() || pattern.followingTokens.any { ft -> (i < tokens.size - 1 && normalizeForMatching(tokens[i+1]).contains(ft)) || (i < tokens.size - 2 && normalizeForMatching(tokens[i+2]).contains(ft)) }
                if (precedingMatch && followingMatch) return listOf(i)
            }
        }
        return null
    }

    private fun tokensMatch(token1: String, token2: String): Boolean = normalizeForMatching(token1) == normalizeForMatching(token2)

    // Unlearn methods (kept simple for template - just ignore)
    fun unlearnFromRemovals(item: NerLabelItem, prev: NerLabels?, curr: NerLabels?, library: PatternLibrary): PatternLibrary = library
}
