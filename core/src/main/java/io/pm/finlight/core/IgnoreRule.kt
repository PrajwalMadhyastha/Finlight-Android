package io.pm.finlight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Enum to define the type of content an IgnoreRule should match against.
 */
@Serializable
enum class RuleType {
    SENDER,
    BODY_PHRASE
}

/**
 * Represents a user-defined rule to ignore an SMS.
 *
 * @param id The unique identifier for the rule.
 * @param type The type of rule (SENDER or BODY_PHRASE).
 * @param pattern The text pattern to match against (e.g., "*JioBal*" for sender, "invoice of" for body).
 * @param isEnabled Whether this rule is currently active.
 * @param isDefault True if this is a pre-populated rule, false if user-added.
 */
@Serializable
@Entity(
    tableName = "ignore_rules",
    indices = [Index(value = ["pattern"], unique = true, name = "index_ignore_rules_pattern_nocase")]
)
data class IgnoreRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: RuleType = RuleType.BODY_PHRASE,
    @ColumnInfo(name = "pattern", collate = ColumnInfo.NOCASE)
    val pattern: String,
    var isEnabled: Boolean = true,
    val isDefault: Boolean = false
)