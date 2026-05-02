package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_images",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // --- FIX: Explicitly declare the index that Room creates for the foreign key ---
    indices = [Index(value = ["transactionId"])],
)
data class TransactionImage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val transactionId: Int,
    val imageUri: String, // Stores the URI of the image in the app's internal storage
)
