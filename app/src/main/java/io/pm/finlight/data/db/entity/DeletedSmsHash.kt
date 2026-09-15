package io.pm.finlight.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Stores the sourceSmsHash of every SMS-sourced transaction that has been
 * manually deleted by the user.
 *
 * Purpose: prevent SmsCatchupWorker (and SmsProcessorWorker) from
 * re-creating a transaction the user intentionally removed. The hash
 * outlives the transaction row by design — no foreign key needed.
 */
@Serializable
@Entity(tableName = "deleted_sms_hashes")
data class DeletedSmsHash(
    @PrimaryKey
    val smsHash: String,
)
