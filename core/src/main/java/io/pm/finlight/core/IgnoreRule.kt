package io.pm.finlight

/**
 * Enum to define the type of content an IgnoreRule should match against.
 */
enum class RuleType {
    SENDER,
    BODY_PHRASE
}

/**
 * Represents a user-defined rule to ignore an SMS.
 * This is a plain data class, free of Android/Room annotations,
 * so it can be used in the pure Kotlin 'core' module.
 *
 * @param id The unique identifier for the rule.
 * @param type The type of rule (SENDER or BODY_PHRASE).
 * @param pattern The text pattern to match against (e.g., "*JioBal*" for sender, "invoice of" for body).
 * @param isEnabled Whether this rule is currently active.
 * @param isDefault True if this is a pre-populated rule, false if user-added.
 */
data class IgnoreRule(
    val id: Int = 0,
    val type: RuleType = RuleType.BODY_PHRASE,
    val pattern: String,
    var isEnabled: Boolean = true,
    val isDefault: Boolean = false
)
