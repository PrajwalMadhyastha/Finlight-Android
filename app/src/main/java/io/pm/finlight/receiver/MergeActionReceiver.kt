package io.pm.finlight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.pm.finlight.SmsRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MergeActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        val parentTxnId = intent.getIntExtra("parentTxnId", -1)
        val childTxnId = intent.getIntExtra("childTxnId", -1)
        val notificationId = childTxnId + 10000

        val db = AppDatabase.getInstance(context)
        val dispatcherProvider = io.pm.finlight.di.ServiceLocator.provideDispatcherProvider(context)
        val transactionRepository =
            TransactionRepository(
                transactionWriteDao = db.transactionWriteDao(),
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                db = db,
                dispatcherProvider = dispatcherProvider,
            )
        val mergeTransactionsUseCase =
            io.pm.finlight.domain.usecase.MergeTransactionsUseCase(
                transactionQueryDao = db.transactionQueryDao(),
                transactionWriteDao = db.transactionWriteDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                mergeRecordDao = db.mergeRecordDao(),
                deletedSmsHashDao = db.deletedSmsHashDao(),
                db = db,
            )

        val pendingResult = goAsync()
        CoroutineScope(dispatcherProvider.io).launch {
            try {
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
                    mergeTransactionsUseCase(parentTxnId, childTxnId, childSmsBody, childSmsDate)
                } else if (action == "ACTION_DISMISS" && childTxnId != -1) {
                    transactionRepository.dismissMerge(childTxnId)
                }
                with(NotificationManagerCompat.from(context)) {
                    cancel(notificationId)
                    if (childTxnId != -1) cancel(childTxnId)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
