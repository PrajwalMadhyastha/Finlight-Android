// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/SplitTransaction.kt
// REASON: FIX - Added the @Serializable annotation. This is required by the
// kotlinx.serialization library to correctly include this entity in the JSON
// data backup, resolving a build error.
// =================================================================================
package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "split_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["parentTransactionId"],
            onDelete = ForeignKey.CASCADE, // If the parent is deleted, its splits are also deleted.
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL, // If a category is deleted, the split remains but uncategorized.
        ),
    ],
    indices = [
        Index(value = ["parentTransactionId"]),
        Index(value = ["categoryId"]),
    ],
)
data class SplitTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val parentTransactionId: Int,
    val amount: Double, // ALWAYS in home currency
    val categoryId: Int?,
    val notes: String?,
    val originalAmount: Double? = null,
)
