// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/Transaction.kt
// REASON: FIX (Performance) - Added a new database index on the `date` column.
// This is a critical performance optimization that will dramatically speed up all
// date-range queries, such as those used in the Spending Analysis hub, by
// preventing slow full-table scans.
// =================================================================================
package io.pm.finlight

import android.annotation.SuppressLint
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["smsSignature"]),
        Index(value = ["date"]) // --- NEW: Add index for date-based queries ---
    ],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val categoryId: Int?,
    val amount: Double, // ALWAYS in home currency
    val date: Long,
    val accountId: Int,
    val notes: String?,
    val transactionType: String = "expense",
    val sourceSmsId: Long? = null,
    val sourceSmsHash: String? = null,
    val source: String = "Manual Entry",
    val originalDescription: String? = null,
    val isExcluded: Boolean = false,
    val smsSignature: String? = null,
    val originalAmount: Double? = null,
    val currencyCode: String? = null,
    val conversionRate: Double? = null,
    // --- NEW: Flag to indicate this is a parent transaction ---
    val isSplit: Boolean = false
)