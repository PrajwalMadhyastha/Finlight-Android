package io.pm.finlight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.pm.finlight.di.ServiceLocator
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

        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
        val transactionRepository = ServiceLocator.provideTransactionRepository(context)
        val mergeTransactionsUseCase = ServiceLocator.provideMergeTransactionsUseCase(context)

        val pendingResult = goAsync()
        CoroutineScope(dispatcherProvider.io).launch {
            try {
                if (action == "ACTION_MERGE" && parentTxnId != -1 && childTxnId != -1) {
                    val childTxn = transactionRepository.getTransactionSync(childTxnId)
                    var childSmsBody: String? = null
                    var childSmsDate: Long? = null
                    if (childTxn?.sourceSmsId != null) {
                        val smsRepository = ServiceLocator.provideSmsRepository(context)
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
