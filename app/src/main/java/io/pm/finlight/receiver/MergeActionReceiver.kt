package io.pm.finlight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.TransactionRepository
import io.pm.finlight.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MergeActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action ?: return
        val parentTxnId = intent.getIntExtra("parentTxnId", -1)
        val childTxnId = intent.getIntExtra("childTxnId", -1)
        val notificationId = childTxnId + 10000

        val db = AppDatabase.getInstance(context)
        val transactionRepository =
            TransactionRepository(
                transactionWriteDao = db.transactionWriteDao(),
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                deletedSmsHashDao = db.deletedSmsHashDao(),
                mergeRecordDao = db.mergeRecordDao(),
                db = db,
            )

        CoroutineScope(Dispatchers.IO).launch {
            if (action == "ACTION_MERGE" && parentTxnId != -1 && childTxnId != -1) {
                val childTxn = transactionRepository.getTransactionSync(childTxnId)
                var childSmsBody: String? = null
                var childSmsDate: Long? = null
                if (childTxn?.sourceSmsId != null) {
                    val smsRepository = SmsRepository(context)
                    val sms = smsRepository.getSmsDetailsById(childTxn.sourceSmsId)
                    if (sms != null) {
                        childSmsBody = sms.body
                        childSmsDate = sms.date
                    }
                }
                transactionRepository.mergeTransactions(parentTxnId, childTxnId, childSmsBody, childSmsDate)
            } else if (action == "ACTION_DISMISS" && childTxnId != -1) {
                transactionRepository.dismissMerge(childTxnId)
            }
            with(NotificationManagerCompat.from(context)) {
                cancel(notificationId)
                if (childTxnId != -1) cancel(childTxnId)
            }
        }
    }
}
