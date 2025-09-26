// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/TransactionTagCrossRef.kt
// REASON: FEATURE (Backup Phase 2) - Added the @Serializable annotation. This is
// required by the kotlinx.serialization library to include this entity's data
// in the JSON backup snapshot.
// =================================================================================
package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * This is a "join table" to create a many-to-many relationship
 * between the 'transactions' table and the 'tags' table.
 */
@Serializable
@Entity(
    tableName = "transaction_tag_cross_ref",
    primaryKeys = ["transactionId", "tagId"],
    indices = [Index(value = ["tagId"])], // --- FIX: Added the missing index ---
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE // If a transaction is deleted, remove its tag links
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE // If a tag is deleted, remove its links from transactions
        )
    ]
)
data class TransactionTagCrossRef(
    val transactionId: Int,
    val tagId: Int
)