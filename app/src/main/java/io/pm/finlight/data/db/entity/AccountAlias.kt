// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/AccountAlias.kt
// REASON: NEW FILE - This entity creates the "memory" for account merges. It
// stores a mapping from an old, merged account name (the alias) to the ID of
// the account that was kept (the destination).
// =================================================================================
package io.pm.finlight.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.pm.finlight.Account

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