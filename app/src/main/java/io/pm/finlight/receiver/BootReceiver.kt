package io.pm.finlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A BroadcastReceiver that listens for the device boot completion event.
 * Its purpose is to re-schedule all necessary background workers (like daily,
 * weekly, and monthly reports) to ensure they persist across device reboots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed. Delegating to ReminderManager to reschedule all work.")
            val pendingResult = goAsync()
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
            CoroutineScope(dispatcherProvider.io).launch {
                try {
                    ReminderManager.rescheduleAllWork(context)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
