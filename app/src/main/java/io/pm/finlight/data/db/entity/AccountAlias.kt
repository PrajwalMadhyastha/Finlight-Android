// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/AccountAlias.kt
// REASON: FEATURE (Backup Phase 2) - Added the @Serializable annotation. This is
// required by the kotlinx.serialization library to include this entity's data
// in the JSON backup snapshot.
// =================================================================================
package io.pm.finlight.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.pm.finlight.Account
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "account_aliases",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["destinationAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["destinationAccountId"])]
)
data class AccountAlias(
    @PrimaryKey
    val aliasName: String, // e.g., "ICICI - xx1234"
    val destinationAccountId: Int // e.g., The ID of the "ICICI Bank" account
)