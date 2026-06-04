// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/receiver/SmsReceiver.kt
// REASON: REFACTOR (Bug Fix) — The receiver is now a thin dispatcher.
// All heavy processing (ML inference, NER, parsing, DB writes) has been moved
// to SmsProcessorWorker, which runs under WorkManager with a 10-minute
// guaranteed execution window. This eliminates the risk of the Android OS
// killing the receiver mid-work (Doze mode, memory pressure) before the
// transaction is saved.
//
// The receiver's only job is now to extract the raw SMS data from the Intent
// and immediately enqueue a SmsProcessorWorker for each message. goAsync()
// is no longer needed.
// =================================================================================
package io.pm.finlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.pm.finlight.workers.SmsProcessorWorker

class SmsReceiver : BroadcastReceiver() {
    private val tag = "SmsReceiver"

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val messagesBySender = messages.groupBy { it.originatingAddress }

        for ((sender, parts) in messagesBySender) {
            if (sender == null) continue
            val body = parts.joinToString("") { it.messageBody }
            val date = parts.first().timestampMillis

            Log.d(tag, "SMS received from $sender. Enqueuing SmsProcessorWorker.")

            val workRequest =
                OneTimeWorkRequestBuilder<SmsProcessorWorker>()
                    .setInputData(
                        workDataOf(
                            SmsProcessorWorker.KEY_SENDER to sender,
                            SmsProcessorWorker.KEY_BODY to body,
                            SmsProcessorWorker.KEY_DATE to date,
                        ),
                    )
                    .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
