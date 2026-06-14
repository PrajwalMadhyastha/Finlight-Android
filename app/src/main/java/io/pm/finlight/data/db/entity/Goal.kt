// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/Goal.kt
// REASON: FEATURE (Issue #104) - Reworked for dynamic progress tracking.
// - `savedAmount` is deprecated (kept for migration compat, defaults to 0.0).
//   Progress is now computed dynamically from linked transactions.
// - Added `notes`, `iconEmoji`, and `priority` fields for personalization
//   and future Waterfall Savings support.
// =================================================================================
package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId"])],
)
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    // Deprecated: progress is now computed from linked transactions.
    // Kept for backward compatibility with existing data.
    var savedAmount: Double = 0.0,
    val targetDate: Long?,
    val accountId: Int,
    val notes: String? = null,
    val iconEmoji: String? = null,
    // Placeholder for future Waterfall Savings priority ordering
    val priority: Int = 0,
)
