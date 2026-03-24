package io.pm.finlight.analyzer


/**
 * MOCK Desktop equivalent of SmsClassifier.
 * 
 * TFLite native bindings are not loading correctly in the pure JVM environment.
 * For Phase 3 Verification (UI & Regex Testing), we mock this classifier to 
 * return random scores, allowing the app to build and run.
 * 
 * TODO: Integrate Native TFLite lib properly in Phase 5.
 */
class DesktopSmsClassifier {

    // Simple Heuristic Keywords for Desktop Testing
    private val spamKeywords = listOf("otp", "code", "login", "auth", "verif", "pack", "expire", "recharge", "offer")
    private val txKeywords = listOf("spent", "debited", "charged", "paid", "sent", "withdrawal", "txn", "acct", "inr", "rs.", "usd")

    init {
        println("DesktopSmsClassifier: Using Heuristic Mode (Smart Mock) for Desktop.")
    }

    fun classify(text: String): Float {
        val lower = text.lowercase()
        
        // 1. Strong SPAM Signals
        if (spamKeywords.any { lower.contains(it) }) {
            return 0.05f + (text.length % 10) / 100f // ~0.05 - 0.15
        }
        
        // 2. Strong TRANSACTION Signals
        var score = 0.5f
        if (txKeywords.any { lower.contains(it) }) {
            score += 0.4f // Boost to 0.9
        }
        
        // 3. Regex Pattern Checks
        // Matches currency-like patterns: Rs. 100, INR 500, $50.00
        val currencyRegex = Regex("""(?i)(rs\.?|inr|usd|\$)\s*[\d,]+(\.\d{1,2})?""")
        if (currencyRegex.containsMatchIn(lower)) {
            score += 0.09f
        }

        // Cap at 0.99
        return score.coerceIn(0.01f, 0.99f)
    }
}
