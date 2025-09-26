// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/Goal.kt
// REASON: FEATURE (Backup Phase 2) - Added the @Serializable annotation. This is
// required by the kotlinx.serialization library to include this entity's data
// in the JSON backup snapshot.
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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    var savedAmount: Double,
    val targetDate: Long?,
    val accountId: Int
)